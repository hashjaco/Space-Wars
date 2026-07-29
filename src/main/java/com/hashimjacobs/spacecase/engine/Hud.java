package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.PowerUp;
import com.hashimjacobs.spacecase.mode.Level;

/** Score, lives, health and active power-ups for each player, plus the wave and boss bars. */
final class Hud {

    private static final double PANEL_WIDTH = 210;
    private static final double BAR_HEIGHT = 14;
    private static final Color BRAND = Color.web("#0ec417");

    private final GraphicsContext gc;
    private final Font labelFont = Font.font("Verdana", FontWeight.BOLD, 15);
    private final Font valueFont = Font.font("Verdana", FontWeight.BOLD, 22);
    private final Font smallFont = Font.font("Verdana", FontWeight.NORMAL, 11);

    Hud(GraphicsContext gc) {
        this.gc = gc;
    }

    void draw(World world, SpawnDirector director) {
        gc.setTextBaseline(VPos.TOP);
        List<PlayerShip> players = world.players();

        for (PlayerShip player : players) {
            boolean rightAligned = player.playerNumber() == 2;
            double x = rightAligned ? GameConfig.WIDTH - PANEL_WIDTH - 16 : 16;
            drawPlayerPanel(player, x);
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

    private void drawPlayerPanel(PlayerShip player, double x) {
        double y = 14;

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(labelFont);
        gc.setFill(Color.web("#b388ff"));
        gc.fillText("PLAYER " + player.playerNumber(), x, y);

        gc.setFont(valueFont);
        gc.setFill(Color.WHITE);
        gc.fillText(String.valueOf(player.score()), x, y + 20);

        double barY = y + 50;
        drawHealthBar(player, x, barY);

        gc.setFont(smallFont);
        gc.setFill(Color.web("#9fb0c9"));
        gc.fillText("LIVES  " + Math.max(0, player.lives()), x, barY + BAR_HEIGHT + 6);

        List<String> effects = activeEffectLabels(player);
        if (!effects.isEmpty()) {
            gc.setFill(BRAND);
            String joined = String.join("  ", effects);
            gc.fillText(joined, x, barY + BAR_HEIGHT + 22);
        }
    }

    private void drawHealthBar(PlayerShip player, double x, double y) {
        double fraction = Math.max(0, player.health()) / (double) GameConfig.PLAYER_HEALTH;

        gc.setFill(Color.web("#22283a"));
        gc.fillRoundRect(x, y, PANEL_WIDTH, BAR_HEIGHT, 7, 7);

        Color fill = fraction > 0.5 ? BRAND : fraction > 0.25 ? Color.GOLD : Color.web("#ff4d4d");
        gc.setFill(fill);
        gc.fillRoundRect(x, y, PANEL_WIDTH * fraction, BAR_HEIGHT, 7, 7);

        gc.setStroke(Color.web("#3b4560"));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x, y, PANEL_WIDTH, BAR_HEIGHT, 7, 7);
    }

    private List<String> activeEffectLabels(PlayerShip player) {
        List<String> labels = new ArrayList<>();
        for (PowerUp.Kind kind : PowerUp.Kind.values()) {
            if (!kind.timed() || !player.hasEffect(kind)) {
                continue;
            }
            int seconds = player.remainingEffectTicks(kind) / 60;
            labels.add(shortName(kind) + " " + seconds + "s");
        }
        return labels;
    }

    private String shortName(PowerUp.Kind kind) {
        String name = switch (kind) {
            case TRI_SHOT -> "TRI";
            case MEGA_LASER -> "MEGA";
            case SPEED -> "SPD";
            case SHIELD -> "SHLD";
            case HEALTH -> "HP";
            case EXTRA_LIFE -> "LIFE";
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
        gc.setFill(Color.web("#b388ff"));
        gc.fillText(heading.toString(), GameConfig.WIDTH / 2, 14);
    }

    /** Flashes while the flagship is arriving, so the fight does not start unannounced. */
    private void drawFlagshipWarning(int tick) {
        if ((tick / 14) % 2 == 0) {
            return;
        }
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(valueFont);
        gc.setFill(Color.web("#ff6b6b"));
        gc.fillText("FLAGSHIP INBOUND", GameConfig.WIDTH / 2, GameConfig.HEIGHT * 0.34);
    }

    private void drawBossBar(EnemyShip boss) {
        double width = 420;
        double x = GameConfig.WIDTH / 2 - width / 2;
        double y = 54;

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(smallFont);
        gc.setFill(Color.web("#ff6b6b"));
        gc.fillText(boss.boss().label().toUpperCase(), GameConfig.WIDTH / 2, 38);

        gc.setFill(Color.web("#2a1620"));
        gc.fillRoundRect(x, y, width, 10, 5, 5);
        gc.setFill(Color.web("#ff3b3b"));
        gc.fillRoundRect(x, y, width * boss.remainingHealthFraction(), 10, 5, 5);
    }
}
