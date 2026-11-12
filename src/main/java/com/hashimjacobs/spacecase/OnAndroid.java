package com.hashimjacobs.spacecase;

/**
 * Whether this process is the Android build.
 *
 * ANDROID_ROOT is in every Android app's environment and in no desktop's, so this needs no Android
 * class on the desktop classpath. Read once; it cannot change while the game runs.
 */
public final class OnAndroid {

    public static final boolean ACTIVE = System.getenv("ANDROID_ROOT") != null;

    private OnAndroid() {
    }
}
