package com.ballisticfx.core;

/**
 * One instant of the flight. Frame: range is horizontal distance downrange, heights are vertical,
 * drift is lateral (positive = right of the bore plane).
 */
public record TrajectoryPoint(
        double timeS,
        double rangeM,
        double heightAboveMuzzleM,
        double heightAboveLineOfSightM,
        double driftM,
        double speedMps,
        double horizontalSpeedMps,
        double verticalSpeedMps,
        double mach,
        double energyJ) {

    TrajectoryPoint lerp(TrajectoryPoint o, double f) {
        return new TrajectoryPoint(
                mix(timeS, o.timeS, f), mix(rangeM, o.rangeM, f),
                mix(heightAboveMuzzleM, o.heightAboveMuzzleM, f),
                mix(heightAboveLineOfSightM, o.heightAboveLineOfSightM, f),
                mix(driftM, o.driftM, f), mix(speedMps, o.speedMps, f),
                mix(horizontalSpeedMps, o.horizontalSpeedMps, f),
                mix(verticalSpeedMps, o.verticalSpeedMps, f),
                mix(mach, o.mach, f), mix(energyJ, o.energyJ, f));
    }

    private static double mix(double a, double b, double f) {
        return a + (b - a) * f;
    }
}
