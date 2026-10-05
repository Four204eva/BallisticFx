package com.ballisticfx.app.services;

import java.io.IOException;

/**
 * Source of the user's GPS position. This is the seam for the Android port: desktop uses the Windows
 * location service, Android would use Gluon Attach's Position service or the platform's fused location.
 */
public interface LocationProvider {

    /**
     * @param altitudeM  metres above sea level, or NaN if the device did not report one
     * @param accuracyM  horizontal accuracy radius in metres, or NaN if unknown
     */
    record Fix(double lat, double lon, double altitudeM, double accuracyM) {}

    /** Short description for status messages, e.g. "Windows Location". */
    String name();

    /** Blocking: waits for a fix (up to ~15 s). Call off the UI thread. */
    Fix currentLocation() throws IOException, InterruptedException;

    /** The best provider for the OS this JVM runs on. */
    static LocationProvider forThisPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? new WindowsLocationProvider() : new UnsupportedLocationProvider();
    }
}
