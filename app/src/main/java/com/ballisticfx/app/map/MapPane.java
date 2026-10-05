package com.ballisticfx.app.map;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

/**
 * Map hosted in a JavaFX WebView. With a Google Maps API key it shows Google Maps (hybrid imagery); with no
 * key, or if Google rejects the key or cannot be reached, it falls back to OpenStreetMap (Leaflet) so the app
 * stays usable. The page (resources/map.html) talks to Java through the {@link Bridge} object.
 *
 * <p>Click the map to place the marker selected in the "Place" toggle; drag either marker to refine it.
 */
public final class MapPane extends StackPane implements MapSurface {

    /** Called from JavaScript. Public, with public methods, because WebEngine reaches it through reflection. */
    public final class Bridge {
        public void onPoint(String which, double lat, double lng) {
            Which w = "shooter".equals(which) ? Which.SHOOTER : Which.TARGET;
            Platform.runLater(() -> {
                points.put(w, new double[]{lat, lng});
                if (onPointPicked != null) onPointPicked.accept(new PointPicked(w, lat, lng));
            });
        }

        public void onProviderFailure(String message) {
            Platform.runLater(() -> handleProviderFailure(message));
        }

        public void log(String message) {
            System.err.println("[map] " + message);
        }
    }

    private final WebEngine engine;
    private final Bridge bridge = new Bridge(); // strong reference: WebEngine only holds JS-visible objects weakly
    private final Map<Which, double[]> points = new EnumMap<>(Which.class);
    private final ObjectProperty<Which> pickMode = new SimpleObjectProperty<>(Which.SHOOTER);
    private final StringProperty provider = new SimpleStringProperty("OpenStreetMap");
    private final Label providerBadge = new Label();
    private Consumer<PointPicked> onPointPicked;
    private Consumer<String> onNotice;
    private String apiKey = "";
    private boolean forceFallback;
    private boolean pageReady;

    public MapPane(String googleApiKey) {
        this.apiKey = googleApiKey == null ? "" : googleApiKey.trim();

        WebView view = new WebView();
        view.setContextMenuEnabled(false);
        engine = view.getEngine();
        engine.setOnError(e -> System.err.println("[map] " + e.getMessage()));
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) onPageLoaded();
        });
        // Expose the bridge as early as possible so page scripts can use it.
        engine.documentProperty().addListener((obs, old, doc) -> {
            if (doc != null) attachBridge();
        });

        getChildren().add(view);
        getChildren().add(buildPickModeOverlay());
        providerBadge.getStyleClass().add("map-badge");
        StackPane.setAlignment(providerBadge, Pos.TOP_RIGHT);
        StackPane.setMargin(providerBadge, new Insets(8));
        getChildren().add(providerBadge);

        pickMode.addListener((o, was, now) -> runScript("setMode('" + modeName(now) + "')"));
        load();
    }

    // ------------------------------------------------------------------ MapSurface

    @Override
    public Node node() {
        return this;
    }

    @Override
    public ObjectProperty<Which> pickModeProperty() {
        return pickMode;
    }

    @Override
    public void setPoint(Which which, double lat, double lon) {
        double[] existing = points.get(which);
        if (existing != null && existing[0] == lat && existing[1] == lon) return; // no-op: don't re-frame the view
        points.put(which, new double[]{lat, lon});
        runScript("place('" + modeName(which) + "'," + lat + "," + lon + ")");
    }

    @Override
    public void clearPoint(Which which) {
        points.remove(which);
        runScript("clearPoint('" + modeName(which) + "')");
    }

    @Override
    public void setOnPointPicked(Consumer<PointPicked> handler) {
        this.onPointPicked = handler;
    }

    @Override
    public void setOnNotice(Consumer<String> handler) {
        this.onNotice = handler;
    }

    @Override
    public void setApiKey(String key) {
        this.apiKey = key == null ? "" : key.trim();
        this.forceFallback = false;
        load();
    }

    @Override
    public String providerName() {
        return provider.get();
    }

    /** Adds an overlay (for example the wind compass) on top of the map. */
    public void addOverlay(Node node, Pos position) {
        StackPane.setAlignment(node, position);
        StackPane.setMargin(node, new Insets(10));
        getChildren().add(node);
    }

    // ------------------------------------------------------------------ internals

    private boolean useGoogle() {
        return !apiKey.isEmpty() && !forceFallback;
    }

    private void load() {
        pageReady = false;
        provider.set(useGoogle() ? "Google Maps" : "OpenStreetMap");
        providerBadge.setText(provider.get());
        engine.loadContent(buildHtml());
    }

    private void attachBridge() {
        try {
            ((JSObject) engine.executeScript("window")).setMember("bridge", bridge);
        } catch (RuntimeException e) {
            System.err.println("[map] could not attach bridge: " + e.getMessage());
        }
    }

    private void onPageLoaded() {
        attachBridge();
        pageReady = true;
        runScript("flushFailure()");
        runScript("setMode('" + modeName(pickMode.get()) + "')");
        points.forEach((which, p) -> runScript("place('" + modeName(which) + "'," + p[0] + "," + p[1] + ")"));
    }

    private void handleProviderFailure(String message) {
        if (useGoogle()) {
            forceFallback = true;
            notice(message + " Showing OpenStreetMap instead.");
            load();
        } else {
            notice(message);
        }
    }

    private void notice(String text) {
        if (onNotice != null) onNotice.accept(text);
    }

    private void runScript(String script) {
        if (!pageReady) return; // state is replayed from `points`/`pickMode` when the page finishes loading
        try {
            engine.executeScript(script);
        } catch (RuntimeException e) {
            System.err.println("[map] script failed: " + script + " (" + e.getMessage() + ")");
        }
    }

    private static String modeName(Which which) {
        return which == Which.SHOOTER ? "shooter" : "target";
    }

    private Node buildPickModeOverlay() {
        ToggleGroup group = new ToggleGroup();
        ToggleButton shooter = new ToggleButton("Shooter");
        ToggleButton target = new ToggleButton("Target");
        shooter.setToggleGroup(group);
        target.setToggleGroup(group);
        shooter.getStyleClass().addAll("pick-button", "pick-shooter");
        target.getStyleClass().addAll("pick-button", "pick-target");
        group.selectedToggleProperty().addListener((o, was, now) -> {
            if (now == null) was.setSelected(true);                 // one of the two is always active
            else pickMode.set(now == shooter ? Which.SHOOTER : Which.TARGET);
        });
        pickMode.addListener((o, was, now) -> (now == Which.SHOOTER ? shooter : target).setSelected(true));
        shooter.setSelected(true);

        Label hint = new Label("Click map to place:");
        hint.getStyleClass().add("map-hint");
        HBox box = new HBox(6, hint, shooter, target);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("map-overlay");
        box.setMaxSize(HBox.USE_PREF_SIZE, HBox.USE_PREF_SIZE);
        // Top-centre: both providers keep their own controls at the corners (zoom, map type, attribution).
        StackPane.setAlignment(box, Pos.TOP_CENTER);
        StackPane.setMargin(box, new Insets(8));
        return box;
    }

    private String buildHtml() {
        String html;
        try (InputStream in = MapPane.class.getResourceAsStream("map.html")) {
            if (in == null) throw new IllegalStateException("map.html is missing from the app resources");
            html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        String provider;
        if (useGoogle()) {
            String url = String.format(Locale.ROOT,
                    "https://maps.googleapis.com/maps/api/js?key=%s&callback=initGoogle&v=weekly",
                    URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
            provider = "<script src=\"" + url + "\" async defer "
                    + "onerror=\"fail('Could not load Google Maps (offline, or blocked).')\"></script>";
        } else {
            // L_DISABLE_3D: JavaFX's WebView mis-renders the translate3d transforms Leaflet uses by default
            // (tiles vanish or show as fragments), so force plain 2D transforms.
            provider = "<link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\">"
                    + "<script>window.L_DISABLE_3D = true;</script>"
                    + "<script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>"
                    + "<script>initLeaflet();</script>";
        }
        return html.replace("<!--PROVIDER-->", provider);
    }
}
