package com.ballisticfx.app.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;

/**
 * Small map overlay: north-up compass with a blue arrow showing where the wind is blowing TOWARD and a
 * yellow marker for the bearing from shooter to target. Gives a quick sanity check that the wind direction
 * entered is the one you meant.
 */
public final class WindCompass extends VBox {
    private static final double SIZE = 96;
    private static final double C = SIZE / 2;

    private final Polygon windArrow = new Polygon(
            C, C - 30, C + 8, C - 14, C + 3, C - 14, C + 3, C + 24, C - 3, C + 24, C - 3, C - 14, C - 8, C - 14);
    private final Polygon fireMarker = new Polygon(C, C - 44, C + 6, C - 34, C - 6, C - 34);
    private final Label caption = new Label();

    public WindCompass() {
        super(4);
        setAlignment(Pos.CENTER);
        getStyleClass().add("map-overlay");
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);

        Circle ring = new Circle(C, C, 40);
        ring.setFill(Color.TRANSPARENT);
        ring.setStroke(Color.web("#ffffff", 0.75));
        ring.setStrokeWidth(1.5);
        Text north = new Text(C - 4, 11, "N");
        north.setFill(Color.WHITE);
        north.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        windArrow.setFill(Color.web("#4fc3f7"));
        windArrow.setStroke(Color.WHITE);
        windArrow.setStrokeWidth(0.8);
        fireMarker.setFill(Color.web("#ffeb3b"));
        fireMarker.setStroke(Color.web("#222222"));
        fireMarker.setStrokeWidth(0.6);
        fireMarker.setVisible(false);

        Pane pane = new Pane(ring, north, windArrow, fireMarker);
        pane.setPrefSize(SIZE, SIZE);
        pane.setMinSize(SIZE, SIZE);
        pane.setMaxSize(SIZE, SIZE);
        caption.getStyleClass().add("map-hint");
        getChildren().addAll(pane, caption);
        setWind(0, 0, "calm");
    }

    /** @param fromDegrees compass bearing the wind comes FROM; the arrow points the opposite way */
    public void setWind(double fromDegrees, double speedMps, String speedText) {
        boolean calm = speedMps < 0.05;
        windArrow.setVisible(!calm);
        windArrow.getTransforms().setAll(new Rotate(fromDegrees + 180.0, C, C));
        caption.setText(calm ? "Wind: calm" : "Wind " + speedText + " from " + Math.round(fromDegrees) + "°");
    }

    /** Bearing from shooter to target, or NaN to hide the marker. */
    public void setBearing(double degrees) {
        fireMarker.setVisible(!Double.isNaN(degrees));
        if (!Double.isNaN(degrees)) fireMarker.getTransforms().setAll(new Rotate(degrees, C, C));
    }
}
