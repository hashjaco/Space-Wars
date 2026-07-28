package com.hashimjacobs.spacecase.engine;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

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

    private final GraphicsContext gc;
    private final Hud hud;
    private double backgroundOffset;

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
            drawSprite(bullet);
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
        Image background = Assets.image(Sprite.BACKGROUND);
        backgroundOffset += GameConfig.BACKGROUND_SCROLL_SPEED;
        if (backgroundOffset >= GameConfig.HEIGHT) {
            backgroundOffset -= GameConfig.HEIGHT;
        }
        // Two copies chase each other down the screen so the starfield never shows a seam.
        gc.drawImage(background, 0, backgroundOffset - GameConfig.HEIGHT, GameConfig.WIDTH, GameConfig.HEIGHT);
        gc.drawImage(background, 0, backgroundOffset, GameConfig.WIDTH, GameConfig.HEIGHT);
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
