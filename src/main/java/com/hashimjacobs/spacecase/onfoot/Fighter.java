package com.hashimjacobs.spacecase.onfoot;

import java.util.HashSet;
import java.util.Set;

/**
 * Anyone standing on the floor: a pilot, a thrall, the Foreman.
 *
 * Position is the feet: {@code x} is the middle of the body, {@code y} the floor it stands on, so
 * art anchors bottom-centre and flips about {@code x}. Velocity is in pixels a tick. Players and
 * enemies share the body and the move machine; only who presses the buttons differs, which is why
 * an enemy attack is started with the same {@link #start} a pilot's press reaches.
 */
public final class Fighter {

    public enum Kind {
        PILOT(44, 118, 1.0, 100, 1.0),
        THRALL(50, 108, 0.9, 34, 0.62),
        FOREMAN(118, 230, 3.6, 520, 0.55);

        final double width, height, mass;
        final int health;
        /** Enemy walk speed as a fraction of the pilot's. */
        final double pace;

        Kind(double width, double height, double mass, int health, double pace) {
            this.width = width;
            this.height = height;
            this.mass = mass;
            this.health = health;
            this.pace = pace;
        }

        public double width() {
            return width;
        }

        public double height() {
            return height;
        }
    }

    public enum State { FREE, ATTACK, GUARD, CHARGE, HITSTUN, LAUNCHED, DOWN, GETUP, DEAD }

    static final int MAX_KI = 100;
    static final int CHARGE_MAX = 90;
    static final int CHARGE_MIN = 14;
    static final int NEEDLE_COST = 22;
    static final int DOWN_TICKS = 38;
    static final int GETUP_TICKS = 22;

    public final Kind kind;
    /** 1 or 2 for a pilot, 0 for everyone else. */
    public final int player;

    double x, y, vx, vy;
    int facing = 1;
    boolean grounded = true;
    int airborneTicks;
    double health;
    double ki = 40;

    State state = State.FREE;
    int stateTicks;
    Move move;
    final Set<Fighter> struck = new HashSet<>();
    int hitstun;
    int juggles;
    int charge;
    /** Power of the lance being fired, 0..1, fixed at release. */
    double lancePower;

    int heldTicks;
    int jumpBuffer, punchBuffer, kickBuffer, specialTapBuffer;
    private Buttons last = Buttons.NONE;
    boolean jumpHeld;

    /** Squash on landing and stretch on take-off, read by the renderer. */
    int landed;
    /** Ticks of red flash after taking a hit. */
    int hurtFlash;
    /** Distance walked, so the walk cycle advances with the feet instead of with the clock. */
    double stride;

    // Enemy brain. Here rather than in a subclass: two fields do not earn a hierarchy.
    int cooldown;
    boolean token;

    Fighter(Kind kind, int player, double x) {
        this.kind = kind;
        this.player = player;
        this.x = x;
        this.y = OnFootStage.FLOOR;
        this.health = kind.health;
    }

    // ---- for the renderer and the tests

    public double x() { return x; }
    public double y() { return y; }
    public double vx() { return vx; }
    public double vy() { return vy; }
    public int facing() { return facing; }
    public boolean grounded() { return grounded; }
    public State state() { return state; }
    public int stateTicks() { return stateTicks; }
    public Move move() { return move; }
    public double health() { return health; }
    public double healthFraction() { return Math.min(1, Math.max(0, health) / kind.health); }
    public double ki() { return ki; }
    public int charge() { return charge; }
    public double lancePower() { return lancePower; }
    public int landed() { return landed; }
    public int hurtFlash() { return hurtFlash; }
    public int heldTicks() { return heldTicks; }
    public double stride() { return stride; }
    public boolean alive() { return state != State.DEAD; }
    public boolean rage() { return kind == Kind.FOREMAN && health <= kind.health * 0.22; }

    double left() { return x - kind.width / 2; }
    double right() { return x + kind.width / 2; }
    double top() { return y - kind.height; }

    /** Can take a hit right now. Getting up is protected, so a knockdown is not a death sentence. */
    boolean hittable() {
        return state != State.DEAD && state != State.GETUP && state != State.DOWN;
    }

    /** Free to act on its own: walking, standing, or in the air under its own power. */
    boolean actionable() {
        return state == State.FREE || state == State.GUARD;
    }

    void enter(State next) {
        state = next;
        stateTicks = 0;
        if (next != State.ATTACK) {
            move = null;
        }
    }

    void start(Move next) {
        enter(State.ATTACK);
        move = next;
        struck.clear();
        if (next == Move.DIVE_KICK) {
            vx = facing * 7.5;
            vy = 9;
        }
    }

    /** Ticks into the current move, and which part of it that is. */
    boolean hitting() {
        return move != null && stateTicks >= move.startup && stateTicks < move.startup + move.active;
    }

    /** Pilot only: turns this tick's buttons into intent, buffered so a press a little early still lands. */
    void think(Buttons b, Physics p) {
        boolean punchEdge = b.punch() && !last.punch();
        boolean kickEdge = b.kick() && !last.kick();
        boolean jumpEdge = b.jump() && !last.jump();
        boolean specialEdge = b.special() && !last.special();
        boolean specialRelease = !b.special() && last.special();
        last = b;
        jumpHeld = b.jump();

        jumpBuffer = jumpEdge ? p.bufferTicks() : Math.max(0, jumpBuffer - 1);
        punchBuffer = punchEdge ? p.bufferTicks() : Math.max(0, punchBuffer - 1);
        kickBuffer = kickEdge ? p.bufferTicks() : Math.max(0, kickBuffer - 1);
        specialTapBuffer = specialEdge && b.down() ? p.bufferTicks() : Math.max(0, specialTapBuffer - 1);

        int dir = (b.right() ? 1 : 0) - (b.left() ? 1 : 0);

        switch (state) {
            case ATTACK -> {
                // A chain or a special cancels a move once it has started hitting, never during wind-up:
                // the cancel window is the whole of active and recovery, which keeps combos easy to land.
                if (move != null && stateTicks >= move.startup) {
                    if (specialTapBuffer > 0 && ki >= NEEDLE_COST && grounded && isNormal(move)) {
                        specialTapBuffer = 0;
                        startNeedle();
                        return;
                    }
                    Move next = punchBuffer > 0 ? move.chain(true) : kickBuffer > 0 ? move.chain(false) : null;
                    if (next != null) {
                        punchBuffer = 0;
                        kickBuffer = 0;
                        start(next);
                    }
                }
                return;
            }
            case CHARGE -> {
                charge = Math.min(CHARGE_MAX, charge + 1);
                ki = Math.max(0, ki - 0.55);
                if (specialRelease || ki <= 0) {
                    if (charge >= CHARGE_MIN) {
                        lancePower = (double) charge / CHARGE_MAX;
                        start(Move.SOLAR_LANCE);
                    } else {
                        enter(State.FREE);
                    }
                    charge = 0;
                }
                return;
            }
            case FREE, GUARD -> {
            }
            default -> {
                return;
            }
        }

        if (grounded && b.guard()) {
            if (state != State.GUARD) {
                enter(State.GUARD);
            }
            return;
        }
        if (state == State.GUARD) {
            enter(State.FREE);
        }

        if (dir != 0) {
            heldTicks++;
            if (grounded) {
                facing = dir;
            }
        } else {
            heldTicks = 0;
        }

        if (specialTapBuffer > 0 && grounded && ki >= NEEDLE_COST) {
            specialTapBuffer = 0;
            startNeedle();
            return;
        }
        if (specialEdge && !b.down() && grounded && ki >= 10) {
            enter(State.CHARGE);
            charge = 0;
            return;
        }
        if (punchBuffer > 0) {
            punchBuffer = 0;
            start(grounded ? Move.JAB : Move.AIR_JAB);
            return;
        }
        if (kickBuffer > 0) {
            kickBuffer = 0;
            start(grounded ? Move.SWEEP : Move.DIVE_KICK);
            return;
        }
        if (jumpBuffer > 0 && (grounded || airborneTicks <= p.coyoteTicks())) {
            jumpBuffer = 0;
            vy = -p.jumpSpeed();
            grounded = false;
            airborneTicks = p.coyoteTicks() + 1;
        }

        double top = heldTicks > p.runAfter() ? p.runSpeed() : p.walkSpeed();
        double accel = grounded ? p.groundAccel() : p.airAccel();
        if (dir != 0) {
            vx = approach(vx, dir * top, accel);
        }
    }

    private void startNeedle() {
        ki -= NEEDLE_COST;
        start(Move.NEEDLE_RAY);
    }

    private static boolean isNormal(Move m) {
        return m == Move.JAB || m == Move.CROSS || m == Move.UPPERCUT || m == Move.SWEEP
                || m == Move.ROUNDHOUSE || m == Move.HEEL;
    }

    /**
     * Integrates one tick: gravity, friction, the floor and the walls.
     *
     * @return true when the fighter landed this tick, so the stage can kick up dust
     */
    boolean integrate(Physics p, double wallLeft, double wallRight) {
        boolean landedNow = false;
        if (!grounded) {
            double g = state == State.LAUNCHED ? p.juggleGravity() : p.gravity();
            // Releasing jump while still rising cuts the jump short, once: variable height.
            if (player > 0 && state == State.FREE && !jumpHeld && vy < -p.jumpSpeed() * p.jumpCut()) {
                vy = -p.jumpSpeed() * p.jumpCut();
            }
            vy = Math.min(p.maxFall(), vy + g);
            vx *= p.airDrag();
            airborneTicks++;
        } else {
            boolean steering = player > 0 && state == State.FREE && heldTicks > 0;
            boolean braced = state == State.ATTACK || state == State.GUARD || state == State.CHARGE;
            if (!steering) {
                vx *= braced ? p.friction() * 0.9 : p.friction();
                if (Math.abs(vx) < 0.05) {
                    vx = 0;
                }
            }
        }
        x += vx;
        y += vy;
        if (grounded) {
            stride += Math.abs(vx);
        }

        if (y >= OnFootStage.FLOOR) {
            double impact = vy;
            y = OnFootStage.FLOOR;
            if (state == State.LAUNCHED && impact > p.bounceSpeed() * 1.4 && juggles < 99) {
                // Slammed into the floor: one bounce, then down for good.
                vy = -impact * p.groundBounce();
                juggles = 99;
            } else {
                vy = 0;
                if (!grounded) {
                    landedNow = true;
                    landed = 8;
                }
                grounded = true;
                airborneTicks = 0;
                if (state == State.LAUNCHED) {
                    enter(health <= 0 ? State.DEAD : State.DOWN);
                    juggles = 0;
                } else if (state == State.ATTACK && move == Move.DIVE_KICK) {
                    enter(State.FREE);
                    vx *= 0.3;
                }
            }
        } else if (grounded && y < OnFootStage.FLOOR) {
            grounded = false;
        }

        if (left() < wallLeft) {
            x = wallLeft + kind.width / 2;
            vx = state == State.LAUNCHED && -vx > p.bounceSpeed() ? -vx * p.wallBounce() : 0;
        } else if (right() > wallRight) {
            x = wallRight - kind.width / 2;
            vx = state == State.LAUNCHED && vx > p.bounceSpeed() ? -vx * p.wallBounce() : 0;
        }
        return landedNow;
    }

    /** Advances the state clocks after the fighter has moved. */
    void tickState() {
        stateTicks++;
        landed = Math.max(0, landed - 1);
        hurtFlash = Math.max(0, hurtFlash - 1);
        ki = Math.min(MAX_KI, ki + (player > 0 && state != State.CHARGE ? 0.06 : 0));
        switch (state) {
            case ATTACK -> {
                if (move != Move.DIVE_KICK && stateTicks >= move.length()) {
                    enter(State.FREE);
                }
                if (move == Move.DIVE_KICK && stateTicks > move.length() + 60) {
                    enter(State.FREE);
                }
            }
            case HITSTUN -> {
                if (stateTicks >= hitstun) {
                    enter(health <= 0 ? State.DEAD : State.FREE);
                }
            }
            case DOWN -> {
                if (stateTicks >= DOWN_TICKS) {
                    enter(health <= 0 ? State.DEAD : State.GETUP);
                }
            }
            case GETUP -> {
                if (stateTicks >= GETUP_TICKS) {
                    enter(State.FREE);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Takes a hit from the front or back of {@code from}.
     *
     * Knockback is divided by mass, so the same uppercut that launches a thrall only rocks the Foreman.
     * A fighter already in the air is re-launched, weaker each time, which is what ends a juggle.
     *
     * @return the damage actually dealt (less when guarded)
     */
    double takeHit(Move m, int damageOverride, int dir, double knockX, double knockY, Physics p) {
        int damage = damageOverride >= 0 ? damageOverride : m.damage;
        boolean frontal = facing == -dir;
        if (state == State.GUARD && frontal && grounded) {
            double chip = damage * 0.15;
            health -= chip;
            ki = Math.min(MAX_KI, ki + 4);
            vx = dir * Math.max(2, knockX * 0.5) / kind.mass;
            stateTicks = 0;
            return chip;
        }
        health -= damage;
        hurtFlash = 8;
        if (kind == Kind.FOREMAN && health > 0 && state == State.ATTACK) {
            return damage;      // armoured through its own attacks: trade with it, do not interrupt it
        }
        if (state == State.CHARGE) {
            charge = 0;
        }
        double kx = dir * knockX / kind.mass;
        double ky = knockY / Math.max(1, kind.mass * 0.8);
        boolean launch = ky < -3 || !grounded || (m.knockdown && kind != Kind.FOREMAN) || health <= 0;
        if (kind == Kind.FOREMAN && health > 0) {
            launch = false;  // the Foreman staggers, it does not fly
        }
        if (launch) {
            double decay = Math.pow(p.juggleDecay(), juggles);
            juggles++;
            vx = kx * decay;
            vy = Math.min(ky, -4) * decay;
            grounded = false;
            facing = -dir;
            enter(State.LAUNCHED);
        } else {
            vx = kx;
            hitstun = m.hitstun;
            facing = -dir;
            enter(State.HITSTUN);
        }
        return damage;
    }

    static double approach(double value, double target, double step) {
        if (value < target) {
            return Math.min(target, value + step);
        }
        return Math.max(target, value - step);
    }
}
