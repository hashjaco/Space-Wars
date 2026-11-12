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
        // Android TV reports twice the pixels it lays out, and JavaFX's canvas there does not honor
        // that pixel scale: the arena was drawn at 1.25x of the logical screen in physical pixels,
        // shrunk into the top-left corner. With HiDPI off, logical and physical pixels are the same
        // and the router scales the whole screen to fit as it does everywhere else. ANDROID_ROOT is
        // set on every Android and nowhere else, so a Retina desktop keeps its sharp scaling.
        if (System.getenv("ANDROID_ROOT") != null) {
            System.setProperty("prism.allowhidpi", "false");
        }
        Main.main(args);
    }
}
