package com.ballisticfx.core;

/**
 * Where the target is relative to the muzzle, in the shooter's local level frame.
 *
 * @param horizontalRangeM    ground (horizontal) distance to the target
 * @param heightDifferenceM   height of the aim point above the muzzle (positive = uphill). When built by
 *                            {@link Geo#geometry} this already includes the earth-curvature drop
 * @param azimuthDegrees      true compass bearing from shooter to target (used for wind and Coriolis)
 * @param latitudeDegrees     shooter latitude (used for Coriolis)
 */
public record ShotGeometry(
        double horizontalRangeM,
        double heightDifferenceM,
        double azimuthDegrees,
        double latitudeDegrees) {

    public ShotGeometry {
        if (!(horizontalRangeM >= 1.0)) {
            throw new IllegalArgumentException("Target must be at least 1 m away");
        }
        if (!(Math.abs(heightDifferenceM) <= 5000.0)) {
            throw new IllegalArgumentException("Height difference is out of range");
        }
        if (!(Math.abs(latitudeDegrees) <= 90.0)) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
    }

    /** Flat, level shot with no earth rotation effects. */
    public static ShotGeometry level(double horizontalRangeM) {
        return new ShotGeometry(horizontalRangeM, 0.0, 0.0, 0.0);
    }

    /** Same line of sight, shortened or lengthened to {@code rangeM}: the aim point slides along the sight line. */
    ShotGeometry atRange(double rangeM) {
        return new ShotGeometry(rangeM, heightDifferenceM * rangeM / horizontalRangeM, azimuthDegrees, latitudeDegrees);
    }
}
