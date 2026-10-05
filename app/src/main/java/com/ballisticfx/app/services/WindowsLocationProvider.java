package com.ballisticfx.app.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Reads the position from the Windows Location service (the same source Maps and Weather use, which
 * combines GPS, Wi-Fi and network positioning) through the .NET {@code GeoCoordinateWatcher} class,
 * driven by a short PowerShell script. No native code, no extra dependencies.
 *
 * <p>Needs Settings &gt; Privacy &amp; security &gt; Location on, with "Let desktop apps access your location" allowed.
 * Accuracy is whatever Windows provides: a GPS puck gives metres, a laptop on Wi-Fi may be tens to hundreds.
 */
final class WindowsLocationProvider implements LocationProvider {
    private static final String SCRIPT = """
            $ErrorActionPreference = 'Stop'
            try {
              Add-Type -AssemblyName System.Device
              $w = New-Object System.Device.Location.GeoCoordinateWatcher([System.Device.Location.GeoPositionAccuracy]::High)
              [void]$w.TryStart($false, [TimeSpan]::FromSeconds(12))
              $deadline = (Get-Date).AddSeconds(12)
              while ((Get-Date) -lt $deadline -and $w.Position.Location.IsUnknown) { Start-Sleep -Milliseconds 250 }
              $loc = $w.Position.Location
              if ($w.Permission -eq 'Denied') {
                Write-Output 'ERR|Windows denied location access. Turn on Settings > Privacy & security > Location, including "Let desktop apps access your location".'
              } elseif ($loc.IsUnknown) {
                Write-Output 'ERR|Windows could not determine a location. Check that Location is turned on and try again.'
              } else {
                $ci = [System.Globalization.CultureInfo]::InvariantCulture
                Write-Output ('OK|' + $loc.Latitude.ToString('R', $ci) + '|' + $loc.Longitude.ToString('R', $ci) + '|' + $loc.Altitude.ToString('R', $ci) + '|' + $loc.HorizontalAccuracy.ToString('R', $ci))
              }
            } catch {
              Write-Output ('ERR|' + $_.Exception.Message)
            }
            """;

    @Override
    public String name() {
        return "Windows Location";
    }

    @Override
    public Fix currentLocation() throws IOException, InterruptedException {
        String encoded = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
                .redirectErrorStream(true)
                .start();
        process.getOutputStream().close();
        if (!process.waitFor(25, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Timed out waiting for Windows Location.");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return parse(output);
    }

    /** Parses the script's single result line; separate from the process call so it can be unit tested. */
    static Fix parse(String output) throws IOException {
        for (String line : output.split("\\R")) {
            line = line.strip();
            if (line.startsWith("ERR|")) throw new IOException(line.substring(4));
            if (line.startsWith("OK|")) {
                String[] p = line.split("\\|");
                if (p.length != 5) break;
                try {
                    return new Fix(Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                            Double.parseDouble(p[3]), Double.parseDouble(p[4]));
                } catch (NumberFormatException e) {
                    break;
                }
            }
        }
        throw new IOException("Unexpected response from Windows Location.");
    }
}
