package com.ballisticfx.app.map;

import java.util.function.Consumer;

import javafx.beans.property.ObjectProperty;
import javafx.scene.Node;

/**
 * What the rest of the app needs from a map. The desktop implementation is {@link MapPane} (Google Maps in a
 * WebView). JavaFX's WebView is not available on Android, so an Android port supplies another implementation
 * of this interface (for example Gluon Maps, or a native map view hosted by the Android app) and nothing else changes.
 */
public interface MapSurface {
    enum Which { SHOOTER, TARGET }

    /** The user clicked the map, or dragged a marker, to put {@code which} at this position. */
    record PointPicked(Which which, double lat, double lon) {}

    Node node();

    /** Which marker the next map click places. */
    ObjectProperty<Which> pickModeProperty();

    /** Moves (or creates) a marker, e.g. when coordinates are typed or fetched from GPS. */
    void setPoint(Which which, double lat, double lon);

    void clearPoint(Which which);

    void setOnPointPicked(Consumer<PointPicked> handler);

    /** Receives human-readable notices, e.g. "Google rejected the key; using OpenStreetMap". */
    void setOnNotice(Consumer<String> handler);

    /** Switches the Google Maps key; an empty key selects the OpenStreetMap fallback. */
    void setApiKey(String key);

    /** "Google Maps" or "OpenStreetMap". */
    String providerName();
}
