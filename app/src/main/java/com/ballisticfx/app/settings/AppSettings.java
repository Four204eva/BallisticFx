package com.ballisticfx.app.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.ballisticfx.app.units.DialUnit;
import com.ballisticfx.app.units.UnitSystem;

/**
 * User preferences, kept in {@code ~/.ballisticfx/settings.properties}. Plain text on purpose: it holds the
 * user's own Google Maps key, which is a billing credential, so the file stays in the user's home folder and
 * is never written into the project.
 */
public final class AppSettings {
    private static final String KEY_MAPS = "googleMapsApiKey";
    private static final String KEY_UNITS = "unitSystem";
    private static final String KEY_DIAL = "dialUnit";
    private static final String KEY_CLICK = "clickValue";
    private static final String KEY_CARTRIDGE = "lastCartridge";

    private final Path file;
    private final Properties props = new Properties();

    private AppSettings(Path file) {
        this.file = file;
    }

    public static Path appDir() {
        return Path.of(System.getProperty("user.home"), ".ballisticfx");
    }

    public static AppSettings load() {
        AppSettings s = new AppSettings(appDir().resolve("settings.properties"));
        if (Files.isRegularFile(s.file)) {
            try (InputStream in = Files.newInputStream(s.file)) {
                s.props.load(in);
            } catch (IOException e) {
                System.err.println("Could not read settings (" + e.getMessage() + "); using defaults.");
            }
        }
        return s;
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "BallisticFX settings");
            }
        } catch (IOException e) {
            System.err.println("Could not save settings: " + e.getMessage());
        }
    }

    /** The stored key, or the GOOGLE_MAPS_API_KEY environment variable, or empty. */
    public String googleMapsApiKey() {
        String stored = props.getProperty(KEY_MAPS, "").trim();
        if (!stored.isEmpty()) return stored;
        String env = System.getenv("GOOGLE_MAPS_API_KEY");
        return env == null ? "" : env.trim();
    }

    /** Only the key typed into the app (not the environment variable). */
    public String storedGoogleMapsApiKey() {
        return props.getProperty(KEY_MAPS, "").trim();
    }

    public void setGoogleMapsApiKey(String key) {
        props.setProperty(KEY_MAPS, key == null ? "" : key.trim());
    }

    public UnitSystem unitSystem() {
        return parseEnum(UnitSystem.class, props.getProperty(KEY_UNITS), UnitSystem.IMPERIAL);
    }

    public void setUnitSystem(UnitSystem value) {
        props.setProperty(KEY_UNITS, value.name());
    }

    public DialUnit dialUnit() {
        return parseEnum(DialUnit.class, props.getProperty(KEY_DIAL), DialUnit.MIL);
    }

    public void setDialUnit(DialUnit value) {
        props.setProperty(KEY_DIAL, value.name());
    }

    public String clickValueLabel() {
        return props.getProperty(KEY_CLICK, "0.1 mil");
    }

    public void setClickValueLabel(String label) {
        props.setProperty(KEY_CLICK, label);
    }

    public String lastCartridge() {
        return props.getProperty(KEY_CARTRIDGE, "");
    }

    public void setLastCartridge(String name) {
        props.setProperty(KEY_CARTRIDGE, name == null ? "" : name);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String text, E fallback) {
        if (text == null) return fallback;
        try {
            return Enum.valueOf(type, text.trim());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
