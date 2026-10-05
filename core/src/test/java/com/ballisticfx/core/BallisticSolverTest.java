package com.ballisticfx.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class BallisticSolverTest {
    /** .308 Win, 175 gr match bullet, G7 0.243, 2600 fps. */
    private static final Projectile BULLET = new Projectile(DragModel.G7, 0.243, 175, 0.308, 1.24, 2600 * Units.FPS_TO_MPS);
    /** 1.5" sight height, zeroed at 100 yd, 1:10" right-hand twist. */
    private static final Rifle RIFLE = new Rifle(1.5 * Units.INCH_TO_M, 100 * Units.YARD_TO_M, 10.0, true);
    private static final SolverOptions PLAIN = SolverOptions.DEFAULT.withCoriolis(false).withSpinDrift(false);

    private static ShotInput input(double range) {
        return new ShotInput(BULLET, RIFLE, Atmosphere.STANDARD, Wind.CALM, ShotGeometry.level(range))
                .withOptions(PLAIN);
    }

    private static ShotInput input(double range, double rise, Atmosphere atm, Wind wind) {
        return new ShotInput(BULLET, RIFLE, atm, null, wind, new ShotGeometry(range, rise, 0.0, 0.0), PLAIN);
    }

    private static FiringSolution solve(double range) {
        return BallisticSolver.solve(input(range));
    }

    // ------------------------------------------------------------------ integrator accuracy

    @Test
    void integratorReproducesTheVacuumParabola() {
        // BC so large that drag is ~0.5 % of gravity: the path must be a parabola.
        Projectile nearVacuum = new Projectile(DragModel.G7, 1000.0, 175, 0.308, 1.24, 900.0);
        ShotInput in = new ShotInput(nearVacuum, RIFLE, Atmosphere.STANDARD, null, Wind.CALM,
                new ShotGeometry(500.0, 0.0, 0.0, 0.0), PLAIN);
        FiringSolution s = BallisticSolver.solve(in);
        double theta = s.boreAngleRad();
        double v = 900.0;
        for (double x : new double[]{100.0, 250.0, 400.0, 500.0}) {
            double expected = x * Math.tan(theta)
                    - Units.STANDARD_GRAVITY * x * x / (2.0 * v * v * Math.cos(theta) * Math.cos(theta));
            assertEquals(expected, s.trajectory().atRange(x).heightAboveMuzzleM(), 0.003, "x=" + x);
        }
    }

    @Test
    void resultIsConvergedInTimeStep() {
        FiringSolution coarse = solve(900.0);
        FiringSolution fine = BallisticSolver.solve(input(900.0).withOptions(PLAIN.withTimeStepS(0.00025)));
        assertEquals(fine.elevationMoa(), coarse.elevationMoa(), 0.01);
        assertEquals(fine.timeOfFlightS(), coarse.timeOfFlightS(), 0.0005);
    }

    // ------------------------------------------------------------------ geometry / zero

    @Test
    void solutionHitsTheAimPointEvenUphill() {
        FiringSolution s = BallisticSolver.solve(input(600.0, 25.0, Atmosphere.STANDARD, Wind.CALM));
        assertEquals(25.0, s.trajectory().atRange(600.0).heightAboveMuzzleM(), 0.002);
        assertEquals(0.0, s.trajectory().atRange(600.0).heightAboveLineOfSightM(), 0.002);
    }

    @Test
    void dialIsZeroAtTheZeroRange() {
        FiringSolution s = solve(RIFLE.zeroRangeM());
        assertEquals(0.0, s.elevationMoa(), 0.005);
    }

    @Test
    void zeroedRifleCrossesTheLineOfSightAtZeroRangeAndRisesAboveItBefore() {
        // Fire with the zero's bore angle: it should be above the sight line mid-way and on it at 100 yd.
        FiringSolution s = solve(RIFLE.zeroRangeM());
        assertEquals(0.0, s.trajectory().atRange(RIFLE.zeroRangeM()).heightAboveLineOfSightM(), 0.002);
        assertTrue(s.trajectory().atRange(50.0).heightAboveLineOfSightM() > 0.0);
        assertTrue(s.maxOrdinateM() > 0.0);
    }

    @Test
    void dialIsRelativeToTheZeroNotTheSightLine() {
        // At 300 m the bore-to-sight-line angle exceeds the dial by exactly the zero angle.
        FiringSolution s = solve(300.0);
        assertEquals(Units.radToMoa(s.boreAngleRad() - s.lineOfSightAngleRad() - s.zeroAngleRad()),
                s.elevationMoa(), 1e-9);
        assertTrue(s.zeroAngleRad() > 0.0);
    }

    @Test
    void zeroAtmosphereMattersWhenTodayIsDifferent() {
        Atmosphere thin = new Atmosphere(30.0, 850.0, 30.0);
        ShotInput sameAir = new ShotInput(BULLET, RIFLE, thin, null, Wind.CALM, ShotGeometry.level(600.0), PLAIN);
        ShotInput zeroedAtSeaLevel = new ShotInput(BULLET, RIFLE, thin, Atmosphere.STANDARD, Wind.CALM,
                ShotGeometry.level(600.0), PLAIN);
        double a = BallisticSolver.solve(sameAir).elevationMoa();
        double b = BallisticSolver.solve(zeroedAtSeaLevel).elevationMoa();
        assertTrue(Math.abs(a - b) > 0.01, "dials " + a + " vs " + b);
    }

    @Test
    void dialGrowsWithRange() {
        double previous = -1.0;
        for (double r = 200.0; r <= 1000.0; r += 200.0) {
            double dial = solve(r).elevationMoa();
            assertTrue(dial > previous, "range " + r);
            previous = dial;
        }
    }

    @Test
    void inclinedShotsFollowTheCosineRuleAgainstTheSlantRange() {
        double horizontal = 600.0;
        for (double rise : new double[]{200.0, -200.0}) {
            double slant = Math.hypot(horizontal, rise);
            double cos = horizontal / slant;
            double levelAtSlant = solve(slant).elevationMoa();
            double inclined = BallisticSolver.solve(input(horizontal, rise, Atmosphere.STANDARD, Wind.CALM)).elevationMoa();
            // Gravity only acts on the horizontal component of the sight line: dial ~ level(slant) * cos(angle).
            assertTrue(inclined < levelAtSlant, "rise " + rise + ": inclined " + inclined + " level " + levelAtSlant);
            assertEquals(levelAtSlant * cos, inclined, 0.03 * inclined, "rise " + rise);
        }
    }

    // ------------------------------------------------------------------ realism

    @Test
    void thousandYardDropAgreesWithAPublishedTable() {
        // Independent reference (longrangescience.com 308 Win table): 175 gr SMK, G7 0.243, 2600 fps,
        // 100 yd zero, standard air -> 40.1 MOA (11.7 mil) at 1000 yd, 1121 fps remaining.
        // Calculators disagree by a few percent (table versions, interpolation, sight height); this engine
        // lands ~3 % high, equivalent to a 0.007 change in BC. Hold it to +-5 %.
        FiringSolution s = solve(1000 * Units.YARD_TO_M);
        assertEquals(40.1, s.elevationMoa(), 0.05 * 40.1, "1000 yd dial");
        assertEquals(1121.0, s.impactVelocityMps() / Units.FPS_TO_MPS, 0.05 * 1121.0, "1000 yd velocity");
        assertEquals(3.5, solve(500 * Units.YARD_TO_M).elevationMil(), 0.05 * 3.5, "500 yd dial (mil)");
    }

    @Test
    void tenMphCrosswindAtThousandYardsIsAboutThreeMil() {
        // Same reference: ~3.0 mil for a 10 mph full-value crosswind at 1000 yd.
        FiringSolution s = BallisticSolver.solve(input(1000 * Units.YARD_TO_M, 0.0, Atmosphere.STANDARD,
                new Wind(10 * Units.MPH_TO_MPS, 90.0)));
        assertEquals(3.0, s.windageMil(), 0.15 * 3.0);
    }

    @Test
    void g1AndG7DescribeTheSameBulletConsistently() {
        // Sierra quotes ~0.505 G1 / ~0.243 G7 for this bullet; a bad table row would break the agreement.
        Projectile g1 = new Projectile(DragModel.G1, 0.505, 175, 0.308, 1.24, 2600 * Units.FPS_TO_MPS);
        double viaG7 = solve(800.0).elevationMoa();
        double viaG1 = BallisticSolver.solve(new ShotInput(g1, RIFLE, Atmosphere.STANDARD, null, Wind.CALM,
                ShotGeometry.level(800.0), PLAIN)).elevationMoa();
        assertEquals(viaG7, viaG1, 0.08 * viaG7);
    }

    @Test
    void higherBcFliesFlatter() {
        double low = solve(800.0).elevationMoa();
        Projectile better = BULLET.withBallisticCoefficient(0.30);
        double high = BallisticSolver.solve(new ShotInput(better, RIFLE, Atmosphere.STANDARD, null, Wind.CALM,
                ShotGeometry.level(800.0), PLAIN)).elevationMoa();
        assertTrue(high < low);
    }

    @Test
    void thinAirReducesTheDial() {
        double sea = solve(800.0).elevationMoa();
        // Zero conditions stay at sea level; only today's air is thin.
        double altitude = BallisticSolver.solve(new ShotInput(BULLET, RIFLE, new Atmosphere(15.0, 800.0, 0.0),
                Atmosphere.STANDARD, Wind.CALM, ShotGeometry.level(800.0), PLAIN)).elevationMoa();
        assertTrue(altitude < sea);
    }

    @Test
    void colderPowderIsSlowerSoNeedsMoreElevation() {
        Projectile sensitive = new Projectile(DragModel.G7, 0.243, 175, 0.308, 1.24, 2600 * Units.FPS_TO_MPS, 15.0, 0.9);
        Atmosphere cold = new Atmosphere(-10.0, 1013.25, 0.0);
        Atmosphere warm = new Atmosphere(35.0, 1013.25, 0.0);
        double inCold = BallisticSolver.solve(new ShotInput(sensitive, RIFLE, cold, Atmosphere.STANDARD, Wind.CALM,
                ShotGeometry.level(700.0), PLAIN)).elevationMoa();
        double inWarm = BallisticSolver.solve(new ShotInput(sensitive, RIFLE, warm, Atmosphere.STANDARD, Wind.CALM,
                ShotGeometry.level(700.0), PLAIN)).elevationMoa();
        assertEquals(2600 * Units.FPS_TO_MPS - 0.9 * 25.0, sensitive.muzzleVelocityAt(-10.0), 1e-9);
        assertTrue(inCold > inWarm);
    }

    @Test
    void gyroscopicStabilityIsPlausible() {
        double sg = BULLET.gyroscopicStability(10.0, 2600 * Units.FPS_TO_MPS, Atmosphere.STANDARD);
        assertTrue(sg > 2.0 && sg < 3.0, "Sg was " + sg);
    }

    // ------------------------------------------------------------------ wind

    @Test
    void crosswindDriftObeysTheLagTimeRule() {
        double range = 500.0;
        double windMps = 10 * Units.MPH_TO_MPS;
        Wind fromRight = new Wind(windMps, 90.0);   // shooting north, wind from the east
        FiringSolution calm = solve(range);
        FiringSolution windy = BallisticSolver.solve(input(range, 0.0, Atmosphere.STANDARD, fromRight));
        double extraDrift = windy.driftAtTargetM() - calm.driftAtTargetM();
        double vacuumTime = range / BULLET.muzzleVelocityMps();
        double lagTimeDrift = -windMps * (windy.timeOfFlightS() - vacuumTime); // negative = pushed left
        assertTrue(extraDrift < 0.0, "wind from the right pushes the bullet left");
        assertEquals(lagTimeDrift, extraDrift, 0.10 * Math.abs(lagTimeDrift));
        assertTrue(windy.windageMoa() > calm.windageMoa(), "dial right into the wind");
    }

    @Test
    void windFromTheLeftDialsLeft() {
        FiringSolution windy = BallisticSolver.solve(input(500.0, 0.0, Atmosphere.STANDARD, new Wind(8.0, 270.0)));
        assertTrue(windy.driftAtTargetM() > 0.0);
        assertTrue(windy.windageRad() < 0.0);
    }

    @Test
    void headwindNeedsMoreElevationAndTailwindLess() {
        double calm = solve(800.0).elevationMoa();
        double head = BallisticSolver.solve(input(800.0, 0.0, Atmosphere.STANDARD, new Wind(10.0, 0.0))).elevationMoa();
        double tail = BallisticSolver.solve(input(800.0, 0.0, Atmosphere.STANDARD, new Wind(10.0, 180.0))).elevationMoa();
        assertTrue(head > calm);
        assertTrue(tail < calm);
    }

    @Test
    void windIsMeasuredAgainstTheLineOfFireNotNorth() {
        // Same wind relative to the shooter -> same answer, whichever way the shooter faces.
        ShotGeometry north = new ShotGeometry(500.0, 0.0, 0.0, 0.0);
        ShotGeometry east = new ShotGeometry(500.0, 0.0, 90.0, 0.0);
        double a = BallisticSolver.solve(new ShotInput(BULLET, RIFLE, Atmosphere.STANDARD, null,
                new Wind(6.0, 45.0), north, PLAIN)).driftAtTargetM();
        double b = BallisticSolver.solve(new ShotInput(BULLET, RIFLE, Atmosphere.STANDARD, null,
                new Wind(6.0, 135.0), east, PLAIN)).driftAtTargetM();
        assertEquals(a, b, 1e-6);
    }

    // ------------------------------------------------------------------ second-order effects

    @Test
    void spinDriftIsRightForRightHandTwistAndLeftForLeftHand() {
        SolverOptions spinOnly = PLAIN.withSpinDrift(true);
        ShotInput right = input(800.0).withOptions(spinOnly);
        ShotInput left = new ShotInput(BULLET, new Rifle(RIFLE.sightHeightM(), RIFLE.zeroRangeM(), 10.0, false),
                Atmosphere.STANDARD, null, Wind.CALM, ShotGeometry.level(800.0), spinOnly);
        double r = BallisticSolver.solve(right).driftAtTargetM();
        double l = BallisticSolver.solve(left).driftAtTargetM();
        assertTrue(r > 0.05 && r < 1.0, "right-hand spin drift " + r);
        assertEquals(-r, l, 1e-9);
        assertEquals(r, BallisticSolver.solve(right).spinDriftAtTargetM(), 1e-9);
    }

    @Test
    void eastwardShotImpactsHigherThanWestward() {
        SolverOptions coriolis = PLAIN.withCoriolis(true);
        ShotInput east = new ShotInput(BULLET, RIFLE, Atmosphere.STANDARD, null, Wind.CALM,
                new ShotGeometry(1000.0, 0.0, 90.0, 45.0), coriolis);
        ShotInput west = new ShotInput(BULLET, RIFLE, Atmosphere.STANDARD, null, Wind.CALM,
                new ShotGeometry(1000.0, 0.0, 270.0, 45.0), coriolis);
        assertTrue(BallisticSolver.solve(east).elevationMoa() < BallisticSolver.solve(west).elevationMoa());
    }

    @Test
    void coriolisDeflectsToTheRightInTheNorthernHemisphere() {
        ShotInput north = new ShotInput(BULLET, RIFLE, Atmosphere.STANDARD, null, Wind.CALM,
                new ShotGeometry(1000.0, 0.0, 0.0, 45.0), PLAIN.withCoriolis(true));
        ShotInput south = new ShotInput(BULLET, RIFLE, Atmosphere.STANDARD, null, Wind.CALM,
                new ShotGeometry(1000.0, 0.0, 0.0, -45.0), PLAIN.withCoriolis(true));
        assertTrue(BallisticSolver.solve(north).driftAtTargetM() > 0.0);
        assertTrue(BallisticSolver.solve(south).driftAtTargetM() < 0.0);
    }

    // ------------------------------------------------------------------ trajectory / table

    @Test
    void sliderInterpolationIsConsistent() {
        Trajectory t = solve(700.0).trajectory();
        TrajectoryPoint start = t.atTime(0.0);
        assertEquals(0.0, start.rangeM(), 1e-12);
        assertEquals(0.0, start.heightAboveMuzzleM(), 1e-12);
        assertEquals(BULLET.muzzleVelocityMps(), start.speedMps(), 1e-6);

        TrajectoryPoint mid = t.atTime(t.flightTimeS() / 2);
        assertTrue(mid.rangeM() > 0.0 && mid.rangeM() < 700.0);
        assertEquals(mid.rangeM(), t.atRange(mid.rangeM()).rangeM(), 1e-6);
        assertEquals(mid.speedMps(), t.atRange(mid.rangeM()).speedMps(), 0.5);
        assertEquals(t.rangeM(), t.atTime(1e9).rangeM(), 1e-9); // clamped
    }

    @Test
    void bulletAlwaysLosesSpeedAndPassesMachOne() {
        Trajectory t = solve(1100.0).trajectory();
        double previous = Double.MAX_VALUE;
        for (TrajectoryPoint p : t.points()) {
            assertTrue(p.speedMps() <= previous + 1e-9);
            previous = p.speedMps();
        }
        assertTrue(t.points().get(0).mach() > 1.0);
        assertTrue(t.points().get(t.points().size() - 1).mach() < 1.3);
    }

    @Test
    void rangeTableIsMonotonicAndMatchesTheMainSolution() {
        List<RangeRow> rows = BallisticSolver.rangeTable(input(600.0),
                List.of(100.0, 200.0, 300.0, 400.0, 500.0, 600.0, 700.0));
        assertEquals(7, rows.size());
        for (int i = 1; i < rows.size(); i++) {
            assertTrue(rows.get(i).elevationRad() > rows.get(i - 1).elevationRad());
            assertTrue(rows.get(i).speedMps() < rows.get(i - 1).speedMps());
            assertTrue(rows.get(i).timeOfFlightS() > rows.get(i - 1).timeOfFlightS());
        }
        FiringSolution main = solve(600.0);
        RangeRow at600 = rows.get(5);
        assertEquals(main.elevationRad(), at600.elevationRad(), 1e-9);
        assertEquals(main.impactVelocityMps(), at600.speedMps(), 1e-6);
    }

    @Test
    void rangeTableStopsAtTheLimitOfReach() {
        List<RangeRow> rows = BallisticSolver.rangeTable(input(600.0), List.of(500.0, 1000.0, 20_000.0, 500.0));
        assertEquals(2, rows.size());
    }

    // ------------------------------------------------------------------ failure modes and library

    @Test
    void unreachableTargetThrows() {
        assertThrows(NoSolutionException.class, () -> solve(8000.0));
    }

    @Test
    void badInputsAreRejectedWithMessages() {
        assertThrows(IllegalArgumentException.class, () -> new Projectile(DragModel.G7, -0.1, 175, 0.308, 1.24, 800));
        assertThrows(IllegalArgumentException.class, () -> new Projectile(null, 0.2, 175, 0.308, 1.24, 800));
        assertThrows(IllegalArgumentException.class, () -> new Rifle(0.04, 0.0, 10, true));
        assertThrows(IllegalArgumentException.class, () -> new ShotGeometry(0.0, 0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Wind(-1.0, 0.0));
    }

    @Test
    void everyBuiltInCartridgeProducesASaneSolution() {
        assertFalse(Cartridges.ALL.isEmpty());
        assertNotNull(Cartridges.defaultCartridge());
        for (Cartridge c : Cartridges.ALL) {
            Projectile p = c.toProjectile();
            Rifle rifle = new Rifle(0.04, 100.0, c.twistInchesPerTurn(), true);
            ShotInput in = new ShotInput(p, rifle, Atmosphere.STANDARD, Wind.CALM, ShotGeometry.level(200.0));
            FiringSolution s = BallisticSolver.solve(in);
            assertTrue(s.elevationMoa() > 0.0, c.name() + " dial " + s.elevationMoa());
            assertTrue(s.impactVelocityMps() < p.muzzleVelocityMps(), c.name());
            // Short, fat pistol bullets are legitimately very stable (9mm ~12); the check catches typos in
            // length/twist data, where Sg would collapse below 1 or explode.
            assertTrue(s.gyroscopicStability() > 1.0 && s.gyroscopicStability() < 25.0,
                    c.name() + " Sg " + s.gyroscopicStability());
        }
    }
}
