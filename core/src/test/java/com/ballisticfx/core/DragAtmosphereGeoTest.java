package com.ballisticfx.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DragAtmosphereGeoTest {

    // ------------------------------------------------------------------ drag tables

    @Test
    void machNodesAreStrictlyIncreasing() {
        for (DragModel model : DragModel.values()) {
            for (int i = 1; i < model.nodeCount(); i++) {
                assertTrue(model.nodeMach(i) > model.nodeMach(i - 1), model + " node " + i);
            }
        }
    }

    @Test
    void tableNodesInterpolationAndClamping() {
        assertEquals(0.4805, DragModel.G1.cd(1.0), 1e-9);
        assertEquals(0.3803, DragModel.G7.cd(1.0), 1e-9);
        assertEquals(0.5 * (0.2993 + 0.3803), DragModel.G7.cd(0.9875), 1e-9);
        assertEquals(0.2629, DragModel.G1.cd(-1.0), 1e-9);
        assertEquals(0.4988, DragModel.G1.cd(9.0), 1e-9);
        assertEquals(0.1198, DragModel.G7.cd(0.0), 1e-9);
        assertEquals(0.1618, DragModel.G7.cd(9.0), 1e-9);
    }

    @Test
    void g7ReferenceProjectileIsSlipperierThanG1EverywhereWhatsoever() {
        for (double m = 0.0; m <= 5.0; m += 0.05) {
            assertTrue(DragModel.G7.cd(m) < DragModel.G1.cd(m), "Mach " + m);
        }
    }

    // ------------------------------------------------------------------ atmosphere

    @Test
    void standardAtmosphere() {
        assertEquals(1.225, Atmosphere.STANDARD.densityKgM3(), 0.002);
        assertEquals(340.3, Atmosphere.STANDARD.speedOfSoundMps(), 0.3);
        assertEquals(1.0, Atmosphere.STANDARD.densityRatio(), 0.002);
        assertEquals(0.0, Atmosphere.STANDARD.densityAltitudeM(), 5.0);
    }

    @Test
    void humidAirIsThinnerAndFaster() {
        Atmosphere dry = new Atmosphere(30.0, 1000.0, 0.0);
        Atmosphere humid = new Atmosphere(30.0, 1000.0, 90.0);
        assertTrue(humid.densityKgM3() < dry.densityKgM3());
        assertTrue(humid.speedOfSoundMps() > dry.speedOfSoundMps());
    }

    @Test
    void highAltitudeAirHasHighDensityAltitude() {
        // ~1600 m station (Boulder, CO) on a warm day.
        Atmosphere boulder = new Atmosphere(25.0, 847.0, 20.0);
        assertTrue(boulder.densityAltitudeM() > 2000.0 && boulder.densityAltitudeM() < 3500.0,
                "DA was " + boulder.densityAltitudeM());
    }

    @Test
    void invalidAtmosphereIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Atmosphere(15.0, 29.92, 50.0)); // inHg typed as hPa
        assertThrows(IllegalArgumentException.class, () -> new Atmosphere(15.0, 1013.0, 120.0));
    }

    // ------------------------------------------------------------------ wind components

    @Test
    void windComponentsRelativeToLineOfFire() {
        Wind fromRight = new Wind(10.0, 90.0);       // from the east
        assertEquals(10.0, fromRight.crosswindFromRightMps(0.0), 1e-9);  // shooting north: east is the right
        assertEquals(0.0, fromRight.headwindMps(0.0), 1e-9);
        assertEquals(-10.0, fromRight.crosswindFromRightMps(180.0), 1e-9); // shooting south: east is the left

        Wind head = new Wind(10.0, 0.0);
        assertEquals(10.0, head.headwindMps(0.0), 1e-9);
        assertEquals(-10.0, head.headwindMps(180.0), 1e-9);
    }

    // ------------------------------------------------------------------ geodesy

    private static double dms(int deg, int min, double sec) {
        return deg + min / 60.0 + sec / 3600.0;
    }

    @Test
    void vincentyMatchesPublishedTestLine() {
        // Vincenty (1975) test line: Flinders Peak -> Buninyong. Published: 54 972.271 m, azimuth 306 52 05.37.
        Geo.Inverse r = Geo.inverse(
                -dms(37, 57, 3.72030), dms(144, 25, 29.52440),
                -dms(37, 39, 10.15610), dms(143, 55, 35.38390));
        assertEquals(54972.271, r.distanceM(), 0.005);
        assertEquals(dms(306, 52, 5.37), r.initialBearingDegrees(), 1e-4);
    }

    @Test
    void ellipsoidDiffersFromSphereAsExpected() {
        // One degree of latitude at the equator is ~110 574 m on WGS84 (a sphere would say 111 195 m).
        assertEquals(110_574.0, Geo.distanceM(0.0, 0.0, 1.0, 0.0), 5.0);
        // One hundredth of a degree of longitude at the equator = a * 0.01 deg in radians.
        assertEquals(6_378_137.0 * Math.toRadians(0.01), Geo.distanceM(0.0, 0.0, 0.0, 0.01), 0.01);
    }

    @Test
    void bearingsAreCompassBearings() {
        assertEquals(0.0, Geo.bearingDegrees(10, 20, 11, 20), 1e-6);
        assertEquals(90.0, Geo.bearingDegrees(0, 0, 0, 1), 1e-6);
        assertEquals(180.0, Geo.bearingDegrees(1, 0, 0, 0), 1e-6);
        assertEquals(270.0, Geo.bearingDegrees(0, 1, 0, 0), 1e-6);
    }

    @Test
    void samePointIsZeroDistance() {
        assertEquals(0.0, Geo.distanceM(40.0, -105.0, 40.0, -105.0), 1e-9);
    }

    @Test
    void geometryCombinesAltitudesHeightsAndCurvature() {
        // ~1113 m due east along the equator; target ground 30 m higher.
        ShotGeometry flat = Geo.geometry(0, 0, 100, 1.5, 0, 0.01, 130, 1.0, false);
        assertEquals(1113.195, flat.horizontalRangeM(), 0.01);
        assertEquals(29.5, flat.heightDifferenceM(), 1e-9);
        assertEquals(90.0, flat.azimuthDegrees(), 1e-6);

        ShotGeometry curved = Geo.geometry(0, 0, 100, 1.5, 0, 0.01, 130, 1.0, true);
        assertEquals(29.5 - Geo.curvatureDropM(1113.195), curved.heightDifferenceM(), 1e-3);
        assertEquals(0.0973, Geo.curvatureDropM(1113.195), 0.0005);
    }
}
