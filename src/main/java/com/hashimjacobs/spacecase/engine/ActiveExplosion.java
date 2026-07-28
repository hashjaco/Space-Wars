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

    private final List<Image> frames;
    private final int ticksPerFrame;
    private final double centerX;
    private final double centerY;
    private final double size;
    private int tick;

    public ActiveExplosion(Explosion explosion, double centerX, double centerY, double size) {
        this.frames = Assets.explosionFrames(explosion);
        this.ticksPerFrame = explosion.ticksPerFrame();
        this.centerX = centerX;
        this.centerY = centerY;
        this.size = size;
    }

    public void tick() {
        tick++;
    }

    public boolean isFinished() {
        boolean finished = frameIndex() >= frames.size();
        return finished;
    }

    public Image currentFrame() {
        int index = Math.min(frameIndex(), frames.size() - 1);
        Image frame = frames.get(index);
        return frame;
    }

    private int frameIndex() {
        int index = tick / ticksPerFrame;
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
