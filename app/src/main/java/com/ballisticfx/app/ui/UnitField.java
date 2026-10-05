package com.ballisticfx.app.ui;

import com.ballisticfx.app.units.Fmt;
import com.ballisticfx.app.units.Quantity;
import com.ballisticfx.app.units.UnitSystem;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.TextField;

/**
 * A numeric text field that shows its value in the current unit system but always holds it in the metric
 * base unit. Switching Metric/Imperial rewrites the text; typing converts back. Blank means "not set"
 * (base is NaN); text that is not a number is flagged with the {@code invalid} style class.
 */
public final class UnitField extends TextField {
    private final Quantity quantity;
    private final int decimals;
    private final ObjectProperty<UnitSystem> system;
    private final ReadOnlyDoubleWrapper base = new ReadOnlyDoubleWrapper(Double.NaN);
    private final StringProperty unit = new SimpleStringProperty();
    private boolean programmatic;
    private boolean invalid;

    public UnitField(Quantity quantity, int decimals, ObjectProperty<UnitSystem> system) {
        this.quantity = quantity;
        this.decimals = decimals;
        this.system = system;
        setPrefColumnCount(8);
        unit.set(quantity.unit(system.get()));

        textProperty().addListener((o, was, now) -> {
            if (!programmatic) parseText(now);
        });
        system.addListener((o, was, now) -> {
            unit.set(quantity.unit(now));
            if (!invalid) render(); // leave half-typed text alone
        });
    }

    /** Sets the value from a base-unit number (NaN clears the field). */
    public void setBase(double baseValue) {
        base.set(baseValue);
        invalid = false;
        getStyleClass().remove("invalid");
        render();
    }

    public double base() {
        return base.get();
    }

    public ReadOnlyDoubleProperty baseProperty() {
        return base.getReadOnlyProperty();
    }

    public StringProperty unitProperty() {
        return unit;
    }

    public boolean isBlank() {
        return getText() == null || getText().isBlank();
    }

    /** True if the text is non-empty but not a usable number. */
    public boolean isInvalid() {
        return invalid;
    }

    private void render() {
        double b = base.get();
        programmatic = true;
        try {
            setText(Double.isNaN(b) ? "" : Fmt.compact(quantity.toDisplay(system.get(), b), decimals));
        } finally {
            programmatic = false;
        }
    }

    private void parseText(String text) {
        String t = text == null ? "" : text.trim().replace(',', '.');
        double parsed = Double.NaN;
        boolean bad = false;
        if (!t.isEmpty()) {
            try {
                parsed = Double.parseDouble(t);
                if (!Double.isFinite(parsed)) {
                    parsed = Double.NaN;
                    bad = true;
                }
            } catch (NumberFormatException e) {
                bad = true;
            }
        }
        invalid = bad;
        if (bad) {
            if (!getStyleClass().contains("invalid")) getStyleClass().add("invalid");
        } else {
            getStyleClass().remove("invalid");
        }
        base.set(Double.isNaN(parsed) ? Double.NaN : quantity.toBase(system.get(), parsed));
    }
}
