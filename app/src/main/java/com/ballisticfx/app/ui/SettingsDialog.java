package com.ballisticfx.app.ui;

import java.util.Optional;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Settings: the Google Maps API key. */
public final class SettingsDialog {
    private SettingsDialog() {}

    /**
     * @param storedKey   the key currently saved in the app (may be empty)
     * @param envKeyInUse true if no key is saved but the GOOGLE_MAPS_API_KEY environment variable supplies one
     * @return the new key (possibly empty to clear it), or empty if the user cancelled
     */
    public static Optional<String> askMapsKey(Window owner, String storedKey, boolean envKeyInUse) {
        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Settings");
        dialog.setHeaderText("Google Maps API key");

        TextField key = new TextField(storedKey);
        key.setPromptText(envKeyInUse ? "Using GOOGLE_MAPS_API_KEY from the environment" : "AIza…");
        key.setPrefColumnCount(40);

        Label help = new Label("""
                The map uses Google Maps when you provide a key, and OpenStreetMap when you do not.

                To get a key: Google Cloud Console > create or pick a project > enable "Maps JavaScript API" \
                > Credentials > Create API key. Billing must be enabled on the project (Google has a free monthly allowance).

                The key is saved only on this computer, in your home folder (.ballisticfx/settings.properties). \
                The app loads the map from a local page, so use a key without an HTTP-referrer restriction; \
                restrict it to the "Maps JavaScript API" instead. Leave this blank to use OpenStreetMap.""");
        help.setWrapText(true);
        help.setMaxWidth(520);

        VBox box = new VBox(10, new Label("API key"), key, help);
        box.setPadding(new Insets(6));
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == ButtonType.OK ? key.getText().trim() : null);
        return dialog.showAndWait();
    }
}
