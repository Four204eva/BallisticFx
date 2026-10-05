package com.ballisticfx.app.units;

/** Which set of units the UI shows. Everything is stored in metric base units; this only affects display. */
public enum UnitSystem {
    METRIC("Metric"),
    IMPERIAL("Imperial");

    private final String label;

    UnitSystem(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
