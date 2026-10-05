package com.ballisticfx.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.ballisticfx.app.map.MapPane;
import com.ballisticfx.app.map.MapSurface.Which;
import com.ballisticfx.app.services.EnvironmentService;
import com.ballisticfx.app.services.LocationProvider;
import com.ballisticfx.app.services.OpenMeteoService;
import com.ballisticfx.app.settings.AppSettings;
import com.ballisticfx.app.settings.CustomLoads;
import com.ballisticfx.app.ui.InputPanel;
import com.ballisticfx.app.ui.InputPanel.InputException;
import com.ballisticfx.app.ui.RangeTableTab;
import com.ballisticfx.app.ui.SettingsDialog;
import com.ballisticfx.app.ui.SolutionBanner;
import com.ballisticfx.app.ui.TrajectoryTab;
import com.ballisticfx.app.ui.UnitField;
import com.ballisticfx.app.ui.WindCompass;
import com.ballisticfx.app.units.ClickValue;
import com.ballisticfx.app.units.DialUnit;
import com.ballisticfx.app.units.Fmt;
import com.ballisticfx.app.units.Quantity;
import com.ballisticfx.app.units.UnitSystem;
import com.ballisticfx.core.BallisticSolver;
import com.ballisticfx.core.FiringSolution;
import com.ballisticfx.core.Geo;
import com.ballisticfx.core.NoSolutionException;
import com.ballisticfx.core.RangeRow;
import com.ballisticfx.core.ShotInput;
import com.ballisticfx.core.Units;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * BallisticFX desktop app. Pick the shooter and target on the map (or use GPS / type coordinates), choose a
 * cartridge, set the weather, and read the firing solution; drag the flight-time slider to see velocity and
 * height above the muzzle at every point of the flight.
 *
 * <p>Fields below are package-private so the screenshot/test harness in this package can drive the UI.
 */
public final class MainApp extends Application {
    private final AppSettings settings = AppSettings.load();
    private final CustomLoads customLoads = new CustomLoads();
    private final ObjectProperty<UnitSystem> system = new SimpleObjectProperty<>(settings.unitSystem());
    private final ObjectProperty<DialUnit> dial = new SimpleObjectProperty<>(settings.dialUnit());
    private final EnvironmentService environment = new OpenMeteoService();
    private final LocationProvider gps = LocationProvider.forThisPlatform();

    private final ExecutorService solver = Executors.newSingleThreadExecutor(r -> daemon(r, "solver"));
    private final ExecutorService lookups = Executors.newCachedThreadPool(r -> daemon(r, "lookup"));
    private final AtomicLong generation = new AtomicLong();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(180));

    InputPanel panel;
    MapPane map;
    private WindCompass compass;
    private final SolutionBanner banner = new SolutionBanner();
    private final TrajectoryTab trajectoryTab = new TrajectoryTab();
    private final RangeTableTab rangeTab = new RangeTableTab();
    private final Label status = new Label();

    private record Result(InputPanel.Request request, FiringSolution solution, List<RangeRow> rows) {}

    private Result lastResult;

    @Override
    public void start(Stage stage) {
        panel = new InputPanel(system, customLoads);
        panel.selectCartridgeByName(settings.lastCartridge());
        panel.clickValue.setValue(ClickValue.byLabel(settings.clickValueLabel()));

        map = new MapPane(settings.googleMapsApiKey());
        compass = new WindCompass();
        map.addOverlay(compass, Pos.BOTTOM_LEFT);
        map.setOnPointPicked(this::onMapPicked);
        map.setOnNotice(message -> showStatus(message, true));

        TabPane tabs = new TabPane(
                tab("Trajectory", scroll(trajectoryTab)),
                tab("Range card", rangeTab));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        VBox results = new VBox(banner, tabs);
        results.setMinHeight(0);

        SplitPane center = new SplitPane(map, results);
        center.setOrientation(Orientation.VERTICAL);
        center.setDividerPositions(0.40);
        SplitPane.setResizableWithParent(map, true);

        SplitPane main = new SplitPane(panel, center);
        main.setDividerPositions(0.25);
        SplitPane.setResizableWithParent(panel, false);

        BorderPane root = new BorderPane(main);
        root.setTop(buildToolbar(stage));

        wireEvents();

        Scene scene = new Scene(root, 1440, 900);
        scene.getStylesheets().add(MainApp.class.getResource("app.css").toExternalForm());
        stage.setTitle("BallisticFX — ballistic calculator");
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();

        updateCompass();
        solveNow();
        showStatus(map.providerName().equals("Google Maps")
                ? "Map: Google Maps. Click the map to place the shooter, then the target."
                : "Map: OpenStreetMap (add a Google Maps key under Settings). Click the map to place the shooter, then the target.",
                false);
    }

    // ------------------------------------------------------------------ layout

    private HBox buildToolbar(Stage stage) {
        Label title = new Label("BallisticFX");
        title.getStyleClass().add("app-title");

        Button settingsButton = new Button("⚙  Maps key…");
        settingsButton.setOnAction(e -> openSettings(stage));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        status.getStyleClass().add("status");
        status.setMaxWidth(Double.MAX_VALUE);
        status.setWrapText(false);

        HBox bar = new HBox(14, title, label("Units"), segmented(system, UnitSystem.values()),
                label("Dial in"), segmented(dial, DialUnit.values()), settingsButton, spacer, status);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 12, 6, 12));
        bar.getStyleClass().add("toolbar");
        HBox.setHgrow(status, Priority.SOMETIMES);
        return bar;
    }

    private void wireEvents() {
        debounce.setOnFinished(e -> solveNow());
        panel.setOnChange(() -> {
            updateCompass();
            debounce.playFromStart();
        });
        system.addListener((o, was, now) -> {
            updateCompass();
            solveNow(); // re-solve (milliseconds) so the range card gets round steps in the new unit
        });
        dial.addListener((o, was, now) -> rerenderForUnits());
        panel.clickValue.valueProperty().addListener((o, was, now) -> rerenderForUnits());

        commitToMap(Which.SHOOTER, panel.shooterLat, panel.shooterLon);
        commitToMap(Which.TARGET, panel.targetLat, panel.targetLon);
        panel.useMyLocation.setOnAction(e -> useMyLocation());
        panel.getElevations.setOnAction(e -> {
            fetchElevation(Which.SHOOTER, true);
            fetchElevation(Which.TARGET, true);
        });
        panel.fetchWeather.setOnAction(e -> fetchWeather());
    }

    private void openSettings(Stage stage) {
        boolean envOnly = settings.storedGoogleMapsApiKey().isEmpty() && !settings.googleMapsApiKey().isEmpty();
        Optional<String> key = SettingsDialog.askMapsKey(stage, settings.storedGoogleMapsApiKey(), envOnly);
        key.ifPresent(k -> {
            settings.setGoogleMapsApiKey(k);
            settings.save();
            map.setApiKey(settings.googleMapsApiKey());
            showStatus(settings.googleMapsApiKey().isEmpty()
                    ? "Map key cleared: using OpenStreetMap."
                    : "Loading Google Maps with your key…", false);
        });
    }

    // ------------------------------------------------------------------ map <-> fields

    private void onMapPicked(MapPane.PointPicked picked) {
        UnitField lat = picked.which() == Which.SHOOTER ? panel.shooterLat : panel.targetLat;
        UnitField lon = picked.which() == Which.SHOOTER ? panel.shooterLon : panel.targetLon;
        lat.setBase(picked.lat());
        lon.setBase(picked.lon());
        if (picked.which() == Which.SHOOTER && panel.targetLat.isBlank()) {
            map.pickModeProperty().set(Which.TARGET); // natural next step
        }
        if (panel.autoElevation.isSelected()) fetchElevation(picked.which(), false);
    }

    /** Typing coordinates by hand moves the map marker once the value is committed (Enter or focus loss). */
    private void commitToMap(Which which, UnitField lat, UnitField lon) {
        Runnable push = () -> {
            if (lat.isInvalid() || lon.isInvalid() || Double.isNaN(lat.base()) || Double.isNaN(lon.base())) return;
            if (Math.abs(lat.base()) > 90 || Math.abs(lon.base()) > 180) return;
            map.setPoint(which, lat.base(), lon.base());
            if (panel.autoElevation.isSelected()) fetchElevation(which, false);
        };
        for (UnitField f : List.of(lat, lon)) {
            f.setOnAction(e -> push.run());
            f.focusedProperty().addListener((o, was, now) -> {
                if (!now) push.run();
            });
        }
    }

    private void useMyLocation() {
        panel.useMyLocation.setDisable(true);
        showStatus("Getting your location from " + gps.name() + "…", false);
        background(gps::currentLocation, fix -> {
            panel.useMyLocation.setDisable(false);
            panel.shooterLat.setBase(fix.lat());
            panel.shooterLon.setBase(fix.lon());
            map.setPoint(Which.SHOOTER, fix.lat(), fix.lon());
            if (panel.targetLat.isBlank()) map.pickModeProperty().set(Which.TARGET);
            // Ground elevation comes from the same terrain model for shooter and target, so their difference
            // is consistent; device altitude is often missing or measured from a different datum.
            if (panel.autoElevation.isSelected()) fetchElevation(Which.SHOOTER, false);
            String accuracy = Double.isNaN(fix.accuracyM()) ? ""
                    : " (accurate to about " + Fmt.num(Quantity.HEIGHT.toDisplay(system.get(), fix.accuracyM()), 0)
                    + " " + Quantity.HEIGHT.unit(system.get()) + ")";
            showStatus("Shooter set from " + gps.name() + accuracy + ".", false);
        }, err -> {
            panel.useMyLocation.setDisable(false);
            showStatus(err.getMessage(), true);
        });
    }

    private void fetchElevation(Which which, boolean reportMissing) {
        UnitField latField = which == Which.SHOOTER ? panel.shooterLat : panel.targetLat;
        UnitField lonField = which == Which.SHOOTER ? panel.shooterLon : panel.targetLon;
        UnitField altField = which == Which.SHOOTER ? panel.shooterAlt : panel.targetAlt;
        double lat = latField.base();
        double lon = lonField.base();
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            if (reportMissing) showStatus("Set the " + (which == Which.SHOOTER ? "shooter" : "target") + " position first.", true);
            return;
        }
        background(() -> environment.elevationM(lat, lon), altitude -> {
            altField.setBase(altitude);
            showStatus(String.format("%s ground elevation %s (terrain model, about 90 m resolution).",
                    which == Which.SHOOTER ? "Shooter" : "Target",
                    Fmt.num(Quantity.HEIGHT.toDisplay(system.get(), altitude), 0) + " " + Quantity.HEIGHT.unit(system.get())),
                    false);
        }, err -> showStatus("Elevation lookup failed: " + err.getMessage(), true));
    }

    private void fetchWeather() {
        double lat = panel.shooterLat.base();
        double lon = panel.shooterLon.base();
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            showStatus("Place the shooter first, then fetch the weather there.", true);
            return;
        }
        showStatus("Fetching weather…", false);
        background(() -> environment.currentWeather(lat, lon), w -> {
            panel.temperature.setBase(w.temperatureC());
            panel.pressure.setBase(w.stationPressureHpa());
            panel.humidity.setBase(w.humidityPercent());
            panel.windSpeed.setBase(w.windSpeedMps());
            panel.windFrom.setBase(w.windFromDegrees());
            showStatus("Weather updated from Open-Meteo. The wind is a 10 m reading at the shooter; "
                    + "wind along the bullet's path and at the target will differ.", false);
        }, err -> showStatus("Weather lookup failed: " + err.getMessage(), true));
    }

    private void updateCompass() {
        double speed = panel.windSpeedMps();
        String text = system.get() == UnitSystem.METRIC
                ? Fmt.num(speed, 1) + " m/s"
                : Fmt.num(speed / Units.MPH_TO_MPS, 1) + " mph";
        compass.setWind(panel.windFromDegrees(), speed, text);
        boolean havePoints = !Double.isNaN(panel.shooterLat.base()) && !Double.isNaN(panel.shooterLon.base())
                && !Double.isNaN(panel.targetLat.base()) && !Double.isNaN(panel.targetLon.base())
                && Math.abs(panel.shooterLat.base()) <= 90 && Math.abs(panel.targetLat.base()) <= 90;
        compass.setBearing(havePoints
                ? Geo.bearingDegrees(panel.shooterLat.base(), panel.shooterLon.base(),
                        panel.targetLat.base(), panel.targetLon.base())
                : Double.NaN);
    }

    // ------------------------------------------------------------------ solving

    private void solveNow() {
        InputPanel.Request request;
        try {
            request = panel.buildShot();
        } catch (InputException e) {
            generation.incrementAndGet(); // drop any solve still in flight
            lastResult = null;
            banner.clear(e.getMessage(), !e.isIncomplete());
            trajectoryTab.clear();
            rangeTab.clear();
            return;
        }
        long id = generation.incrementAndGet();
        UnitSystem units = system.get();
        solver.execute(() -> {
            try {
                ShotInput input = request.input();
                FiringSolution solution = BallisticSolver.solve(input);
                List<RangeRow> rows = BallisticSolver.rangeTable(input,
                        tableRanges(input.geometry().horizontalRangeM(), units));
                Result result = new Result(request, solution, rows);
                Platform.runLater(() -> {
                    if (id == generation.get()) {
                        lastResult = result;
                        render(result, true);
                    }
                });
            } catch (NoSolutionException | IllegalArgumentException e) {
                Platform.runLater(() -> {
                    if (id == generation.get()) {
                        lastResult = null;
                        banner.clear(e.getMessage(), true);
                        trajectoryTab.clear();
                        rangeTab.clear();
                    }
                });
            }
        });
    }

    /** Range-card distances: round numbers in the user's unit, from one step up to ~1.3x the target, plus the target. */
    private static List<Double> tableRanges(double targetRangeM, UnitSystem units) {
        double toMeters = units == UnitSystem.METRIC ? 1.0 : Units.YARD_TO_M;
        double step = niceStep(targetRangeM / toMeters / 10.0) * toMeters;
        List<Double> ranges = new ArrayList<>();
        for (double r = step; r <= targetRangeM * 1.3 + 1e-6; r += step) {
            if (Math.abs(r - targetRangeM) > 0.5) ranges.add(r);
        }
        ranges.add(targetRangeM);
        ranges.sort(Double::compare);
        return ranges;
    }

    private static double niceStep(double raw) {
        if (!(raw > 0)) return 1.0;
        double exp = Math.pow(10, Math.floor(Math.log10(raw)));
        double f = raw / exp;
        return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * exp;
    }

    private void render(Result r, boolean newTrajectory) {
        InputPanel.Request request = r.request();
        ShotInput input = request.input();
        UnitSystem units = system.get();
        ClickValue click = panel.selectedClickValue();
        banner.show(r.solution(), input.geometry(), input.wind(), request.rawHeightDifferenceM(),
                request.slantRangeM(), units, dial.get(), click);
        if (newTrajectory) {
            trajectoryTab.show(r.solution().trajectory(), input.geometry().heightDifferenceM(),
                    input.atmosphere().speedOfSoundMps(), units);
            rangeTab.show(r.rows(), input.geometry().horizontalRangeM(), units, dial.get(), click);
        } else {
            trajectoryTab.setUnitSystem(units);
            rangeTab.restyle(units, dial.get(), click);
        }
    }

    /** Dial unit or click size changed: re-render the existing answer without re-solving. */
    private void rerenderForUnits() {
        if (lastResult != null) render(lastResult, false);
    }

    // ------------------------------------------------------------------ helpers

    private <T> void background(ThrowingSupplier<T> work, Consumer<T> onSuccess, Consumer<Exception> onFailure) {
        lookups.execute(() -> {
            try {
                T value = work.get();
                Platform.runLater(() -> onSuccess.accept(value));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Platform.runLater(() -> onFailure.accept(e));
            }
        });
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    void showStatus(String text, boolean error) {
        status.setText(text);
        status.getStyleClass().removeAll("status-error", "status-ok");
        status.getStyleClass().add(error ? "status-error" : "status-ok");
    }

    private void shutdown() {
        settings.setUnitSystem(system.get());
        settings.setDialUnit(dial.get());
        settings.setClickValueLabel(panel.selectedClickValue().label());
        settings.setLastCartridge(panel.selectedCartridgeName());
        settings.save();
        solver.shutdownNow();
        lookups.shutdownNow();
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    private static Tab tab(String title, javafx.scene.Node content) {
        Tab t = new Tab(title, content);
        t.setClosable(false);
        return t;
    }

    private static ScrollPane scroll(javafx.scene.Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("flat-scroll");
        return sp;
    }

    private static Label label(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("dim");
        return l;
    }

    @SuppressWarnings("unchecked")
    private static <E> HBox segmented(ObjectProperty<E> property, E[] values) {
        ToggleGroup group = new ToggleGroup();
        HBox box = new HBox();
        box.getStyleClass().add("segmented");
        for (E value : values) {
            ToggleButton button = new ToggleButton(value.toString());
            button.setToggleGroup(group);
            button.setUserData(value);
            button.getStyleClass().add("segment");
            button.setSelected(value == property.get());
            box.getChildren().add(button);
        }
        group.selectedToggleProperty().addListener((o, was, now) -> {
            if (now == null) was.setSelected(true);
            else property.set((E) now.getUserData());
        });
        return box;
    }

    public static void main(String[] args) {
        JdkCheck.exitIfIncompatible();
        launch(args);
    }
}
