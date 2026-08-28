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

import com.hashimjacobs.spacecase.ui.Tokens;
import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.PowerUp;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Settings;

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
    private static final double PANEL_HEIGHT = 97;

    /** Inset from whichever corner a panel is anchored to. */
    private static final double PANEL_MARGIN = 16;
    private static final Color BRAND = Tokens.BRAND;

    /** Share of full health below which the bar starts pulsing. Mirrored by GameLoop's alarm. */
    private static final double LOW_HEALTH_FRACTION = 0.25;

    /** Lives at or below which the counter starts flashing. One means the next death ends it. */
    private static final int LOW_LIVES = 1;

    private static final Color DANGER = Tokens.DANGER;
    private static final Color LIVES_NORMAL = Tokens.TEXT_SECONDARY;

    private final GraphicsContext gc;
    private final Settings settings;
    private final Font labelFont = Font.font(Tokens.BODY, FontWeight.BOLD, 15);
    private final Font valueFont = Font.font(Tokens.BODY, FontWeight.BOLD, 22);
    private final Font smallFont = Font.font(Tokens.BODY, FontWeight.NORMAL, 11);

    Hud(GraphicsContext gc, Settings settings) {
        this.gc = gc;
        this.settings = settings;
    }

    void draw(World world, SpawnDirector director) {
        gc.setTextBaseline(VPos.TOP);
        List<PlayerShip> players = world.players();

        // A corner each, filling across before down: one and two keep the top-left and top-right
        // they have always had, and an online room's third and fourth take the bottom corners.
        for (PlayerShip player : players) {
            int seat = player.playerNumber() - 1;
            double x = seat % 2 == 1 ? GameConfig.WIDTH - PANEL_WIDTH - PANEL_MARGIN : PANEL_MARGIN;
            double y = seat >= 2 ? GameConfig.HEIGHT - PANEL_HEIGHT - PANEL_MARGIN : 14;
            drawPlayerPanel(player, x, y, world.tick());
        }

        // Battle mode has no enemies, so it has no level and no boss to progress through either.
        if (world.rules().spawnEnemies()) {
            drawLevelAndWave(director);
        }
        EnemyShip boss = world.boss();
        if (boss != null) {
            drawBossBar(boss);
        }
        if (director.bossWarning()) {
            drawFlagshipWarning(world.tick());
        }
    }

    private void drawPlayerPanel(PlayerShip player, double x, double y, int tick) {
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(labelFont);
        gc.setFill(Tokens.LABEL);
        gc.fillText("PLAYER " + player.playerNumber(), x, y);

        gc.setFont(valueFont);
        gc.setFill(Color.WHITE);
        gc.fillText(String.valueOf(player.score()), x, y + 20);

        double barY = y + 50;
        drawHealthBar(player, x, barY, tick);

        gc.setFont(smallFont);
        gc.setFill(livesColour(player, tick));
        gc.fillText("LIVES  " + Math.max(0, player.lives()), x, barY + BAR_HEIGHT + 6);

        List<String> effects = activeEffectLabels(player);
        if (!effects.isEmpty()) {
            gc.setFill(BRAND);
            String joined = String.join("  ", effects);
            gc.fillText(joined, x, barY + BAR_HEIGHT + 22);
        }
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

    private void drawHealthBar(PlayerShip player, double x, double y, int tick) {
        double fraction = Math.max(0, player.health()) / (double) player.maxHealth();

        gc.setFill(Tokens.TRACK);
        gc.fillRoundRect(x, y, PANEL_WIDTH, BAR_HEIGHT, 7, 7);

        Color fill = fraction > 0.5 ? BRAND : fraction > 0.25 ? Color.GOLD : Tokens.DANGER_LOW;
        gc.setFill(fill);
        gc.fillRoundRect(x, y, PANEL_WIDTH * fraction, BAR_HEIGHT, 7, 7);

        if (fraction <= LOW_HEALTH_FRACTION && !player.isOut()) {
            drawLowHealthGlow(x, y, fraction, tick);
        }

        // White on impact, so a hit reads on the bar as well as on the hull. Eighteen ticks is
        // short enough to register as a flash without needing to blink.
        boolean hit = player.justHit();
        gc.setStroke(hit ? Color.WHITE : Tokens.EDGE_STRONG);
        gc.setLineWidth(hit ? 2 : 1);
        gc.strokeRoundRect(x, y, PANEL_WIDTH, BAR_HEIGHT, 7, 7);
        gc.setLineWidth(1);
    }

    /** A breathing red wash over the remaining health, additive so it reads as a glow. */
    private void drawLowHealthGlow(double x, double y, double fraction, int tick) {
        // Held at the midpoint of the breath under reduced flash, so the wash still marks the bar
        // as critical without the oscillation.
        double pulse = settings.reducedFlash() ? 0.35 : 0.35 + 0.35 * Math.sin(tick * 0.22);
        gc.save();
        gc.setGlobalBlendMode(BlendMode.SCREEN);
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
            boolean stacked = kind == PowerUp.Kind.TRI_SHOT && player.triStacks() > 1;
            labels.add(stacked ? shortName(kind) + " x" + player.triStacks() : shortName(kind));
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
                .append("LEVEL ").append(level.number())
                .append("   ").append(level.label().toUpperCase())
                .append("   WAVE ").append(director.waveInLevel())
                .append('/').append(level.wavesBeforeBoss());
        if (director.loop() > 1) {
            heading.append("   LOOP ").append(director.loop());
        }

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(labelFont);
        gc.setFill(Tokens.LABEL);
        gc.fillText(heading.toString(), GameConfig.WIDTH / 2, 14);
    }

    /** Flashes while the flagship is arriving, so the fight does not start unannounced. */
    private void drawFlagshipWarning(int tick) {
        if (!settings.reducedFlash() && (tick / 14) % 2 == 0) {
            return;
        }
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(valueFont);
        gc.setFill(Tokens.DANGER_SOFT);
        gc.fillText("FLAGSHIP INBOUND", GameConfig.WIDTH / 2, GameConfig.HEIGHT * 0.34);
    }

    private void drawBossBar(EnemyShip boss) {
        double width = 420;
        double x = GameConfig.WIDTH / 2 - width / 2;
        double y = 54;

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(smallFont);
        gc.setFill(Tokens.DANGER_SOFT);
        gc.fillText(boss.boss().label().toUpperCase(), GameConfig.WIDTH / 2, 38);

        gc.setFill(Tokens.TRACK_DANGER);
        gc.fillRoundRect(x, y, width, 10, 5, 5);
        gc.setFill(Tokens.DANGER_BAR);
        gc.fillRoundRect(x, y, width * boss.remainingHealthFraction(), 10, 5, 5);
    }
}
