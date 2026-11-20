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
        }
        // JavaFX's GPU check turns down ARM Linux boards whose drivers it does not know, the
        // Raspberry Pi 5's V3D among them, and drops to software rendering, which cannot hold the
        // frame rate there. Its OpenGL works fine, so skip the check. Here rather than in Main,
        // because the java launcher starts an Application main class without calling its main().
        // A -Dprism.forceGPU on the command line wins.
        if (!OnAndroid.ACTIVE && System.getProperty("os.name").equals("Linux")
                && System.getProperty("os.arch").equals("aarch64")
                && System.getProperty("prism.forceGPU") == null) {
            System.setProperty("prism.forceGPU", "true");
        }
        Main.main(args);
    }
}
