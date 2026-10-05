package com.ballisticfx.app.services;

import java.io.IOException;

/** Placeholder for platforms without a location backend yet. */
final class UnsupportedLocationProvider implements LocationProvider {
    @Override
    public String name() {
        return "none";
    }

    @Override
    public Fix currentLocation() throws IOException {
        throw new IOException("GPS lookup is not available on this platform yet. "
                + "Place the shooter on the map or type coordinates.");
    }
}
