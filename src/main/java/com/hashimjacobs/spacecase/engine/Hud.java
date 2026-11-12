package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import com.hashimjacobs.spacecase.ui.Meter;
import com.hashimjacobs.spacecase.ui.Tokens;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.PowerUp;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Settings;
import com.hashimjacobs.spacecase.ui.CanvasFx;

/** Score, lives, health and active power-ups for each player, plus the wave and boss bars. */
final class Hud {

    private static final double PANEL_WIDTH = 210;
    private static final double BAR_HEIGHT = 14;

    /**
     * How far a player panel reaches below its top edge, so the lower two can be anchored to the
     * bottom of the arena rather than guessed at.
     *
     * Measured from the lowest thing {@link #drawPlayerPanel} draws -- the effects line at
     * {@code barY + BAR_HEIGHT + 22}, plus its own text height. Nothing else in the HUD uses the
     * bottom of the screen, so there is nothing down there to collide with.
     */
    private static final double PANEL_HEIGHT = 118;   // +8 for the special meter, +13 for its hint row

    /** Inset from whichever corner a panel is anchored to. */
    private static final double PANEL_MARGIN = 16;
    private static final Color BRAND = Tokens.BRAND;

    /** Share of full health below which the bar starts pulsing. Mirrored by GameLoop's alarm. */
    private static final double LOW_HEALTH_FRACTION = 0.25;

    /** Lives at or below which the counter starts flashing. One means the next death ends it. */
    private static final int LOW_LIVES = 1;

    private static final Color DANGER = Tokens.DANGER;
    private static final Color LIVES_NORMAL = Tokens.TEXT_SECONDARY;

    /** Not final: the seat panels are drawn through it onto the side bars when there are any. */
    private GraphicsContext gc;
    /** The side-bar canvas and its reach each side of the arena; see {@code Renderer.setBars}. */
    private GraphicsContext bars;
    private double side;
    private final Settings settings;
    private final Font labelFont = Font.font(Tokens.BODY, FontWeight.BOLD, Tokens.SIZE_CAPTION);
    private final Font chipFont = Font.font(Tokens.BODY, FontWeight.BOLD, Tokens.SIZE_SMALL);
    private final Font bannerFont = Font.font(Tokens.BODY, FontWeight.BOLD, Tokens.SIZE_HEADING);
    private final Font scoreFont = Font.font(Assets.displayFontFamily(), Tokens.SIZE_TITLE);
    private final Font smallFont = Font.font(Tokens.BODY, FontWeight.NORMAL, Tokens.SIZE_CAPTION);

    // Drawing memory, one per seat: where each bar stood a moment ago, and the score mid-roll.
    // Never read by the simulation.
    private final Meter.Lag[] healthLag = {new Meter.Lag(), new Meter.Lag(), new Meter.Lag(), new Meter.Lag()};
    private final double[] shownScore = new double[4];
    private Meter.Lag bossLag;

    Hud(GraphicsContext gc, Settings settings) {
        this.gc = gc;
        this.settings = settings;
    }

    void setBars(GraphicsContext bars, double side) {
        this.bars = bars;
        this.side = side;
    }

    void draw(World world, SpawnDirector director) {
        gc.setTextBaseline(VPos.TOP);
        List<PlayerShip> players = world.players();

        // A corner each, filling across before down: one and two keep the top-left and top-right
        // they have always had, and an online room's third and fourth take the bottom corners.
        // On a screen wider than the arena the panels move out into the bars, as far as there is
        // room, and the arena's corners are left to the fight.
        double out = bars == null ? 0 : Math.min(side, PANEL_WIDTH + 2 * PANEL_MARGIN);
        GraphicsContext arena = gc;
        if (bars != null) {
            gc = bars;
            gc.setTextBaseline(VPos.TOP);
            gc.setTextAlign(TextAlignment.LEFT);
        }
        try {
            for (PlayerShip player : players) {
                int seat = player.playerNumber() - 1;
                double x = seat % 2 == 1 ? GameConfig.WIDTH - PANEL_WIDTH - PANEL_MARGIN + out
                        : PANEL_MARGIN - out;
                double y = seat >= 2 ? GameConfig.HEIGHT - PANEL_HEIGHT - PANEL_MARGIN : 14;
                drawPlayerPanel(player, x, y, world.tick());
            }
        } finally {
            gc = arena;
        }

        // Battle mode has no enemies, so it has no level and no boss to progress through either.
        if (world.rules().spawnEnemies()) {
            drawLevelAndWave(director);
            drawChallengeLine(world, director);
        }
        EnemyShip boss = world.boss();
        if (boss != null) {
            drawBossBar(boss);
        } else {
            bossLag = null;
        }
        if (director.bossWarning()) {
            drawWarning("FLAGSHIP INBOUND", world.tick());
        } else if (director.warning() != null) {
            drawWarning(director.warning(), world.tick());
        }
    }

    private void drawPlayerPanel(PlayerShip player, double x, double y, int tick) {
        int seat = Math.min(3, player.playerNumber() - 1);
        gc.setFill(Tokens.PLATE);
        gc.fillRoundRect(x - 8, y - 6, PANEL_WIDTH + 16, PANEL_HEIGHT + 8, Tokens.RADIUS_L, Tokens.RADIUS_L);
        gc.setStroke(Tokens.EDGE);
        gc.setLineWidth(Tokens.STROKE_HAIR);
        gc.strokeRoundRect(x - 8, y - 6, PANEL_WIDTH + 16, PANEL_HEIGHT + 8, Tokens.RADIUS_L, Tokens.RADIUS_L);

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(labelFont);
        gc.setFill(Tokens.LABEL);
        // The pilot's own name, as under the hull; the seat number only where there is no name.
        String name = player.name() == null || player.name().isBlank()
                ? "PLAYER " + player.playerNumber() : player.name().toUpperCase();
        gc.fillText(name.length() > 24 ? name.substring(0, 23) + "\u2026" : name, x, y);

        // The score rolls up to its value rather than jumping, so a kill is seen to pay.
        double gap = player.score() - shownScore[seat];
        shownScore[seat] = Math.abs(gap) < 1 ? player.score() : shownScore[seat] + gap * 0.18;
        gc.setFont(scoreFont);
        gc.setFill(Color.WHITE);
        gc.fillText(String.format("%,d", Math.round(shownScore[seat])), x, y + 13);
        if (player.combo() > 1 && tick - player.comboTick() <= PlayerShip.COMBO_WINDOW_TICKS) {
            chip(String.format("x%.1f", player.comboMultiplier()), x + PANEL_WIDTH, y + 22, Tokens.HEALTH_MID);
        }

        double barY = y + 50;
        drawHealthBar(player, seat, x, barY, tick);
        drawSpecialMeter(player, x, barY + BAR_HEIGHT + 5, tick);
        barY += SPECIAL_HEIGHT + 5;

        // Under the special's hint row, not on it: the hint is right-aligned and long enough to reach LIVES.
        drawLives(player, x, barY + BAR_HEIGHT + 17, tick);

        List<String> effects = activeEffectLabels(player);
        if (!effects.isEmpty()) {
            gc.setFont(smallFont);
            gc.setFill(BRAND);
            gc.fillText(String.join("  ", effects), x, barY + BAR_HEIGHT + 31);
        }
    }

    /** A small filled tag, right-aligned at {@code right}: the combo, the hydra's head count. */
    private void chip(String text, double right, double y, Color color) {
        double width = text.length() * 7.6 + 12;
        gc.setFill(Tokens.veil(0.55));
        gc.fillRoundRect(right - width, y, width, 18, Tokens.RADIUS, Tokens.RADIUS);
        gc.setStroke(color);
        gc.setLineWidth(Tokens.STROKE_HAIR);
        gc.strokeRoundRect(right - width, y, width, 18, Tokens.RADIUS, Tokens.RADIUS);
        gc.setFont(chipFont);
        gc.setFill(color);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(text, right - width / 2, y + 2);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * Lives as hulls that can be counted at a glance, with the number kept beside them for anyone
     * who would rather read it.
     */
    private void drawLives(PlayerShip player, double x, double y, int tick) {
        int lives = Math.max(0, player.lives());
        Color color = livesColour(player, tick);
        gc.setFill(color);
        int pips = Math.min(lives, 6);
        for (int i = 0; i < pips; i++) {
            double px = x + i * 13;
            gc.fillPolygon(new double[] {px + 5, px + 10, px + 5, px}, new double[] {y, y + 11, y + 8, y + 11}, 4);
        }
        gc.setFont(smallFont);
        gc.fillText("LIVES " + lives, x + Math.max(1, pips) * 13 + 6, y);
    }

    /**
     * Flashes the counter once a player is one death from being out.
     *
     * Alternates the colour rather than the visibility on purpose: a number that vanishes half the
     * time is harder to read at a glance, and this way the count stays legible to anyone who does
     * not register the flash at all.
     */
    private Color livesColour(PlayerShip player, int tick) {
        if (player.lives() > LOW_LIVES || player.isOut()) {
            return LIVES_NORMAL;
        }
        if (settings.reducedFlash()) {
            // Hold the warning colour rather than alternating: the point of the row above is that
            // the count stays legible, and a steady red still reads as "one from out".
            return Tokens.DANGER_LOW;
        }
        return (tick / 20) % 2 == 0 ? Tokens.DANGER_LOW : LIVES_NORMAL;
    }

    private static final double SPECIAL_HEIGHT = 5;

    /**
     * The special's meter, a thin bar under the hull's, in the airframe's own color. Full, it
     * breathes and names the buttons, because a meter nobody knows how to spend is decoration.
     */
    private void drawSpecialMeter(PlayerShip player, double x, double y, int tick) {
        double fraction = player.specialCharge() / (double) GameConfig.SPECIAL_FULL;
        Color tint = Color.web(player.loadout().chassis().specialColor());
        boolean full = player.specialReady();
        double breath = full && !settings.reducedFlash() ? 0.65 + 0.35 * Math.sin(tick * 0.15) : 1;
        Meter.draw(gc, x, y, PANEL_WIDTH, SPECIAL_HEIGHT, fraction, fraction,
                tint.deriveColor(0, 1, 1, breath), Tokens.TRACK, 0, false);
        if (full) {
            gc.setFont(smallFont);
            gc.setFill(tint);
            gc.setTextAlign(TextAlignment.RIGHT);
            String keys = player.playerNumber() == 2 ? "B or /" : "B or Z";
            gc.fillText(player.loadout().chassis().specialLabel().toUpperCase() + "  ·  " + keys,
                    x + PANEL_WIDTH, y + SPECIAL_HEIGHT + 3);
            gc.setTextAlign(TextAlignment.LEFT);
        }
    }

    private void drawHealthBar(PlayerShip player, int seat, double x, double y, int tick) {
        double fraction = Math.max(0, player.health()) / (double) player.maxHealth();
        Meter.draw(gc, x, y, PANEL_WIDTH, BAR_HEIGHT, fraction, healthLag[seat].follow(fraction),
                Tokens.healthColor(fraction), Tokens.TRACK, 4, fraction <= LOW_HEALTH_FRACTION);

        if (fraction <= LOW_HEALTH_FRACTION && !player.isOut()) {
            drawLowHealthGlow(x, y, fraction, tick);
        }

        // White on impact, so a hit reads on the bar as well as on the hull. Eighteen ticks is
        // short enough to register as a flash without needing to blink.
        if (player.justHit()) {
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(2);
            gc.strokeRoundRect(x, y, PANEL_WIDTH, BAR_HEIGHT, 7, 7);
            gc.setLineWidth(1);
        }
    }

    /** A breathing red wash over the remaining health, additive so it reads as a glow. */
    private void drawLowHealthGlow(double x, double y, double fraction, int tick) {
        // Held at the midpoint of the breath under reduced flash, so the wash still marks the bar
        // as critical without the oscillation.
        double pulse = settings.reducedFlash() ? 0.35 : 0.35 + 0.35 * Math.sin(tick * 0.22);
        gc.save();
        CanvasFx.blend(gc, BlendMode.SCREEN);
        // A halo spilling past the bar's edge: with the alarm sound gone, this is the warning.
        for (int ring = 3; ring >= 1; ring--) {
            gc.setGlobalAlpha(pulse * 0.22);
            gc.setFill(DANGER);
            gc.fillRoundRect(x - ring * 3, y - ring * 3, PANEL_WIDTH + ring * 6, BAR_HEIGHT + ring * 6, 7 + ring * 3, 7 + ring * 3);
        }
        gc.setGlobalAlpha(pulse);
        gc.setFill(DANGER);
        gc.fillRoundRect(x, y, PANEL_WIDTH * fraction, BAR_HEIGHT, 7, 7);
        gc.restore();
    }

    /**
     * What the pilot is carrying. No countdowns any more -- nothing expires, so the only number
     * worth printing is how many tri-shots are stacked, and only once there is more than one.
     *
     * HEALTH and EXTRA_LIFE are spent the instant they are collected and are never held, so
     * iterating every kind still lists exactly the four that can be.
     */
    private List<String> activeEffectLabels(PlayerShip player) {
        List<String> labels = new ArrayList<>();
        for (PowerUp.Kind kind : PowerUp.Kind.values()) {
            if (!player.hasEffect(kind)) {
                continue;
            }
            boolean stacked = kind.maxStacks() > 1 && player.stacks(kind) > 1;
            labels.add(stacked ? shortName(kind) + " x" + player.stacks(kind) : shortName(kind));
        }
        return labels;
    }

    private String shortName(PowerUp.Kind kind) {
        String name = switch (kind) {
            case TRI_SHOT -> "TRI";
            case MEGA_LASER -> "MEGA";
            case ROCKETS -> "RKT";
            case SPEED -> "SPD";
            case SHIELD -> "SHLD";
            case HEALTH -> "HP";
            case EXTRA_LIFE -> "LIFE";
            case SCYTHE -> "SCY";
            case FLAK -> "FLAK";
            case NOVA -> "NOVA";
        };
        return name;
    }

    /**
     * One line, because the boss name and bar claim the rows directly underneath.
     *
     * Names the level's number and how far through its waves you are, so a second pass through
     * somewhere is distinguishable from the first -- the old single global wave counter read "WAVE 19"
     * on the last level and looked identical whichever loop it was.
     */
    private void drawLevelAndWave(SpawnDirector director) {
        Level level = director.level();
        StringBuilder heading = new StringBuilder()
                .append(level.galaxy().label().toUpperCase())
                .append("   LEVEL ").append(level.number())
                .append("   ").append(level.label().toUpperCase())
                .append("   WAVE ").append(director.waveInLevel())
                .append('/').append(level.wavesBeforeBoss());
        if (director.loop() > 1) {
            heading.append("   LOOP ").append(director.loop());
        }

        // A plate between the two top panels, so the line reads over any sky.
        double width = 500;
        gc.setFill(Tokens.PLATE);
        gc.fillRoundRect(GameConfig.WIDTH / 2 - width / 2, 8, width, 22, Tokens.RADIUS_L, Tokens.RADIUS_L);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(chipFont);
        gc.setFill(Color.web(level.galaxy().accent()));
        gc.fillText(heading.toString(), GameConfig.WIDTH / 2, 12);
    }

    /**
     * The two challenges a pilot can watch happen: the clock against par, and the level's cores.
     * Bottom centre, the one edge no panel or boss bar claims.
     */
    private void drawChallengeLine(World world, SpawnDirector director) {
        int ticks = director.ticksIntoLevel();
        boolean overPar = ticks > director.parTicks();
        double width = 230;
        double x = GameConfig.WIDTH / 2 - width / 2;
        double y = GameConfig.HEIGHT - 32;
        gc.setFill(Tokens.PLATE);
        gc.fillRoundRect(x, y, width, 22, Tokens.RADIUS_L, Tokens.RADIUS_L);
        gc.setFont(chipFont);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(overPar ? Tokens.TEXT_FAINT : Color.WHITE);
        gc.fillText(clock(ticks) + " / PAR " + clock(director.parTicks()), x + 14, y + 4);
        // The preset, named on every frame: a screenshot or a clip of a run says what it was run on.
        String preset = director.difficulty().label().toUpperCase();
        chip(preset, GameConfig.WIDTH / 2 + (preset.length() * 7.6 + 12) / 2, y - 24, Color.WHITE);
        double cx = x + width - 62;
        for (int i = 0; i < 3; i++) {
            boolean taken = (world.coresTaken() & (1 << i)) != 0;
            double cy = y + 11;
            double[] xs = {cx, cx + 6, cx, cx - 6};
            double[] ys = {cy - 7, cy, cy + 7, cy};
            if (taken) {
                gc.setFill(CORE);
                gc.fillPolygon(xs, ys, 4);
            } else {
                gc.setStroke(world.coreKnown(i) ? CORE : Tokens.TEXT_FAINT);
                gc.setLineWidth(1.5);
                gc.strokePolygon(xs, ys, 4);
            }
            cx += 18;
        }
    }

    private static String clock(int ticks) {
        int seconds = ticks / 60;
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    /** The hidden cores' color, on the HUD and in the arena alike. */
    static final Color CORE = Color.web("#7FE8FF");

    /** Flashes while the flagship is arriving, so the fight does not start unannounced. */
    void drawNetBanner(String line) {
        gc.setFill(Tokens.veil(0.6));
        gc.fillRoundRect(GameConfig.WIDTH * 0.2, GameConfig.HEIGHT * 0.44, GameConfig.WIDTH * 0.6, 44, 10, 10);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(bannerFont);
        gc.setFill(Tokens.HEALTH_MID);
        gc.fillText(line, GameConfig.WIDTH / 2, GameConfig.HEIGHT * 0.44 + 29);
    }

    private void drawWarning(String text, int tick) {
        if (!settings.reducedFlash() && (tick / 14) % 2 == 0) {
            return;
        }
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(bannerFont);
        gc.setFill(Tokens.DANGER_SOFT);
        gc.fillText(text, GameConfig.WIDTH / 2, GameConfig.HEIGHT * 0.34);
    }

    private void drawBossBar(EnemyShip boss) {
        double width = 500;
        double x = GameConfig.WIDTH / 2 - width / 2;
        double y = 54;
        if (bossLag == null) {
            bossLag = new Meter.Lag();
        }

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(labelFont);
        gc.setFill(Tokens.DANGER_SOFT);
        gc.fillText(boss.boss().label().toUpperCase(), x, 37);
        // A flagship its parts shield says what to shoot: the heads first, then the body.
        if (boss.armoured() && boss.boss() == com.hashimjacobs.spacecase.entity.Boss.HYDRA) {
            chip("SEVER THE HEADS  " + boss.livingParts() + "/" + boss.parts().size(), x + width, 33,
                    Tokens.DANGER_SOFT);
        }

        // Ticked in thirds because that is where a flagship changes how it fights (BossPhase).
        double fraction = boss.remainingHealthFraction();
        Meter.draw(gc, x, y, width, 12, fraction, bossLag.follow(fraction),
                Tokens.DANGER_BAR, Tokens.TRACK_DANGER, 3, false);
    }
}
