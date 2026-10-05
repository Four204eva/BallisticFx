# BallisticFX

A ballistic calculator for Windows, written in Java with a JavaFX interface. Pick the shooter and the target on a
map (Google Maps, or OpenStreetMap as a fallback), or use GPS or typed coordinates. Choose a cartridge, set the
weather, and it gives you the firing solution: what to dial for elevation and windage. A flight-time slider
shows velocity, height above the muzzle, energy and drift at every point of the bullet's flight.

## Open it in IntelliJ IDEA

1. **File > Open**, select this folder (the one containing `pom.xml`), and trust the project.
2. Wait for the Maven import to finish. Then set **File > Project Structure > Project > SDK** to a **JDK 21**.
   **Do not use JDK 27** (see *Troubleshooting*). `core` compiles to Java 17 bytecode, `app` to Java 21.
3. Pick the **BallisticFX** run configuration (top right) and press Run.
   It starts `com.ballisticfx.app.Launcher`. IntelliJ may log *"Unsupported JavaFX configuration: classes were loaded
   from 'unnamed module'"*; that warning is harmless.
4. Tests: right-click the `core` or `app` module > *Run 'All Tests'*, or Maven tool window > *Lifecycle > test*.

From a terminal with Maven installed: `mvn install` (builds and tests both modules and installs `core` locally),
then `mvn -pl app javafx:run`.

## Troubleshooting

**The app crashes on startup, or `hs_err_pid*.log` files appear, or you see `Module jdk.jsobject not found, required
by javafx.web`.** You are running on a JDK that has no `jdk.jsobject` module. The map runs in JavaFX's WebView, which
needs it. JDK 27 removed it and no released JavaFX (checked up to JavaFX 27) provides its own copy, so WebView cannot
run there at all. Run on **JDK 21** (tested). I have not tested JDK 25 or 26. On an unsupported JDK started through
`Launcher` or `MainApp.main`, the app now stops with an explanatory message instead of crashing the JVM; the
`javafx:run` Maven goal fails earlier, with the `jdk.jsobject` message above.

Fix: IntelliJ > File > Project Structure > Project > SDK > JDK 21 (and for the terminal, set `JAVA_HOME` to it).

## Using it

1. **Place the shooter and target.** Click the map (the *Shooter / Target* toggle at the top of the map chooses which
   marker you are placing), drag a marker to refine it, press **Use my location (GPS)**, or type coordinates.
   Ground elevation for both points is looked up automatically (about 90 m terrain model).
2. **Choose a cartridge** (17 built-in loads from .22 LR to .50 BMG), or edit the fields and **Save as...** your own load.
3. **Set the weather**, or press **Fetch live weather** (Open-Meteo, no key needed). Enter wind speed and the compass
   direction it blows *from*.
4. **Read the answer**: elevation and windage in MIL or MOA, with click counts for your turret.
   Elevation is measured from your zero, like a real turret.
5. **Drag the Flight time slider** (or press Play) to see the bullet's time, range, height above the muzzle,
   height against the sight line, velocity, Mach, energy and drift at that instant. The **Range card** tab gives
   the dope for other distances along the same line of sight.

Metric/Imperial and MIL/MOA switch in the toolbar. Everything is stored in metric internally, so switching never
loses precision.

### Google Maps

Without a key the app uses OpenStreetMap. To use Google Maps: Google Cloud Console > create or pick a project >
enable **Maps JavaScript API** > Credentials > Create API key > make sure billing is enabled (Google has a free
monthly allowance). Then paste the key into **Maps key...** in the toolbar (or set a `GOOGLE_MAPS_API_KEY`
environment variable).

- The key is stored in `~/.ballisticfx/settings.properties` on your computer, never in the project.
- The map page is loaded locally, so use a key **without an HTTP-referrer restriction**; restrict it to the
  *Maps JavaScript API* instead.
- If Google rejects the key or cannot be reached, the app says so and falls back to OpenStreetMap by itself.
- The Google integration uses the classic `google.maps.Marker`, which Google has deprecated but still supports
  (they promise 12 months' notice before removal). It is isolated in one function in `map.html`
  (`makeMarker`) so moving to `AdvancedMarkerElement` later is a small change.

### GPS

"Use my location" reads the Windows Location service, which combines GPS, Wi-Fi and network positioning. Turn on
**Settings > Privacy & security > Location**, including "Let desktop apps access your location". Accuracy is
whatever Windows provides: a laptop on Wi-Fi is typically tens of metres, a USB GPS puck is a few metres.

## What is modelled

Point-mass trajectory (RK4, 1 ms step) with:

- **G1 and G7 drag** functions (tables cross-checked against py_ballisticcalc), with local Mach number.
- **Moist-air density** from temperature, station pressure and humidity, thinning with height along the flight.
- **Wind** as a moving air mass, relative to the line of fire.
- **Coriolis / Eotvos** (from the shooter's latitude and the bearing to the target) and **spin drift** (Litz approximation).
- **Sight height, zero range and zero atmosphere** (so the dial is measured from your zero), **powder-temperature**
  velocity shift, uphill/downhill shots, and **earth curvature** for long shots.
- **WGS84 geodesy** (Vincenty), because a spherical earth can be wrong by about 0.5 % in range.

Not modelled: aerodynamic jump, velocity-banded BCs, wind that changes along the path, scope cant, bullet-specific
drag curves. Wind at 10 m from a weather model is only a starting point.

### How accurate is it?

Against a published table for the 175 gr SMK (G7 0.243, 2600 fps, 100 yd zero, standard air) the solver lands about
3 % high at 1000 yd (about 41 MOA against 40.1 MOA). That is equal to a 0.007 change in BC, which is smaller than the
real-world uncertainty in a bullet's BC. The integrator is exact in a vacuum (tested against the analytic parabola)
and the wind drift follows the lag-time rule. Your own chronographed velocity and a measured BC matter far more than
anything here. **Confirm on target**, and use it responsibly and legally.

The built-in cartridge figures are typical published values entered from memory, not checked against each
manufacturer's current data sheet. Replace muzzle velocity and BC with your own.

## Project layout

```
pom.xml                         parent (modules: core, app)
core/   ballisticfx-core        pure Java 17, no dependencies: physics, geodesy, cartridge library, tests
app/    ballisticfx-app         JavaFX 21 desktop UI
  ui/        input panel, solution banner, trajectory + slider, range card, wind compass
  map/       MapSurface (interface) and MapPane (Google Maps / OpenStreetMap in a WebView), map.html
  services/  EnvironmentService (Open-Meteo), LocationProvider (Windows Location)
  units/     Metric/Imperial conversion and formatting
  settings/  ~/.ballisticfx preferences and saved custom loads
.run/       IntelliJ run configuration
```

## Porting to Android

The design keeps everything platform-specific behind small seams, but **no Android build has been made or tested**.

- `core` has no UI, network or JavaFX dependency and targets Java 17 bytecode, so it can be used unchanged as a
  library module in an Android Studio app (Kotlin or Java), or inside a Gluon build.
- **Gluon Mobile (GluonFX plugin + GraalVM)** can compile this JavaFX UI for Android. Three things must be replaced:
  - `MapSurface`: JavaFX `WebView` (used by `MapPane`) is not available on Android. Write another implementation,
    for example with Gluon Maps (OpenStreetMap-based, pure JavaFX), or a native map in the host app.
  - `LocationProvider`: add an implementation on Gluon Attach's *Position* service (`WindowsLocationProvider` is only
    chosen on Windows).
  - Layout: the three-pane desktop layout needs a phone arrangement. The panels (`InputPanel`, `SolutionBanner`,
    `TrajectoryTab`, `RangeTableTab`) are separate classes, so they can be rearranged without touching the logic.
- `EnvironmentService` (weather and elevation) uses plain `java.net.http` and Jackson, which should work on both.

## Not set up yet

- Packaging a Windows installer or `.exe` (the JDK's `jpackage` tool is the usual route).
- Saving the full input state between sessions (only units, dial unit, click value, last cartridge, the Maps key and
  your custom loads are remembered).
