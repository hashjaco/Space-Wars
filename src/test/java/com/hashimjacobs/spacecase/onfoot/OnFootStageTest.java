package com.hashimjacobs.spacecase.onfoot;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The on-foot rules, without a toolkit: physics, the combo chains, the specials, and the stage as a whole. */
class OnFootStageTest {

    private static final Physics P = Physics.DEFAULT;
    private static final Buttons IDLE = Buttons.NONE;

    private static Buttons press(boolean jump, boolean down, boolean punch, boolean kick, boolean special) {
        return new Buttons(false, false, jump, down, punch, kick, special, false);
    }

    private static Fighter pilot() {
        return new Fighter(Fighter.Kind.PILOT, 1, 500);
    }

    /** One tick as the stage runs it for a single fighter. */
    private static void tick(Fighter f, Buttons b) {
        f.think(b, P);
        f.integrate(P, 0, 10_000);
        f.tickState();
    }

    private static double apex(int holdTicks) {
        Fighter f = pilot();
        double top = f.y;
        for (int t = 0; t < 120; t++) {
            tick(f, t < holdTicks ? press(true, false, false, false, false) : IDLE);
            top = Math.min(top, f.y);
        }
        return OnFootStage.FLOOR - top;
    }

    @Test
    void heldJumpGoesHigherThanATap() {
        double full = apex(60);
        double tap = apex(2);
        assertTrue(full > 130 && full < 170, "full jump " + full);
        assertTrue(tap < full * 0.6, "tap " + tap + " vs full " + full);
    }

    @Test
    void coyoteTimeForgivesALateJumpButNotAVeryLateOne() {
        Fighter late = pilot();
        late.grounded = false;
        late.y = OnFootStage.FLOOR - 40;
        late.airborneTicks = P.coyoteTicks();
        late.think(press(true, false, false, false, false), P);
        assertEquals(-P.jumpSpeed(), late.vy, 1e-9);

        Fighter tooLate = pilot();
        tooLate.grounded = false;
        tooLate.y = OnFootStage.FLOOR - 40;
        tooLate.airborneTicks = P.coyoteTicks() + 2;
        tooLate.think(press(true, false, false, false, false), P);
        assertEquals(0, tooLate.vy, 1e-9);
    }

    @Test
    void frictionBringsARunToAStop() {
        Fighter f = pilot();
        f.vx = P.runSpeed();
        for (int t = 0; t < 40; t++) {
            tick(f, IDLE);
        }
        assertEquals(0, f.vx, 1e-9);
    }

    @Test
    void knockbackIsDividedByMass() {
        Fighter thrall = new Fighter(Fighter.Kind.THRALL, 0, 0);
        Fighter foreman = new Fighter(Fighter.Kind.FOREMAN, 0, 0);
        thrall.takeHit(Move.CROSS, -1, 1, Move.CROSS.knockX, 0, P);
        foreman.takeHit(Move.CROSS, -1, 1, Move.CROSS.knockX, 0, P);
        assertEquals(thrall.vx * Fighter.Kind.THRALL.mass / Fighter.Kind.FOREMAN.mass, foreman.vx, 1e-9);
    }

    @Test
    void punchPunchPunchChainsIntoTheLauncherButOnlyInsideTheWindow() {
        Fighter f = pilot();
        Buttons punch = press(false, false, true, false, false);
        tick(f, punch);
        assertSame(Move.JAB, f.move);
        Move[] expect = {Move.CROSS, Move.UPPERCUT};
        for (Move next : expect) {
            Move from = f.move;
            while (f.move == from && f.stateTicks < from.startup) {
                tick(f, IDLE);
            }
            tick(f, punch);
            assertSame(next, f.move);
            tick(f, IDLE);
        }

        // Pressed after the jab has finished: a fresh jab, not a cross.
        Fighter slow = pilot();
        tick(slow, punch);
        for (int t = 0; t < Move.JAB.length() + 2; t++) {
            tick(slow, IDLE);
        }
        tick(slow, punch);
        assertSame(Move.JAB, slow.move);
    }

    @Test
    void punchPunchKickEndsInTheHeel() {
        Fighter f = pilot();
        tick(f, press(false, false, true, false, false));
        while (f.stateTicks < Move.JAB.startup) {
            tick(f, IDLE);
        }
        tick(f, press(false, false, true, false, false));
        tick(f, IDLE);
        while (f.stateTicks < Move.CROSS.startup) {
            tick(f, IDLE);
        }
        tick(f, press(false, false, false, true, false));
        assertSame(Move.HEEL, f.move);
    }

    @Test
    void holdingSpecialChargesTheLanceAndDownSpecialIsTheRay() {
        Fighter f = pilot();
        f.ki = 100;
        Buttons hold = press(false, false, false, false, true);
        for (int t = 0; t < 46; t++) {
            tick(f, hold);
        }
        assertEquals(Fighter.State.CHARGE, f.state);
        tick(f, IDLE);
        assertSame(Move.SOLAR_LANCE, f.move);
        assertTrue(f.lancePower > 0.4 && f.lancePower < 0.6, "power " + f.lancePower);
        assertTrue(f.ki < 100, "charging spends ki");

        Fighter ray = pilot();
        ray.ki = 100;
        tick(ray, press(false, true, false, false, true));
        assertSame(Move.NEEDLE_RAY, ray.move);
        assertEquals(100 - Fighter.NEEDLE_COST, ray.ki, 0.2);
    }

    /** A crude bot: walk right, face the nearest enemy, chain punches, ray when there is ki to spare. */
    private static List<Buttons> bot(OnFootStage s) {
        List<Buttons> out = new ArrayList<>();
        for (Fighter p : s.pilots()) {
            Fighter near = null;
            for (Fighter e : s.enemies()) {
                if (e.alive() && (near == null || Math.abs(e.x - p.x) < Math.abs(near.x - p.x))) {
                    near = e;
                }
            }
            double goal = near != null ? near.x : s.hasKey() ? s.keyX() : p.x + 400;
            double gap = goal - p.x;
            boolean close = near != null && Math.abs(gap) < 70 + near.kind.width / 2;
            boolean pulse = s.tick() % 4 < 2;
            boolean ray = near != null && p.ki >= 60 && Math.abs(gap) < 400 && s.tick() % 30 == 0;
            out.add(new Buttons(!close && gap < 0 || close && gap < 0 && p.facing > 0,
                    !close && gap > 0 || close && gap > 0 && p.facing < 0,
                    false, ray, close && pulse, false, ray, false));
        }
        return out;
    }

    private static OnFootStage play(long seed, int players, int maxTicks) {
        OnFootStage s = new OnFootStage(seed, players, P);
        for (int t = 0; t < maxTicks && !s.done(); t++) {
            s.step(bot(s));
        }
        return s;
    }

    @Test
    void theSameSeedAndInputsPlayTheSameStage() {
        assertEquals(play(7, 2, 3000).checksum(), play(7, 2, 3000).checksum());
    }

    @Test
    void aBotCanFinishTheStage() {
        OnFootStage s = play(11, 2, 60 * 60 * 6);
        assertTrue(s.done(), "stuck at " + s.beat() + " segment " + s.segment());
    }

    private static OnFootStage play(long seed, int players, int maxTicks, Mission mission) {
        OnFootStage s = new OnFootStage(seed, players, P, mission);
        for (int t = 0; t < maxTicks && !s.done(); t++) {
            s.step(bot(s));
        }
        return s;
    }

    @Test
    void everyMissionIsDeterministicAndABotCanFinishIt() {
        for (Mission mission : Mission.values()) {
            assertEquals(play(7, 2, 3000, mission).checksum(), play(7, 2, 3000, mission).checksum(),
                    mission + " diverged");
            for (int players = 1; players <= 2; players++) {
                OnFootStage s = play(11, players, 60 * 60 * 8, mission);
                assertTrue(s.done(), mission + " with " + players + " stuck at " + s.beat() + " segment " + s.segment());
            }
        }
    }

    @Test
    void eachGalaxyBossUsesItsSignature() {
        for (Mission mission : Mission.values()) {
            if (mission == Mission.UNDERCITY) {
                continue;
            }
            OnFootStage s = new OnFootStage(11, 2, P, mission);
            boolean bossSeen = false, signature = false;
            for (int t = 0; t < 60 * 60 * 8 && !s.done(); t++) {
                s.step(bot(s));
                Fighter boss = s.boss();
                bossSeen |= boss != null && boss.kind == mission.boss;
                signature |= !s.hazards().isEmpty() || boss != null && boss.move() == Move.GRAVITY_WELL;
            }
            assertTrue(bossSeen, mission + " never fielded " + mission.boss);
            assertTrue(signature, mission + "'s boss never used its signature");
        }
    }

    @Test
    void theMissionsSitOneAGalaxyAfterTheirOwnLevels() {
        java.util.Set<com.hashimjacobs.spacecase.mode.Galaxy> galaxies = new java.util.HashSet<>();
        for (Mission mission : Mission.values()) {
            assertEquals(mission, OnFootStage.missionAfter(mission.level()));
            assertTrue(galaxies.add(mission.level().galaxy()), "two in " + mission.level().galaxy());
        }
        assertEquals(null, OnFootStage.missionAfter(com.hashimjacobs.spacecase.mode.Level.ORBITAL_APPROACH));
    }

    @Test
    void aBruteShrugsOffJabsUntilTheyPileUpButAHeavyHitLands() {
        Fighter brute = new Fighter(Fighter.Kind.BRUTE, 0, 500);
        int jabs = 0;
        while (brute.state != Fighter.State.HITSTUN && jabs < 20) {
            brute.takeHit(Move.JAB, -1, 1, Move.JAB.knockX, Move.JAB.knockY, P);
            jabs++;
        }
        assertEquals((int) Math.ceil(Fighter.BRUTE_ARMOR / Move.JAB.damage), jabs,
                "jabs bounce off until their damage reaches the armor");

        Fighter fresh = new Fighter(Fighter.Kind.BRUTE, 0, 500);
        fresh.takeHit(Move.UPPERCUT, -1, 1, Move.UPPERCUT.knockX, Move.UPPERCUT.knockY, P);
        assertTrue(fresh.state != Fighter.State.FREE, "a heavy hit gets through at once");
    }

    @Test
    void aPilotCannotBeHitAgainUntilItsMercyRunsOut() {
        Fighter f = pilot();
        f.takeHit(Move.CLAW, -1, 1, Move.CLAW.knockX, Move.CLAW.knockY, P);
        assertSame(Fighter.State.HITSTUN, f.state);
        while (f.state != Fighter.State.FREE) {
            tick(f, IDLE);
        }
        assertTrue(!f.hittable(), "protected right after the stun ends");
        for (int t = 0; t < Fighter.MERCY_TICKS; t++) {
            tick(f, IDLE);
        }
        assertTrue(f.hittable(), "and fair game again once the mercy is spent");
    }

    @Test
    void theFloorNeverHoldsMoreThanTheCapAndTheBossFightsAlone() {
        for (Mission mission : Mission.values()) {
            OnFootStage s = new OnFootStage(5, 1, P, mission);
            for (int t = 0; t < 60 * 60 * 6 && !s.done(); t++) {
                s.step(bot(s));
                assertTrue(s.liveEnemies() <= OnFootStage.MAX_ON_FLOOR + 1, mission + " had " + s.liveEnemies());
                if (s.boss() != null && s.boss().alive()) {
                    assertEquals(1, s.liveEnemies(), mission + "'s boss has company");
                }
            }
        }
    }

    @Test
    void theStageFieldsRiveteersAndBrutesAlongsideTheThralls() {
        OnFootStage s = new OnFootStage(11, 2, P);
        java.util.Set<Fighter.Kind> seen = java.util.EnumSet.noneOf(Fighter.Kind.class);
        for (int t = 0; t < 60 * 60 * 6 && !s.done(); t++) {
            s.step(bot(s));
            for (Fighter e : s.enemies()) {
                seen.add(e.kind);
            }
        }
        assertTrue(seen.containsAll(java.util.EnumSet.of(Fighter.Kind.THRALL, Fighter.Kind.RIVETEER,
                Fighter.Kind.BRUTE, Fighter.Kind.FOREMAN)), "met: " + seen);
    }

    @Test
    void fireJumpsEvenWhileUpIsHeldAsAStickDriftingUpWhileRunningHolds() {
        Fighter f = pilot();
        Buttons upAndRight = new Buttons(false, true, true, false, false, false, false, false, false);
        for (int t = 0; t < 120; t++) {
            tick(f, upAndRight);    // the first tick's up is a jump; then it lands, up still held
        }
        assertTrue(f.grounded, "landed, still holding up");
        tick(f, new Buttons(false, true, true, false, false, false, false, false, true));
        assertTrue(f.vy < 0, "A pressed fresh is a jump though up was already down");
    }

    @Test
    void aJumpPressedDuringHitStopIsKeptForWhenItEnds() {
        Fighter f = pilot();
        for (int t = 0; t < 30; t++) {
            tick(f, IDLE);
        }
        f.latch(press(true, false, false, false, false), P);   // tapped and released inside the freeze
        tick(f, IDLE);
        assertTrue(f.vy < 0, "the tap made during hit-stop jumps once it is over");
    }
}
