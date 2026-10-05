package com.ballisticfx.core;

/** One line of a range card: the dope for a given distance along the current line of sight. */
public record RangeRow(
        double rangeM,
        double elevationRad,
        double windageRad,
        double timeOfFlightS,
        double speedMps,
        double mach,
        double energyJ,
        double driftM) {

    public double elevationMoa() {
        return Units.radToMoa(elevationRad);
    }

    public double elevationMil() {
        return Units.radToMil(elevationRad);
    }

    public double windageMoa() {
        return Units.radToMoa(windageRad);
    }

    public double windageMil() {
        return Units.radToMil(windageRad);
    }
}
