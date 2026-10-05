package com.ballisticfx.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ballisticfx.app.settings.CustomLoads;
import com.ballisticfx.app.units.ClickValue;
import com.ballisticfx.app.units.Fmt;
import com.ballisticfx.app.units.Quantity;
import com.ballisticfx.app.units.UnitSystem;
import com.ballisticfx.core.Atmosphere;
import com.ballisticfx.core.Cartridge;
import com.ballisticfx.core.Cartridges;
import com.ballisticfx.core.DragModel;
import com.ballisticfx.core.Geo;
import com.ballisticfx.core.Projectile;
import com.ballisticfx.core.Rifle;
import com.ballisticfx.core.ShotGeometry;
import com.ballisticfx.core.ShotInput;
import com.ballisticfx.core.SolverOptions;
import com.ballisticfx.core.Units;
import com.ballisticfx.core.Wind;

import javafx.beans.property.ObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Every input the user can change, grouped into sections, plus the validation that turns the form into a
 * core {@link ShotInput}. Values are held in metric base units (see {@link UnitField}).
 */
public final class InputPanel extends ScrollPane {

    /** A form problem. {@code incomplete} means "not filled in yet" (shown as a hint, not an error). */
    public static final class InputException extends Exception {
        private static final long serialVersionUID = 1L;
        private final boolean incomplete;

        InputException(String message, boolean incomplete) {
            super(message);
            this.incomplete = incomplete;
        }

        public boolean isIncomplete() {
            return incomplete;
        }
    }

    /** A validated shot, plus the raw numbers the UI wants to show. */
    public record Request(ShotInput input, double rawHeightDifferenceM, double slantRangeM) {}

    private final ObjectProperty<UnitSystem> system;
    private final CustomLoads customLoads;
    private Runnable onChange = () -> {};
    private boolean suppress;

    // ---- locations
    public final UnitField shooterLat;
    public final UnitField shooterLon;
    public final UnitField shooterAlt;
    public final UnitField muzzleHeight;
    public final UnitField targetLat;
    public final UnitField targetLon;
    public final UnitField targetAlt;
    public final UnitField aimHeight;
    public final CheckBox autoElevation = new CheckBox("Look up ground elevation when a point is set");
    public final Button useMyLocation = new Button("Use my location (GPS)");
    public final Button getElevations = new Button("Get elevations");

    // ---- cartridge
    public final ComboBox<Cartridge> presets = new ComboBox<>();
    public final ChoiceBox<DragModel> dragModel = new ChoiceBox<>();
    public final UnitField bc;
    public final UnitField weight;
    public final UnitField diameter;
    public final UnitField length;
    public final UnitField muzzleVelocity;
    public final UnitField mvTempSensitivity;
    private final Button saveLoad = new Button("Save as…");
    private final Button deleteLoad = new Button("Delete");

    // ---- rifle and scope
    public final UnitField sightHeight;
    public final UnitField zeroRange;
    public final UnitField twist;
    public final CheckBox rightTwist = new CheckBox("Right-hand twist");
    public final ComboBox<ClickValue> clickValue = new ComboBox<>();
    private final CheckBox zeroSameAsToday = new CheckBox("Zeroed in today's conditions");
    private final UnitField zeroTemperature;
    private final UnitField zeroPressure;
    private final UnitField zeroHumidity;

    // ---- weather
    public final UnitField temperature;
    public final UnitField pressure;
    public final UnitField humidity;
    public final UnitField windSpeed;
    public final UnitField windFrom;
    public final Button fetchWeather = new Button("Fetch live weather");
    private final Label airInfo = new Label();

    // ---- options
    private final CheckBox coriolis = new CheckBox("Coriolis / Eötvös (uses latitude and bearing)");
    private final CheckBox spinDrift = new CheckBox("Spin drift");
    private final CheckBox curvature = new CheckBox("Earth curvature (matters beyond ~1 km)");

    private final List<UnitField> allFields = new ArrayList<>();

    public InputPanel(ObjectProperty<UnitSystem> system, CustomLoads customLoads) {
        this.system = system;
        this.customLoads = customLoads;

        shooterLat = field(Quantity.DEGREES, 6);
        shooterLon = field(Quantity.DEGREES, 6);
        shooterAlt = field(Quantity.HEIGHT, 1);
        muzzleHeight = field(Quantity.HEIGHT, 2);
        targetLat = field(Quantity.DEGREES, 6);
        targetLon = field(Quantity.DEGREES, 6);
        targetAlt = field(Quantity.HEIGHT, 1);
        aimHeight = field(Quantity.HEIGHT, 2);

        bc = field(Quantity.NONE, 3);
        weight = field(Quantity.MASS, 1);
        diameter = field(Quantity.LENGTH, 3);
        length = field(Quantity.LENGTH, 3);
        muzzleVelocity = field(Quantity.SPEED, 1);
        mvTempSensitivity = field(Quantity.MV_SENSITIVITY, 2);

        sightHeight = field(Quantity.LENGTH, 2);
        zeroRange = field(Quantity.RANGE, 1);
        twist = field(Quantity.LENGTH, 1);
        zeroTemperature = field(Quantity.TEMPERATURE, 1);
        zeroPressure = field(Quantity.PRESSURE, 2);
        zeroHumidity = field(Quantity.PERCENT, 0);

        temperature = field(Quantity.TEMPERATURE, 1);
        pressure = field(Quantity.PRESSURE, 2);
        humidity = field(Quantity.PERCENT, 0);
        windSpeed = field(Quantity.WIND, 1);
        windFrom = field(Quantity.DEGREES, 0);

        setDefaults();
        wireChangeEvents();

        VBox box = new VBox(8,
                section("Locations", locationsGrid(), true),
                section("Cartridge", cartridgeGrid(), true),
                section("Rifle & scope", rifleGrid(), true),
                section("Weather", weatherGrid(), true),
                section("Options", optionsBox(), false));
        box.setPadding(new Insets(8));
        setContent(box);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setMinWidth(330);
        getStyleClass().add("input-panel");
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    // ------------------------------------------------------------------ defaults and events

    private void setDefaults() {
        UnitSystem s = system.get();
        shooterAlt.setBase(0);
        targetAlt.setBase(0);
        muzzleHeight.setBase(1.0);
        aimHeight.setBase(1.0);
        autoElevation.setSelected(true);

        dragModel.getItems().setAll(DragModel.values());
        refreshPresetItems();
        applyCartridge(Cartridges.defaultCartridge());
        presets.setValue(Cartridges.defaultCartridge());
        mvTempSensitivity.setBase(0);

        sightHeight.setBase(1.5 * 25.4);
        zeroRange.setBase(s == UnitSystem.IMPERIAL ? 100 * Units.YARD_TO_M : 100.0);
        rightTwist.setSelected(true);
        clickValue.getItems().setAll(ClickValue.ALL);
        clickValue.setValue(ClickValue.byLabel("0.1 mil"));
        zeroSameAsToday.setSelected(true);
        zeroTemperature.setBase(15.0);
        zeroPressure.setBase(1013.25);
        zeroHumidity.setBase(0);

        temperature.setBase(15.0);
        pressure.setBase(1013.25);
        humidity.setBase(50);
        windSpeed.setBase(0);
        windFrom.setBase(0);

        coriolis.setSelected(true);
        spinDrift.setSelected(true);
        curvature.setSelected(true);
    }

    private void wireChangeEvents() {
        for (UnitField f : allFields) f.baseProperty().addListener((o, was, now) -> changed());
        dragModel.valueProperty().addListener((o, was, now) -> changed());
        rightTwist.selectedProperty().addListener((o, was, now) -> changed());
        zeroSameAsToday.selectedProperty().addListener((o, was, now) -> changed());
        coriolis.selectedProperty().addListener((o, was, now) -> changed());
        spinDrift.selectedProperty().addListener((o, was, now) -> changed());
        curvature.selectedProperty().addListener((o, was, now) -> changed());
        system.addListener((o, was, now) -> updateAirInfo());
        presets.setOnAction(e -> {
            Cartridge c = presets.getValue();
            if (c != null) applyCartridge(c);
        });
        presets.valueProperty().addListener((o, was, now) ->
                deleteLoad.setDisable(now == null || !CustomLoads.GROUP.equals(now.group())));
        deleteLoad.setDisable(true);
        saveLoad.setOnAction(e -> saveCurrentLoad());
        deleteLoad.setOnAction(e -> deleteSelectedLoad());
        zeroTemperature.disableProperty().bind(zeroSameAsToday.selectedProperty());
        zeroPressure.disableProperty().bind(zeroSameAsToday.selectedProperty());
        zeroHumidity.disableProperty().bind(zeroSameAsToday.selectedProperty());
    }

    private void changed() {
        updateAirInfo();
        if (!suppress) onChange.run();
    }

    // ------------------------------------------------------------------ cartridge handling

    public void applyCartridge(Cartridge c) {
        suppress = true;
        try {
            dragModel.setValue(c.dragModel());
            bc.setBase(c.bc());
            weight.setBase(c.weightGrains() * Units.GRAIN_TO_G);
            diameter.setBase(c.diameterInches() * 25.4);
            length.setBase(c.lengthInches() * 25.4);
            muzzleVelocity.setBase(c.muzzleVelocityFps() * Units.FPS_TO_MPS);
            twist.setBase(c.twistInchesPerTurn() * 25.4);
        } finally {
            suppress = false;
        }
        changed();
    }

    /** All built-in loads followed by the user's saved ones. */
    private void refreshPresetItems() {
        Cartridge selected = presets.getValue();
        List<Cartridge> items = new ArrayList<>(Cartridges.ALL);
        items.addAll(customLoads.all());
        presets.getItems().setAll(items);
        if (selected != null && items.contains(selected)) presets.setValue(selected);
    }

    public void selectCartridgeByName(String name) {
        if (name == null || name.isBlank()) return;
        presets.getItems().stream().filter(c -> c.name().equals(name)).findFirst().ifPresent(c -> {
            presets.setValue(c);
            applyCartridge(c);
        });
    }

    /** The load currently in the form as a {@link Cartridge} (for saving), if the fields are valid. */
    private Optional<Cartridge> currentLoad(String name) {
        try {
            return Optional.of(new Cartridge(name, CustomLoads.GROUP, dragModel.getValue(),
                    requireNumber(bc, "ballistic coefficient"),
                    requireNumber(weight, "bullet weight") / Units.GRAIN_TO_G,
                    requireNumber(diameter, "bullet diameter") / 25.4,
                    requireNumber(length, "bullet length") / 25.4,
                    requireNumber(muzzleVelocity, "muzzle velocity") / Units.FPS_TO_MPS,
                    requireNumber(twist, "twist") / 25.4));
        } catch (InputException e) {
            return Optional.empty();
        }
    }

    private void saveCurrentLoad() {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
        dialog.setTitle("Save load");
        dialog.setHeaderText("Save the current cartridge fields as your own load");
        dialog.setContentText("Name:");
        dialog.getEditor().setText(presets.getValue() != null ? presets.getValue().name() + " (mine)" : "");
        dialog.showAndWait().map(String::strip).filter(n -> !n.isEmpty()).ifPresent(name -> {
            Optional<Cartridge> load = currentLoad(name);
            if (load.isEmpty()) {
                new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING,
                        "Fill in every cartridge field with a valid number first.").showAndWait();
                return;
            }
            try {
                customLoads.put(load.get());
            } catch (java.io.IOException ex) {
                new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                        "Could not save the load: " + ex.getMessage()).showAndWait();
                return;
            }
            refreshPresetItems();
            presets.getItems().stream().filter(c -> c.name().equalsIgnoreCase(name)).findFirst()
                    .ifPresent(presets::setValue);
        });
    }

    private void deleteSelectedLoad() {
        Cartridge c = presets.getValue();
        if (c == null || !CustomLoads.GROUP.equals(c.group())) return;
        try {
            customLoads.remove(c);
        } catch (java.io.IOException ex) {
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                    "Could not delete the load: " + ex.getMessage()).showAndWait();
            return;
        }
        presets.setValue(null);
        refreshPresetItems();
    }

    // ------------------------------------------------------------------ building the shot

    /**
     * Validates the form and builds the shot.
     *
     * @throws InputException with {@code isIncomplete()} true when only the positions are missing
     */
    public Request buildShot() throws InputException {
        Projectile projectile = buildProjectile();
        Rifle rifle = buildRifle();
        Atmosphere atmosphere = buildAtmosphere(temperature, pressure, humidity, "weather");
        Atmosphere zeroAtmosphere = zeroSameAsToday.isSelected() ? null
                : buildAtmosphere(zeroTemperature, zeroPressure, zeroHumidity, "zero conditions");
        Wind wind = buildWind();

        if (shooterLat.isBlank() || shooterLon.isBlank() || targetLat.isBlank() || targetLon.isBlank()) {
            throw new InputException(
                    "Place the shooter and the target: click the map, use GPS, or type coordinates.", true);
        }
        double sLat = requireRange(shooterLat, -90, 90, "shooter latitude");
        double sLon = requireRange(shooterLon, -180, 180, "shooter longitude");
        double tLat = requireRange(targetLat, -90, 90, "target latitude");
        double tLon = requireRange(targetLon, -180, 180, "target longitude");
        double sAlt = requireNumber(shooterAlt, "shooter altitude");
        double tAlt = requireNumber(targetAlt, "target altitude");
        double muzzle = requireNumber(muzzleHeight, "muzzle height");
        double aim = requireNumber(aimHeight, "target aim height");

        ShotGeometry geometry;
        try {
            geometry = Geo.geometry(sLat, sLon, sAlt, muzzle, tLat, tLon, tAlt, aim, curvature.isSelected());
        } catch (IllegalArgumentException e) {
            throw new InputException(e.getMessage(), false);
        }
        SolverOptions options = SolverOptions.DEFAULT
                .withCoriolis(coriolis.isSelected()).withSpinDrift(spinDrift.isSelected());
        double rawRise = (tAlt + aim) - (sAlt + muzzle);
        double slant = Math.hypot(geometry.horizontalRangeM(), rawRise);
        try {
            return new Request(new ShotInput(projectile, rifle, atmosphere, zeroAtmosphere, wind, geometry, options),
                    rawRise, slant);
        } catch (IllegalArgumentException e) {
            throw new InputException(e.getMessage(), false);
        }
    }

    private Projectile buildProjectile() throws InputException {
        double bcValue = requireNumber(bc, "ballistic coefficient");
        if (bcValue > 2.0) {
            throw new InputException("A ballistic coefficient above 2 is almost certainly a typo.", false);
        }
        try {
            return new Projectile(dragModel.getValue(), bcValue,
                    requireNumber(weight, "bullet weight") / Units.GRAIN_TO_G,
                    requireNumber(diameter, "bullet diameter") / 25.4,
                    requireNumber(length, "bullet length") / 25.4,
                    requireNumber(muzzleVelocity, "muzzle velocity"),
                    15.0,
                    requireNumber(mvTempSensitivity, "velocity temperature sensitivity"));
        } catch (IllegalArgumentException e) {
            throw new InputException(e.getMessage(), false);
        }
    }

    private Rifle buildRifle() throws InputException {
        try {
            return new Rifle(requireNumber(sightHeight, "sight height") / 1000.0,
                    requireNumber(zeroRange, "zero range"),
                    requireNumber(twist, "twist") / 25.4,
                    rightTwist.isSelected());
        } catch (IllegalArgumentException e) {
            throw new InputException(e.getMessage(), false);
        }
    }

    private Atmosphere buildAtmosphere(UnitField t, UnitField p, UnitField h, String what) throws InputException {
        try {
            return new Atmosphere(requireNumber(t, what + " temperature"), requireNumber(p, what + " pressure"),
                    requireNumber(h, what + " humidity"));
        } catch (IllegalArgumentException e) {
            throw new InputException(e.getMessage(), false);
        }
    }

    private Wind buildWind() throws InputException {
        try {
            return new Wind(requireNumber(windSpeed, "wind speed"), requireNumber(windFrom, "wind direction"));
        } catch (IllegalArgumentException e) {
            throw new InputException(e.getMessage(), false);
        }
    }

    private static double requireNumber(UnitField f, String what) throws InputException {
        if (f.isInvalid() || Double.isNaN(f.base())) {
            throw new InputException("Enter a valid number for " + what + ".", false);
        }
        return f.base();
    }

    private static double requireRange(UnitField f, double min, double max, String what) throws InputException {
        double v = requireNumber(f, what);
        if (v < min || v > max) {
            throw new InputException(String.format("The %s must be between %.0f and %.0f.", what, min, max), false);
        }
        return v;
    }

    // ------------------------------------------------------------------ small public helpers

    public double windSpeedMps() {
        return Double.isNaN(windSpeed.base()) ? 0.0 : windSpeed.base();
    }

    public double windFromDegrees() {
        return Double.isNaN(windFrom.base()) ? 0.0 : windFrom.base();
    }

    public ClickValue selectedClickValue() {
        return clickValue.getValue() == null ? ClickValue.ALL.get(0) : clickValue.getValue();
    }

    public String selectedCartridgeName() {
        return presets.getValue() == null ? "" : presets.getValue().name();
    }

    private void updateAirInfo() {
        if (airInfo == null) return;
        try {
            Atmosphere a = new Atmosphere(requireNumber(temperature, "t"), requireNumber(pressure, "p"),
                    requireNumber(humidity, "h"));
            UnitSystem s = system.get();
            boolean metric = s == UnitSystem.METRIC;
            String density = metric ? Fmt.num(a.densityKgM3(), 3) + " kg/m³"
                    : Fmt.num(a.densityKgM3() * 0.062428, 4) + " lb/ft³";
            String densityAlt = Fmt.num(Quantity.HEIGHT.toDisplay(s, a.densityAltitudeM()), 0)
                    + " " + Quantity.HEIGHT.unit(s);
            airInfo.setText(String.format("Air density %s (%s%% of standard)%nDensity altitude %s · Speed of sound %s",
                    density, Fmt.num(a.densityRatio() * 100.0, 0), densityAlt,
                    Fmt.speed(s, a.speedOfSoundMps())));
        } catch (InputException | IllegalArgumentException e) {
            airInfo.setText("");
        }
    }

    // ------------------------------------------------------------------ layout

    private UnitField field(Quantity q, int decimals) {
        UnitField f = new UnitField(q, decimals, system);
        allFields.add(f);
        return f;
    }

    private GridPane locationsGrid() {
        GridPane g = grid();
        int r = 0;
        r = heading(g, r, "Shooter");
        r = row(g, r, "Latitude", shooterLat);
        r = row(g, r, "Longitude", shooterLon);
        r = row(g, r, "Ground altitude", shooterAlt);
        r = row(g, r, "Muzzle height", muzzleHeight);
        useMyLocation.setMaxWidth(Double.MAX_VALUE);
        g.add(useMyLocation, 0, r++, 3, 1);
        r = heading(g, r, "Target");
        r = row(g, r, "Latitude", targetLat);
        r = row(g, r, "Longitude", targetLon);
        r = row(g, r, "Ground altitude", targetAlt);
        r = row(g, r, "Aim-point height", aimHeight);
        g.add(autoElevation, 0, r++, 3, 1);
        getElevations.setMaxWidth(Double.MAX_VALUE);
        g.add(getElevations, 0, r, 3, 1);
        return g;
    }

    private GridPane cartridgeGrid() {
        presets.setPromptText("Choose a cartridge…");
        presets.setMaxWidth(Double.MAX_VALUE);
        presets.setVisibleRowCount(14);
        presets.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Cartridge c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label name = new Label(c.name());
                Label group = new Label(c.group());
                group.getStyleClass().add("dim");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                setText(null);
                setGraphic(new HBox(8, name, spacer, group));
            }
        });
        presets.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Cartridge c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? presets.getPromptText() : c.name());
            }
        });

        GridPane g = grid();
        int r = 0;
        g.add(presets, 0, r++, 3, 1);
        HBox buttons = new HBox(6, saveLoad, deleteLoad);
        g.add(buttons, 0, r++, 3, 1);

        dragModel.setMaxWidth(Double.MAX_VALUE);
        g.add(new Label("Drag model"), 0, r);
        g.add(dragModel, 1, r++, 2, 1);
        GridPane.setHgrow(dragModel, Priority.ALWAYS);
        r = row(g, r, "Ballistic coeff.", bc);
        r = row(g, r, "Bullet weight", weight);
        r = row(g, r, "Diameter", diameter);
        r = row(g, r, "Length", length);
        r = row(g, r, "Muzzle velocity", muzzleVelocity);
        r = row(g, r, "Velocity vs temp.", mvTempSensitivity);
        Label note = new Label("BC must match the drag model (G1 or G7) it was published for. "
                + "Velocity vs temp. is the change per degree from 15 °C; leave 0 if unknown.");
        note.setWrapText(true);
        note.getStyleClass().add("dim");
        g.add(note, 0, r, 3, 1);
        return g;
    }

    private GridPane rifleGrid() {
        clickValue.setMaxWidth(Double.MAX_VALUE);
        GridPane g = grid();
        int r = 0;
        r = row(g, r, "Sight height", sightHeight);
        r = row(g, r, "Zero range", zeroRange);
        r = row(g, r, "Barrel twist (1 turn in)", twist);
        g.add(rightTwist, 0, r++, 3, 1);
        g.add(new Label("Turret click"), 0, r);
        g.add(clickValue, 1, r++, 2, 1);
        GridPane.setHgrow(clickValue, Priority.ALWAYS);
        g.add(zeroSameAsToday, 0, r++, 3, 1);
        r = row(g, r, "Zero temperature", zeroTemperature);
        r = row(g, r, "Zero pressure", zeroPressure);
        r = row(g, r, "Zero humidity", zeroHumidity);
        Label note = new Label("Elevation dial is measured from this zero, like a real turret.");
        note.setWrapText(true);
        note.getStyleClass().add("dim");
        g.add(note, 0, r, 3, 1);
        return g;
    }

    private GridPane weatherGrid() {
        GridPane g = grid();
        int r = 0;
        r = row(g, r, "Temperature", temperature);
        r = row(g, r, "Station pressure", pressure);
        r = row(g, r, "Humidity", humidity);
        r = row(g, r, "Wind speed", windSpeed);
        r = row(g, r, "Wind from (compass)", windFrom);
        fetchWeather.setMaxWidth(Double.MAX_VALUE);
        g.add(fetchWeather, 0, r++, 3, 1);
        airInfo.getStyleClass().add("dim");
        airInfo.setWrapText(true);
        g.add(airInfo, 0, r, 3, 1);
        updateAirInfo();
        return g;
    }

    private VBox optionsBox() {
        VBox box = new VBox(6, coriolis, spinDrift, curvature);
        box.setPadding(new Insets(6));
        return box;
    }

    private static TitledPane section(String title, Node content, boolean expanded) {
        TitledPane pane = new TitledPane(title, content);
        pane.setExpanded(expanded);
        pane.setAnimated(false);
        return pane;
    }

    private static GridPane grid() {
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(5);
        g.setPadding(new Insets(6));
        return g;
    }

    private static int heading(GridPane g, int r, String text) {
        Label l = new Label(text);
        l.getStyleClass().add("subheading");
        g.add(l, 0, r, 3, 1);
        return r + 1;
    }

    private static int row(GridPane g, int r, String label, UnitField f) {
        Label unit = new Label();
        unit.textProperty().bind(f.unitProperty());
        unit.getStyleClass().add("unit");
        unit.setMinWidth(40);
        f.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(f, Priority.ALWAYS);
        g.add(new Label(label), 0, r);
        g.add(f, 1, r);
        g.add(unit, 2, r);
        return r + 1;
    }
}
