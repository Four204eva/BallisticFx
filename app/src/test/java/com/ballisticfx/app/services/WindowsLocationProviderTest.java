package com.ballisticfx.app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class WindowsLocationProviderTest {

    @Test
    void parsesASuccessfulFix() throws IOException {
        LocationProvider.Fix fix = WindowsLocationProvider.parse("OK|39.5|-105.25|1612.5|18\r\n");
        assertEquals(39.5, fix.lat(), 0.0);
        assertEquals(-105.25, fix.lon(), 0.0);
        assertEquals(1612.5, fix.altitudeM(), 0.0);
        assertEquals(18.0, fix.accuracyM(), 0.0);
    }

    @Test
    void missingAltitudeComesBackAsNaN() throws IOException {
        LocationProvider.Fix fix = WindowsLocationProvider.parse("OK|39.5|-105.25|NaN|65");
        assertTrue(Double.isNaN(fix.altitudeM()));
        assertEquals(65.0, fix.accuracyM(), 0.0);
    }

    @Test
    void ignoresNoiseAroundTheResultLine() throws IOException {
        LocationProvider.Fix fix = WindowsLocationProvider.parse("some warning\n\nOK|-33.9|151.2|20|10\nmore noise\n");
        assertEquals(-33.9, fix.lat(), 0.0);
        assertEquals(151.2, fix.lon(), 0.0);
    }

    @Test
    void surfacesTheScriptsErrorMessage() {
        IOException e = assertThrows(IOException.class,
                () -> WindowsLocationProvider.parse("ERR|Windows denied location access."));
        assertEquals("Windows denied location access.", e.getMessage());
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IOException.class, () -> WindowsLocationProvider.parse(""));
        assertThrows(IOException.class, () -> WindowsLocationProvider.parse("OK|1|2"));
        assertThrows(IOException.class, () -> WindowsLocationProvider.parse("OK|a|b|c|d"));
        assertThrows(IOException.class, () -> WindowsLocationProvider.parse("hello"));
    }

    @Test
    void platformProviderIsAlwaysAvailable() {
        // Never null: unsupported platforms get a provider that explains itself.
        assertTrue(!LocationProvider.forThisPlatform().name().isBlank());
    }
}
