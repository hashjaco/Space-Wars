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
            com.hashimjacobs.spacecase.asset.PlatformAudio.startEarly();
            // EXPERIMENT: JavaFX starts its texture pool at 64 MB and evicts textures it has not
            // drawn lately before it will grow. An animation is a different image every frame, so
            // if the frames do not all fit, each is evicted and uploaded again on the next loop --
            // and an upload converts the image through a fresh buffer its own size. That would be
            // the quarter megabyte of garbage a frame measured on the Shield. Must be set before
            // the toolkit starts.
            System.setProperty("prism.targetvram", "192m");
        }
        Main.main(args);
    }
}
