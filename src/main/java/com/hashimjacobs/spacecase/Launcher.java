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
        if (OnAndroid.ACTIVE) {
            // On the Shield the game canvas, the one thing redrawn every frame, was shown at five
            // eighths size in the top-left corner while the menus around it scaled correctly. That
            // is the shape of a dirty-region redraw losing the screen's scale, so Android repaints
            // the whole frame instead of only what changed.
            System.setProperty("prism.dirtyopts", "false");
        }
        Main.main(args);
    }
}
