package com.ballisticfx.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Integrates one flight for a given bore angle (RK4, point-mass model).
 *
 * <p>Frame: x = horizontal distance toward the target, y = up, z = right; origin is the muzzle.
 * Forces: drag (standard drag function scaled by the ballistic coefficient, using local Mach number and
 * local air density), gravity, wind (as a moving air mass), and Coriolis.
 * Air density and speed of sound follow the standard lapse rate with height above the muzzle.
 */
final class ShotSimulator {
    /** Returned by {@link #miss} when the bullet never reaches the target plane. */
    static final double NOT_REACHED = -1e9;

    private static final double MIN_SPEED_MPS = 30.0;
    private static final double LAPSE_K_PER_M = 0.0065;
    /** g / (R * L) - 1 for the ISA troposphere: density ~ (T/T0)^4.2559. */
    private static final double DENSITY_EXPONENT = 4.2559;
    /**
     * Drag constant: (pi/4) * (1 in)^2 / (1 lb), the area-to-mass ratio of the reference projectile whose
     * ballistic coefficient is 1 lb/in^2. Deceleration = 0.5 * rho * Cd * v^2 * DRAG_CONSTANT / BC.
     */
    private static final double DRAG_CONSTANT = Math.PI / 4.0 * 0.0254 * 0.0254 / 0.45359237;

    record Sample(double t, double x, double y, double z, double vx, double vy, double vz) {
        double speed() {
            return Math.sqrt(vx * vx + vy * vy + vz * vz);
        }
    }

    /** {@code end} is null if the bullet stalled or timed out before reaching the target plane. */
    record FlightResult(Sample end, List<Sample> samples) {
        boolean reached() {
            return end != null;
        }
    }

    private final Projectile projectile;
    private final ShotGeometry geometry;
    private final SolverOptions options;

    private final double muzzleVelocity;
    private final double dragFactor;      // 0.5 * DRAG_CONSTANT / BC
    private final DragModel dragModel;
    private final double rho;
    private final double soundSpeed;
    private final double tempK;
    private final double windX;           // air velocity in the shot frame (m/s)
    private final double windZ;
    private final double sinAz;
    private final double cosAz;
    private final double omegaNorth;
    private final double omegaUp;
    private final double gyroscopicStability;
    private final double spinDriftCoefficientM;

    private final double[] k1 = new double[6];
    private final double[] k2 = new double[6];
    private final double[] k3 = new double[6];
    private final double[] k4 = new double[6];
    private final double[] tmp = new double[6];

    ShotSimulator(Projectile projectile, Rifle rifle, Atmosphere atmosphere, Wind wind,
                  ShotGeometry geometry, SolverOptions options) {
        this.projectile = projectile;
        this.geometry = geometry;
        this.options = options;

        this.muzzleVelocity = projectile.muzzleVelocityAt(atmosphere.temperatureC());
        this.dragModel = projectile.dragModel();
        this.dragFactor = 0.5 * DRAG_CONSTANT / projectile.ballisticCoefficient();
        this.rho = atmosphere.densityKgM3();
        this.soundSpeed = atmosphere.speedOfSoundMps();
        this.tempK = atmosphere.temperatureKelvin();

        double azimuth = Math.toRadians(geometry.azimuthDegrees());
        this.sinAz = Math.sin(azimuth);
        this.cosAz = Math.cos(azimuth);
        double latitude = Math.toRadians(geometry.latitudeDegrees());
        this.omegaNorth = Units.EARTH_ANGULAR_VELOCITY * Math.cos(latitude);
        this.omegaUp = Units.EARTH_ANGULAR_VELOCITY * Math.sin(latitude);

        // The air moves TOWARD (from + 180 deg). Relative to the line of fire that is -cos / -sin of (from - azimuth).
        double rel = Math.toRadians(wind.fromDegrees()) - azimuth;
        this.windX = -wind.speedMps() * Math.cos(rel);
        this.windZ = -wind.speedMps() * Math.sin(rel);

        this.gyroscopicStability = projectile.gyroscopicStability(rifle.twistInchesPerTurn(), muzzleVelocity, atmosphere);
        // Litz: spin drift (inches) = 1.25 * (Sg + 1.2) * t^1.83
        this.spinDriftCoefficientM = !options.spinDrift() ? 0.0
                : (rifle.rightHandTwist() ? 1.0 : -1.0) * 1.25 * (gyroscopicStability + 1.2) * Units.INCH_TO_M;
    }

    double gyroscopicStability() {
        return gyroscopicStability;
    }

    double muzzleVelocity() {
        return muzzleVelocity;
    }

    ShotGeometry geometry() {
        return geometry;
    }

    /** Sideways displacement caused by gyroscopic spin drift after {@code t} seconds. */
    double spinDriftM(double t) {
        return spinDriftCoefficientM * Math.pow(t, 1.83);
    }

    /** Mach number of the bullet relative to the surrounding air, using the local speed of sound. */
    double machAt(Sample s) {
        double rx = s.vx() - windX;
        double rz = s.vz() - windZ;
        double speed = Math.sqrt(rx * rx + s.vy() * s.vy() + rz * rz);
        return speed / localSoundSpeed(s.y());
    }

    private double localSoundSpeed(double y) {
        return soundSpeed * Math.sqrt(heightFactor(y));
    }

    private double heightFactor(double y) {
        double f = 1.0 - LAPSE_K_PER_M * y / tempK;
        return Math.max(f, 0.2); // only reachable for absurd heights; keeps pow/sqrt real
    }

    private void derivative(double[] s, double[] d) {
        double vx = s[3];
        double vy = s[4];
        double vz = s[5];
        double rx = vx - windX;
        double rz = vz - windZ;
        double speed = Math.sqrt(rx * rx + vy * vy + rz * rz);

        double f = heightFactor(s[1]);
        double density = rho * Math.pow(f, DENSITY_EXPONENT);
        double mach = speed / (soundSpeed * Math.sqrt(f));
        double k = dragFactor * density * dragModel.cd(mach) * speed;

        double ax = -k * rx;
        double ay = -k * vy - Units.STANDARD_GRAVITY;
        double az = -k * rz;

        if (options.coriolis()) {
            // a = -2 (Omega x v), evaluated in East-North-Up and rotated back into the shot frame.
            double east = vx * sinAz + vz * cosAz;
            double north = vx * cosAz - vz * sinAz;
            double up = vy;
            double ae = -2.0 * (omegaNorth * up - omegaUp * north);
            double an = -2.0 * (omegaUp * east);
            double au = -2.0 * (-omegaNorth * east);
            ax += ae * sinAz + an * cosAz;
            ay += au;
            az += ae * cosAz - an * sinAz;
        }

        d[0] = vx;
        d[1] = vy;
        d[2] = vz;
        d[3] = ax;
        d[4] = ay;
        d[5] = az;
    }

    private void step(double[] s, double dt) {
        derivative(s, k1);
        for (int i = 0; i < 6; i++) tmp[i] = s[i] + 0.5 * dt * k1[i];
        derivative(tmp, k2);
        for (int i = 0; i < 6; i++) tmp[i] = s[i] + 0.5 * dt * k2[i];
        derivative(tmp, k3);
        for (int i = 0; i < 6; i++) tmp[i] = s[i] + dt * k3[i];
        derivative(tmp, k4);
        for (int i = 0; i < 6; i++) s[i] += dt / 6.0 * (k1[i] + 2.0 * k2[i] + 2.0 * k3[i] + k4[i]);
    }

    /** Vertical miss at the target plane in metres (positive = high), or {@link #NOT_REACHED}. */
    double miss(double boreAngleRad) {
        FlightResult flight = fly(boreAngleRad, false);
        return flight.reached() ? flight.end().y() - geometry.heightDifferenceM() : NOT_REACHED;
    }

    FlightResult fly(double boreAngleRad, boolean record) {
        double targetX = geometry.horizontalRangeM();
        double dt = options.timeStepS();
        double[] s = {0.0, 0.0, 0.0,
                muzzleVelocity * Math.cos(boreAngleRad), muzzleVelocity * Math.sin(boreAngleRad), 0.0};
        double t = 0.0;
        int stepCount = 0;
        List<Sample> samples = record ? new ArrayList<>() : null;
        if (samples != null) samples.add(sample(t, s));

        while (t < options.maxFlightTimeS()) {
            double px = s[0], py = s[1], pz = s[2], pvx = s[3], pvy = s[4], pvz = s[5];
            step(s, dt);
            if (s[0] >= targetX) {
                double f = (targetX - px) / (s[0] - px);
                Sample end = new Sample(t + dt * f,
                        px + (s[0] - px) * f, py + (s[1] - py) * f, pz + (s[2] - pz) * f,
                        pvx + (s[3] - pvx) * f, pvy + (s[4] - pvy) * f, pvz + (s[5] - pvz) * f);
                if (samples != null) samples.add(end);
                return new FlightResult(end, samples);
            }
            t += dt;
            stepCount++;
            if (s[3] <= 0.0 || Math.sqrt(s[3] * s[3] + s[4] * s[4] + s[5] * s[5]) < MIN_SPEED_MPS) break;
            if (samples != null && stepCount % options.sampleEveryStep() == 0) samples.add(sample(t, s));
        }
        return new FlightResult(null, samples);
    }

    private static Sample sample(double t, double[] s) {
        return new Sample(t, s[0], s[1], s[2], s[3], s[4], s[5]);
    }

    Projectile projectile() {
        return projectile;
    }
}
