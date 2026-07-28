package com.hashimjacobs.spacecase.engine;

import java.util.List;

import javafx.scene.image.Image;

import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.Explosion;

/**
 * One playing explosion.
 *
 * Frames come from the preloaded set in {@link Assets}. The old code built its frame list by
 * walking the sprite directory at the moment of each kill, decoding up to 49 PNGs synchronously on
 * the render thread.
 */
public final class ActiveExplosion {

    private final Explosion explosion;
    private final double centerX;
    private final double centerY;
    private final double size;
    private int tick;

    public ActiveExplosion(Explosion explosion, double centerX, double centerY, double size) {
        this.explosion = explosion;
        this.centerX = centerX;
        this.centerY = centerY;
        this.size = size;
    }

    public void tick() {
        tick++;
    }

    public boolean isFinished() {
        boolean finished = frameIndex() >= explosion.frameCount();
        return finished;
    }

    /**
     * Resolved at draw time rather than in the constructor, so spawning an explosion needs no
     * decoded images and the simulation stays testable without the JavaFX toolkit.
     */
    public Image currentFrame() {
        List<Image> frames = Assets.explosionFrames(explosion);
        int index = Math.min(frameIndex(), frames.size() - 1);
        Image frame = frames.get(index);
        return frame;
    }

    private int frameIndex() {
        int index = tick / explosion.ticksPerFrame();
        return index;
    }

    public double drawX() {
        double x = centerX - size / 2;
        return x;
    }

    public double drawY() {
        double y = centerY - size / 2;
        return y;
    }

    public double size() {
        return size;
    }
}
