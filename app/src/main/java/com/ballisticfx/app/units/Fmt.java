package com.ballisticfx.app.units;

import java.util.Locale;

import com.ballisticfx.core.Units;

/** Number formatting for results, in the user's unit system. Always uses '.' as the decimal separator. */
public final class Fmt {
    private Fmt() {}

    public static String num(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    /** Like {@link #num} but drops trailing zeros ("2600.0" becomes "2600"). */
    public static String compact(double value, int decimals) {
        String s = num(value, decimals);
        if (s.indexOf('.') >= 0) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        return s.equals("-0") ? "0" : s;
    }

    public static String signed(double value, int decimals) {
        return String.format(Locale.ROOT, "%+." + decimals + "f", value);
    }

    // ---- distances / speeds / energy

    public static double rangeValue(UnitSystem s, double meters) {
        return Quantity.RANGE.toDisplay(s, meters);
    }

    public static String range(UnitSystem s, double meters) {
        return num(rangeValue(s, meters), 0) + " " + Quantity.RANGE.unit(s);
    }

    public static String height(UnitSystem s, double meters, int decimals) {
        return num(Quantity.HEIGHT.toDisplay(s, meters), decimals) + " " + Quantity.HEIGHT.unit(s);
    }

    /** Small distances such as bullet drop and wind drift: cm or inches. */
    public static double smallValue(UnitSystem s, double meters) {
        return s == UnitSystem.METRIC ? meters * 100.0 : meters / Units.INCH_TO_M;
    }

    public static String smallUnit(UnitSystem s) {
        return s == UnitSystem.METRIC ? "cm" : "in";
    }

    public static String small(UnitSystem s, double meters, boolean withSign) {
        double v = smallValue(s, meters);
        return (withSign ? signed(v, 1) : num(v, 1)) + " " + smallUnit(s);
    }

    public static double speedValue(UnitSystem s, double mps) {
        return Quantity.SPEED.toDisplay(s, mps);
    }

    public static String speed(UnitSystem s, double mps) {
        return num(speedValue(s, mps), 0) + " " + Quantity.SPEED.unit(s);
    }

    public static double energyValue(UnitSystem s, double joules) {
        return s == UnitSystem.METRIC ? joules : joules * Units.J_TO_FTLBF;
    }

    public static String energy(UnitSystem s, double joules) {
        return num(energyValue(s, joules), 0) + " " + (s == UnitSystem.METRIC ? "J" : "ft·lbf");
    }

    // ---- angles

    public static String angle(DialUnit unit, double radians) {
        return num(Math.abs(unit.fromRadians(radians)), unit == DialUnit.MOA ? 1 : 2) + " " + unit;
    }

    public static String angleSigned(DialUnit unit, double radians) {
        return signed(unit.fromRadians(radians), unit == DialUnit.MOA ? 1 : 2);
    }
}
