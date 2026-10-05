package com.ballisticfx.core;

/**
 * A bullet and how fast it leaves the muzzle.
 *
 * @param dragModel                       the drag function the ballistic coefficient refers to
 * @param ballisticCoefficient            BC in lb/in^2 (the usual published form, e.g. 0.243 G7)
 * @param weightGrains                    bullet weight
 * @param diameterInches                  bullet diameter (caliber)
 * @param lengthInches                    overall bullet length, used for gyroscopic stability and spin drift
 * @param muzzleVelocityMps               velocity measured at {@code velocityRefTempC}
 * @param velocityRefTempC                powder temperature at which {@code muzzleVelocityMps} was measured
 * @param velocityTempSensitivityMpsPerC  change in muzzle velocity per degree C of powder temperature (0 = ignore)
 */
public record Projectile(
        DragModel dragModel,
        double ballisticCoefficient,
        double weightGrains,
        double diameterInches,
        double lengthInches,
        double muzzleVelocityMps,
        double velocityRefTempC,
        double velocityTempSensitivityMpsPerC) {

    public Projectile {
        if (dragModel == null) throw new IllegalArgumentException("Choose a drag model (G1 or G7)");
        if (!(ballisticCoefficient > 0.0)) {
            throw new IllegalArgumentException("Ballistic coefficient must be positive");
        }
        if (!(weightGrains > 0.0)) throw new IllegalArgumentException("Bullet weight must be positive");
        if (!(diameterInches > 0.0)) throw new IllegalArgumentException("Bullet diameter must be positive");
        if (!(lengthInches > 0.0)) throw new IllegalArgumentException("Bullet length must be positive");
        if (!(muzzleVelocityMps >= 50.0 && muzzleVelocityMps <= 2000.0)) {
            throw new IllegalArgumentException("Muzzle velocity must be between 50 and 2000 m/s");
        }
    }

    /** Convenience: powder-temperature effects ignored. */
    public Projectile(DragModel dragModel, double bc, double weightGrains, double diameterInches,
                      double lengthInches, double muzzleVelocityMps) {
        this(dragModel, bc, weightGrains, diameterInches, lengthInches, muzzleVelocityMps, 15.0, 0.0);
    }

    public Projectile withBallisticCoefficient(double bc) {
        return new Projectile(dragModel, bc, weightGrains, diameterInches, lengthInches, muzzleVelocityMps,
                velocityRefTempC, velocityTempSensitivityMpsPerC);
    }

    public Projectile withMuzzleVelocityMps(double mv) {
        return new Projectile(dragModel, ballisticCoefficient, weightGrains, diameterInches, lengthInches, mv,
                velocityRefTempC, velocityTempSensitivityMpsPerC);
    }

    public double massKg() {
        return weightGrains * Units.GRAIN_TO_KG;
    }

    /** Muzzle velocity corrected for powder temperature (air temperature is used as the powder temperature). */
    public double muzzleVelocityAt(double powderTempC) {
        return muzzleVelocityMps + velocityTempSensitivityMpsPerC * (powderTempC - velocityRefTempC);
    }

    /** Muzzle energy at the reference velocity, joules. */
    public double muzzleEnergyJ() {
        return 0.5 * massKg() * muzzleVelocityMps * muzzleVelocityMps;
    }

    /**
     * Miller gyroscopic stability factor, corrected for velocity and atmosphere. Roughly: below 1.0 the
     * bullet tumbles, 1.0 to 1.4 is marginal, above 1.5 is stable.
     */
    public double gyroscopicStability(double twistInchesPerTurn, double velocityMps, Atmosphere atmosphere) {
        double twistCalibers = twistInchesPerTurn / diameterInches;
        double lengthCalibers = lengthInches / diameterInches;
        double base = 30.0 * weightGrains
                / (twistCalibers * twistCalibers * Math.pow(diameterInches, 3)
                * lengthCalibers * (1.0 + lengthCalibers * lengthCalibers));
        double velocityFactor = Math.cbrt(velocityMps / Units.FPS_TO_MPS / 2800.0);
        double tempF = atmosphere.temperatureC() * 9.0 / 5.0 + 32.0;
        double pressureInHg = atmosphere.stationPressureHpa() / Units.HPA_PER_INHG;
        return base * velocityFactor * (tempF + 460.0) / 519.0 * 29.92 / pressureInHg;
    }
}
