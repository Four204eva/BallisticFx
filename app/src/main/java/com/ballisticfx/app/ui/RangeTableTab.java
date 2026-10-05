package com.ballisticfx.app.ui;

import java.util.List;
import java.util.function.Function;

import com.ballisticfx.app.units.ClickValue;
import com.ballisticfx.app.units.DialUnit;
import com.ballisticfx.app.units.Fmt;
import com.ballisticfx.app.units.UnitSystem;
import com.ballisticfx.core.RangeRow;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Range card along the current line of sight: dial and wind for each distance, so the shooter can re-dial if
 * the target moves. The row nearest the actual target is highlighted.
 */
public final class RangeTableTab extends VBox {
    private final TableView<RangeRow> table = new TableView<>();
    private final Label note = new Label();

    private UnitSystem system = UnitSystem.METRIC;
    private DialUnit dial = DialUnit.MIL;
    private ClickValue click = ClickValue.ALL.get(0);
    private double targetRangeM = Double.NaN;

    public RangeTableTab() {
        super(6);
        setPadding(new Insets(8));
        table.setPlaceholder(new Label("No range card yet."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(RangeRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().remove("target-row");
                if (!empty && row != null && Math.abs(row.rangeM() - targetRangeM) < 0.5) {
                    getStyleClass().add("target-row");
                }
            }
        });
        table.getColumns().addAll(List.of(
                column("Range", r -> Fmt.num(Fmt.rangeValue(system, r.rangeM()), 0) + " " + rangeUnit()),
                column("Elevation", r -> Fmt.angleSigned(dial, r.elevationRad()) + " " + dial),
                column("Clicks", r -> String.valueOf(click.clicks(r.elevationRad()))),
                column("Windage", r -> Fmt.angleSigned(dial, r.windageRad()) + " " + dial),
                column("Wind clicks", r -> String.valueOf(click.clicks(r.windageRad()))),
                column("Time", r -> Fmt.num(r.timeOfFlightS(), 3) + " s"),
                column("Velocity", r -> Fmt.speed(system, r.speedMps())),
                column("Mach", r -> Fmt.num(r.mach(), 2)),
                column("Energy", r -> Fmt.energy(system, r.energyJ())),
                column("Drift", r -> Fmt.small(system, r.driftM(), true))));
        VBox.setVgrow(table, Priority.ALWAYS);

        note.setWrapText(true);
        note.getStyleClass().add("dim");
        note.setText("Along your line of sight to the target, same weather and wind. Elevation: + is UP; "
                + "windage: + is RIGHT. The highlighted row is your target.");
        getChildren().addAll(note, table);
    }

    public void show(List<RangeRow> rows, double targetRangeM, UnitSystem system, DialUnit dial, ClickValue click) {
        this.targetRangeM = targetRangeM;
        this.system = system;
        this.dial = dial;
        this.click = click;
        table.getItems().setAll(rows);
        table.refresh();
    }

    public void clear() {
        table.getItems().clear();
    }

    /** Re-renders cells after a units / dial / click change without recomputing. */
    public void restyle(UnitSystem system, DialUnit dial, ClickValue click) {
        this.system = system;
        this.dial = dial;
        this.click = click;
        table.refresh();
    }

    private String rangeUnit() {
        return system == UnitSystem.METRIC ? "m" : "yd";
    }

    private TableColumn<RangeRow, String> column(String title, Function<RangeRow, String> text) {
        TableColumn<RangeRow, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(text.apply(cd.getValue())));
        c.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
        c.setSortable(false);
        return c;
    }
}
