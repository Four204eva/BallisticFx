package com.ballisticfx.app.ui;

import com.ballisticfx.app.units.ClickValue;
import com.ballisticfx.app.units.DialUnit;
import com.ballisticfx.app.units.Fmt;
import com.ballisticfx.app.units.UnitSystem;
import com.ballisticfx.core.FiringSolution;
import com.ballisticfx.core.ShotGeometry;
import com.ballisticfx.core.Units;
import com.ballisticfx.core.Wind;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** The headline answer: elevation and windage to dial, with click counts, plus the shot's key numbers. */
public final class SolutionBanner extends HBox {
    private final Label elevationTitle = title("ELEVATION");
    private final Label elevationMain = big();
    private final Label elevationSub = sub();
    private final Label windageTitle = title("WINDAGE");
    private final Label windageMain = big();
    private final Label windageSub = sub();
    private final Label message = new Label();
    private final GridPane details = new GridPane();

    public SolutionBanner() {
        super(12);
        setPadding(new Insets(8, 12, 8, 12));
        getStyleClass().add("banner");

        VBox elevation = card(elevationTitle, elevationMain, elevationSub);
        VBox windage = card(windageTitle, windageMain, windageSub);
        elevation.getStyleClass().add("dial-card");
        windage.getStyleClass().add("dial-card");

        details.setHgap(10);
        details.setVgap(1);
        // Gap between the two key/value column pairs.
        javafx.scene.layout.ColumnConstraints[] cols = new javafx.scene.layout.ColumnConstraints[4];
        for (int i = 0; i < cols.length; i++) cols[i] = new javafx.scene.layout.ColumnConstraints();
        cols[1].setMinWidth(210);
        details.getColumnConstraints().setAll(cols);
        message.setWrapText(true);
        message.getStyleClass().add("banner-message");
        VBox detailsCard = new VBox(4, details, message);
        detailsCard.getStyleClass().add("card");
        detailsCard.setPadding(new Insets(8, 12, 8, 12));
        HBox.setHgrow(detailsCard, Priority.ALWAYS);

        getChildren().addAll(elevation, windage, detailsCard);
        clear("Place the shooter and the target to get a firing solution.", false);
    }

    /** Shows an error or hint instead of a solution. */
    public void clear(String text, boolean error) {
        elevationMain.setText("—");
        windageMain.setText("—");
        elevationSub.setText("");
        windageSub.setText("");
        details.getChildren().clear();
        message.setText(text);
        message.getStyleClass().removeAll("error-text", "hint-text");
        message.getStyleClass().add(error ? "error-text" : "hint-text");
    }

    public void show(FiringSolution s, ShotGeometry g, Wind wind, double rawRiseM, double slantRangeM,
                     UnitSystem system, DialUnit dial, ClickValue click) {
        message.setText("");
        DialUnit other = dial.other();

        double elevation = s.elevationRad();
        elevationTitle.setText("ELEVATION  " + (elevation >= 0 ? "▲ UP" : "▼ DOWN"));
        elevationMain.setText(Fmt.angle(dial, elevation));
        elevationSub.setText(Fmt.angle(other, elevation) + "  ·  " + clicksText(click, Math.abs(elevation)));

        double windage = s.windageRad();
        windageTitle.setText("WINDAGE  " + (windage >= 0 ? "▶ RIGHT" : "◀ LEFT"));
        windageMain.setText(Fmt.angle(dial, windage));
        windageSub.setText(Fmt.angle(other, windage) + "  ·  " + clicksText(click, Math.abs(windage)));

        details.getChildren().clear();
        double head = wind.headwindMps(g.azimuthDegrees());
        double cross = wind.crosswindFromRightMps(g.azimuthDegrees());
        String windUnit = windUnit(system);

        // Left column: the geometry and what the bullet does.
        pair(0, 0, "Range", Fmt.range(system, g.horizontalRangeM()) + "  (slant " + Fmt.range(system, slantRangeM) + ")");
        pair(1, 0, "Bearing", Fmt.num(g.azimuthDegrees(), 1) + "° true");
        pair(2, 0, "Height diff.", Fmt.height(system, rawRiseM, 1) + (rawRiseM >= 0 ? " uphill" : " downhill"));
        pair(3, 0, "Flight time", Fmt.num(s.timeOfFlightS(), 3) + " s");
        pair(4, 0, "Impact velocity", Fmt.speed(system, s.impactVelocityMps()) + "  (Mach " + Fmt.num(s.impactMach(), 2) + ")");
        pair(5, 0, "Impact energy", Fmt.energy(system, s.impactEnergyJ()));
        // Right column: wind, drift and stability.
        pair(0, 2, "Crosswind", Fmt.num(speedInWindUnits(system, Math.abs(cross)), 1) + " " + windUnit
                + (cross >= 0 ? " from right" : " from left"));
        pair(1, 2, head >= 0 ? "Headwind" : "Tailwind", Fmt.num(speedInWindUnits(system, Math.abs(head)), 1) + " " + windUnit);
        pair(2, 2, "Drift, uncorrected", Fmt.small(system, s.driftAtTargetM(), true));
        pair(3, 2, "  of which spin", Fmt.small(system, s.spinDriftAtTargetM(), true));
        pair(4, 2, "Stability Sg", Fmt.num(s.gyroscopicStability(), 2) + stabilityNote(s.gyroscopicStability()));
    }

    private static String stabilityNote(double sg) {
        if (sg < 1.0) return "  ⚠ unstable: bullet may tumble";
        if (sg < 1.4) return "  marginal";
        return "";
    }

    private static String windUnit(UnitSystem system) {
        return system == UnitSystem.METRIC ? "m/s" : "mph";
    }

    private static double speedInWindUnits(UnitSystem system, double mps) {
        return system == UnitSystem.METRIC ? mps : mps / Units.MPH_TO_MPS;
    }

    private static String clicksText(ClickValue click, double angleRad) {
        return click.clicks(angleRad) + " clicks (" + click.label() + ")";
    }

    /** Puts a key/value pair at (row, column) and (row, column + 1) of the details grid. */
    private void pair(int row, int column, String key, String value) {
        Label k = new Label(key);
        k.getStyleClass().add("dim");
        Label v = new Label(value);
        details.add(k, column, row);
        details.add(v, column + 1, row);
    }

    private static VBox card(Label title, Label main, Label sub) {
        VBox box = new VBox(2, title, main, sub);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(8, 14, 8, 14));
        box.setMinWidth(210);
        box.getStyleClass().add("card");
        return box;
    }

    private static Label title(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("card-title");
        return l;
    }

    private static Label big() {
        Label l = new Label();
        l.getStyleClass().add("big-number");
        return l;
    }

    private static Label sub() {
        Label l = new Label();
        l.getStyleClass().add("dim");
        return l;
    }
}
