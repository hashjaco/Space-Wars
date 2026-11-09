package com.hashimjacobs.spacecase.asset;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Every frame the CG art enums promise is on disk, for every pose and variant, and every state a
 * flagship art declares. Empty enums pass trivially: the point is that a constant cannot be added
 * ahead of its files.
 */
class CgArtProvenanceTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void everyDeclaredBossStateFrameExists() {
        List<String> missing = new ArrayList<>();
        for (BossArt art : BossArt.values()) {
            for (BossArt.State state : BossArt.State.values()) {
                for (int frame = 1; frame <= art.frameCount(state); frame++) {
                    String path = art.framePath(state, frame);
                    if (!Files.exists(RESOURCES.resolve(path.substring(1)))) {
                        missing.add(path);
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "flagship state frames are missing: " + missing);
    }

    @Test
    void everyMapPlanetFrameAndSkyExists() {
        List<String> missing = new ArrayList<>();
        for (MapArt art : MapArt.values()) {
            for (int frame = 1; frame <= MapArt.FRAMES; frame++) {
                if (!Files.exists(RESOURCES.resolve(art.planetFramePath(frame).substring(1)))) {
                    missing.add(art.planetFramePath(frame));
                }
            }
            if (!Files.exists(RESOURCES.resolve(art.skyPath().substring(1)))) {
                missing.add(art.skyPath());
            }
        }
        assertTrue(missing.isEmpty(), "universe map art is missing: " + missing);
    }

    @Test
    void everyEnemyArtFrameExists() {
        List<String> missing = new ArrayList<>();
        for (EnemyArt art : EnemyArt.values()) {
            for (int frame = 1; frame <= art.frameCount(); frame++) {
                if (!Files.exists(RESOURCES.resolve(art.framePath(frame).substring(1)))) {
                    missing.add(art.framePath(frame));
                }
            }
        }
        assertTrue(missing.isEmpty(), "enemy loop frames are missing: " + missing);
    }

    @Test
    void everyRoundFrameExists() {
        List<String> missing = new ArrayList<>();
        for (Round round : Round.values()) {
            for (int frame = 1; frame <= round.frameCount(); frame++) {
                if (!Files.exists(RESOURCES.resolve(round.framePath(frame).substring(1)))) {
                    missing.add(round.framePath(frame));
                }
            }
        }
        assertTrue(missing.isEmpty(), "creature round frames are missing: " + missing);
    }

    /**
     * The on-foot cast: every folder a kind's states and moves draw from (see OnFootRenderer.animFor and
     * Move.anim) has a first frame. The renderer falls back to a puppeted still without one, which is a
     * safety net, not a look.
     */
    @Test
    void everyOnFootMoveHasFrames() {
        java.util.Map<String, List<String>> cast = java.util.Map.of(
                "pilot", List.of("idle", "walk", "run", "jump", "hurt", "death", "combo_1", "combo_2", "combo_3",
                        "dodge", "flourish", "special", "victory"),
                "thrall", List.of("combat_idle", "walk", "hurt", "death", "combo_1", "combo_2"),
                "foreman", List.of("combat_idle", "walk", "hurt", "death", "combo_1", "combo_2", "combo_3", "special"),
                "riveteer", List.of("combat_idle", "walk", "hurt", "death", "special"),
                "brute", List.of("combat_idle", "walk", "hurt", "death", "combo_1", "combo_2"));
        List<String> missing = new ArrayList<>();
        cast.forEach((who, moves) -> {
            for (String move : moves) {
                Path first = RESOURCES.resolve("sprites/onfoot/" + who + "/" + move + "/1.png");
                if (!Files.exists(first)) {
                    missing.add(who + "/" + move);
                }
            }
        });
        assertTrue(missing.isEmpty(), "on-foot moves with no frames: " + missing);
    }

    @Test
    void everyChassisArtFrameExists() {
        // Every chassis, bare and with every kit, in every view. Kit names spelled out: asset cannot see garage.
        List<String> missing = new ArrayList<>();
        for (ChassisArt art : ChassisArt.values()) {
            for (String kit : new String[] {null, "fins", "armour", "lance"}) {
                for (ChassisArt.View view : ChassisArt.View.values()) {
                    for (int frame = 1; frame <= view.frameCount(); frame++) {
                        String path = art.framePath(kit, view, frame);
                        if (!Files.exists(RESOURCES.resolve(path.substring(1)))) {
                            missing.add(path);
                        }
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "hull loop frames are missing: " + missing);
    }

    @Test
    void cgDirectoriesAreNamedInTheManifest() throws IOException {
        String manifest = Files.readString(Path.of("ASSETS.md"));
        List<String> unlisted = new ArrayList<>();
        for (String dir : new String[] {"enemy-cg", "hull-cg", "sky-cg", "map-cg"}) {
            if (Files.isDirectory(RESOURCES.resolve("sprites").resolve(dir)) && !manifest.contains(dir)) {
                unlisted.add(dir);
            }
        }
        assertTrue(unlisted.isEmpty(), "CG asset directories not in ASSETS.md: " + unlisted);
    }
}
