package com.ballisticfx.core;

/** Unit constants and angle helpers. The engine works in SI (m, s, kg) unless a name says otherwise. */
public final class Units {
    private Units() {}

    public static final double GRAIN_TO_G = 0.06479891;
    public static final double GRAIN_TO_KG = GRAIN_TO_G / 1000.0;
    public static final double INCH_TO_M = 0.0254;
    public static final double FOOT_TO_M = 0.3048;
    public static final double YARD_TO_M = 0.9144;
    public static final double FPS_TO_MPS = 0.3048;
    public static final double MPH_TO_MPS = 0.44704;
    public static final double J_TO_FTLBF = 0.7375621493;
    public static final double HPA_PER_INHG = 33.8638866667;

    public static final double STANDARD_GRAVITY = 9.80665;
    /** Earth's sidereal rotation rate, rad/s. */
    public static final double EARTH_ANGULAR_VELOCITY = 7.2921159e-5;
    /** IUGG mean radius, used only for the earth-curvature drop correction. */
    public static final double EARTH_MEAN_RADIUS_M = 6_371_008.8;

    /** True MOA: 1/60 of a degree (1.047 in at 100 yd), as used by most scope turrets. */
    public static double radToMoa(double rad) {
        return Math.toDegrees(rad) * 60.0;
    }

    public static double moaToRad(double moa) {
        return Math.toRadians(moa / 60.0);
    }

    /** Milliradian: 1/1000 of a radian (1 cm at 10 m). */
    public static double radToMil(double rad) {
        return rad * 1000.0;
    }

    public static double milToRad(double mil) {
        return mil / 1000.0;
    }
}
