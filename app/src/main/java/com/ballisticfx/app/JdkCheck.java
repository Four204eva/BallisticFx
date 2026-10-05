package com.ballisticfx.app;

import java.awt.GraphicsEnvironment;
import java.util.Optional;

import javax.swing.JOptionPane;

/**
 * Refuses to start on a JDK that cannot host the map, instead of letting the JVM crash.
 *
 * <p>The map runs in JavaFX's WebView, which needs {@code netscape.javascript.JSObject} from the JDK module
 * {@code jdk.jsobject}. JDK 27 no longer ships that module (it went away with the Applet API), and no released
 * JavaFX provides its own copy. On such a JDK the WebView fails inside native code and takes the whole JVM down
 * with an {@code hs_err_pid*.log}; JDK 21 has the module and works.
 */
final class JdkCheck {
    private JdkCheck() {}

    /** True if this JDK provides the class WebView needs. */
    static boolean hasJsObject() {
        try {
            Class.forName("netscape.javascript.JSObject", false, JdkCheck.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** A human-readable explanation, or empty if this JDK is fine. */
    static Optional<String> problem() {
        return hasJsObject() ? Optional.empty() : Optional.of(message(Runtime.version().toString()));
    }

    static String message(String javaVersion) {
        return "BallisticFX cannot start on Java " + javaVersion + ".\n\n"
                + "Its map needs the 'jdk.jsobject' module, which this Java does not include "
                + "(newer JDKs such as 27 removed it). JDK 21 includes it.\n\n"
                + "In IntelliJ: File > Project Structure > Project > SDK, choose a JDK 21, then run again.\n"
                + "From a terminal: set JAVA_HOME to a JDK 21.";
    }

    /** Call first thing in {@code main}: prints and shows the problem, then exits, so nothing crashes later. */
    static void exitIfIncompatible() {
        Optional<String> problem = problem();
        if (problem.isEmpty()) return;
        System.err.println(problem.get());
        if (!GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(null, problem.get(), "BallisticFX", JOptionPane.ERROR_MESSAGE);
        }
        System.exit(2);
    }
}
