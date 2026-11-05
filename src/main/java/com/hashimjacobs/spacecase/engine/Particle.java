package com.hashimjacobs.spacecase.engine;

/**
 * One cosmetic spark. Ticked by {@link World#tickScenery()}, drawn by the renderer, never folded into
 * the checksum: it is decoration, and it is seeded from the tick rather than from the shared
 * {@code Random} so it cannot move a spawn roll (docs/ROADMAP.md rule 8).
 */
public final class Particle {

    public enum Kind {
        /** Hot debris off a hull: orange, short. */
        SPARK(18),
        /** Muzzle glow at the player's gun: white, very short. */
        MUZZLE(4),
        /** Drifting cinders after a flagship goes: slower, longer. */
        EMBER(40),
        /** Electrical arcing off a hull that is nearly gone: bright, short, jittery. */
        ARC(10),
        /** Smoke off the same hull: slow, dim, growing. */
        SMOKE(48);

        final int life;

        Kind(int life) {
            this.life = life;
        }
    }

    private final Kind kind;
    private double x;
    private double y;
    private double velocityX;
    private double velocityY;
    private int life;

    Particle(Kind kind, double x, double y, double velocityX, double velocityY) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.life = kind.life;
    }

    void tick() {
        x += velocityX;
        y += velocityY;
        velocityX *= 0.92;
        velocityY *= 0.92;
        life--;
    }

    public boolean isDead() {
        return life <= 0;
    }

    public Kind kind() {
        return kind;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    /** 1 when born, 0 when spent. */
    public double remaining() {
        return Math.max(0, life / (double) kind.life);
    }
}
