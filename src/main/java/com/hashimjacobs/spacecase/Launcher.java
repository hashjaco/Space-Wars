package com.hashimjacobs.spacecase;

/**
 * Jar entry point.
 *
 * A main class that extends Application refuses to start when the JavaFX modules are on the
 * classpath rather than the module path, which is how a plain {@code java -jar} run resolves them.
 * Delegating from a class that does not extend Application sidesteps that check.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}
