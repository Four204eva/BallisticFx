package com.ballisticfx.core;

/**
 * Numerical settings and optional physics.
 *
 * @param timeStepS        RK4 step; 1 ms is converged to well under 0.01 MOA for rifle ranges
 * @param sampleEveryStep  record one trajectory sample every N steps (for the time slider and charts)
 * @param maxFlightTimeS   give up on a flight after this long
 * @param coriolis         include Coriolis/Eötvös acceleration (needs the shooter's latitude and the azimuth)
 * @param spinDrift        include gyroscopic spin drift (Litz approximation)
 */
public record SolverOptions(
        double timeStepS,
        int sampleEveryStep,
        double maxFlightTimeS,
        boolean coriolis,
        boolean spinDrift) {

    public static final SolverOptions DEFAULT = new SolverOptions(0.001, 5, 30.0, true, true);

    public SolverOptions {
        if (!(timeStepS > 0.0 && timeStepS <= 0.01)) {
            throw new IllegalArgumentException("Time step must be between 0 and 10 ms");
        }
        if (sampleEveryStep < 1) throw new IllegalArgumentException("Sample interval must be at least 1");
        if (!(maxFlightTimeS > 0.0)) throw new IllegalArgumentException("Max flight time must be positive");
    }

    public SolverOptions withTimeStepS(double dt) {
        return new SolverOptions(dt, sampleEveryStep, maxFlightTimeS, coriolis, spinDrift);
    }

    public SolverOptions withCoriolis(boolean enabled) {
        return new SolverOptions(timeStepS, sampleEveryStep, maxFlightTimeS, enabled, spinDrift);
    }

    public SolverOptions withSpinDrift(boolean enabled) {
        return new SolverOptions(timeStepS, sampleEveryStep, maxFlightTimeS, coriolis, enabled);
    }
}
