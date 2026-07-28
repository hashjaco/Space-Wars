package com.hashimjacobs.spacecase.engine;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Asteroid;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.Entity;
import com.hashimjacobs.spacecase.entity.Facing;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.PowerUp;

/**
 * Draws the arena onto a single full-window canvas.
 *
 * The old engine rendered the same world twice, into two canvases inside two ScrollPanes that each
 * showed half the window, even though the world was exactly one window wide.
 */
public final class Renderer {

    /** Scroll multipliers per starfield layer: distant stars drift, near ones race. */
    private static final Sprite[] BACKGROUND_LAYERS = {
            Sprite.BACKGROUND_FAR, Sprite.BACKGROUND_MID, Sprite.BACKGROUND_NEAR};
    private static final double[] LAYER_SPEEDS = {0.35, 0.75, 1.5};

    private static final Paint PLAYER_HALO = halo(Color.web("#7ce8ff"));
    private static final Paint ENEMY_HALO = halo(Color.web("#ff8a5a"));

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
    private final double[] layerOffsets = new double[BACKGROUND_LAYERS.length];

    public Renderer(GraphicsContext gc) {
        this.gc = gc;
        this.hud = new Hud(gc);
    }

    public void draw(World world, SpawnDirector director) {
        drawScrollingBackground();

        for (Asteroid asteroid : world.asteroids()) {
            drawSprite(asteroid);
        }
        for (PowerUp powerUp : world.powerUps()) {
            drawSprite(powerUp);
        }
        for (EnemyShip enemy : world.enemies()) {
            drawSprite(enemy);
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

    private void drawScrollingBackground() {
        // Fill first: the layers have transparent gaps, so without this the previous frame shows.
        gc.setFill(Color.web("#0a0e1a"));
        gc.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);

        for (int layer = 0; layer < BACKGROUND_LAYERS.length; layer++) {
            Image image = Assets.image(BACKGROUND_LAYERS[layer]);
            layerOffsets[layer] += GameConfig.BACKGROUND_SCROLL_SPEED * LAYER_SPEEDS[layer];
            if (layerOffsets[layer] >= GameConfig.HEIGHT) {
                layerOffsets[layer] -= GameConfig.HEIGHT;
            }
            // Two copies chase each other down the screen so a layer never shows a seam.
            double y = layerOffsets[layer];
            gc.drawImage(image, 0, y - GameConfig.HEIGHT, GameConfig.WIDTH, GameConfig.HEIGHT);
            gc.drawImage(image, 0, y, GameConfig.WIDTH, GameConfig.HEIGHT);
        }
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
