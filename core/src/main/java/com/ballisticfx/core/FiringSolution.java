package com.ballisticfx.core;

/**
 * The answer: what to dial and what the bullet does on the way.
 *
 * @param boreAngleRad          bore elevation above the horizontal that puts the bullet on the aim point
 * @param lineOfSightAngleRad   angle of the sight line to the aim point above the horizontal
 * @param zeroAngleRad          bore elevation that zeroes the rifle at its zero range
 * @param elevationRad          turret correction from the zero: positive = dial/hold UP
 * @param windageRad            turret correction: positive = dial/hold RIGHT (the bullet drifts left of the aim)
 * @param driftAtTargetM        total sideways deflection at the target if you do not correct, positive = right
 * @param spinDriftAtTargetM    the spin-drift share of that deflection
 * @param maxOrdinateM          highest the bullet rises above the line of sight
 * @param trajectory            sampled flight (never null from {@link BallisticSolver#solve})
 */
public record FiringSolution(
        double boreAngleRad,
        double lineOfSightAngleRad,
        double zeroAngleRad,
        double elevationRad,
        double windageRad,
        double timeOfFlightS,
        double impactVelocityMps,
        double impactMach,
        double impactEnergyJ,
        double driftAtTargetM,
        double spinDriftAtTargetM,
        double maxOrdinateM,
        double gyroscopicStability,
        Trajectory trajectory) {

    public double elevationMoa() {
        return Units.radToMoa(elevationRad);
    }

    public double elevationMil() {
        return Units.radToMil(elevationRad);
    }

    public double windageMoa() {
        return Units.radToMoa(windageRad);
    }

    public double windageMil() {
        return Units.radToMil(windageRad);
    }
}
