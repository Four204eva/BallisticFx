package com.ballisticfx.app.ui;

import java.util.ArrayList;
import java.util.List;

import com.ballisticfx.app.units.Fmt;
import com.ballisticfx.app.units.Quantity;
import com.ballisticfx.app.units.UnitSystem;
import com.ballisticfx.core.Trajectory;
import com.ballisticfx.core.TrajectoryPoint;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Bullet path and velocity charts with a flight-time slider. Drag the slider (or press Play) to see the time,
 * range, height above the muzzle, velocity, energy and drift at every point of the flight.
 */
public final class TrajectoryTab extends VBox {
    private static final int MAX_CHART_POINTS = 160;

    private final NumberAxis pathX = new NumberAxis();
    private final NumberAxis pathY = new NumberAxis();
    private final NumberAxis speedX = new NumberAxis();
    private final NumberAxis speedY = new NumberAxis();
    private final LineChart<Number, Number> pathChart = new LineChart<>(pathX, pathY);
    private final LineChart<Number, Number> speedChart = new LineChart<>(speedX, speedY);

    // Series order matters: the stylesheet colours them by index (default-color0..3).
    private final XYChart.Series<Number, Number> pathSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> losSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> pathMarker = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> targetMarker = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> speedSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> machOneSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> speedMarker = new XYChart.Series<>();

    private final Slider slider = new Slider(0, 1, 0);
    private final Button play = new Button("▶  Play");
    private final Label timeLabel = new Label();
    private final Label tTime = value();
    private final Label tRange = value();
    private final Label tHeight = value();
    private final Label tHeightLos = value();
    private final Label tSpeed = value();
    private final Label tMach = value();
    private final Label tEnergy = value();
    private final Label tDrift = value();
    private final Timeline animation = new Timeline();

    private Trajectory trajectory;
    private double targetHeightM;
    private double machOneMps;
    private UnitSystem system = UnitSystem.METRIC;

    public TrajectoryTab() {
        super(8);
        setPadding(new Insets(8));

        pathChart.setTitle("Bullet path (height above the muzzle)");
        speedChart.setTitle("Velocity");
        for (LineChart<Number, Number> c : List.of(pathChart, speedChart)) {
            c.setAnimated(false);
            c.setLegendVisible(false);
            c.setCreateSymbols(true);
            c.setMinHeight(170);
            HBox.setHgrow(c, Priority.ALWAYS);
        }
        speedChart.getStyleClass().add("speed-chart");
        for (NumberAxis axis : List.of(pathX, speedX)) {
            axis.setAutoRanging(false);
            axis.setLowerBound(0);
        }
        pathY.setForceZeroInRange(true);
        speedY.setForceZeroInRange(false);
        pathChart.getData().addAll(List.of(pathSeries, losSeries, pathMarker, targetMarker));
        speedChart.getData().addAll(List.of(speedSeries, machOneSeries, speedMarker));

        HBox charts = new HBox(8, pathChart, speedChart);
        VBox.setVgrow(charts, Priority.ALWAYS);

        play.setOnAction(e -> togglePlay());
        slider.setMaxWidth(Double.MAX_VALUE);
        slider.setShowTickMarks(true);
        slider.valueProperty().addListener((o, was, now) -> updateReadout());
        timeLabel.getStyleClass().add("slider-time");
        timeLabel.setMinWidth(90);
        HBox sliderRow = new HBox(10, new Label("Flight time"), slider, timeLabel, play);
        sliderRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(slider, Priority.ALWAYS);

        GridPane tiles = new GridPane();
        tiles.setHgap(8);
        tiles.setVgap(8);
        Label[] titles = {tag("Time"), tag("Range downrange"), tag("Height above muzzle"), tag("Height vs sight line"),
                tag("Velocity"), tag("Mach"), tag("Energy"), tag("Drift")};
        Label[] values = {tTime, tRange, tHeight, tHeightLos, tSpeed, tMach, tEnergy, tDrift};
        for (int i = 0; i < values.length; i++) {
            VBox tile = new VBox(1, titles[i], values[i]);
            tile.getStyleClass().add("tile");
            tile.setMaxWidth(Double.MAX_VALUE);
            tiles.add(tile, i, 0);
            javafx.scene.layout.ColumnConstraints cc = new javafx.scene.layout.ColumnConstraints();
            cc.setPercentWidth(100.0 / values.length);
            tiles.getColumnConstraints().add(cc);
        }

        getChildren().addAll(charts, sliderRow, tiles);
        clear();
    }

    public void clear() {
        stopPlay();
        trajectory = null;
        for (XYChart.Series<Number, Number> s : List.of(pathSeries, losSeries, pathMarker, targetMarker,
                speedSeries, machOneSeries, speedMarker)) {
            s.getData().clear();
        }
        slider.setDisable(true);
        play.setDisable(true);
        slider.setValue(0);
        timeLabel.setText("");
        for (Label l : List.of(tTime, tRange, tHeight, tHeightLos, tSpeed, tMach, tEnergy, tDrift)) l.setText("—");
    }

    /**
     * @param targetHeightM  aim-point height above the muzzle (shown as the target marker)
     * @param machOneMps     speed of sound at the shooter (the dashed line on the velocity chart)
     */
    public void show(Trajectory trajectory, double targetHeightM, double machOneMps, UnitSystem system) {
        stopPlay();
        this.trajectory = trajectory;
        this.targetHeightM = targetHeightM;
        this.machOneMps = machOneMps;
        this.system = system;

        double fraction = slider.getMax() > 0 ? slider.getValue() / slider.getMax() : 0.0;
        double flight = trajectory.flightTimeS();
        slider.setDisable(false);
        play.setDisable(false);
        slider.setMax(flight);
        slider.setMajorTickUnit(flight / 5.0);
        slider.setMinorTickCount(4);
        slider.setBlockIncrement(flight / 200.0);
        slider.setValue(fraction * flight);
        redrawCharts();
        updateReadout();
    }

    public void setUnitSystem(UnitSystem system) {
        this.system = system;
        if (trajectory != null) {
            redrawCharts();
            updateReadout();
        }
    }

    // ------------------------------------------------------------------ charts

    private void redrawCharts() {
        Trajectory t = trajectory;
        String rangeUnit = Quantity.RANGE.unit(system);
        String heightUnit = Quantity.HEIGHT.unit(system);
        String speedUnit = Quantity.SPEED.unit(system);

        double maxRange = Fmt.rangeValue(system, t.rangeM());
        double tick = niceStep(maxRange / 6.0);
        double upper = Math.ceil(maxRange / tick) * tick;
        for (NumberAxis axis : List.of(pathX, speedX)) {
            axis.setUpperBound(upper);
            axis.setTickUnit(tick);
            axis.setLabel("Range (" + rangeUnit + ")");
        }
        pathY.setLabel("Height (" + heightUnit + ")");
        speedY.setLabel("Velocity (" + speedUnit + ")");

        List<XYChart.Data<Number, Number>> path = new ArrayList<>();
        List<XYChart.Data<Number, Number>> los = new ArrayList<>();
        List<XYChart.Data<Number, Number>> speed = new ArrayList<>();
        List<TrajectoryPoint> points = t.points();
        int stride = Math.max(1, points.size() / MAX_CHART_POINTS);
        for (int i = 0; i < points.size(); i += stride) {
            addPoint(points.get(i), path, los, speed);
        }
        TrajectoryPoint last = points.get(points.size() - 1);
        if ((points.size() - 1) % stride != 0) addPoint(last, path, los, speed);

        pathSeries.getData().setAll(path);
        losSeries.getData().setAll(los);
        speedSeries.getData().setAll(speed);
        double mach1 = Quantity.SPEED.toDisplay(system, machOneMps);
        machOneSeries.getData().setAll(List.of(new XYChart.Data<>(0, mach1), new XYChart.Data<>(upper, mach1)));
        targetMarker.getData().setAll(List.of(new XYChart.Data<Number, Number>(
                Fmt.rangeValue(system, t.rangeM()), Quantity.HEIGHT.toDisplay(system, targetHeightM))));
    }

    private void addPoint(TrajectoryPoint p, List<XYChart.Data<Number, Number>> path,
                          List<XYChart.Data<Number, Number>> los, List<XYChart.Data<Number, Number>> speed) {
        double x = Fmt.rangeValue(system, p.rangeM());
        path.add(new XYChart.Data<>(x, Quantity.HEIGHT.toDisplay(system, p.heightAboveMuzzleM())));
        los.add(new XYChart.Data<>(x, Quantity.HEIGHT.toDisplay(system, p.heightAboveMuzzleM() - p.heightAboveLineOfSightM())));
        speed.add(new XYChart.Data<>(x, Fmt.speedValue(system, p.speedMps())));
    }

    /** A tick step of 1, 2 or 5 times a power of ten that is at least {@code raw}. */
    static double niceStep(double raw) {
        if (!(raw > 0)) return 1.0;
        double exp = Math.pow(10, Math.floor(Math.log10(raw)));
        double f = raw / exp;
        double nice = f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10;
        return nice * exp;
    }

    // ------------------------------------------------------------------ slider readout

    private void updateReadout() {
        if (trajectory == null) return;
        TrajectoryPoint p = trajectory.atTime(slider.getValue());
        timeLabel.setText(Fmt.num(p.timeS(), 3) + " s");

        double x = Fmt.rangeValue(system, p.rangeM());
        pathMarker.getData().setAll(List.of(new XYChart.Data<Number, Number>(
                x, Quantity.HEIGHT.toDisplay(system, p.heightAboveMuzzleM()))));
        speedMarker.getData().setAll(List.of(new XYChart.Data<Number, Number>(x, Fmt.speedValue(system, p.speedMps()))));

        String rangeUnit = Quantity.RANGE.unit(system);
        String heightUnit = Quantity.HEIGHT.unit(system);
        tTime.setText(Fmt.num(p.timeS(), 3) + " s");
        tRange.setText(Fmt.num(x, 1) + " " + rangeUnit);
        tHeight.setText(Fmt.signed(Quantity.HEIGHT.toDisplay(system, p.heightAboveMuzzleM()), 2) + " " + heightUnit);
        tHeightLos.setText(Fmt.small(system, p.heightAboveLineOfSightM(), true));
        tSpeed.setText(Fmt.num(Fmt.speedValue(system, p.speedMps()), 1) + " " + Quantity.SPEED.unit(system));
        tMach.setText(Fmt.num(p.mach(), 2) + (p.mach() >= 1.0 ? " ▲" : " ▼"));
        tMach.getStyleClass().removeAll("supersonic", "subsonic");
        tMach.getStyleClass().add(p.mach() >= 1.0 ? "supersonic" : "subsonic");
        tEnergy.setText(Fmt.energy(system, p.energyJ()));
        tDrift.setText(Fmt.small(system, p.driftM(), true));
    }

    // ------------------------------------------------------------------ playback

    private void togglePlay() {
        if (animation.getStatus() == Animation.Status.RUNNING) {
            stopPlay();
            return;
        }
        double flight = slider.getMax();
        if (slider.getValue() >= flight - 1e-9) slider.setValue(0);
        double remaining = flight - slider.getValue();
        // Slow motion: a 1.5 s flight plays over ~4 s, long flights are capped at 10 s.
        double seconds = Math.min(10.0, Math.max(2.5, flight * 2.6)) * (remaining / flight);
        animation.getKeyFrames().setAll(new KeyFrame(Duration.seconds(seconds),
                new KeyValue(slider.valueProperty(), flight)));
        animation.setOnFinished(e -> play.setText("▶  Play"));
        play.setText("❚❚  Pause");
        animation.playFromStart();
    }

    private void stopPlay() {
        animation.stop();
        play.setText("▶  Play");
    }

    // ------------------------------------------------------------------ small widgets

    private static Label tag(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("tile-title");
        return l;
    }

    private static Label value() {
        Label l = new Label("—");
        l.getStyleClass().add("tile-value");
        return l;
    }
}
