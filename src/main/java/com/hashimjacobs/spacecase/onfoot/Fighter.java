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
        FOREMAN(118, 230, 3.6, 520, 0.55),
        /** Ranged: keeps its distance and puts a rivet down the floor after a readable wind-up. */
        RIVETEER(48, 112, 0.85, 40, 0.5),
        /** Heavy: shrugs off light hits until enough of them pile up, and rams. */
        BRUTE(74, 150, 2.0, 140, 0.45),
        /** Forgeworks boss: the slam that leaves the floor burning. */
        WARDEN(128, 240, 3.8, 600, 0.52),
        /** Under-Ice boss: spikes that bloom under your feet. */
        MATRIARCH(110, 236, 3.4, 680, 0.6),
        /** Mag-Storm boss: the dash you jump. */
        TYRANT(96, 222, 3.0, 760, 0.7),
        /** Hulk Drift boss: the pull you run against, then the pound you jump. */
        REVENANT(104, 250, 3.6, 840, 0.5);

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

        /** A stage's last fighter: no crowd token, its own bar, and its death ends the fight. */
        public boolean boss() {
            return switch (this) {
                case FOREMAN, WARDEN, MATRIARCH, TYRANT, REVENANT -> true;
                default -> false;
            };
        }
    }

    public enum State { FREE, ATTACK, GUARD, CHARGE, HITSTUN, LAUNCHED, DOWN, GETUP, DEAD }

    static final int MAX_KI = 100;
    /** Light damage a Brute absorbs before it finally flinches. */
    static final double BRUTE_ARMOR = 30;
    static final int CHARGE_MAX = 90;
    static final int CHARGE_MIN = 14;
    static final int NEEDLE_COST = 22;
    static final int DOWN_TICKS = 38;
    static final int GETUP_TICKS = 22;
    /** Ticks a pilot can't be hit after recovering from a hit: room to walk out of a crowd. */
    static final int MERCY_TICKS = 40;

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
    /** Light damage a Brute has soaked since it last flinched. */
    double soaked;
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
    /** Ticks left of a pilot's post-hit protection (see {@link #MERCY_TICKS}). */
    int mercy;
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
    public int mercy() { return mercy; }
    public int heldTicks() { return heldTicks; }
    public double stride() { return stride; }
    public boolean alive() { return state != State.DEAD; }
    public boolean rage() { return kind.boss() && health <= kind.health * 0.22; }

    double left() { return x - kind.width / 2; }
    double right() { return x + kind.width / 2; }
    double top() { return y - kind.height; }

    /** Can take a hit right now. Getting up is protected, so a knockdown is not a death sentence. */
    boolean hittable() {
        return state != State.DEAD && state != State.GETUP && state != State.DOWN && mercy == 0;
    }

    /** Free to act on its own: walking, standing, or in the air under its own power. */
    boolean actionable() {
        return state == State.FREE || state == State.GUARD;
    }

    void enter(State next) {
        if (player > 0 && next == State.FREE && (state == State.HITSTUN || state == State.GETUP)) {
            mercy = MERCY_TICKS;
        }
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

    /**
     * Hit-stop holds the world but not the player: fresh presses go into the same buffers {@link #think}
     * fills, and are acted on when it ends. {@code last} is left alone, so think still sees every edge.
     */
    void latch(Buttons b, Physics p) {
        if (b.jump() && !last.jump() || b.jumpAlt() && !last.jumpAlt()) {
            jumpBuffer = p.bufferTicks();
        }
        if (b.punch() && !last.punch()) {
            punchBuffer = p.bufferTicks();
        }
        if (b.kick() && !last.kick()) {
            kickBuffer = p.bufferTicks();
        }
    }

    /** Pilot only: turns this tick's buttons into intent, buffered so a press a little early still lands. */
    void think(Buttons b, Physics p) {
        boolean punchEdge = b.punch() && !last.punch();
        boolean kickEdge = b.kick() && !last.kick();
        // Either source pressed fresh is a jump, even while the other is held (see Buttons).
        boolean jumpEdge = b.jump() && !last.jump() || b.jumpAlt() && !last.jumpAlt();
        boolean specialEdge = b.special() && !last.special();
        boolean specialRelease = !b.special() && last.special();
        last = b;
        jumpHeld = b.jump() || b.jumpAlt();

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
        mercy = Math.max(0, mercy - 1);
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
        if (kind.boss() && health > 0 && state == State.ATTACK) {
            return damage;      // armoured through its own attacks: trade with it, do not interrupt it
        }
        if (kind == Kind.BRUTE && health > 0 && !m.heavy()) {
            soaked += damage;
            if (soaked < BRUTE_ARMOR) {
                return damage;  // jabs bounce off; a heavy hit, or enough jabs, makes it flinch
            }
            soaked = 0;
        }
        if (state == State.CHARGE) {
            charge = 0;
        }
        double kx = dir * knockX / kind.mass;
        double ky = knockY / Math.max(1, kind.mass * 0.8);
        boolean launch = ky < -3 || !grounded || (m.knockdown && !kind.boss()) || health <= 0;
        if (kind.boss() && health > 0) {
            launch = false;  // a boss staggers, it does not fly
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
