package com.ballisticfx.app.units;

import java.util.List;

/** One turret click: its size in {@code unit}. Used to turn an angle into a click count. */
public record ClickValue(String label, DialUnit unit, double size) {
    public static final List<ClickValue> ALL = List.of(
            new ClickValue("1/4 MOA", DialUnit.MOA, 0.25),
            new ClickValue("1/8 MOA", DialUnit.MOA, 0.125),
            new ClickValue("1/2 MOA", DialUnit.MOA, 0.5),
            new ClickValue("1 MOA", DialUnit.MOA, 1.0),
            new ClickValue("0.1 mil", DialUnit.MIL, 0.1),
            new ClickValue("0.05 mil", DialUnit.MIL, 0.05));

    public static ClickValue byLabel(String label) {
        return ALL.stream().filter(c -> c.label().equals(label)).findFirst().orElse(ALL.get(0));
    }

    /** Whole clicks for an angle, rounded to the nearest click. */
    public long clicks(double angleRad) {
        return Math.round(unit.fromRadians(angleRad) / size);
    }

    @Override
    public String toString() {
        return label;
    }
}
