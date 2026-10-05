package com.ballisticfx.app.services;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Free, key-less lookups against Open-Meteo: current weather and a ~90 m terrain elevation model. */
public final class OpenMeteoService implements EnvironmentService {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public Weather currentWeather(double lat, double lon) throws IOException, InterruptedException {
        String url = String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f"
                        + "&current=temperature_2m,relative_humidity_2m,surface_pressure,wind_speed_10m,wind_direction_10m"
                        + "&wind_speed_unit=ms", lat, lon);
        JsonNode current = get(url).path("current");
        if (current.isMissingNode() || !current.has("surface_pressure")) {
            throw new IOException("Unexpected weather response");
        }
        return new Weather(
                current.path("temperature_2m").asDouble(),
                current.path("surface_pressure").asDouble(),
                current.path("relative_humidity_2m").asDouble(),
                current.path("wind_speed_10m").asDouble(),
                current.path("wind_direction_10m").asDouble());
    }

    @Override
    public double elevationM(double lat, double lon) throws IOException, InterruptedException {
        String url = String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/elevation?latitude=%.5f&longitude=%.5f", lat, lon);
        JsonNode elevation = get(url).path("elevation");
        if (!elevation.isArray() || elevation.isEmpty()) throw new IOException("Unexpected elevation response");
        return elevation.get(0).asDouble();
    }

    private JsonNode get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " from " + URI.create(url).getHost());
        }
        return json.readTree(response.body());
    }
}
