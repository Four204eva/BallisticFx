package com.ballisticfx.app.units;

import com.ballisticfx.core.Units;

/** Angular unit used for the headline turret numbers. */
public enum DialUnit {
    MOA("MOA"),
    MIL("MIL");

    private final String label;

    DialUnit(String label) {
        this.label = label;
    }

    public double fromRadians(double rad) {
        return this == MOA ? Units.radToMoa(rad) : Units.radToMil(rad);
    }

    public DialUnit other() {
        return this == MOA ? MIL : MOA;
    }

    @Override
    public String toString() {
        return label;
    }
}
