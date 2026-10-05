package com.ballisticfx.app.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.ballisticfx.core.Cartridge;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** The user's own saved loads, kept as JSON in {@code ~/.ballisticfx/custom-loads.json}. */
public final class CustomLoads {
    public static final String GROUP = "Custom";

    private final Path file = AppSettings.appDir().resolve("custom-loads.json");
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final List<Cartridge> loads = new ArrayList<>();

    public CustomLoads() {
        if (Files.isRegularFile(file)) {
            try {
                loads.addAll(json.readValue(file.toFile(), new TypeReference<List<Cartridge>>() {}));
            } catch (IOException e) {
                System.err.println("Could not read custom loads (" + e.getMessage() + "); starting empty.");
            }
        }
    }

    public List<Cartridge> all() {
        return List.copyOf(loads);
    }

    /** Adds the load, replacing any custom load of the same name. */
    public void put(Cartridge load) throws IOException {
        loads.removeIf(c -> c.name().equalsIgnoreCase(load.name()));
        loads.add(load);
        write();
    }

    public void remove(Cartridge load) throws IOException {
        loads.removeIf(c -> c.name().equalsIgnoreCase(load.name()));
        write();
    }

    private void write() throws IOException {
        Files.createDirectories(file.getParent());
        json.writeValue(file.toFile(), loads);
    }
}
