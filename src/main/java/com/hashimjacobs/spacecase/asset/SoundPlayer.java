package com.hashimjacobs.spacecase.asset;

/**
 * Plays a sound effect.
 *
 * The engine depends on this rather than on {@link SoundBank} directly, so collision handling can be
 * tested without starting the JavaFX toolkit that AudioClip requires.
 */
@FunctionalInterface
public interface SoundPlayer {

    void play(SoundFx effect);

    /** For tests and for any context where audio is unavailable. */
    SoundPlayer SILENT = effect -> {
    };
}
