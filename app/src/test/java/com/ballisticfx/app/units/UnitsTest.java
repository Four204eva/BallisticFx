package com.ballisticfx.app.units;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class UnitsTest {

    @Test
    void knownImperialConversions() {
        assertEquals(100.0, Quantity.RANGE.toDisplay(UnitSystem.IMPERIAL, 91.44), 1e-9);
        assertEquals(100.0 * 0.9144, Quantity.RANGE.toBase(UnitSystem.IMPERIAL, 100.0), 1e-9);
        assertEquals(1.5, Quantity.LENGTH.toDisplay(UnitSystem.IMPERIAL, 38.1), 1e-9);
        assertEquals(2600.0, Quantity.SPEED.toDisplay(UnitSystem.IMPERIAL, 792.48), 1e-6);
        assertEquals(175.0, Quantity.MASS.toDisplay(UnitSystem.IMPERIAL, 175 * 0.06479891), 1e-9);
        assertEquals(10.0, Quantity.WIND.toDisplay(UnitSystem.IMPERIAL, 4.4704), 1e-9);
        assertEquals(29.92, Quantity.PRESSURE.toDisplay(UnitSystem.IMPERIAL, 1013.21), 0.001);
        // 1 m/s per deg C is 1.8227 fps per deg F.
        assertEquals(1.8227, Quantity.MV_SENSITIVITY.toDisplay(UnitSystem.IMPERIAL, 1.0), 1e-4);
    }

    @Test
    void temperatureUsesAnOffsetNotJustAScale() {
        assertEquals(59.0, Quantity.TEMPERATURE.toDisplay(UnitSystem.IMPERIAL, 15.0), 1e-9);
        assertEquals(15.0, Quantity.TEMPERATURE.toBase(UnitSystem.IMPERIAL, 59.0), 1e-9);
        assertEquals(-40.0, Quantity.TEMPERATURE.toDisplay(UnitSystem.IMPERIAL, -40.0), 1e-9);
        assertEquals(212.0, Quantity.TEMPERATURE.toDisplay(UnitSystem.IMPERIAL, 100.0), 1e-9);
    }

    @Test
    void metricIsTheIdentity() {
        for (Quantity q : Quantity.values()) {
            assertEquals(123.456, q.toDisplay(UnitSystem.METRIC, 123.456), 0.0, q.name());
            assertEquals(123.456, q.toBase(UnitSystem.METRIC, 123.456), 0.0, q.name());
        }
    }

    @Test
    void everyQuantityRoundTripsInBothSystems() {
        for (Quantity q : Quantity.values()) {
            for (UnitSystem s : UnitSystem.values()) {
                double base = 37.25;
                assertEquals(base, q.toBase(s, q.toDisplay(s, base)), 1e-9, q + " " + s);
            }
        }
    }

    @Test
    void unitLabels() {
        assertEquals("m", Quantity.RANGE.unit(UnitSystem.METRIC));
        assertEquals("yd", Quantity.RANGE.unit(UnitSystem.IMPERIAL));
        assertEquals("°F", Quantity.TEMPERATURE.unit(UnitSystem.IMPERIAL));
        assertEquals("inHg", Quantity.PRESSURE.unit(UnitSystem.IMPERIAL));
    }

    @Test
    void compactFormattingDropsTrailingZerosAndNegativeZero() {
        assertEquals("2600", Fmt.compact(2600.0, 1));
        assertEquals("0.243", Fmt.compact(0.243, 3));
        assertEquals("1.5", Fmt.compact(1.50, 2));
        assertEquals("0", Fmt.compact(-0.0004, 2));
        assertEquals("-12.25", Fmt.compact(-12.25, 2));
        assertEquals("1013.25", Fmt.compact(1013.25, 2));
    }

    @Test
    void formattingIgnoresTheDefaultLocale() {
        java.util.Locale saved = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY); // decimal comma
            assertEquals("1.50", Fmt.num(1.5, 2));
            assertEquals("+1.5", Fmt.signed(1.5, 1));
        } finally {
            java.util.Locale.setDefault(saved);
        }
    }

    @Test
    void smallDistancesAreCentimetresOrInches() {
        assertEquals(25.4, Fmt.smallValue(UnitSystem.IMPERIAL, 0.645160), 1e-3);
        assertEquals(50.0, Fmt.smallValue(UnitSystem.METRIC, 0.5), 1e-9);
        assertEquals("cm", Fmt.smallUnit(UnitSystem.METRIC));
        assertEquals("in", Fmt.smallUnit(UnitSystem.IMPERIAL));
    }

    @Test
    void clickCountsAreWholeClicksOfTheRightSize() {
        double oneMil = 0.001;
        assertEquals(10, ClickValue.byLabel("0.1 mil").clicks(oneMil));
        assertEquals(20, ClickValue.byLabel("0.05 mil").clicks(oneMil));
        double oneMoa = Math.toRadians(1.0 / 60.0);
        assertEquals(4, ClickValue.byLabel("1/4 MOA").clicks(oneMoa));
        assertEquals(8, ClickValue.byLabel("1/8 MOA").clicks(oneMoa));
        assertEquals(1, ClickValue.byLabel("1 MOA").clicks(oneMoa));
        assertEquals(0, ClickValue.byLabel("0.1 mil").clicks(0.00004)); // rounds to nearest click
        assertSame(ClickValue.ALL.get(0), ClickValue.byLabel("no such label"));
    }

    @Test
    void dialUnitConversions() {
        assertEquals(1.0, DialUnit.MIL.fromRadians(0.001), 1e-12);
        assertEquals(60.0, DialUnit.MOA.fromRadians(Math.toRadians(1.0)), 1e-9);
        assertEquals(DialUnit.MIL, DialUnit.MOA.other());
    }
}
