package com.hashimjacobs.spacecase.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.Effect;

import com.hashimjacobs.spacecase.OnAndroid;

/**
 * Blend modes and effects on the game's canvases, skipped on Android.
 *
 * On the Shield a canvas drawn with either was shown at five eighths of its size in the top-left
 * corner, while the menus, which use neither on a canvas, scaled correctly. Without them the glows
 * and shading are flatter there, but the arena is the right size. The desktop draws exactly as before.
 */
public final class CanvasFx {

    private CanvasFx() {
    }

    public static void blend(GraphicsContext gc, BlendMode mode) {
        if (!OnAndroid.ACTIVE) {
            gc.setGlobalBlendMode(mode);
        }
    }

    public static void effect(GraphicsContext gc, Effect effect) {
        if (!OnAndroid.ACTIVE) {
            gc.setEffect(effect);
        }
    }
}
