package com.ballisticfx.core;

/**
 * Geodesy on the WGS84 ellipsoid (Vincenty's inverse formula), which is what GPS and map coordinates use.
 * A spherical-earth shortcut can be wrong by about 0.5 % in range, which is more than the drop tables tolerate.
 */
public final class Geo {
    private Geo() {}

    private static final double A = 6_378_137.0;
    private static final double F = 1.0 / 298.257223563;
    private static final double B = (1.0 - F) * A;

    /** Result of an inverse geodesic problem. */
    public record Inverse(double distanceM, double initialBearingDegrees) {}

    /** Distance (m) and initial true bearing (0..360, clockwise from north) from point 1 to point 2. */
    public static Inverse inverse(double lat1, double lon1, double lat2, double lon2) {
        if (lat1 == lat2 && lon1 == lon2) return new Inverse(0.0, 0.0);

        double l = Math.toRadians(lon2 - lon1);
        double u1 = Math.atan((1.0 - F) * Math.tan(Math.toRadians(lat1)));
        double u2 = Math.atan((1.0 - F) * Math.tan(Math.toRadians(lat2)));
        double sinU1 = Math.sin(u1), cosU1 = Math.cos(u1);
        double sinU2 = Math.sin(u2), cosU2 = Math.cos(u2);

        double lambda = l;
        double sinSigma = 0, cosSigma = 0, sigma = 0, cos2Alpha = 0, cos2SigmaM = 0;
        boolean converged = false;
        for (int i = 0; i < 200; i++) {
            double sinLambda = Math.sin(lambda), cosLambda = Math.cos(lambda);
            double t1 = cosU2 * sinLambda;
            double t2 = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda;
            sinSigma = Math.sqrt(t1 * t1 + t2 * t2);
            if (sinSigma == 0.0) return new Inverse(0.0, 0.0);
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda;
            sigma = Math.atan2(sinSigma, cosSigma);
            double sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma;
            cos2Alpha = 1.0 - sinAlpha * sinAlpha;
            cos2SigmaM = cos2Alpha != 0.0 ? cosSigma - 2.0 * sinU1 * sinU2 / cos2Alpha : 0.0;
            double c = F / 16.0 * cos2Alpha * (4.0 + F * (4.0 - 3.0 * cos2Alpha));
            double previous = lambda;
            lambda = l + (1.0 - c) * F * sinAlpha
                    * (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM)));
            if (Math.abs(lambda - previous) < 1e-12) {
                converged = true;
                break;
            }
        }
        if (!converged) return greatCircle(lat1, lon1, lat2, lon2); // near-antipodal; irrelevant at rifle range

        double uSq = cos2Alpha * (A * A - B * B) / (B * B);
        double bigA = 1.0 + uSq / 16384.0 * (4096.0 + uSq * (-768.0 + uSq * (320.0 - 175.0 * uSq)));
        double bigB = uSq / 1024.0 * (256.0 + uSq * (-128.0 + uSq * (74.0 - 47.0 * uSq)));
        double deltaSigma = bigB * sinSigma * (cos2SigmaM + bigB / 4.0
                * (cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM)
                - bigB / 6.0 * cos2SigmaM * (-3.0 + 4.0 * sinSigma * sinSigma)
                * (-3.0 + 4.0 * cos2SigmaM * cos2SigmaM)));
        double distance = B * bigA * (sigma - deltaSigma);
        double bearing = Math.atan2(cosU2 * Math.sin(lambda),
                cosU1 * sinU2 - sinU1 * cosU2 * Math.cos(lambda));
        return new Inverse(distance, normalizeDegrees(Math.toDegrees(bearing)));
    }

    public static double distanceM(double lat1, double lon1, double lat2, double lon2) {
        return inverse(lat1, lon1, lat2, lon2).distanceM();
    }

    public static double bearingDegrees(double lat1, double lon1, double lat2, double lon2) {
        return inverse(lat1, lon1, lat2, lon2).initialBearingDegrees();
    }

    /** Height the ground "falls away" over a horizontal distance because the earth is round (no refraction). */
    public static double curvatureDropM(double horizontalDistanceM) {
        return horizontalDistanceM * horizontalDistanceM / (2.0 * Units.EARTH_MEAN_RADIUS_M);
    }

    /**
     * Builds the shot geometry from two map positions.
     *
     * @param shooterGroundAltM ground altitude above sea level at the shooter
     * @param muzzleHeightM     muzzle height above the ground at the shooter
     * @param targetGroundAltM  ground altitude above sea level at the target
     * @param aimHeightM        height of the aim point above the ground at the target
     * @param curvature         include the earth-curvature drop of the target (matters beyond ~1 km)
     */
    public static ShotGeometry geometry(
            double shooterLat, double shooterLon, double shooterGroundAltM, double muzzleHeightM,
            double targetLat, double targetLon, double targetGroundAltM, double aimHeightM,
            boolean curvature) {
        Inverse inv = inverse(shooterLat, shooterLon, targetLat, targetLon);
        double rise = (targetGroundAltM + aimHeightM) - (shooterGroundAltM + muzzleHeightM);
        if (curvature) rise -= curvatureDropM(inv.distanceM());
        return new ShotGeometry(inv.distanceM(), rise, inv.initialBearingDegrees(), shooterLat);
    }

    private static Inverse greatCircle(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        double dist = 2.0 * Units.EARTH_MEAN_RADIUS_M * Math.atan2(Math.sqrt(h), Math.sqrt(1.0 - h));
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return new Inverse(dist, normalizeDegrees(Math.toDegrees(Math.atan2(y, x))));
    }

    private static double normalizeDegrees(double deg) {
        return ((deg % 360.0) + 360.0) % 360.0;
    }
}
