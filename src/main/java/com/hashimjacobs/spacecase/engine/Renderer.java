package com.hashimjacobs.spacecase.engine;

import java.util.List;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.BossArt;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Asteroid;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.Entity;
import com.hashimjacobs.spacecase.entity.Facing;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.PowerUp;
import com.hashimjacobs.spacecase.mode.Debrief;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Standing;

/**
 * Draws the arena onto a single full-window canvas.
 *
 * The old engine rendered the same world twice, into two canvases inside two ScrollPanes that each
 * showed half the window, even though the world was exactly one window wide.
 */
public final class Renderer {

    /** Scroll multipliers, matched to the layer order in {@code Level.layers()}: far, mid, near. */
    private static final double[] LAYER_SPEEDS = {0.35, 0.75, 1.5};

    private static final Paint PLAYER_HALO = halo(Color.web("#7ce8ff"));
    private static final Paint ENEMY_HALO = halo(Color.web("#ff8a5a"));

    private static final Font PILOT_NAME_FONT = Font.font("Verdana", FontWeight.BOLD, 11);

    private static Paint halo(Color core) {
        RadialGradient gradient = new RadialGradient(
                0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, core.deriveColor(0, 1, 1, 0.5)),
                new Stop(0.45, core.deriveColor(0, 1, 1, 0.22)),
                new Stop(1, Color.TRANSPARENT));
        return gradient;
    }

    private final GraphicsContext gc;
    private final Hud hud;
    private final DebriefOverlay debriefOverlay;

    public Renderer(GraphicsContext gc) {
        this.gc = gc;
        this.hud = new Hud(gc);
        this.debriefOverlay = new DebriefOverlay(gc);
    }

    public void draw(World world, SpawnDirector director) {
        drawScrollingBackground(director.level(), world.tick());

        for (Asteroid asteroid : world.asteroids()) {
            drawSprite(asteroid);
        }
        for (PowerUp powerUp : world.powerUps()) {
            drawSprite(powerUp);
        }
        for (EnemyShip enemy : world.enemies()) {
            if (enemy.isBoss()) {
                drawBoss(enemy, world.tick());
            } else {
                drawSprite(enemy);
            }
        }
        for (Bullet bullet : world.bullets()) {
            drawBullet(bullet);
        }
        for (PlayerShip player : world.players()) {
            drawPlayer(player, world.tick());
        }
        for (ActiveExplosion explosion : world.explosions()) {
            Image frame = explosion.currentFrame();
            gc.drawImage(frame, explosion.drawX(), explosion.drawY(), explosion.size(), explosion.size());
        }

        hud.draw(world, director);
    }

    /** The between-levels report, over a frozen world. */
    public void drawDebrief(Level level, List<Debrief> debriefs, List<Standing> standings,
                            boolean armed) {
        debriefOverlay.draw(level, debriefs, standings, armed);
    }

    /** Blackout during the warp, at the given 0..1 opacity. */
    public void drawWarpVeil(double opacity) {
        if (opacity <= 0) {
            return;
        }
        gc.setFill(Color.color(0, 0, 0, Math.min(1, opacity)));
        gc.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);
    }

    /**
     * The current level's parallax starfield.
     *
     * The offsets are a function of the simulation tick rather than something accumulated per draw,
     * because draws happen once per display refresh while the simulation steps at a fixed rate --
     * accumulating here scrolled the sky twice as fast on a 120 Hz monitor. Flooring the result also
     * keeps each layer on whole pixels, which stops JavaFX interpolating a faint seam into the wrap.
     */
    private void drawScrollingBackground(Level level, int tick) {
        // Fill first: the layers have transparent gaps, so without this the previous frame shows.
        gc.setFill(Color.web("#0a0e1a"));
        gc.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);

        List<Sprite> layers = level.layers();
        for (int layer = 0; layer < layers.size(); layer++) {
            Image image = Assets.image(layers.get(layer));
            double scrolled = tick * GameConfig.BACKGROUND_SCROLL_SPEED * LAYER_SPEEDS[layer];
            double y = Math.floor(scrolled % GameConfig.HEIGHT);
            // Two copies chase each other down the screen so a layer never shows a seam.
            gc.drawImage(image, 0, y - GameConfig.HEIGHT, GameConfig.WIDTH, GameConfig.HEIGHT);
            gc.drawImage(image, 0, y, GameConfig.WIDTH, GameConfig.HEIGHT);
        }
    }

    /**
     * Bosses run an idle animation, so they are the one entity whose image comes from a frame
     * sequence rather than from {@code entity.sprite()}. Driving the frame off the world tick rather
     * than off a counter on the ship keeps every boss on screen in step and needs no per-entity state.
     */
    private void drawBoss(EnemyShip boss, int tick) {
        BossArt art = boss.boss().art();
        List<Image> frames = Assets.bossFrames(art);
        Image frame = frames.get(art.frameIndexAt(tick));
        gc.drawImage(frame, boss.x(), boss.y(), boss.width(), boss.height());
    }

    private void drawPlayer(PlayerShip player, int tick) {
        if (player.isOut()) {
            return;
        }
        // Blink while the respawn grace period is running.
        boolean blinkedOut = player.isInvulnerable() && (tick / 6) % 2 == 0;
        if (blinkedOut) {
            return;
        }
        if (player.hasEffect(PowerUp.Kind.SHIELD)) {
            Image aura = Assets.image(Sprite.SHIELD_AURA);
            double size = Math.max(player.width(), player.height()) * 1.5;
            // The shield art is near-opaque, so fade it to keep the ship underneath readable.
            gc.setGlobalAlpha(0.45);
            gc.drawImage(aura, player.centerX() - size / 2, player.centerY() - size / 2, size, size);
            gc.setGlobalAlpha(1.0);
        }
        drawSprite(player, player.facing());
        drawPilotName(player);
    }

    /**
     * The pilot's name under the hull.
     *
     * Below the ship whichever way it faces, so a battle-mode player two reads it the same way up as
     * player one, and drawn after the sprite so the hull never covers it.
     */
    private void drawPilotName(PlayerShip player) {
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.TOP);
        gc.setFont(PILOT_NAME_FONT);
        gc.setFill(Color.web("#9fb0c9"));
        gc.fillText(player.name(), player.centerX(), player.y() + player.height() + 3);
    }

    /**
     * Bullets get a soft halo behind them. The projectile art is the author's own and did not need
     * redrawing; a glow reads better than new sprites would.
     *
     * The gradient matters: a flat oval under a screen blend paints a solid disc that swallows the
     * sprite instead of haloing it.
     */
    private void drawBullet(Bullet bullet) {
        Paint halo = bullet.firedByPlayer() ? PLAYER_HALO : ENEMY_HALO;
        double size = Math.max(bullet.width(), bullet.height()) * 1.35;

        gc.save();
        gc.setGlobalBlendMode(BlendMode.SCREEN);
        gc.setFill(halo);
        gc.fillOval(bullet.centerX() - size / 2, bullet.centerY() - size / 2, size, size);
        gc.restore();

        drawSprite(bullet);
    }

    private void drawSprite(Entity entity) {
        drawSprite(entity, Facing.UP);
    }

    private void drawSprite(Entity entity, Facing facing) {
        Image image = Assets.image(entity.sprite());
        if (facing == Facing.UP) {
            gc.drawImage(image, entity.x(), entity.y(), entity.width(), entity.height());
            return;
        }
        // Rotate about the sprite's centre so a downward-facing ship points at its opponent.
        gc.save();
        gc.translate(entity.centerX(), entity.centerY());
        gc.rotate(facing.rotationDegrees());
        gc.drawImage(image, -entity.width() / 2, -entity.height() / 2, entity.width(), entity.height());
        gc.restore();
    }

    public void drawPausedVeil() {
        gc.setFill(Color.color(0, 0, 0, 0.55));
        gc.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);
    }
}
