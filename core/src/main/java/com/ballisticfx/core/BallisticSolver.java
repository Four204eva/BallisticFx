package com.ballisticfx.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the firing solution: the bore angle that puts the bullet on the aim point, expressed as the
 * turret correction from the rifle's zero, plus the wind correction and the full flight.
 *
 * <p>What is modelled: G1/G7 drag with local Mach number, moist-air density thinning with height,
 * wind, gravity, Coriolis/Eötvös, Litz spin drift, powder-temperature velocity shift, sight height,
 * zero range and zero atmosphere, uphill/downhill geometry, and earth curvature (via {@link Geo#geometry}).
 * <p>What is not: aerodynamic jump, velocity-banded BCs, varying wind along the path, scope cant,
 * Magnus/pitching effects. Treat results as a starting point and confirm on target.
 */
public final class BallisticSolver {
    private BallisticSolver() {}

    /** Residual (metres at the target plane) below which the bore-angle search stops. 10 micrometres. */
    private static final double TOLERANCE_M = 1e-5;

    /** @throws NoSolutionException if the target is beyond what the load can reach */
    public static FiringSolution solve(ShotInput input) {
        return solveInternal(input, zeroBoreAngle(input), true);
    }

    /**
     * Range card along the current line of sight: for each requested range the aim point slides along the
     * sight line (so an uphill target is uphill at every row). Stops at the first unreachable range.
     */
    public static List<RangeRow> rangeTable(ShotInput input, List<Double> rangesM) {
        double zero = zeroBoreAngle(input);
        List<RangeRow> rows = new ArrayList<>(rangesM.size());
        for (double range : rangesM) {
            if (!(range >= 1.0)) continue;
            try {
                FiringSolution s = solveInternal(input.withGeometry(input.geometry().atRange(range)), zero, false);
                rows.add(new RangeRow(range, s.elevationRad(), s.windageRad(), s.timeOfFlightS(),
                        s.impactVelocityMps(), s.impactMach(), s.impactEnergyJ(), s.driftAtTargetM()));
            } catch (NoSolutionException e) {
                break;
            }
        }
        return rows;
    }

    /** Bore elevation that hits a point at scope height at the zero range: level, calm, same load. */
    static double zeroBoreAngle(ShotInput in) {
        Rifle rifle = in.rifle();
        ShotGeometry zeroGeometry = new ShotGeometry(rifle.zeroRangeM(), rifle.sightHeightM(), 0.0, 0.0);
        SolverOptions plain = in.options().withCoriolis(false).withSpinDrift(false);
        ShotSimulator sim = new ShotSimulator(in.projectile(), rifle, in.effectiveZeroAtmosphere(),
                Wind.CALM, zeroGeometry, plain);
        return solveBore(sim);
    }

    private static FiringSolution solveInternal(ShotInput in, double zeroBore, boolean record) {
        Projectile projectile = in.projectile();
        Rifle rifle = in.rifle();
        ShotGeometry geometry = in.geometry();
        ShotSimulator sim = new ShotSimulator(projectile, rifle, in.atmosphere(), in.wind(), geometry, in.options());

        double bore = solveBore(sim);
        ShotSimulator.FlightResult flight = sim.fly(bore, record);
        ShotSimulator.Sample end = flight.end();

        double range = geometry.horizontalRangeM();
        double targetY = geometry.heightDifferenceM();
        double sightH = rifle.sightHeightM();
        double sightSlope = (targetY - sightH) / range;
        double losAngle = Math.atan2(targetY - sightH, range);

        double spinDrift = sim.spinDriftM(end.t());
        double drift = end.z() + spinDrift;
        double speed = end.speed();

        Trajectory trajectory = null;
        double maxOrdinate = Double.NaN;
        if (record) {
            List<TrajectoryPoint> points = new ArrayList<>(flight.samples().size());
            maxOrdinate = Double.NEGATIVE_INFINITY;
            for (ShotSimulator.Sample s : flight.samples()) {
                double aboveLos = s.y() - (sightH + sightSlope * s.x());
                maxOrdinate = Math.max(maxOrdinate, aboveLos);
                double v = s.speed();
                points.add(new TrajectoryPoint(
                        s.t(), s.x(), s.y(), aboveLos,
                        s.z() + sim.spinDriftM(s.t()),
                        v, Math.hypot(s.vx(), s.vz()), s.vy(),
                        sim.machAt(s), 0.5 * projectile.massKg() * v * v));
            }
            trajectory = new Trajectory(points);
        }

        return new FiringSolution(
                bore,
                losAngle,
                zeroBore,
                bore - zeroBore - losAngle,
                -Math.atan2(drift, range),
                end.t(),
                speed,
                sim.machAt(end),
                0.5 * projectile.massKg() * speed * speed,
                drift,
                spinDrift,
                maxOrdinate,
                sim.gyroscopicStability(),
                trajectory);
    }

    /**
     * Root-finds the bore angle whose flight passes through the aim point. Fast path: secant iterations
     * (the miss distance is smooth and nearly linear in angle). Fallback: scan up from the line of sight
     * until the shot goes high, then bisect. The fallback also decides "out of reach".
     */
    private static double solveBore(ShotSimulator sim) {
        ShotGeometry g = sim.geometry();
        double range = g.horizontalRangeM();

        double a = Math.atan2(g.heightDifferenceM(), range);
        double fa = sim.miss(a);
        if (fa > ShotSimulator.NOT_REACHED) {
            double b = a - fa / range;
            double fb = sim.miss(b);
            for (int i = 0; i < 20 && fb > ShotSimulator.NOT_REACHED; i++) {
                if (Math.abs(fb) < TOLERANCE_M) return b;
                double denominator = fb - fa;
                if (denominator == 0.0) break;
                double c = b - fb * (b - a) / denominator;
                if (!(Math.abs(c) < 1.4)) break;
                a = b;
                fa = fb;
                b = c;
                fb = sim.miss(b);
            }
            if (fb > ShotSimulator.NOT_REACHED && Math.abs(fb) < TOLERANCE_M) return b;
        }
        return bisect(sim, g);
    }

    private static double bisect(ShotSimulator sim, ShotGeometry g) {
        double lower = Math.atan2(g.heightDifferenceM(), g.horizontalRangeM()) - 0.01;
        double lo = lower;
        double hi = Double.NaN;
        for (double angle = lower; angle <= lower + 0.8; angle += 0.004) {
            if (sim.miss(angle) >= 0.0) {
                hi = angle;
                break;
            }
            lo = angle;
        }
        if (Double.isNaN(hi)) {
            throw new NoSolutionException(String.format(
                    "Target at %.0f m is out of reach for this load (the bullet runs out of velocity first).",
                    g.horizontalRangeM()));
        }
        for (int i = 0; i < 60; i++) {
            double mid = 0.5 * (lo + hi);
            if (sim.miss(mid) >= 0.0) hi = mid; else lo = mid;
        }
        return 0.5 * (lo + hi);
    }
}
