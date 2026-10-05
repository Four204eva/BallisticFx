package com.ballisticfx.app.services;

import java.io.IOException;

/**
 * Looks up conditions at a map position. Blocking calls: run them off the UI thread.
 * The desktop implementation uses Open-Meteo; an Android port can supply its own.
 */
public interface EnvironmentService {

    /**
     * Current conditions. {@code windFromDegrees} is the compass bearing the wind blows FROM and the wind is
     * the 10 m reading, so real wind along the bullet's path will differ.
     */
    record Weather(double temperatureC, double stationPressureHpa, double humidityPercent,
                   double windSpeedMps, double windFromDegrees) {}

    Weather currentWeather(double lat, double lon) throws IOException, InterruptedException;

    /** Ground elevation in metres above sea level. */
    double elevationM(double lat, double lon) throws IOException, InterruptedException;
}
