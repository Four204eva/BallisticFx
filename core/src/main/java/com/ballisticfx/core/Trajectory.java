package com.ballisticfx.core;

import java.util.List;
import java.util.function.ToDoubleFunction;

/** The sampled flight path from muzzle to target. Drives the time slider and the charts. */
public final class Trajectory {
    private final List<TrajectoryPoint> points;

    public Trajectory(List<TrajectoryPoint> points) {
        if (points.size() < 2) throw new IllegalArgumentException("A trajectory needs at least two points");
        this.points = List.copyOf(points);
    }

    public List<TrajectoryPoint> points() {
        return points;
    }

    public double flightTimeS() {
        return points.get(points.size() - 1).timeS();
    }

    public double rangeM() {
        return points.get(points.size() - 1).rangeM();
    }

    /** Interpolated state at {@code timeS}, clamped to the flight. */
    public TrajectoryPoint atTime(double timeS) {
        return interpolate(clamp(timeS, flightTimeS()), TrajectoryPoint::timeS);
    }

    /** Interpolated state at horizontal {@code rangeM}, clamped to the flight. */
    public TrajectoryPoint atRange(double rangeM) {
        return interpolate(clamp(rangeM, rangeM()), TrajectoryPoint::rangeM);
    }

    private static double clamp(double v, double max) {
        return Math.max(0.0, Math.min(v, max));
    }

    private TrajectoryPoint interpolate(double value, ToDoubleFunction<TrajectoryPoint> key) {
        int lo = 0;
        int hi = points.size() - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (key.applyAsDouble(points.get(mid)) <= value) lo = mid; else hi = mid;
        }
        TrajectoryPoint a = points.get(lo);
        TrajectoryPoint b = points.get(hi);
        double span = key.applyAsDouble(b) - key.applyAsDouble(a);
        return span <= 0.0 ? a : a.lerp(b, (value - key.applyAsDouble(a)) / span);
    }
}
