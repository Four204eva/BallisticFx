package com.ballisticfx.core;

/**
 * Air conditions at the shooter.
 *
 * @param temperatureC            air temperature, degrees Celsius
 * @param stationPressureHpa      absolute pressure where the shooter stands. NOT the sea-level-corrected
 *                                value weather reports quote (use the "station"/"surface" figure)
 * @param relativeHumidityPercent 0..100
 */
public record Atmosphere(double temperatureC, double stationPressureHpa, double relativeHumidityPercent) {
    private static final double R_DRY = 287.058;
    private static final double R_VAPOR = 461.495;
    private static final double GAMMA = 1.4;

    /** ICAO standard atmosphere at sea level (15 C, 1013.25 hPa, dry): 1.225 kg/m^3. */
    public static final Atmosphere STANDARD = new Atmosphere(15.0, 1013.25, 0.0);

    public Atmosphere {
        if (!(temperatureC > -90 && temperatureC < 70)) {
            throw new IllegalArgumentException("Temperature must be between -90 and 70 °C");
        }
        if (!(stationPressureHpa >= 200 && stationPressureHpa <= 1200)) {
            throw new IllegalArgumentException("Station pressure must be between 200 and 1200 hPa");
        }
        if (!(relativeHumidityPercent >= 0.0 && relativeHumidityPercent <= 100.0)) {
            throw new IllegalArgumentException("Humidity must be between 0 and 100 %");
        }
    }

    public double temperatureKelvin() {
        return temperatureC + 273.15;
    }

    /** Water-vapour partial pressure in hPa (Buck 1996 saturation curve). */
    public double vaporPressureHpa() {
        double t = temperatureC;
        double saturation = 6.1121 * Math.exp((18.678 - t / 234.5) * (t / (257.14 + t)));
        return saturation * relativeHumidityPercent / 100.0;
    }

    /** Moist-air density, kg/m^3. */
    public double densityKgM3() {
        double pv = vaporPressureHpa() * 100.0;
        double pd = stationPressureHpa * 100.0 - pv;
        double tk = temperatureKelvin();
        return pd / (R_DRY * tk) + pv / (R_VAPOR * tk);
    }

    /** Density as a fraction of the ICAO standard 1.225 kg/m^3. */
    public double densityRatio() {
        return densityKgM3() / 1.225;
    }

    /** Speed of sound, m/s, using the virtual temperature to account for humidity. */
    public double speedOfSoundMps() {
        double virtualTemp = temperatureKelvin() / (1.0 - 0.378 * vaporPressureHpa() / stationPressureHpa);
        return Math.sqrt(GAMMA * R_DRY * virtualTemp);
    }

    /** Density altitude in metres: the ISA altitude that would give this air density. */
    public double densityAltitudeM() {
        // Invert rho/rho0 = (1 - L h / T0)^4.2559 for the ISA troposphere.
        double ratio = densityRatio();
        return (288.15 / 0.0065) * (1.0 - Math.pow(ratio, 1.0 / 4.2559));
    }
}
