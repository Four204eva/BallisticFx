package com.ballisticfx.app.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ballisticfx.app.units.DialUnit;
import com.ballisticfx.app.units.UnitSystem;
import com.ballisticfx.core.Cartridge;
import com.ballisticfx.core.DragModel;

/** Runs against a throwaway user.home so the developer's real ~/.ballisticfx is never touched. */
class PersistenceTest {
    private String savedHome;

    @BeforeEach
    void redirectHome(@TempDir Path tmp) {
        savedHome = System.getProperty("user.home");
        System.setProperty("user.home", tmp.toString());
    }

    @AfterEach
    void restoreHome() {
        System.setProperty("user.home", savedHome);
    }

    @Test
    void customLoadsRoundTripThroughJson() throws IOException {
        Cartridge mine = new Cartridge("My .243 handload", CustomLoads.GROUP, DragModel.G7, 0.285, 105,
                0.243, 1.31, 2950.0, 8.0);
        new CustomLoads().put(mine);

        CustomLoads reloaded = new CustomLoads();
        assertEquals(1, reloaded.all().size());
        assertEquals(mine, reloaded.all().get(0));
        // The reloaded load must still be a usable projectile.
        assertEquals(DragModel.G7, reloaded.all().get(0).toProjectile().dragModel());
    }

    @Test
    void savingTheSameNameReplacesAndRemoveDeletes() throws IOException {
        CustomLoads loads = new CustomLoads();
        loads.put(new Cartridge("Load A", CustomLoads.GROUP, DragModel.G1, 0.40, 150, 0.308, 1.1, 2800, 12));
        loads.put(new Cartridge("load a", CustomLoads.GROUP, DragModel.G1, 0.45, 150, 0.308, 1.1, 2800, 12));
        assertEquals(1, loads.all().size());
        assertEquals(0.45, loads.all().get(0).bc(), 0.0);

        loads.remove(loads.all().get(0));
        assertTrue(new CustomLoads().all().isEmpty());
    }

    @Test
    void aCorruptCustomLoadsFileDoesNotCrashStartup() throws IOException {
        Path dir = AppSettings.appDir();
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("custom-loads.json"), "{ not json");
        assertTrue(new CustomLoads().all().isEmpty());
    }

    @Test
    void settingsRoundTripAndDefaults() {
        AppSettings fresh = AppSettings.load();
        assertEquals(UnitSystem.IMPERIAL, fresh.unitSystem());
        assertEquals(DialUnit.MIL, fresh.dialUnit());
        assertEquals("0.1 mil", fresh.clickValueLabel());
        assertEquals("", fresh.storedGoogleMapsApiKey());

        fresh.setUnitSystem(UnitSystem.METRIC);
        fresh.setDialUnit(DialUnit.MOA);
        fresh.setClickValueLabel("1/4 MOA");
        fresh.setGoogleMapsApiKey("  AIzaFakeKeyForTest  ");
        fresh.setLastCartridge(".308 Win 175 gr SMK");
        fresh.save();

        AppSettings again = AppSettings.load();
        assertEquals(UnitSystem.METRIC, again.unitSystem());
        assertEquals(DialUnit.MOA, again.dialUnit());
        assertEquals("1/4 MOA", again.clickValueLabel());
        assertEquals("AIzaFakeKeyForTest", again.storedGoogleMapsApiKey()); // trimmed
        assertEquals(".308 Win 175 gr SMK", again.lastCartridge());
    }

    @Test
    void garbageEnumValuesFallBackToDefaults() throws IOException {
        Path dir = AppSettings.appDir();
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("settings.properties"), "unitSystem=FURLONGS\ndialUnit=\n");
        AppSettings s = AppSettings.load();
        assertEquals(UnitSystem.IMPERIAL, s.unitSystem());
        assertEquals(DialUnit.MIL, s.dialUnit());
    }

    @Test
    void settingsFileLivesInTheUserHomeNotTheProject() {
        AppSettings s = AppSettings.load();
        s.setGoogleMapsApiKey("k");
        s.save();
        assertTrue(Files.isRegularFile(AppSettings.appDir().resolve("settings.properties")));
        assertTrue(AppSettings.appDir().startsWith(System.getProperty("user.home")));
    }
}
