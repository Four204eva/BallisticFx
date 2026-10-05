package com.ballisticfx.app.units;

/**
 * A kind of measurement with a metric and an imperial display unit. Values are held in the metric
 * <em>base</em> unit; imperial = base * scale + offset (the offset only matters for temperature).
 *
 * <p>Base units: m for RANGE/HEIGHT, mm for LENGTH, m/s for SPEED/WIND, g for MASS, deg C, hPa,
 * and m/s per deg C for MV_SENSITIVITY.
 */
public enum Quantity {
    NONE("", "", 1.0, 0.0),
    DEGREES("°", "°", 1.0, 0.0),
    PERCENT("%", "%", 1.0, 0.0),
    RANGE("m", "yd", 1.0 / 0.9144, 0.0),
    HEIGHT("m", "ft", 1.0 / 0.3048, 0.0),
    LENGTH("mm", "in", 1.0 / 25.4, 0.0),
    SPEED("m/s", "fps", 1.0 / 0.3048, 0.0),
    WIND("m/s", "mph", 1.0 / 0.44704, 0.0),
    MASS("g", "gr", 1.0 / 0.06479891, 0.0),
    TEMPERATURE("°C", "°F", 1.8, 32.0),
    PRESSURE("hPa", "inHg", 1.0 / 33.8638866667, 0.0),
    /** Powder temperature sensitivity: 1 m/s per deg C = 1.8227 fps per deg F. */
    MV_SENSITIVITY("m/s/°C", "fps/°F", 1.0 / (0.3048 * 1.8), 0.0);

    private final String metricUnit;
    private final String imperialUnit;
    private final double scale;
    private final double offset;

    Quantity(String metricUnit, String imperialUnit, double scale, double offset) {
        this.metricUnit = metricUnit;
        this.imperialUnit = imperialUnit;
        this.scale = scale;
        this.offset = offset;
    }

    public String unit(UnitSystem system) {
        return system == UnitSystem.METRIC ? metricUnit : imperialUnit;
    }

    /** Base (metric) value to the number shown in {@code system}. */
    public double toDisplay(UnitSystem system, double base) {
        return system == UnitSystem.METRIC ? base : base * scale + offset;
    }

    /** A number typed in {@code system} to the base (metric) value. */
    public double toBase(UnitSystem system, double display) {
        return system == UnitSystem.METRIC ? display : (display - offset) / scale;
    }
}
