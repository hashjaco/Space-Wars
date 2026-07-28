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

        if (world.rules().spawnEnemies()) {
            drawWave(director.wave());
        }
        EnemyShip boss = world.boss();
        if (boss != null) {
            drawBossBar(boss);
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

    private void drawWave(int wave) {
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(labelFont);
        gc.setFill(Color.web("#7f8ca6"));
        gc.fillText("WAVE " + wave, GameConfig.WIDTH / 2, 14);
    }

    private void drawBossBar(EnemyShip boss) {
        double width = 420;
        double x = GameConfig.WIDTH / 2 - width / 2;
        double y = 54;

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(smallFont);
        gc.setFill(Color.web("#ff6b6b"));
        gc.fillText("BOSS", GameConfig.WIDTH / 2, 38);

        gc.setFill(Color.web("#2a1620"));
        gc.fillRoundRect(x, y, width, 10, 5, 5);
        gc.setFill(Color.web("#ff3b3b"));
        gc.fillRoundRect(x, y, width * boss.remainingHealthFraction(), 10, 5, 5);
    }
}
