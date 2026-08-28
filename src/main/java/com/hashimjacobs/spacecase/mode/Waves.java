package com.hashimjacobs.spacecase.mode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.hashimjacobs.spacecase.entity.WaveShip;

import static com.hashimjacobs.spacecase.entity.EnemyShip.EnemyKind.CRUISER;
import static com.hashimjacobs.spacecase.entity.EnemyShip.EnemyKind.FIGHTER;
import static com.hashimjacobs.spacecase.entity.EnemyShip.EnemyKind.SCOUT;
import static com.hashimjacobs.spacecase.entity.WaveShip.at;

/**
 * Every level's waves, written out ship by ship.
 *
 * A wave is a list of ships and nothing else. There is deliberately no {@code Wave} type, because a
 * wave has no properties of its own -- when one wants a name or its own timeout, that is the moment
 * to make it a type, and not before.
 *
 * Here rather than on {@link Level} for the reason {@code Boss.music()} is a method rather than a
 * ninth constructor argument: the eleven-argument constructor stays as it is. The difference is
 * only volume -- {@code music()} is three lines and this is thirteen hundred, and burying fifty
 * levels under that would stop Level.java being the table a person can read top to bottom.
 *
 * Two rules the whole file obeys, both of which {@code WavesTest} enforces:
 *
 * <ul>
 * <li><strong>Across is a fraction of {@code Orientation.arenaBreadth()}</strong>, never pixels.
 *     The breadth is 996 in a top-down level and 864 in a side-on one, so a pixel written for one
 *     is in the wrong place in the other. Setback is pixels, and always negative -- it is depth
 *     behind the entry edge, which is how a formation gets its shape without needing a timer.</li>
 * <li><strong>In a CAVE level every ship sits inside the band 0.33 to 0.67.</strong> That band is
 *     open at every depth, every tick and every seed: a tunnel's inset is at most
 *     {@code (1 - minLaneFraction) / 2} of the breadth, which for CAVE is a third. Author outside
 *     it and {@code SpawnDirector} clamps the ship to the wall -- legal, and not the formation
 *     drawn here, which is worse than a crash because nothing reports it.</li>
 * </ul>
 *
 * The hull is never named in a row. {@code SpawnDirector} asks {@link Level#enemySprite} for it, so
 * the same row wears a foundry hull in the Undercity and an ice hull in the Frost Ring, and a wave
 * stays themed without knowing which level it is in.
 *
 * Pure data -- no asset loading -- so the tables are walkable in tests that never start the toolkit.
 */
public final class Waves {

    private Waves() {
    }

    /** One wave. Exists so a level's waves read as rows rather than as nested {@code List.of}. */
    private static List<WaveShip> wave(WaveShip... ships) {
        return List.of(ships);
    }

    /** How many ships each of a wave's three groups fields, in the order they are launched. */
    private static final int[] GROUP_SIZES = {6, 8, 10};

    /** How much further back each group after the first arrives, and how deep an infill ship sits. */
    private static final double DEPTH_STEP = 150;

    /** How many groups a wave is made of. {@code engine.SpawnDirector} launches them one at a time. */
    public static final int GROUPS_PER_WAVE = GROUP_SIZES.length;

    /**
     * One launched group: the authored row, densified to {@link #GROUP_SIZES} and pushed deeper.
     *
     * A wave is three of these in sequence rather than one row of five, and the next only goes out
     * once the last is dead. The rows themselves are untouched -- 235 of them across fifty levels,
     * every position chosen -- so this widens what a wave is without re-authoring any of it.
     *
     * <strong>Infill is at the midpoint of the widest gap, never a mirror.</strong> Most authored
     * rows are symmetric wedges, so {@code across -> 1 - across} reproduces the row exactly and
     * stacks every padded ship on top of one that is already there. Midpoints also cannot leave the
     * band a row was written in, which is what keeps the twelve CAVE levels inside 0.33-0.67 instead
     * of being clamped to the wall by {@code SpawnDirector.insideLane} -- legal, silent, and not the
     * formation anybody drew.
     *
     * An infill ship copies its left neighbour whole, so it is that neighbour's archetype at that
     * neighbour's tuning and flying that neighbour's style. Deliberately: a stock scout appearing in
     * the middle of a rank of buffed cruisers is the kind of thing nothing reports.
     *
     * Pure and deterministic -- no draw from the generator, which is rule 8 in {@code docs/ROADMAP.md}.
     * The seeded spawn tests describe the same run they always did.
     *
     * @param index which group of the wave, 0 to {@code GROUPS_PER_WAVE - 1}
     */
    public static List<WaveShip> group(List<WaveShip> row, int index) {
        List<WaveShip> ships = new ArrayList<>(row);
        ships.sort(Comparator.comparingDouble(WaveShip::across));
        // Nothing to interpolate between. Unreachable today -- the shortest authored row is four --
        // and here so that writing a one-ship row is a thin wave rather than a crash.
        while (ships.size() > 1 && ships.size() < GROUP_SIZES[index]) {
            int after = widestGap(ships);
            WaveShip left = ships.get(after);
            double midpoint = (left.across() + ships.get(after + 1).across()) / 2;
            ships.add(after + 1, left.movedTo(midpoint, left.setback() - DEPTH_STEP));
        }
        if (index == 0) {
            return List.copyOf(ships);
        }
        // Later groups arrive further back, so a wave reads as three passes rather than as one
        // formation that keeps reappearing on the entry line.
        return ships.stream()
                .map(ship -> ship.movedTo(ship.across(), ship.setback() - index * DEPTH_STEP))
                .toList();
    }

    /**
     * The index of the ship the next infill goes after: the left side of the widest gap across.
     *
     * Widest rather than first, so a rank fills evenly instead of crowding one flank. Ties take the
     * leftmost, which is only reachable when a row is perfectly regular and then the choice does not
     * matter.
     */
    private static int widestGap(List<WaveShip> ships) {
        int widest = 0;
        double span = -1;
        for (int i = 0; i < ships.size() - 1; i++) {
            double gap = ships.get(i + 1).across() - ships.get(i).across();
            if (gap > span) {
                span = gap;
                widest = i;
            }
        }
        return widest;
    }

    /**
     * The waves this level fields before its flagship, in order.
     *
     * A switch rather than a map so that deleting the default arm makes the compiler demand a row
     * for every level -- which is a stronger guarantee than a test, and free.
     */
    public static List<List<WaveShip>> forLevel(Level level) {
        return switch (level) {

            // ---- Galaxy 1: Verdance (levels 1-10) --------------------------------------------
            //
            // The galaxy that teaches. Waves open as shapes you can read at a glance and close as
            // shapes you have to answer: a rank, then a rank with something behind it, then a heavy
            // that has to be dealt with before the escort reaches you. Almost everything here flies
            // STRAIGHT or HUNT, because a player learning the game should be learning the hulls
            // rather than five kinds of trajectory.

            /*
             * The tutorial, in wave form. Wave one tunes nothing whatsoever on purpose -- the first
             * six ships anybody ever meets should behave exactly as the archetype they are there to
             * teach, so a scout is a scout before it is ever anything else.
             */
            case ORBITAL_APPROACH -> List.of(
                    wave(at(SCOUT, 0.20, -240).straight(), at(SCOUT, 0.32, -140).straight(),
                         at(SCOUT, 0.44, -40).straight(), at(SCOUT, 0.56, -40).straight(),
                         at(SCOUT, 0.68, -140).straight(), at(SCOUT, 0.80, -240).straight()),
                    // The same wedge, with the point turned around and two fighters holding it up.
                    wave(at(SCOUT, 0.10, -40).straight(), at(SCOUT, 0.86, -40).straight(),
                         at(FIGHTER, 0.36, -180), at(FIGHTER, 0.58, -180),
                         at(SCOUT, 0.28, -340).straight(), at(SCOUT, 0.68, -340).straight()),
                    // One heavy, escorted: the first thing in the game you cannot simply outshoot.
                    wave(at(FIGHTER, 0.24), at(FIGHTER, 0.70),
                         at(SCOUT, 0.12, -120).straight(), at(SCOUT, 0.84, -120).straight(),
                         at(CRUISER, 0.46, -340).size(1.15).health(1.25).speed(0.85)));

            /*
             * First air, and the first wave that moves as something alive rather than as a formation.
             * The hive's scouts weave; the fighters behind them do not, so there is always a stable
             * thing to shoot at behind the thing that will not hold still.
             */
            case VERDANT_AIRSPACE -> List.of(
                    wave(at(SCOUT, 0.18).weaving(), at(SCOUT, 0.38).weaving(),
                         at(SCOUT, 0.58).weaving(), at(SCOUT, 0.78).weaving(),
                         at(FIGHTER, 0.48, -260).straight()),
                    wave(at(FIGHTER, 0.16, -60).straight(), at(FIGHTER, 0.76, -60).straight(),
                         at(SCOUT, 0.34, -200).weaving(), at(SCOUT, 0.60, -200).weaving(),
                         at(SCOUT, 0.46, -360).weaving(), at(SCOUT, 0.24, -360).weaving()),
                    // A drone swarm around one fat brood-carrier. Kill the carrier or keep swatting.
                    wave(at(CRUISER, 0.44).size(1.25).health(1.35).speed(0.75).fireGap(1.4),
                         at(SCOUT, 0.14, -140).weaving(), at(SCOUT, 0.32, -180).weaving(),
                         at(SCOUT, 0.64, -180).weaving(), at(SCOUT, 0.82, -140).weaving(),
                         at(SCOUT, 0.48, -320).weaving()));

            /*
             * Falling through the canopy, so the waves fall too: everything here is fast and thin
             * and arrives from a long way back. The first level where a wave has real depth.
             */
            case CANOPY_DESCENT -> List.of(
                    wave(at(SCOUT, 0.22, -40).speed(1.25).straight(),
                         at(SCOUT, 0.42, -200).speed(1.25).straight(),
                         at(SCOUT, 0.62, -360).speed(1.25).straight(),
                         at(SCOUT, 0.80, -520).speed(1.25).straight(),
                         at(SCOUT, 0.10, -680).speed(1.25).straight()),
                    wave(at(FIGHTER, 0.30).drifting(), at(FIGHTER, 0.64).drifting(),
                         at(SCOUT, 0.16, -200).diving(), at(SCOUT, 0.80, -200).diving(),
                         at(SCOUT, 0.48, -380).diving()),
                    wave(at(CRUISER, 0.28, -80).size(1.1).health(1.2),
                         at(CRUISER, 0.62, -80).size(1.1).health(1.2),
                         at(SCOUT, 0.46, -260).diving(), at(SCOUT, 0.14, -420).weaving(),
                         at(SCOUT, 0.82, -420).weaving()));

            /*
             * Scrap country. Four waves now, and the first appearance of the shape the rest of the
             * game leans on: a heavy that parks in your lane and has to be removed.
             */
            case RUST_CANYON -> List.of(
                    wave(at(SCOUT, 0.14).straight(), at(SCOUT, 0.30).straight(),
                         at(SCOUT, 0.50).straight(), at(SCOUT, 0.70).straight(),
                         at(SCOUT, 0.86).straight()),
                    wave(at(FIGHTER, 0.22, -60), at(FIGHTER, 0.50, -60), at(FIGHTER, 0.74, -60),
                         at(SCOUT, 0.36, -240).weaving(), at(SCOUT, 0.62, -240).weaving()),
                    wave(at(CRUISER, 0.46).size(1.2).health(1.3).holding(),
                         at(SCOUT, 0.12, -160).diving(), at(SCOUT, 0.84, -160).diving(),
                         at(FIGHTER, 0.30, -320).straight(), at(FIGHTER, 0.64, -320).straight()),
                    // Salvage rig and its cutters: undersized, cheap, and there are a lot of them.
                    wave(at(SCOUT, 0.08).size(0.85).health(0.8).weaving(),
                         at(SCOUT, 0.24).size(0.85).health(0.8).weaving(),
                         at(SCOUT, 0.40).size(0.85).health(0.8).weaving(),
                         at(SCOUT, 0.58).size(0.85).health(0.8).weaving(),
                         at(SCOUT, 0.76).size(0.85).health(0.8).weaving(),
                         at(CRUISER, 0.46, -340).size(1.3).health(1.5).speed(0.7).fireGap(0.8)));

            /*
             * The first tunnel. A wave here is a column, not a rank -- everything sits inside the
             * band the lane guarantees, so there is no room to spread out and the depth does the
             * work instead. That constraint is the level, and it is why Undercity does not feel
             * like Orbital Approach for any reason other than the sky.
             */
            case UNDERCITY -> List.of(
                    wave(at(SCOUT, 0.38).straight(), at(SCOUT, 0.56).straight(),
                         at(SCOUT, 0.46, -180).straight(), at(SCOUT, 0.36, -360).straight(),
                         at(SCOUT, 0.58, -360).straight()),
                    // Two gate guards with a file of quick ones threading between them.
                    wave(at(FIGHTER, 0.36).holding(), at(FIGHTER, 0.54).holding(),
                         at(SCOUT, 0.45, -160).speed(1.3).straight(),
                         at(SCOUT, 0.45, -300).speed(1.3).straight(),
                         at(SCOUT, 0.45, -440).speed(1.3).straight()),
                    // Foundry seconds: two thin cruisers, so the lane is blocked twice over without
                    // either one being a fight on its own.
                    wave(at(CRUISER, 0.36).size(0.85).health(0.8),
                         at(CRUISER, 0.54).size(0.85).health(0.8),
                         at(FIGHTER, 0.45, -220).straight(), at(SCOUT, 0.37, -400).straight(),
                         at(SCOUT, 0.55, -400).straight()),
                    // The throat plug. One slow, oversized, fast-firing thing you have to get past.
                    wave(at(CRUISER, 0.44, -60).size(1.35).health(1.6).speed(0.7).fireGap(0.75),
                         at(FIGHTER, 0.36, -260).straight(), at(FIGHTER, 0.56, -260).straight(),
                         at(SCOUT, 0.40, -440).straight(), at(SCOUT, 0.54, -440).straight()));

            /*
             * Open space and nothing to hide behind, so this is the galaxy's widest level: waves use
             * the whole breadth and arrive from both edges at once.
             */
            case VOID_RIFT -> List.of(
                    wave(at(SCOUT, 0.04).straight(), at(SCOUT, 0.22).straight(),
                         at(SCOUT, 0.44).straight(), at(SCOUT, 0.66).straight(),
                         at(SCOUT, 0.88).straight()),
                    wave(at(FIGHTER, 0.06, -40).drifting(), at(FIGHTER, 0.86, -40).drifting(),
                         at(SCOUT, 0.24, -220).weaving(), at(SCOUT, 0.70, -220).weaving(),
                         at(FIGHTER, 0.46, -400)),
                    wave(at(CRUISER, 0.16).health(1.2), at(CRUISER, 0.74).health(1.2),
                         at(SCOUT, 0.46, -180).diving(), at(SCOUT, 0.30, -340).weaving(),
                         at(SCOUT, 0.62, -340).weaving()),
                    wave(at(FIGHTER, 0.12).shots(2).fireGap(1.5).straight(),
                         at(FIGHTER, 0.38).shots(2).fireGap(1.5).straight(),
                         at(FIGHTER, 0.62).shots(2).fireGap(1.5).straight(),
                         at(FIGHTER, 0.86).shots(2).fireGap(1.5).straight(),
                         at(CRUISER, 0.46, -300).size(1.2).health(1.35).holding()));

            /*
             * Inside a star. Five waves, everything fast and bright, and the first wave in the game
             * built entirely out of things that do not track you -- a wall to be flown through.
             */
            case STAR_CORE -> List.of(
                    wave(at(SCOUT, 0.10).speed(1.3).straight(), at(SCOUT, 0.28).speed(1.3).straight(),
                         at(SCOUT, 0.46).speed(1.3).straight(), at(SCOUT, 0.64).speed(1.3).straight(),
                         at(SCOUT, 0.82).speed(1.3).straight()),
                    wave(at(SCOUT, 0.16, -60).weaving(), at(SCOUT, 0.38, -60).weaving(),
                         at(SCOUT, 0.60, -60).weaving(), at(SCOUT, 0.82, -60).weaving(),
                         at(FIGHTER, 0.48, -280).holding()),
                    wave(at(FIGHTER, 0.20).diving(), at(FIGHTER, 0.48).diving(),
                         at(FIGHTER, 0.74).diving(),
                         at(SCOUT, 0.34, -260).straight(), at(SCOUT, 0.62, -260).straight()),
                    wave(at(CRUISER, 0.30, -40).size(1.15).health(1.25).fireGap(0.85),
                         at(CRUISER, 0.62, -40).size(1.15).health(1.25).fireGap(0.85),
                         at(SCOUT, 0.14, -240).weaving(), at(SCOUT, 0.84, -240).weaving(),
                         at(SCOUT, 0.48, -400).diving()),
                    wave(at(SCOUT, 0.06).straight(), at(SCOUT, 0.24).straight(),
                         at(FIGHTER, 0.44).shots(2).fireGap(1.4).straight(),
                         at(SCOUT, 0.66).straight(), at(SCOUT, 0.86).straight(),
                         at(CRUISER, 0.46, -360).size(1.3).health(1.45).speed(0.75).holding()));

            /*
             * Running for the exit, so everything here is chasing: the galaxy's densest use of DIVE,
             * and the first level where two heavies hold station at once.
             */
            case ESCAPE_VECTOR -> List.of(
                    wave(at(SCOUT, 0.20, -40).diving(), at(SCOUT, 0.42, -160).diving(),
                         at(SCOUT, 0.64, -280).diving(), at(SCOUT, 0.84, -400).diving(),
                         at(SCOUT, 0.08, -520).diving()),
                    wave(at(FIGHTER, 0.26).diving(), at(FIGHTER, 0.68).diving(),
                         at(SCOUT, 0.12, -200).weaving(), at(SCOUT, 0.86, -200).weaving(),
                         at(SCOUT, 0.48, -360).weaving()),
                    wave(at(CRUISER, 0.34).holding(), at(CRUISER, 0.60).holding(),
                         at(SCOUT, 0.14, -220).diving(), at(SCOUT, 0.82, -220).diving(),
                         at(FIGHTER, 0.46, -400).diving()),
                    wave(at(FIGHTER, 0.10).straight(), at(FIGHTER, 0.32).straight(),
                         at(FIGHTER, 0.60).straight(), at(FIGHTER, 0.84).straight(),
                         at(SCOUT, 0.46, -280).diving(), at(SCOUT, 0.22, -440).diving()),
                    wave(at(CRUISER, 0.44, -40).size(1.35).health(1.55).speed(0.8).fireGap(0.8),
                         at(FIGHTER, 0.16, -220).diving(), at(FIGHTER, 0.76, -220).diving(),
                         at(SCOUT, 0.30, -400).weaving(), at(SCOUT, 0.62, -400).weaving(),
                         at(SCOUT, 0.46, -560).diving()));

            /*
             * Side-on, and a tunnel: the only level in the galaxy with both constraints at once.
             * Across runs top to bottom here and setback pushes a ship further out to the right, so
             * a "column" is horizontal. Everything still sits inside the same 0.33-0.67 band, which
             * is the point of authoring in fractions -- these rows would read identically top-down.
             */
            case DUST_REACH -> List.of(
                    wave(at(SCOUT, 0.36).straight(), at(SCOUT, 0.52).straight(),
                         at(SCOUT, 0.44, -200).straight(), at(SCOUT, 0.36, -400).straight(),
                         at(SCOUT, 0.54, -400).straight()),
                    wave(at(FIGHTER, 0.38).holding(), at(FIGHTER, 0.56).holding(),
                         at(SCOUT, 0.46, -180).speed(1.25).straight(),
                         at(SCOUT, 0.46, -340).speed(1.25).straight()),
                    wave(at(CRUISER, 0.40).size(1.15).health(1.3).speed(0.85),
                         at(SCOUT, 0.35, -200).weaving(), at(SCOUT, 0.55, -200).weaving(),
                         at(FIGHTER, 0.45, -380).straight(), at(SCOUT, 0.45, -540).straight()),
                    wave(at(SCOUT, 0.34).size(0.85).health(0.8).weaving(),
                         at(SCOUT, 0.44).size(0.85).health(0.8).weaving(),
                         at(SCOUT, 0.56).size(0.85).health(0.8).weaving(),
                         at(FIGHTER, 0.40, -240).straight(), at(FIGHTER, 0.54, -240).straight(),
                         at(CRUISER, 0.44, -460).health(1.3).speed(0.8)),
                    wave(at(CRUISER, 0.38, -60).size(1.3).health(1.5).speed(0.7).fireGap(0.8),
                         at(CRUISER, 0.54, -60).size(1.3).health(1.5).speed(0.7).fireGap(0.8),
                         at(SCOUT, 0.46, -300).diving(), at(SCOUT, 0.36, -460).straight(),
                         at(SCOUT, 0.56, -460).straight()));

            /*
             * Where the thing with three heads lives, so the waves before it are the animals that
             * live with it: nothing flies in formation here, everything weaves, and the last wave
             * is a nest rather than a patrol.
             */
            case HOLLOW_WOMB -> List.of(
                    wave(at(SCOUT, 0.36).weaving(), at(SCOUT, 0.48).weaving(),
                         at(SCOUT, 0.60).weaving(), at(SCOUT, 0.44, -220).weaving(),
                         at(SCOUT, 0.54, -400).weaving()),
                    wave(at(FIGHTER, 0.38).weaving(), at(FIGHTER, 0.56).weaving(),
                         at(SCOUT, 0.46, -200).diving(), at(SCOUT, 0.36, -380).weaving(),
                         at(SCOUT, 0.58, -380).weaving()),
                    wave(at(CRUISER, 0.44).size(1.2).health(1.35).speed(0.8).holding(),
                         at(SCOUT, 0.35, -200).weaving(), at(SCOUT, 0.57, -200).weaving(),
                         at(SCOUT, 0.46, -380).weaving(), at(FIGHTER, 0.40, -540).weaving()),
                    wave(at(SCOUT, 0.35).size(0.8).health(0.75).weaving(),
                         at(SCOUT, 0.42).size(0.8).health(0.75).weaving(),
                         at(SCOUT, 0.50).size(0.8).health(0.75).weaving(),
                         at(SCOUT, 0.58).size(0.8).health(0.75).weaving(),
                         at(FIGHTER, 0.46, -260).holding(), at(FIGHTER, 0.38, -420).weaving()),
                    wave(at(CRUISER, 0.36, -40).size(1.3).health(1.5).speed(0.75).fireGap(0.8),
                         at(CRUISER, 0.54, -40).size(1.3).health(1.5).speed(0.75).fireGap(0.8),
                         at(SCOUT, 0.46, -280).weaving(), at(SCOUT, 0.36, -460).weaving(),
                         at(SCOUT, 0.56, -460).weaving(), at(FIGHTER, 0.46, -620).diving()));

            // ---- Galaxy 2: Ashfall (levels 11-20) --------------------------------------------
            //
            // The galaxy with a ceiling, so its waves crowd. Where Verdance spread across the lane,
            // Ashfall stacks down it: more ships per wave, more of them holding station, and the
            // first heavies that fire in volleys rather than singly. Four of its ten levels are
            // underground or hugging the ground, and those are the tightest waves in the game.

            case CINDER_BELT -> List.of(
                    wave(at(SCOUT, 0.12).straight(), at(SCOUT, 0.30).straight(),
                         at(SCOUT, 0.50).straight(), at(SCOUT, 0.70).straight(),
                         at(SCOUT, 0.88).straight(), at(FIGHTER, 0.50, -280).holding()),
                    wave(at(FIGHTER, 0.20, -40), at(FIGHTER, 0.50, -40), at(FIGHTER, 0.78, -40),
                         at(SCOUT, 0.34, -240).weaving(), at(SCOUT, 0.64, -240).weaving(),
                         at(SCOUT, 0.48, -420).diving()),
                    wave(at(CRUISER, 0.28).health(1.2).holding(), at(CRUISER, 0.66).health(1.2).holding(),
                         at(SCOUT, 0.14, -200).diving(), at(SCOUT, 0.84, -200).diving(),
                         at(FIGHTER, 0.48, -380).straight()),
                    wave(at(SCOUT, 0.08).size(0.85).health(0.85).weaving(),
                         at(SCOUT, 0.26).size(0.85).health(0.85).weaving(),
                         at(SCOUT, 0.46).size(0.85).health(0.85).weaving(),
                         at(SCOUT, 0.66).size(0.85).health(0.85).weaving(),
                         at(SCOUT, 0.86).size(0.85).health(0.85).weaving(),
                         at(CRUISER, 0.46, -360).size(1.25).health(1.4).shots(2).fireGap(1.4)));

            case ASHFALL_SKY -> List.of(
                    wave(at(SCOUT, 0.16).drifting(), at(SCOUT, 0.38).drifting(),
                         at(SCOUT, 0.60).drifting(), at(SCOUT, 0.82).drifting(),
                         at(FIGHTER, 0.48, -300).straight()),
                    wave(at(FIGHTER, 0.14).straight(), at(FIGHTER, 0.40).straight(),
                         at(FIGHTER, 0.62).straight(), at(FIGHTER, 0.86).straight(),
                         at(SCOUT, 0.30, -260).diving(), at(SCOUT, 0.70, -260).diving()),
                    wave(at(CRUISER, 0.44).size(1.25).health(1.4).speed(0.8).shots(2).fireGap(1.5),
                         at(SCOUT, 0.12, -180).weaving(), at(SCOUT, 0.30, -180).weaving(),
                         at(SCOUT, 0.64, -180).weaving(), at(SCOUT, 0.84, -180).weaving()),
                    wave(at(FIGHTER, 0.24).holding(), at(FIGHTER, 0.72).holding(),
                         at(SCOUT, 0.46, -160).diving(), at(SCOUT, 0.14, -340).drifting(),
                         at(SCOUT, 0.84, -340).drifting(), at(CRUISER, 0.48, -520).health(1.3)));

            case SLAGFIELDS -> List.of(
                    wave(at(SCOUT, 0.10, -40).straight(), at(SCOUT, 0.28, -160).straight(),
                         at(SCOUT, 0.48, -280).straight(), at(SCOUT, 0.68, -400).straight(),
                         at(SCOUT, 0.86, -520).straight()),
                    wave(at(CRUISER, 0.20).speed(0.8).holding(), at(CRUISER, 0.72).speed(0.8).holding(),
                         at(FIGHTER, 0.46, -200).straight(),
                         at(SCOUT, 0.34, -380).weaving(), at(SCOUT, 0.60, -380).weaving()),
                    wave(at(FIGHTER, 0.16).shots(2).fireGap(1.5).straight(),
                         at(FIGHTER, 0.38).shots(2).fireGap(1.5).straight(),
                         at(FIGHTER, 0.62).shots(2).fireGap(1.5).straight(),
                         at(FIGHTER, 0.84).shots(2).fireGap(1.5).straight(),
                         at(SCOUT, 0.48, -300).diving()),
                    // A slag hauler and its whole crew. The heaviest single hull in the galaxy so far.
                    wave(at(CRUISER, 0.44, -60).size(1.4).health(1.6).speed(0.7).fireGap(0.8),
                         at(SCOUT, 0.10, -220).weaving(), at(SCOUT, 0.28, -220).weaving(),
                         at(SCOUT, 0.66, -220).weaving(), at(SCOUT, 0.86, -220).weaving(),
                         at(FIGHTER, 0.46, -420).holding()));

            /*
             * Vents: a tunnel, so back inside the band. Ashfall's tunnels are tighter than
             * Verdance's because they arrive four waves deep rather than three, and every one of
             * them puts something in the lane that has to be removed rather than dodged.
             */
            case MAGMA_VENTS -> List.of(
                    wave(at(SCOUT, 0.38).straight(), at(SCOUT, 0.54).straight(),
                         at(SCOUT, 0.46, -180).straight(), at(SCOUT, 0.38, -360).straight(),
                         at(SCOUT, 0.54, -360).straight()),
                    wave(at(FIGHTER, 0.37).holding(), at(FIGHTER, 0.55).holding(),
                         at(SCOUT, 0.46, -200).speed(1.3).straight(),
                         at(SCOUT, 0.46, -360).speed(1.3).straight(),
                         at(SCOUT, 0.46, -520).speed(1.3).straight()),
                    wave(at(CRUISER, 0.36).size(0.9).health(0.9), at(CRUISER, 0.54).size(0.9).health(0.9),
                         at(FIGHTER, 0.45, -240).straight(), at(SCOUT, 0.37, -420).weaving(),
                         at(SCOUT, 0.55, -420).weaving()),
                    wave(at(CRUISER, 0.44, -40).size(1.35).health(1.65).speed(0.7).shots(2).fireGap(1.3),
                         at(FIGHTER, 0.36, -260).holding(), at(FIGHTER, 0.56, -260).holding(),
                         at(SCOUT, 0.40, -440).straight(), at(SCOUT, 0.52, -440).straight(),
                         at(SCOUT, 0.46, -600).diving()));

            /*
             * The Forgeworks. Five waves in a tunnel, which is the tightest budget in the galaxy --
             * so this is where the lane genuinely closes: two heavies abreast leave a gap you have
             * to fly rather than a gap you drift through.
             */
            case THE_FORGEWORKS -> List.of(
                    wave(at(SCOUT, 0.36).straight(), at(SCOUT, 0.46).straight(),
                         at(SCOUT, 0.56).straight(), at(SCOUT, 0.41, -200).straight(),
                         at(SCOUT, 0.51, -200).straight(), at(SCOUT, 0.46, -400).straight()),
                    wave(at(FIGHTER, 0.36, -40).holding(), at(FIGHTER, 0.56, -40).holding(),
                         at(SCOUT, 0.46, -220).diving(), at(SCOUT, 0.38, -400).weaving(),
                         at(SCOUT, 0.54, -400).weaving()),
                    wave(at(CRUISER, 0.35).size(1.1).health(1.25).speed(0.8),
                         at(CRUISER, 0.55).size(1.1).health(1.25).speed(0.8),
                         at(SCOUT, 0.45, -260).straight(), at(SCOUT, 0.45, -420).straight()),
                    wave(at(FIGHTER, 0.37).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.47).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.57).shots(2).fireGap(1.4).holding(),
                         at(SCOUT, 0.40, -280).diving(), at(SCOUT, 0.54, -280).diving()),
                    wave(at(CRUISER, 0.44, -60).size(1.4).health(1.7).speed(0.65).fireGap(0.75),
                         at(FIGHTER, 0.36, -280).holding(), at(FIGHTER, 0.56, -280).holding(),
                         at(SCOUT, 0.40, -460).weaving(), at(SCOUT, 0.52, -460).weaving(),
                         at(SCOUT, 0.46, -620).diving()));

            case PYROCLAST -> List.of(
                    wave(at(SCOUT, 0.08).weaving(), at(SCOUT, 0.26).weaving(),
                         at(SCOUT, 0.46).weaving(), at(SCOUT, 0.66).weaving(),
                         at(SCOUT, 0.86).weaving(), at(SCOUT, 0.46, -280).weaving()),
                    wave(at(FIGHTER, 0.18).drifting(), at(FIGHTER, 0.44).drifting(),
                         at(FIGHTER, 0.70).drifting(),
                         at(SCOUT, 0.30, -260).diving(), at(SCOUT, 0.62, -260).diving()),
                    wave(at(CRUISER, 0.24).health(1.3).holding(), at(CRUISER, 0.68).health(1.3).holding(),
                         at(SCOUT, 0.46, -180).diving(), at(SCOUT, 0.12, -360).weaving(),
                         at(SCOUT, 0.84, -360).weaving()),
                    wave(at(FIGHTER, 0.10).straight(), at(FIGHTER, 0.32).straight(),
                         at(FIGHTER, 0.60).straight(), at(FIGHTER, 0.86).straight(),
                         at(CRUISER, 0.46, -320).size(1.25).health(1.4).shots(2).fireGap(1.4)),
                    wave(at(CRUISER, 0.30, -40).size(1.3).health(1.5).speed(0.75).fireGap(0.8),
                         at(CRUISER, 0.62, -40).size(1.3).health(1.5).speed(0.75).fireGap(0.8),
                         at(SCOUT, 0.12, -260).diving(), at(SCOUT, 0.86, -260).diving(),
                         at(SCOUT, 0.46, -420).weaving(), at(FIGHTER, 0.46, -600).holding()));

            /*
             * Side-on and open, so this is the galaxy's one level with room to breathe -- and the
             * waves use it: full-breadth ladders, and the only place in Ashfall where a wave arrives
             * as a rank rather than as a column.
             */
            case SUNWARD_DIVE -> List.of(
                    wave(at(SCOUT, 0.10).straight(), at(SCOUT, 0.28).straight(),
                         at(SCOUT, 0.46).straight(), at(SCOUT, 0.64).straight(),
                         at(SCOUT, 0.82).straight()),
                    wave(at(FIGHTER, 0.14).drifting(), at(FIGHTER, 0.46).drifting(),
                         at(FIGHTER, 0.78).drifting(),
                         at(SCOUT, 0.30, -240).diving(), at(SCOUT, 0.62, -240).diving()),
                    wave(at(CRUISER, 0.20).health(1.25).speed(0.85),
                         at(CRUISER, 0.70).health(1.25).speed(0.85),
                         at(SCOUT, 0.46, -200).weaving(), at(SCOUT, 0.12, -380).weaving(),
                         at(SCOUT, 0.80, -380).weaving()),
                    wave(at(SCOUT, 0.06).speed(1.3).straight(), at(SCOUT, 0.24).speed(1.3).straight(),
                         at(SCOUT, 0.44).speed(1.3).straight(), at(SCOUT, 0.64).speed(1.3).straight(),
                         at(SCOUT, 0.84).speed(1.3).straight(),
                         at(FIGHTER, 0.46, -320).holding()),
                    wave(at(CRUISER, 0.44, -40).size(1.35).health(1.55).shots(2).fireGap(1.3),
                         at(FIGHTER, 0.12, -240).straight(), at(FIGHTER, 0.78, -240).straight(),
                         at(SCOUT, 0.30, -420).diving(), at(SCOUT, 0.62, -420).diving(),
                         at(SCOUT, 0.46, -580).weaving()));

            case CORONAL_ARC -> List.of(
                    wave(at(SCOUT, 0.14).drifting(), at(SCOUT, 0.34).drifting(),
                         at(SCOUT, 0.56).drifting(), at(SCOUT, 0.78).drifting(),
                         at(FIGHTER, 0.46, -280).straight()),
                    wave(at(FIGHTER, 0.20).holding(), at(FIGHTER, 0.48).holding(),
                         at(FIGHTER, 0.76).holding(),
                         at(SCOUT, 0.32, -240).diving(), at(SCOUT, 0.64, -240).diving()),
                    wave(at(CRUISER, 0.44).size(1.3).health(1.45).speed(0.75).shots(2).fireGap(1.4),
                         at(SCOUT, 0.10, -200).weaving(), at(SCOUT, 0.28, -200).weaving(),
                         at(SCOUT, 0.66, -200).weaving(), at(SCOUT, 0.86, -200).weaving()),
                    wave(at(SCOUT, 0.08).diving(), at(SCOUT, 0.26).diving(), at(SCOUT, 0.46).diving(),
                         at(SCOUT, 0.68).diving(), at(SCOUT, 0.88).diving(),
                         at(CRUISER, 0.46, -340).health(1.35).holding()),
                    wave(at(CRUISER, 0.26, -40).size(1.25).health(1.45).speed(0.8),
                         at(CRUISER, 0.66, -40).size(1.25).health(1.45).speed(0.8),
                         at(FIGHTER, 0.46, -240).shots(2).fireGap(1.4).holding(),
                         at(SCOUT, 0.14, -420).weaving(), at(SCOUT, 0.80, -420).weaving()));

            case EMBER_CANYON -> List.of(
                    wave(at(SCOUT, 0.18).straight(), at(SCOUT, 0.34).straight(),
                         at(SCOUT, 0.52).straight(), at(SCOUT, 0.70).straight(),
                         at(SCOUT, 0.86).straight(), at(SCOUT, 0.46, -260).diving()),
                    wave(at(FIGHTER, 0.22, -40).straight(), at(FIGHTER, 0.48, -40).straight(),
                         at(FIGHTER, 0.74, -40).straight(),
                         at(SCOUT, 0.34, -240).weaving(), at(SCOUT, 0.62, -240).weaving(),
                         at(SCOUT, 0.46, -420).weaving()),
                    wave(at(CRUISER, 0.30).size(1.15).health(1.3).holding(),
                         at(CRUISER, 0.62).size(1.15).health(1.3).holding(),
                         at(SCOUT, 0.16, -220).diving(), at(SCOUT, 0.80, -220).diving(),
                         at(FIGHTER, 0.46, -400).straight()),
                    wave(at(FIGHTER, 0.12).shots(2).fireGap(1.5).drifting(),
                         at(FIGHTER, 0.36).shots(2).fireGap(1.5).drifting(),
                         at(FIGHTER, 0.62).shots(2).fireGap(1.5).drifting(),
                         at(FIGHTER, 0.86).shots(2).fireGap(1.5).drifting(),
                         at(SCOUT, 0.46, -300).diving(), at(SCOUT, 0.24, -460).diving()),
                    wave(at(CRUISER, 0.44, -60).size(1.4).health(1.6).speed(0.7).fireGap(0.75),
                         at(FIGHTER, 0.18, -260).holding(), at(FIGHTER, 0.74, -260).holding(),
                         at(SCOUT, 0.32, -440).weaving(), at(SCOUT, 0.60, -440).weaving(),
                         at(SCOUT, 0.46, -600).diving()));

            /*
             * Caldera Heart: six waves, in a tunnel, before Vaunt. The longest run of waves in the
             * galaxy and the tightest lane, which is the combination the whole galaxy has been
             * building toward. The last two are as heavy as anything before the fifth galaxy.
             */
            case CALDERA_HEART -> List.of(
                    wave(at(SCOUT, 0.37).straight(), at(SCOUT, 0.47).straight(),
                         at(SCOUT, 0.57).straight(), at(SCOUT, 0.42, -200).straight(),
                         at(SCOUT, 0.52, -200).straight()),
                    wave(at(FIGHTER, 0.37).holding(), at(FIGHTER, 0.55).holding(),
                         at(SCOUT, 0.46, -220).diving(), at(SCOUT, 0.38, -400).weaving(),
                         at(SCOUT, 0.54, -400).weaving()),
                    wave(at(CRUISER, 0.36).size(1.05).health(1.2), at(CRUISER, 0.55).size(1.05).health(1.2),
                         at(SCOUT, 0.45, -240).straight(), at(SCOUT, 0.45, -400).straight(),
                         at(FIGHTER, 0.45, -560).holding()),
                    wave(at(SCOUT, 0.35).size(0.85).health(0.85).weaving(),
                         at(SCOUT, 0.43).size(0.85).health(0.85).weaving(),
                         at(SCOUT, 0.51).size(0.85).health(0.85).weaving(),
                         at(SCOUT, 0.59).size(0.85).health(0.85).weaving(),
                         at(FIGHTER, 0.46, -280).shots(2).fireGap(1.4).holding()),
                    wave(at(CRUISER, 0.35, -40).size(1.25).health(1.45).speed(0.75),
                         at(CRUISER, 0.55, -40).size(1.25).health(1.45).speed(0.75),
                         at(FIGHTER, 0.45, -280).holding(), at(SCOUT, 0.39, -460).diving(),
                         at(SCOUT, 0.53, -460).diving()),
                    // Vaunt's honour guard, and the hardest wave in the galaxy.
                    wave(at(CRUISER, 0.44, -60).size(1.45).health(1.6).speed(0.65).shots(2).fireGap(1.2),
                         at(FIGHTER, 0.36, -280).shots(2).fireGap(1.5).holding(),
                         at(FIGHTER, 0.56, -280).shots(2).fireGap(1.5).holding(),
                         at(SCOUT, 0.40, -460).diving(), at(SCOUT, 0.52, -460).diving(),
                         at(SCOUT, 0.46, -620).weaving()));

            // ---- Galaxy 3: Cryonis (levels 21-30) --------------------------------------------
            //
            // The galaxy that alternates, and its waves alternate with it. Its clock runs
            // 3,5,3,5,3,5,4,6,4,6, and the wave design leans into that rather than fighting it: the
            // three-wave levels field few, heavy, slow encounters and the five- and six-wave levels
            // field many light fast ones. A short level here should feel like three real fights,
            // not like a level that ended early.
            //
            // The other identity is drift. Ice does not chase; more of this galaxy weaves and
            // drifts than any other, and the heavies hold rather than close.

            /*
             * Side-on, and only three waves, so each one is an event. Every ship in the level is
             * either heavy or holding -- there is nothing here to swat.
             */
            case FROST_RING -> List.of(
                    wave(at(SCOUT, 0.12).drifting(), at(SCOUT, 0.30).drifting(),
                         at(SCOUT, 0.48).drifting(), at(SCOUT, 0.66).drifting(),
                         at(SCOUT, 0.84).drifting(), at(SCOUT, 0.48, -280).speed(1.3).straight()),
                    wave(at(FIGHTER, 0.14).holding(), at(FIGHTER, 0.80).holding(),
                         at(CRUISER, 0.44, -200).health(1.3).speed(0.8),
                         at(SCOUT, 0.06, -360).weaving(), at(SCOUT, 0.86, -360).weaving()),
                    wave(at(CRUISER, 0.20, -40).size(1.3).health(1.5).speed(0.75).shots(2).fireGap(1.3),
                         at(CRUISER, 0.66, -40).size(1.3).health(1.5).speed(0.75).shots(2).fireGap(1.3),
                         at(FIGHTER, 0.44, -260).holding(),
                         at(SCOUT, 0.14, -440).diving(), at(SCOUT, 0.74, -440).diving()));

            /* Five waves, so these are the light ones: fast, thin, and there are a lot of them. */
            case RIME_SKY -> List.of(
                    wave(at(SCOUT, 0.10).size(0.85).health(0.85).drifting(),
                         at(SCOUT, 0.30).size(0.85).health(0.85).drifting(),
                         at(SCOUT, 0.50).size(0.85).health(0.85).drifting(),
                         at(SCOUT, 0.70).size(0.85).health(0.85).drifting(),
                         at(SCOUT, 0.88).size(0.85).health(0.85).drifting()),
                    wave(at(SCOUT, 0.16).weaving(), at(SCOUT, 0.38).weaving(),
                         at(SCOUT, 0.60).weaving(), at(SCOUT, 0.82).weaving(),
                         at(FIGHTER, 0.48, -280).straight()),
                    wave(at(FIGHTER, 0.20).drifting(), at(FIGHTER, 0.48).drifting(),
                         at(FIGHTER, 0.76).drifting(),
                         at(SCOUT, 0.34, -240).diving(), at(SCOUT, 0.62, -240).diving()),
                    wave(at(SCOUT, 0.08).speed(1.3).straight(), at(SCOUT, 0.28).speed(1.3).straight(),
                         at(SCOUT, 0.48).speed(1.3).straight(), at(SCOUT, 0.68).speed(1.3).straight(),
                         at(SCOUT, 0.88).speed(1.3).straight(),
                         at(CRUISER, 0.46, -320).health(1.25).holding()),
                    wave(at(CRUISER, 0.30).size(1.2).health(1.35).speed(0.8).holding(),
                         at(CRUISER, 0.62).size(1.2).health(1.35).speed(0.8).holding(),
                         at(SCOUT, 0.14, -240).weaving(), at(SCOUT, 0.84, -240).weaving(),
                         at(FIGHTER, 0.46, -420).diving()));

            /* Three waves on a shelf of ice: heavy, slow, and every one of them parks in your lane. */
            case GLACIER_SHELF -> List.of(
                    wave(at(CRUISER, 0.22).health(1.25).speed(0.8).holding(),
                         at(CRUISER, 0.68).health(1.25).speed(0.8).holding(),
                         at(SCOUT, 0.46, -220).weaving(), at(SCOUT, 0.12, -400).drifting(),
                         at(SCOUT, 0.82, -400).drifting()),
                    wave(at(FIGHTER, 0.16).shots(2).fireGap(1.5).holding(),
                         at(FIGHTER, 0.40).shots(2).fireGap(1.5).holding(),
                         at(FIGHTER, 0.64).shots(2).fireGap(1.5).holding(),
                         at(FIGHTER, 0.86).shots(2).fireGap(1.5).holding(),
                         at(SCOUT, 0.46, -320).diving(), at(SCOUT, 0.26, -480).diving()),
                    wave(at(CRUISER, 0.44, -40).size(1.45).health(1.6).speed(0.65).fireGap(0.8),
                         at(CRUISER, 0.18, -240).size(1.15).health(1.3).holding(),
                         at(CRUISER, 0.72, -240).size(1.15).health(1.3).holding(),
                         at(SCOUT, 0.34, -440).diving(), at(SCOUT, 0.60, -440).diving()));

            case UNDER_ICE -> List.of(
                    wave(at(SCOUT, 0.14).weaving(), at(SCOUT, 0.34).weaving(),
                         at(SCOUT, 0.56).weaving(), at(SCOUT, 0.78).weaving(),
                         at(SCOUT, 0.46, -260).weaving()),
                    wave(at(FIGHTER, 0.18, -40).straight(), at(FIGHTER, 0.46, -40).straight(),
                         at(FIGHTER, 0.76, -40).straight(),
                         at(SCOUT, 0.32, -240).drifting(), at(SCOUT, 0.64, -240).drifting()),
                    wave(at(SCOUT, 0.08).diving(), at(SCOUT, 0.26).diving(), at(SCOUT, 0.46).diving(),
                         at(SCOUT, 0.66).diving(), at(SCOUT, 0.88).diving(),
                         at(FIGHTER, 0.46, -300).holding()),
                    wave(at(CRUISER, 0.26).size(1.15).health(1.3).holding(),
                         at(CRUISER, 0.66).size(1.15).health(1.3).holding(),
                         at(SCOUT, 0.46, -200).weaving(), at(SCOUT, 0.14, -380).weaving(),
                         at(SCOUT, 0.84, -380).weaving()),
                    wave(at(CRUISER, 0.44, -40).size(1.35).health(1.5).speed(0.75).shots(2).fireGap(1.3),
                         at(FIGHTER, 0.16, -240).drifting(), at(FIGHTER, 0.76, -240).drifting(),
                         at(SCOUT, 0.32, -420).diving(), at(SCOUT, 0.62, -420).diving(),
                         at(SCOUT, 0.46, -580).weaving()));

            /* A crevasse: three waves, a tunnel, and nowhere at all to go. The tightest level yet. */
            case CREVASSE -> List.of(
                    wave(at(FIGHTER, 0.37).holding(), at(FIGHTER, 0.55).holding(),
                         at(SCOUT, 0.46, -220).straight(), at(SCOUT, 0.38, -400).straight(),
                         at(SCOUT, 0.54, -400).straight(), at(SCOUT, 0.46, -560).diving()),
                    wave(at(CRUISER, 0.36).size(1.1).health(1.3).speed(0.8),
                         at(CRUISER, 0.55).size(1.1).health(1.3).speed(0.8),
                         at(SCOUT, 0.45, -260).weaving(), at(SCOUT, 0.38, -440).weaving(),
                         at(SCOUT, 0.53, -440).weaving()),
                    wave(at(CRUISER, 0.44, -60).size(1.4).health(1.7).speed(0.65).fireGap(0.75),
                         at(FIGHTER, 0.36, -280).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.56, -280).shots(2).fireGap(1.4).holding(),
                         at(SCOUT, 0.40, -460).diving(), at(SCOUT, 0.52, -460).diving(),
                         at(SCOUT, 0.46, -620).weaving()));

            case BLACK_TRENCH -> List.of(
                    wave(at(SCOUT, 0.38).weaving(), at(SCOUT, 0.54).weaving(),
                         at(SCOUT, 0.46, -200).weaving(), at(SCOUT, 0.38, -380).weaving(),
                         at(SCOUT, 0.55, -380).weaving()),
                    wave(at(SCOUT, 0.36).size(0.8).health(0.8).straight(),
                         at(SCOUT, 0.44).size(0.8).health(0.8).straight(),
                         at(SCOUT, 0.52).size(0.8).health(0.8).straight(),
                         at(SCOUT, 0.60).size(0.8).health(0.8).straight(),
                         at(FIGHTER, 0.46, -280).holding()),
                    wave(at(FIGHTER, 0.37).drifting(), at(FIGHTER, 0.56).drifting(),
                         at(SCOUT, 0.46, -220).diving(), at(SCOUT, 0.38, -400).weaving(),
                         at(SCOUT, 0.54, -400).weaving()),
                    wave(at(CRUISER, 0.36).size(1.15).health(1.3).speed(0.8).holding(),
                         at(CRUISER, 0.55).size(1.15).health(1.3).speed(0.8).holding(),
                         at(SCOUT, 0.45, -260).diving(), at(SCOUT, 0.45, -420).weaving()),
                    wave(at(CRUISER, 0.44, -40).size(1.4).health(1.65).speed(0.7).fireGap(0.8),
                         at(FIGHTER, 0.36, -260).holding(), at(FIGHTER, 0.56, -260).holding(),
                         at(SCOUT, 0.40, -440).weaving(), at(SCOUT, 0.52, -440).weaving(),
                         at(SCOUT, 0.46, -600).diving()));

            case GEYSER_FLATS -> List.of(
                    wave(at(SCOUT, 0.10).diving(), at(SCOUT, 0.30).diving(),
                         at(SCOUT, 0.50).diving(), at(SCOUT, 0.70).diving(),
                         at(SCOUT, 0.90).diving()),
                    wave(at(FIGHTER, 0.20).holding(), at(FIGHTER, 0.50).holding(),
                         at(FIGHTER, 0.78).holding(),
                         at(SCOUT, 0.34, -260).weaving(), at(SCOUT, 0.64, -260).weaving()),
                    wave(at(CRUISER, 0.28).size(1.2).health(1.35).speed(0.8),
                         at(CRUISER, 0.64).size(1.2).health(1.35).speed(0.8),
                         at(SCOUT, 0.46, -220).diving(), at(SCOUT, 0.12, -400).drifting(),
                         at(SCOUT, 0.84, -400).drifting()),
                    wave(at(CRUISER, 0.44, -40).size(1.4).health(1.6).speed(0.7).shots(2).fireGap(1.3),
                         at(FIGHTER, 0.14, -240).shots(2).fireGap(1.5).holding(),
                         at(FIGHTER, 0.78, -240).shots(2).fireGap(1.5).holding(),
                         at(SCOUT, 0.32, -420).diving(), at(SCOUT, 0.62, -420).diving(),
                         at(SCOUT, 0.46, -580).weaving()));

            /* Six waves against a wall of hail: the galaxy's long level, and its lightest ships. */
            case HAILWALL -> List.of(
                    wave(at(SCOUT, 0.06).size(0.8).health(0.8).drifting(),
                         at(SCOUT, 0.24).size(0.8).health(0.8).drifting(),
                         at(SCOUT, 0.44).size(0.8).health(0.8).drifting(),
                         at(SCOUT, 0.64).size(0.8).health(0.8).drifting(),
                         at(SCOUT, 0.84).size(0.8).health(0.8).drifting()),
                    wave(at(SCOUT, 0.12).weaving(), at(SCOUT, 0.32).weaving(),
                         at(SCOUT, 0.52).weaving(), at(SCOUT, 0.72).weaving(),
                         at(SCOUT, 0.90).weaving(), at(SCOUT, 0.46, -280).weaving()),
                    wave(at(FIGHTER, 0.16).straight(), at(FIGHTER, 0.40).straight(),
                         at(FIGHTER, 0.64).straight(), at(FIGHTER, 0.86).straight(),
                         at(SCOUT, 0.46, -280).diving()),
                    wave(at(SCOUT, 0.08).speed(1.35).straight(), at(SCOUT, 0.28).speed(1.35).straight(),
                         at(SCOUT, 0.48).speed(1.35).straight(), at(SCOUT, 0.68).speed(1.35).straight(),
                         at(SCOUT, 0.88).speed(1.35).straight(),
                         at(FIGHTER, 0.46, -320).holding()),
                    wave(at(CRUISER, 0.24).size(1.15).health(1.3).holding(),
                         at(CRUISER, 0.68).size(1.15).health(1.3).holding(),
                         at(SCOUT, 0.46, -220).diving(), at(SCOUT, 0.14, -400).weaving(),
                         at(SCOUT, 0.82, -400).weaving(), at(FIGHTER, 0.46, -560).drifting()),
                    wave(at(CRUISER, 0.44, -40).size(1.4).health(1.6).speed(0.7).fireGap(0.8),
                         at(FIGHTER, 0.16, -260).holding(), at(FIGHTER, 0.76, -260).holding(),
                         at(SCOUT, 0.32, -440).diving(), at(SCOUT, 0.62, -440).diving(),
                         at(SCOUT, 0.46, -600).weaving()));

            /* Side-on again, and drifting wreckage: nothing in the first two waves tracks you. */
            case SHATTER_DRIFT -> List.of(
                    wave(at(SCOUT, 0.10).straight(), at(SCOUT, 0.28).straight(),
                         at(SCOUT, 0.46).straight(), at(SCOUT, 0.64).straight(),
                         at(SCOUT, 0.84).straight()),
                    wave(at(SCOUT, 0.14).drifting(), at(SCOUT, 0.36).drifting(),
                         at(SCOUT, 0.58).drifting(), at(SCOUT, 0.80).drifting(),
                         at(FIGHTER, 0.46, -280).drifting(), at(FIGHTER, 0.24, -440).drifting()),
                    wave(at(CRUISER, 0.18).size(1.2).health(1.35).speed(0.8).holding(),
                         at(CRUISER, 0.70).size(1.2).health(1.35).speed(0.8).holding(),
                         at(SCOUT, 0.44, -220).diving(), at(SCOUT, 0.12, -400).weaving(),
                         at(SCOUT, 0.78, -400).weaving()),
                    wave(at(CRUISER, 0.44, -40).size(1.4).health(1.6).speed(0.7).shots(2).fireGap(1.3),
                         at(FIGHTER, 0.14, -240).holding(), at(FIGHTER, 0.76, -240).holding(),
                         at(SCOUT, 0.30, -420).diving(), at(SCOUT, 0.62, -420).diving(),
                         at(SCOUT, 0.46, -580).weaving()));

            /*
             * Six waves in a tunnel before the Empress. Cryonis has spent ten levels alternating
             * between few-and-heavy and many-and-light; this level does both, in order.
             */
            case THE_FROZEN_HEART -> List.of(
                    wave(at(SCOUT, 0.36).size(0.8).health(0.8).weaving(),
                         at(SCOUT, 0.44).size(0.8).health(0.8).weaving(),
                         at(SCOUT, 0.52).size(0.8).health(0.8).weaving(),
                         at(SCOUT, 0.60).size(0.8).health(0.8).weaving()),
                    wave(at(SCOUT, 0.37).straight(), at(SCOUT, 0.47).straight(),
                         at(SCOUT, 0.57).straight(), at(SCOUT, 0.42, -200).straight(),
                         at(SCOUT, 0.52, -200).straight(), at(SCOUT, 0.47, -400).straight()),
                    wave(at(FIGHTER, 0.37).holding(), at(FIGHTER, 0.55).holding(),
                         at(SCOUT, 0.46, -220).diving(), at(SCOUT, 0.38, -400).weaving(),
                         at(SCOUT, 0.54, -400).weaving()),
                    wave(at(CRUISER, 0.36).size(1.1).health(1.3).speed(0.8),
                         at(CRUISER, 0.55).size(1.1).health(1.3).speed(0.8),
                         at(SCOUT, 0.45, -260).straight(), at(SCOUT, 0.45, -420).straight()),
                    wave(at(FIGHTER, 0.37).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.47).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.57).shots(2).fireGap(1.4).holding(),
                         at(SCOUT, 0.40, -280).diving(), at(SCOUT, 0.54, -280).diving()),
                    wave(at(CRUISER, 0.44, -60).size(1.45).health(1.7).speed(0.65).fireGap(0.75),
                         at(CRUISER, 0.36, -280).size(1.1).health(1.3).holding(),
                         at(CRUISER, 0.54, -280).size(1.1).health(1.3).holding(),
                         at(SCOUT, 0.40, -480).diving(), at(SCOUT, 0.52, -480).diving(),
                         at(SCOUT, 0.46, -640).weaving()));

            // ---- Galaxy 4: Tempest (levels 31-40) --------------------------------------------
            //
            // The galaxy with no floor, so its waves have no rank. Where Verdance spread across the
            // lane and Ashfall stacked down it, Tempest layers: deep setbacks, so a wave arrives in
            // three or four separate ranks and the front one is dead before the back one is on
            // screen. DIVE and DRIFT do most of the work -- downdraft and wind -- and only one of
            // the ten levels is a tunnel, so there is room for it.

            case CLOUDWALL -> List.of(
                    wave(at(SCOUT, 0.14, -40).drifting(), at(SCOUT, 0.36, -40).drifting(),
                         at(SCOUT, 0.60, -40).drifting(), at(SCOUT, 0.84, -40).drifting(),
                         at(SCOUT, 0.48, -320).drifting(), at(SCOUT, 0.24, -560).drifting()),
                    wave(at(FIGHTER, 0.20, -40).straight(), at(FIGHTER, 0.72, -40).straight(),
                         at(SCOUT, 0.46, -260).diving(), at(SCOUT, 0.14, -480).weaving(),
                         at(SCOUT, 0.82, -480).weaving()),
                    wave(at(CRUISER, 0.28).size(1.15).health(1.3).holding(),
                         at(CRUISER, 0.64).size(1.15).health(1.3).holding(),
                         at(FIGHTER, 0.46, -280).drifting(), at(SCOUT, 0.16, -520).diving(),
                         at(SCOUT, 0.80, -520).diving()),
                    wave(at(CRUISER, 0.44, -40).size(1.35).health(1.5).speed(0.75).shots(2).fireGap(1.3),
                         at(FIGHTER, 0.18, -280).holding(), at(FIGHTER, 0.74, -280).holding(),
                         at(SCOUT, 0.32, -500).diving(), at(SCOUT, 0.62, -500).diving(),
                         at(SCOUT, 0.46, -720).weaving()));

            case THUNDERHEAD -> List.of(
                    wave(at(SCOUT, 0.10).diving(), at(SCOUT, 0.30, -180).diving(),
                         at(SCOUT, 0.50, -360).diving(), at(SCOUT, 0.70, -540).diving(),
                         at(SCOUT, 0.90, -720).diving()),
                    wave(at(SCOUT, 0.16).size(0.85).health(0.85).weaving(),
                         at(SCOUT, 0.34).size(0.85).health(0.85).weaving(),
                         at(SCOUT, 0.58).size(0.85).health(0.85).weaving(),
                         at(SCOUT, 0.78).size(0.85).health(0.85).weaving(),
                         at(FIGHTER, 0.46, -320).shots(2).fireGap(1.5).holding()),
                    wave(at(FIGHTER, 0.18).drifting(), at(FIGHTER, 0.44).drifting(),
                         at(FIGHTER, 0.74).drifting(),
                         at(CRUISER, 0.46, -320).health(1.3).holding(),
                         at(SCOUT, 0.14, -540).diving(), at(SCOUT, 0.80, -540).diving()),
                    wave(at(CRUISER, 0.26, -40).size(1.3).health(1.45).speed(0.8),
                         at(CRUISER, 0.66, -40).size(1.3).health(1.45).speed(0.8),
                         at(SCOUT, 0.46, -300).diving(), at(SCOUT, 0.20, -520).weaving(),
                         at(SCOUT, 0.74, -520).weaving(), at(FIGHTER, 0.46, -740).holding()));

            /* The eye of it: calm in the middle, and everything comes from the edges. */
            case THE_EYE -> List.of(
                    wave(at(SCOUT, 0.04).straight(), at(SCOUT, 0.16).straight(),
                         at(SCOUT, 0.84).straight(), at(SCOUT, 0.94).straight(),
                         at(SCOUT, 0.08, -300).straight(), at(SCOUT, 0.90, -300).straight()),
                    wave(at(FIGHTER, 0.06).drifting(), at(FIGHTER, 0.88).drifting(),
                         at(SCOUT, 0.18, -260).diving(), at(SCOUT, 0.78, -260).diving(),
                         at(SCOUT, 0.46, -480).weaving()),
                    wave(at(CRUISER, 0.10).size(1.15).health(1.3).holding(),
                         at(CRUISER, 0.78).size(1.15).health(1.3).holding(),
                         at(FIGHTER, 0.46, -280).straight(), at(SCOUT, 0.28, -500).diving(),
                         at(SCOUT, 0.66, -500).diving()),
                    wave(at(SCOUT, 0.02).speed(1.3).straight(), at(SCOUT, 0.20).speed(1.3).straight(),
                         at(SCOUT, 0.46).speed(1.3).straight(), at(SCOUT, 0.72).speed(1.3).straight(),
                         at(SCOUT, 0.92).speed(1.3).straight(),
                         at(CRUISER, 0.46, -360).health(1.35).holding()),
                    wave(at(CRUISER, 0.44, -40).size(1.4).health(1.55).speed(0.7).fireGap(0.8),
                         at(FIGHTER, 0.08, -300).holding(), at(FIGHTER, 0.86, -300).holding(),
                         at(SCOUT, 0.26, -520).diving(), at(SCOUT, 0.68, -520).diving(),
                         at(SCOUT, 0.46, -740).weaving()));

            case RING_DEBRIS -> List.of(
                    wave(at(SCOUT, 0.06).straight(), at(SCOUT, 0.24).straight(),
                         at(SCOUT, 0.44).straight(), at(SCOUT, 0.64).straight(),
                         at(SCOUT, 0.86).straight(), at(SCOUT, 0.46, -320).straight()),
                    wave(at(SCOUT, 0.10).drifting(), at(SCOUT, 0.32).drifting(),
                         at(SCOUT, 0.56).drifting(), at(SCOUT, 0.80).drifting(),
                         at(FIGHTER, 0.46, -300).drifting(), at(FIGHTER, 0.22, -520).drifting()),
                    wave(at(CRUISER, 0.20).size(1.25).health(1.35).speed(0.8),
                         at(CRUISER, 0.70).size(1.25).health(1.35).speed(0.8),
                         at(SCOUT, 0.46, -260).diving(), at(SCOUT, 0.12, -480).weaving(),
                         at(SCOUT, 0.84, -480).weaving()),
                    wave(at(FIGHTER, 0.12).shots(2).fireGap(1.5).holding(),
                         at(FIGHTER, 0.36).shots(2).fireGap(1.5).holding(),
                         at(FIGHTER, 0.62).shots(2).fireGap(1.5).holding(),
                         at(FIGHTER, 0.86).shots(2).fireGap(1.5).holding(),
                         at(SCOUT, 0.46, -340).diving(), at(SCOUT, 0.24, -560).diving()),
                    wave(at(CRUISER, 0.28, -40).size(1.35).health(1.5).speed(0.75).shots(2).fireGap(1.3),
                         at(CRUISER, 0.64, -40).size(1.35).health(1.5).speed(0.75).shots(2).fireGap(1.3),
                         at(SCOUT, 0.46, -320).diving(), at(SCOUT, 0.14, -540).weaving(),
                         at(SCOUT, 0.82, -540).weaving(), at(FIGHTER, 0.46, -760).holding()));

            /* The galaxy's only ground, so this is the one level whose waves sit still. */
            case STATIC_CANYON -> List.of(
                    wave(at(SCOUT, 0.16).straight(), at(SCOUT, 0.34).straight(),
                         at(SCOUT, 0.54).straight(), at(SCOUT, 0.74).straight(),
                         at(SCOUT, 0.88).straight()),
                    wave(at(FIGHTER, 0.20).holding(), at(FIGHTER, 0.48).holding(),
                         at(FIGHTER, 0.76).holding(),
                         at(SCOUT, 0.34, -280).diving(), at(SCOUT, 0.62, -280).diving()),
                    wave(at(CRUISER, 0.26).size(1.2).health(1.35).holding(),
                         at(CRUISER, 0.66).size(1.2).health(1.35).holding(),
                         at(SCOUT, 0.46, -260).weaving(), at(SCOUT, 0.14, -460).weaving(),
                         at(SCOUT, 0.82, -460).weaving()),
                    wave(at(FIGHTER, 0.14).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.38).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.62).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.86).shots(2).fireGap(1.4).holding(),
                         at(CRUISER, 0.46, -340).health(1.4).holding()),
                    wave(at(CRUISER, 0.44, -40).size(1.45).health(1.6).speed(0.65).fireGap(0.75),
                         at(CRUISER, 0.16, -300).size(1.1).health(1.3).holding(),
                         at(CRUISER, 0.74, -300).size(1.1).health(1.3).holding(),
                         at(SCOUT, 0.32, -520).diving(), at(SCOUT, 0.62, -520).diving()));

            /* Back inside the band: the galaxy's one tunnel, and its tightest five waves. */
            case MAG_STORM_CAVERNS -> List.of(
                    wave(at(SCOUT, 0.37).straight(), at(SCOUT, 0.47).straight(),
                         at(SCOUT, 0.57).straight(), at(SCOUT, 0.42, -220).straight(),
                         at(SCOUT, 0.52, -220).straight(), at(SCOUT, 0.47, -440).straight()),
                    wave(at(FIGHTER, 0.37).holding(), at(FIGHTER, 0.56).holding(),
                         at(SCOUT, 0.46, -240).diving(), at(SCOUT, 0.38, -440).weaving(),
                         at(SCOUT, 0.54, -440).weaving()),
                    wave(at(CRUISER, 0.36).size(1.1).health(1.3).speed(0.8),
                         at(CRUISER, 0.55).size(1.1).health(1.3).speed(0.8),
                         at(SCOUT, 0.45, -280).straight(), at(SCOUT, 0.45, -460).straight(),
                         at(FIGHTER, 0.45, -640).holding()),
                    wave(at(SCOUT, 0.35).size(0.85).health(0.85).weaving(),
                         at(SCOUT, 0.43).size(0.85).health(0.85).weaving(),
                         at(SCOUT, 0.51).size(0.85).health(0.85).weaving(),
                         at(SCOUT, 0.59).size(0.85).health(0.85).weaving(),
                         at(FIGHTER, 0.46, -300).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.46, -520).shots(2).fireGap(1.4).holding()),
                    wave(at(CRUISER, 0.44, -60).size(1.4).health(1.7).speed(0.65).fireGap(0.75),
                         at(CRUISER, 0.36, -300).size(1.1).health(1.3).holding(),
                         at(CRUISER, 0.54, -300).size(1.1).health(1.3).holding(),
                         at(SCOUT, 0.40, -520).diving(), at(SCOUT, 0.52, -520).diving()));

            /* Six waves, straight down. Everything here dives, because the level does. */
            case DEEP_DESCENT -> List.of(
                    wave(at(SCOUT, 0.12).diving(), at(SCOUT, 0.32).diving(),
                         at(SCOUT, 0.52).diving(), at(SCOUT, 0.72).diving(),
                         at(SCOUT, 0.90).diving()),
                    wave(at(SCOUT, 0.08, -40).diving(), at(SCOUT, 0.28, -240).diving(),
                         at(SCOUT, 0.48, -440).diving(), at(SCOUT, 0.68, -640).diving(),
                         at(SCOUT, 0.88, -840).diving(), at(SCOUT, 0.46, -1040).diving()),
                    wave(at(FIGHTER, 0.18).diving(), at(FIGHTER, 0.46).diving(),
                         at(FIGHTER, 0.76).diving(),
                         at(SCOUT, 0.32, -300).weaving(), at(SCOUT, 0.62, -300).weaving()),
                    wave(at(CRUISER, 0.24).size(1.2).health(1.35).holding(),
                         at(CRUISER, 0.68).size(1.2).health(1.35).holding(),
                         at(SCOUT, 0.46, -280).diving(), at(SCOUT, 0.14, -500).diving(),
                         at(SCOUT, 0.82, -500).diving(), at(FIGHTER, 0.46, -720).drifting()),
                    wave(at(FIGHTER, 0.10).shots(2).fireGap(1.5).diving(),
                         at(FIGHTER, 0.34).shots(2).fireGap(1.5).diving(),
                         at(FIGHTER, 0.62).shots(2).fireGap(1.5).diving(),
                         at(FIGHTER, 0.88).shots(2).fireGap(1.5).diving(),
                         at(CRUISER, 0.46, -380).health(1.4).holding()),
                    wave(at(CRUISER, 0.44, -40).size(1.45).health(1.6).speed(0.7).fireGap(0.75),
                         at(FIGHTER, 0.16, -300).diving(), at(FIGHTER, 0.76, -300).diving(),
                         at(SCOUT, 0.32, -520).diving(), at(SCOUT, 0.62, -520).diving(),
                         at(SCOUT, 0.46, -740).weaving()));

            /* Vaunt returns, so the deck it walks is guarded by the heaviest escort in the galaxy. */
            case UPPER_DECK -> List.of(
                    wave(at(SCOUT, 0.14).straight(), at(SCOUT, 0.34).straight(),
                         at(SCOUT, 0.56).straight(), at(SCOUT, 0.78).straight(),
                         at(FIGHTER, 0.46, -300).holding()),
                    wave(at(FIGHTER, 0.18).holding(), at(FIGHTER, 0.46).holding(),
                         at(FIGHTER, 0.74).holding(),
                         at(SCOUT, 0.30, -280).diving(), at(SCOUT, 0.64, -280).diving(),
                         at(SCOUT, 0.46, -500).weaving()),
                    wave(at(CRUISER, 0.22).size(1.2).health(1.35).speed(0.8).holding(),
                         at(CRUISER, 0.70).size(1.2).health(1.35).speed(0.8).holding(),
                         at(SCOUT, 0.46, -280).diving(), at(SCOUT, 0.14, -500).weaving(),
                         at(SCOUT, 0.82, -500).weaving()),
                    wave(at(SCOUT, 0.06).size(0.85).health(0.85).diving(),
                         at(SCOUT, 0.26).size(0.85).health(0.85).diving(),
                         at(SCOUT, 0.46).size(0.85).health(0.85).diving(),
                         at(SCOUT, 0.66).size(0.85).health(0.85).diving(),
                         at(SCOUT, 0.88).size(0.85).health(0.85).diving(),
                         at(CRUISER, 0.46, -380).health(1.4).holding()),
                    wave(at(FIGHTER, 0.12).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.36).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.62).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.86).shots(2).fireGap(1.4).holding(),
                         at(SCOUT, 0.46, -340).diving(), at(SCOUT, 0.24, -560).diving()),
                    wave(at(CRUISER, 0.28, -40).size(1.4).health(1.55).speed(0.7).shots(2).fireGap(1.25),
                         at(CRUISER, 0.62, -40).size(1.4).health(1.55).speed(0.7).shots(2).fireGap(1.25),
                         at(FIGHTER, 0.46, -320).holding(), at(SCOUT, 0.16, -540).diving(),
                         at(SCOUT, 0.80, -540).diving(), at(SCOUT, 0.46, -760).weaving()));

            /* Side-on, six waves, and lightning: the fastest ships in the galaxy. */
            case LIGHTNING_REACH -> List.of(
                    wave(at(SCOUT, 0.10).speed(1.35).straight(), at(SCOUT, 0.28).speed(1.35).straight(),
                         at(SCOUT, 0.46).speed(1.35).straight(), at(SCOUT, 0.64).speed(1.35).straight(),
                         at(SCOUT, 0.84).speed(1.35).straight()),
                    wave(at(SCOUT, 0.14, -40).diving(), at(SCOUT, 0.36, -260).diving(),
                         at(SCOUT, 0.58, -480).diving(), at(SCOUT, 0.80, -700).diving(),
                         at(SCOUT, 0.46, -920).diving()),
                    wave(at(FIGHTER, 0.12).drifting(), at(FIGHTER, 0.44).drifting(),
                         at(FIGHTER, 0.80).drifting(),
                         at(SCOUT, 0.28, -300).weaving(), at(SCOUT, 0.64, -300).weaving()),
                    wave(at(CRUISER, 0.18).size(1.2).health(1.35).holding(),
                         at(CRUISER, 0.72).size(1.2).health(1.35).holding(),
                         at(SCOUT, 0.44, -280).diving(), at(SCOUT, 0.12, -500).diving(),
                         at(SCOUT, 0.80, -500).diving()),
                    wave(at(FIGHTER, 0.10).shots(2).fireGap(1.5).straight(),
                         at(FIGHTER, 0.34).shots(2).fireGap(1.5).straight(),
                         at(FIGHTER, 0.60).shots(2).fireGap(1.5).straight(),
                         at(FIGHTER, 0.84).shots(2).fireGap(1.5).straight(),
                         at(SCOUT, 0.46, -340).speed(1.35).diving()),
                    wave(at(CRUISER, 0.44, -40).size(1.45).health(1.6).speed(0.7).fireGap(0.75),
                         at(FIGHTER, 0.14, -300).holding(), at(FIGHTER, 0.76, -300).holding(),
                         at(SCOUT, 0.30, -520).speed(1.3).diving(),
                         at(SCOUT, 0.62, -520).speed(1.3).diving(),
                         at(SCOUT, 0.46, -740).weaving()));

            /* Storm Crown: the galaxy's finale, and the deepest waves in it. */
            case STORM_CROWN -> List.of(
                    wave(at(SCOUT, 0.08).drifting(), at(SCOUT, 0.28).drifting(),
                         at(SCOUT, 0.48).drifting(), at(SCOUT, 0.68).drifting(),
                         at(SCOUT, 0.88).drifting(), at(SCOUT, 0.46, -320).drifting()),
                    wave(at(FIGHTER, 0.16).diving(), at(FIGHTER, 0.46).diving(),
                         at(FIGHTER, 0.78).diving(),
                         at(SCOUT, 0.30, -300).weaving(), at(SCOUT, 0.64, -300).weaving(),
                         at(SCOUT, 0.46, -520).diving()),
                    wave(at(CRUISER, 0.24).size(1.2).health(1.4).holding(),
                         at(CRUISER, 0.68).size(1.2).health(1.4).holding(),
                         at(SCOUT, 0.46, -300).diving(), at(SCOUT, 0.12, -520).weaving(),
                         at(SCOUT, 0.84, -520).weaving()),
                    wave(at(SCOUT, 0.06).speed(1.3).diving(), at(SCOUT, 0.26).speed(1.3).diving(),
                         at(SCOUT, 0.46).speed(1.3).diving(), at(SCOUT, 0.68).speed(1.3).diving(),
                         at(SCOUT, 0.90).speed(1.3).diving(),
                         at(CRUISER, 0.46, -400).health(1.45).holding()),
                    wave(at(FIGHTER, 0.12).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.36).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.62).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.88).shots(2).fireGap(1.4).holding(),
                         at(SCOUT, 0.46, -360).diving(), at(SCOUT, 0.24, -580).diving()),
                    wave(at(CRUISER, 0.26, -40).size(1.4).health(1.6).speed(0.7).shots(2).fireGap(1.25),
                         at(CRUISER, 0.64, -40).size(1.4).health(1.6).speed(0.7).shots(2).fireGap(1.25),
                         at(FIGHTER, 0.46, -340).shots(2).fireGap(1.4).holding(),
                         at(SCOUT, 0.16, -560).diving(), at(SCOUT, 0.80, -560).diving(),
                         at(SCOUT, 0.46, -780).weaving()));

            // ---- Galaxy 5: Null (levels 41-50) -----------------------------------------------
            //
            // The only galaxy whose clock shortens, running 6,6,6,6,5,5,5,4,4,4 -- and its waves
            // shorten with it, in the sense that matters: the early levels are long and crowded and
            // the late ones are four waves of almost nothing but heavies. By Event Horizon a wave is
            // two oversized hulls and an escort, and there is no swatting left to do.
            //
            // Structural identity is dead things. Nothing here is a patrol; the first waves of most
            // levels drift or hold rather than close, and what hunts you does it slowly.

            case DEAD_BELT -> List.of(
                    wave(at(SCOUT, 0.08).drifting(), at(SCOUT, 0.26).drifting(),
                         at(SCOUT, 0.46).drifting(), at(SCOUT, 0.66).drifting(),
                         at(SCOUT, 0.88).drifting(), at(SCOUT, 0.46, -320).drifting()),
                    wave(at(SCOUT, 0.12).weaving(), at(SCOUT, 0.32).weaving(),
                         at(SCOUT, 0.56).weaving(), at(SCOUT, 0.80).weaving(),
                         at(FIGHTER, 0.46, -300).holding()),
                    wave(at(FIGHTER, 0.16).straight(), at(FIGHTER, 0.42).straight(),
                         at(FIGHTER, 0.68).straight(), at(FIGHTER, 0.88).straight(),
                         at(SCOUT, 0.34, -300).diving(), at(SCOUT, 0.62, -300).diving()),
                    wave(at(CRUISER, 0.22).size(1.2).health(1.35).holding(),
                         at(CRUISER, 0.70).size(1.2).health(1.35).holding(),
                         at(SCOUT, 0.46, -280).diving(), at(SCOUT, 0.14, -500).weaving(),
                         at(SCOUT, 0.82, -500).weaving()),
                    wave(at(FIGHTER, 0.12).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.36).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.62).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.86).shots(2).fireGap(1.4).holding(),
                         at(CRUISER, 0.46, -380).health(1.45).holding()),
                    wave(at(CRUISER, 0.28, -40).size(1.4).health(1.55).speed(0.7).fireGap(0.8),
                         at(CRUISER, 0.62, -40).size(1.4).health(1.55).speed(0.7).fireGap(0.8),
                         at(SCOUT, 0.46, -320).diving(), at(SCOUT, 0.16, -540).weaving(),
                         at(SCOUT, 0.80, -540).weaving(), at(FIGHTER, 0.46, -760).holding()));

            /* The emptiest sky in the game, so its waves are the sparsest -- until they are not. */
            case HULK_DRIFT -> List.of(
                    wave(at(SCOUT, 0.20).drifting(), at(SCOUT, 0.50).drifting(),
                         at(SCOUT, 0.78).drifting(), at(SCOUT, 0.46, -400).drifting()),
                    wave(at(SCOUT, 0.14).weaving(), at(SCOUT, 0.38).weaving(),
                         at(SCOUT, 0.62).weaving(), at(SCOUT, 0.86).weaving(),
                         at(SCOUT, 0.46, -320).weaving()),
                    wave(at(CRUISER, 0.26).size(1.25).health(1.4).speed(0.75).holding(),
                         at(CRUISER, 0.66).size(1.25).health(1.4).speed(0.75).holding(),
                         at(SCOUT, 0.46, -300).diving(), at(SCOUT, 0.20, -520).weaving()),
                    wave(at(FIGHTER, 0.16).drifting(), at(FIGHTER, 0.44).drifting(),
                         at(FIGHTER, 0.74).drifting(),
                         at(SCOUT, 0.30, -300).diving(), at(SCOUT, 0.62, -300).diving(),
                         at(SCOUT, 0.46, -520).weaving()),
                    wave(at(CRUISER, 0.18).size(1.15).health(1.3).holding(),
                         at(CRUISER, 0.46).size(1.15).health(1.3).holding(),
                         at(CRUISER, 0.72).size(1.15).health(1.3).holding(),
                         at(SCOUT, 0.32, -340).diving(), at(SCOUT, 0.62, -340).diving()),
                    wave(at(CRUISER, 0.44, -40).size(1.5).health(1.6).speed(0.65).shots(2).fireGap(1.2),
                         at(FIGHTER, 0.16, -320).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.76, -320).shots(2).fireGap(1.4).holding(),
                         at(SCOUT, 0.32, -540).diving(), at(SCOUT, 0.62, -540).diving(),
                         at(SCOUT, 0.46, -760).weaving()));

            /* A world that went out. Everything in the first half of this level is already dead. */
            case SHROUD -> List.of(
                    wave(at(SCOUT, 0.10).size(0.85).health(0.85).drifting(),
                         at(SCOUT, 0.30).size(0.85).health(0.85).drifting(),
                         at(SCOUT, 0.50).size(0.85).health(0.85).drifting(),
                         at(SCOUT, 0.70).size(0.85).health(0.85).drifting(),
                         at(SCOUT, 0.90).size(0.85).health(0.85).drifting()),
                    wave(at(SCOUT, 0.16).straight(), at(SCOUT, 0.36).straight(),
                         at(SCOUT, 0.60).straight(), at(SCOUT, 0.82).straight(),
                         at(SCOUT, 0.46, -320).straight(), at(SCOUT, 0.26, -540).straight()),
                    wave(at(FIGHTER, 0.20).holding(), at(FIGHTER, 0.50).holding(),
                         at(FIGHTER, 0.78).holding(),
                         at(SCOUT, 0.34, -300).weaving(), at(SCOUT, 0.64, -300).weaving()),
                    wave(at(CRUISER, 0.24).size(1.25).health(1.4).speed(0.75).holding(),
                         at(CRUISER, 0.68).size(1.25).health(1.4).speed(0.75).holding(),
                         at(SCOUT, 0.46, -300).diving(), at(SCOUT, 0.14, -520).weaving(),
                         at(SCOUT, 0.82, -520).weaving()),
                    wave(at(FIGHTER, 0.14).shots(2).fireGap(1.4).drifting(),
                         at(FIGHTER, 0.38).shots(2).fireGap(1.4).drifting(),
                         at(FIGHTER, 0.64).shots(2).fireGap(1.4).drifting(),
                         at(FIGHTER, 0.86).shots(2).fireGap(1.4).drifting(),
                         at(CRUISER, 0.46, -380).health(1.45).holding()),
                    wave(at(CRUISER, 0.44, -40).size(1.5).health(1.6).speed(0.65).fireGap(0.75),
                         at(CRUISER, 0.20, -320).size(1.15).health(1.3).holding(),
                         at(CRUISER, 0.70, -320).size(1.15).health(1.3).holding(),
                         at(SCOUT, 0.34, -540).diving(), at(SCOUT, 0.60, -540).diving()));

            /* First sighting of the hole, so this is the last level that still feels like flying. */
            case LENS_CORRIDOR -> List.of(
                    wave(at(SCOUT, 0.12).weaving(), at(SCOUT, 0.32).weaving(),
                         at(SCOUT, 0.56).weaving(), at(SCOUT, 0.80).weaving(),
                         at(SCOUT, 0.46, -320).weaving()),
                    wave(at(SCOUT, 0.08).drifting(), at(SCOUT, 0.28).drifting(),
                         at(SCOUT, 0.50).drifting(), at(SCOUT, 0.72).drifting(),
                         at(SCOUT, 0.90).drifting(), at(FIGHTER, 0.46, -340).holding()),
                    wave(at(FIGHTER, 0.18).straight(), at(FIGHTER, 0.46).straight(),
                         at(FIGHTER, 0.76).straight(),
                         at(SCOUT, 0.32, -300).diving(), at(SCOUT, 0.62, -300).diving()),
                    wave(at(CRUISER, 0.22).size(1.25).health(1.4).holding(),
                         at(CRUISER, 0.70).size(1.25).health(1.4).holding(),
                         at(SCOUT, 0.46, -300).diving(), at(SCOUT, 0.14, -520).weaving(),
                         at(SCOUT, 0.82, -520).weaving()),
                    wave(at(CRUISER, 0.16).size(1.15).health(1.3).holding(),
                         at(CRUISER, 0.46).size(1.15).health(1.3).holding(),
                         at(CRUISER, 0.74).size(1.15).health(1.3).holding(),
                         at(SCOUT, 0.30, -340).diving(), at(SCOUT, 0.64, -340).diving()),
                    wave(at(CRUISER, 0.28, -40).size(1.45).health(1.6).speed(0.7).shots(2).fireGap(1.2),
                         at(CRUISER, 0.62, -40).size(1.45).health(1.6).speed(0.7).shots(2).fireGap(1.2),
                         at(FIGHTER, 0.46, -340).holding(), at(SCOUT, 0.16, -560).diving(),
                         at(SCOUT, 0.80, -560).diving(), at(SCOUT, 0.46, -780).weaving()));

            /* Side-on, five waves, and tidal shear: everything arrives sheared into two ranks. */
            case TIDAL_SHEAR -> List.of(
                    wave(at(SCOUT, 0.10).straight(), at(SCOUT, 0.26).straight(),
                         at(SCOUT, 0.42).straight(),
                         at(SCOUT, 0.60, -320).straight(), at(SCOUT, 0.76, -320).straight(),
                         at(SCOUT, 0.90, -320).straight()),
                    wave(at(SCOUT, 0.14).drifting(), at(SCOUT, 0.36).drifting(),
                         at(SCOUT, 0.58).drifting(), at(SCOUT, 0.82).drifting(),
                         at(FIGHTER, 0.46, -340).holding()),
                    wave(at(FIGHTER, 0.12).holding(), at(FIGHTER, 0.44).holding(),
                         at(FIGHTER, 0.78).holding(),
                         at(SCOUT, 0.28, -320).diving(), at(SCOUT, 0.64, -320).diving()),
                    wave(at(CRUISER, 0.18).size(1.25).health(1.4).speed(0.75).holding(),
                         at(CRUISER, 0.72).size(1.25).health(1.4).speed(0.75).holding(),
                         at(SCOUT, 0.44, -300).diving(), at(SCOUT, 0.12, -520).weaving(),
                         at(SCOUT, 0.80, -520).weaving()),
                    wave(at(CRUISER, 0.44, -40).size(1.5).health(1.65).speed(0.65).fireGap(0.75),
                         at(FIGHTER, 0.14, -320).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.76, -320).shots(2).fireGap(1.4).holding(),
                         at(SCOUT, 0.30, -540).diving(), at(SCOUT, 0.62, -540).diving(),
                         at(SCOUT, 0.46, -760).weaving()));

            /* A dead structure, and a tunnel. Five waves inside the band, all of them heavy. */
            case THE_SHELL -> List.of(
                    wave(at(SCOUT, 0.37).straight(), at(SCOUT, 0.47).straight(),
                         at(SCOUT, 0.57).straight(), at(SCOUT, 0.42, -240).straight(),
                         at(SCOUT, 0.52, -240).straight(), at(SCOUT, 0.47, -480).straight()),
                    wave(at(FIGHTER, 0.37).holding(), at(FIGHTER, 0.56).holding(),
                         at(SCOUT, 0.46, -260).diving(), at(SCOUT, 0.38, -460).weaving(),
                         at(SCOUT, 0.54, -460).weaving()),
                    wave(at(CRUISER, 0.36).size(1.15).health(1.35).speed(0.8),
                         at(CRUISER, 0.55).size(1.15).health(1.35).speed(0.8),
                         at(SCOUT, 0.45, -300).straight(), at(SCOUT, 0.45, -480).straight(),
                         at(FIGHTER, 0.45, -660).holding()),
                    wave(at(FIGHTER, 0.37).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.47).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.57).shots(2).fireGap(1.4).holding(),
                         at(SCOUT, 0.40, -300).diving(), at(SCOUT, 0.54, -300).diving(),
                         at(CRUISER, 0.45, -520).health(1.45).holding()),
                    wave(at(CRUISER, 0.44, -60).size(1.45).health(1.75).speed(0.65).fireGap(0.75),
                         at(CRUISER, 0.36, -320).size(1.15).health(1.35).holding(),
                         at(CRUISER, 0.54, -320).size(1.15).health(1.35).holding(),
                         at(SCOUT, 0.40, -540).diving(), at(SCOUT, 0.52, -540).diving()));

            case ERGOSPHERE -> List.of(
                    wave(at(SCOUT, 0.10).drifting(), at(SCOUT, 0.32).drifting(),
                         at(SCOUT, 0.56).drifting(), at(SCOUT, 0.80).drifting(),
                         at(SCOUT, 0.46, -340).drifting()),
                    wave(at(FIGHTER, 0.16).drifting(), at(FIGHTER, 0.46).drifting(),
                         at(FIGHTER, 0.78).drifting(),
                         at(SCOUT, 0.30, -320).weaving(), at(SCOUT, 0.64, -320).weaving()),
                    wave(at(CRUISER, 0.20).size(1.25).health(1.4).holding(),
                         at(CRUISER, 0.70).size(1.25).health(1.4).holding(),
                         at(SCOUT, 0.46, -300).diving(), at(SCOUT, 0.14, -520).weaving(),
                         at(SCOUT, 0.82, -520).weaving()),
                    wave(at(CRUISER, 0.14).size(1.2).health(1.35).holding(),
                         at(CRUISER, 0.46).size(1.2).health(1.35).holding(),
                         at(CRUISER, 0.76).size(1.2).health(1.35).holding(),
                         at(SCOUT, 0.30, -360).diving(), at(SCOUT, 0.64, -360).diving()),
                    wave(at(CRUISER, 0.28, -40).size(1.5).health(1.65).speed(0.65).shots(2).fireGap(1.2),
                         at(CRUISER, 0.62, -40).size(1.5).health(1.65).speed(0.65).shots(2).fireGap(1.2),
                         at(FIGHTER, 0.46, -340).shots(2).fireGap(1.4).holding(),
                         at(SCOUT, 0.16, -560).diving(), at(SCOUT, 0.80, -560).diving()));

            /* Four waves now, and the brightest backdrop in the game. Nothing light survives here. */
            case PHOTON_RING -> List.of(
                    wave(at(FIGHTER, 0.14).holding(), at(FIGHTER, 0.40).holding(),
                         at(FIGHTER, 0.64).holding(), at(FIGHTER, 0.88).holding(),
                         at(SCOUT, 0.46, -340).diving()),
                    wave(at(CRUISER, 0.22).size(1.25).health(1.4).holding(),
                         at(CRUISER, 0.70).size(1.25).health(1.4).holding(),
                         at(SCOUT, 0.46, -300).diving(), at(SCOUT, 0.14, -520).weaving(),
                         at(SCOUT, 0.82, -520).weaving()),
                    wave(at(CRUISER, 0.14).size(1.2).health(1.4).holding(),
                         at(CRUISER, 0.46).size(1.2).health(1.4).holding(),
                         at(CRUISER, 0.76).size(1.2).health(1.4).holding(),
                         at(FIGHTER, 0.30, -360).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.62, -360).shots(2).fireGap(1.4).holding()),
                    wave(at(CRUISER, 0.44, -40).size(1.55).health(1.7).speed(0.6).fireGap(0.7),
                         at(CRUISER, 0.18, -320).size(1.2).health(1.4).holding(),
                         at(CRUISER, 0.72, -320).size(1.2).health(1.4).holding(),
                         at(SCOUT, 0.32, -560).diving(), at(SCOUT, 0.62, -560).diving(),
                         at(SCOUT, 0.46, -780).weaving()));

            /* The last tunnel in the game. Four waves, and the lane is shut for three of them. */
            case THE_THROAT -> List.of(
                    wave(at(FIGHTER, 0.37).holding(), at(FIGHTER, 0.47).holding(),
                         at(FIGHTER, 0.57).holding(),
                         at(SCOUT, 0.42, -300).diving(), at(SCOUT, 0.52, -300).diving()),
                    wave(at(CRUISER, 0.36).size(1.2).health(1.4).speed(0.8),
                         at(CRUISER, 0.55).size(1.2).health(1.4).speed(0.8),
                         at(SCOUT, 0.45, -300).straight(), at(SCOUT, 0.45, -480).straight(),
                         at(FIGHTER, 0.45, -660).holding()),
                    wave(at(CRUISER, 0.36).size(1.15).health(1.35).holding(),
                         at(CRUISER, 0.46).size(1.15).health(1.35).holding(),
                         at(CRUISER, 0.56).size(1.15).health(1.35).holding(),
                         at(SCOUT, 0.41, -340).diving(), at(SCOUT, 0.51, -340).diving()),
                    wave(at(CRUISER, 0.44, -60).size(1.5).health(1.8).speed(0.6).fireGap(0.7),
                         at(CRUISER, 0.36, -320).size(1.2).health(1.4).holding(),
                         at(CRUISER, 0.54, -320).size(1.2).health(1.4).holding(),
                         at(FIGHTER, 0.45, -540).shots(2).fireGap(1.4).holding(),
                         at(SCOUT, 0.40, -720).diving(), at(SCOUT, 0.52, -720).diving()));

            /*
             * The last four waves in the campaign, and the end of the shortening: by here a wave is
             * two oversized hulls and whatever is still moving behind them. Nothing drifts, nothing
             * is undersized, and every wave has at least one thing in it that must be killed rather
             * than avoided. The fourth is the heaviest wave in the game.
             */
            case EVENT_HORIZON -> List.of(
                    wave(at(CRUISER, 0.20).size(1.3).health(1.45).holding(),
                         at(CRUISER, 0.70).size(1.3).health(1.45).holding(),
                         at(FIGHTER, 0.46, -320).holding(),
                         at(SCOUT, 0.14, -540).diving(), at(SCOUT, 0.82, -540).diving()),
                    wave(at(CRUISER, 0.12).size(1.25).health(1.4).holding(),
                         at(CRUISER, 0.46).size(1.25).health(1.4).holding(),
                         at(CRUISER, 0.78).size(1.25).health(1.4).holding(),
                         at(FIGHTER, 0.28, -360).shots(2).fireGap(1.4).holding(),
                         at(FIGHTER, 0.64, -360).shots(2).fireGap(1.4).holding()),
                    wave(at(CRUISER, 0.30, -40).size(1.5).health(1.65).speed(0.65).shots(2).fireGap(1.2),
                         at(CRUISER, 0.62, -40).size(1.5).health(1.65).speed(0.65).shots(2).fireGap(1.2),
                         at(FIGHTER, 0.16, -340).holding(), at(FIGHTER, 0.76, -340).holding(),
                         at(SCOUT, 0.46, -560).diving(), at(SCOUT, 0.30, -780).diving()),
                    wave(at(CRUISER, 0.44, -40).size(1.6).health(1.8).speed(0.6).fireGap(0.7),
                         at(CRUISER, 0.16, -340).size(1.3).health(1.5).holding(),
                         at(CRUISER, 0.72, -340).size(1.3).health(1.5).holding(),
                         at(FIGHTER, 0.32, -580).shots(2).fireGap(1.35).holding(),
                         at(FIGHTER, 0.60, -580).shots(2).fireGap(1.35).holding(),
                         at(SCOUT, 0.46, -800).diving()));
        };
    }
}
