package com.ballisticfx.app;

/**
 * Plain entry point that does not extend {@code Application}. Starting a JavaFX app from a class that extends
 * Application fails with "JavaFX runtime components are missing" when the JavaFX jars are on the classpath
 * (which is how IntelliJ runs a Maven project), so IntelliJ's run configuration points here instead.
 */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        MainApp.main(args);
    }
}
