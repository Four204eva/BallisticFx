package com.ballisticfx.core;

/**
 * Wind as meteorologists report it: {@code fromDegrees} is the compass bearing the wind blows FROM
 * (270 = a westerly wind, blowing toward the east).
 */
public record Wind(double speedMps, double fromDegrees) {
    public static final Wind CALM = new Wind(0.0, 0.0);

    public Wind {
        if (!(speedMps >= 0.0 && speedMps <= 80.0)) {
            throw new IllegalArgumentException("Wind speed must be between 0 and 80 m/s");
        }
    }

    /** Wind component along the line of fire; positive = headwind (blowing back toward the shooter). */
    public double headwindMps(double azimuthDegrees) {
        return speedMps * Math.cos(Math.toRadians(fromDegrees - azimuthDegrees));
    }

    /** Wind component across the line of fire; positive = from the shooter's right (pushes the bullet left). */
    public double crosswindFromRightMps(double azimuthDegrees) {
        return speedMps * Math.sin(Math.toRadians(fromDegrees - azimuthDegrees));
    }
}
