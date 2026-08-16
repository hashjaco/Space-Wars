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
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.BossArt;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Asteroid;
import com.hashimjacobs.spacecase.entity.BossHead;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.BurrowingWorm;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.Entity;
import com.hashimjacobs.spacecase.entity.Facing;
import com.hashimjacobs.spacecase.entity.Orientation;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.PowerUp;
import com.hashimjacobs.spacecase.garage.GarageSession;
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

    /** Burst of red around a ship that just took damage; pairs with the scorched hull frames. */
    private static final Paint HIT_HALO = halo(Color.web("#ff3b3b"));

    /**
     * Acid gets its own glow, in the same murky yellow-green as the blob.
     *
     * Not the bright green of the ordinary bolt's halo -- the whole point of the separate art is
     * that the two must not read as the same projectile.
     */
    private static final Paint ACID_HALO = halo(Color.web("#a8b81e"));

    /** Hydra necks: a dark edge under a hide-coloured core, matching the generated torso. */
    private static final Color NECK_OUTLINE = Color.web("#131c10");
    private static final Color NECK_HIDE = Color.web("#24361f");

    private static final Font PILOT_NAME_FONT = Font.font("Verdana", FontWeight.BOLD, 11);

    /** One frame at 60Hz. Above this the readout goes red. */
    private static final double FRAME_BUDGET_MS = 1000.0 / 60;

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
    private final GarageOverlay garageOverlay;

    public Renderer(GraphicsContext gc) {
        this.gc = gc;
        this.hud = new Hud(gc);
        this.debriefOverlay = new DebriefOverlay(gc);
        this.garageOverlay = new GarageOverlay(gc);
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

    /**
     * Worst frame of the last second, in milliseconds, with the step count that came with it.
     *
     * Toggled with F3 and drawn last so it sits over every overlay. Green while the worst frame
     * still fits in a 60Hz budget, red once it does not -- the point is to be readable at a glance
     * mid-fight, not to be precise.
     */
    public void drawFrameMeter(double worstMs, int steps) {
        gc.setFont(PILOT_NAME_FONT);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.BOTTOM);
        gc.setFill(worstMs <= FRAME_BUDGET_MS ? Color.web("#0ec417") : Color.web("#ff2b2b"));
        gc.fillText(String.format("worst %.1f ms  x%d", worstMs, steps), 16, GameConfig.HEIGHT - 12);
    }

    /** The between-levels report, over a frozen world. */
    public void drawDebrief(Level level, List<Debrief> debriefs, List<Standing> standings,
                            boolean armed) {
        debriefOverlay.draw(level, debriefs, standings, armed);
    }

    /** The between-levels garage, over a frozen world. */
    public void drawGarage(GarageSession session, int tick) {
        garageOverlay.draw(session, tick);
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

        Orientation facing = level.orientation();
        // The trailing copy sits one arena back along whichever way the level runs.
        double backX = facing.vx(facing.arenaDepth(), 0);
        double backY = facing.vy(facing.arenaDepth(), 0);

        List<Sprite> layers = level.layers();
        for (int layer = 0; layer < layers.size(); layer++) {
            Image image = Assets.image(layers.get(layer));
            double scrolled = tick * GameConfig.BACKGROUND_SCROLL_SPEED * LAYER_SPEEDS[layer];
            double travelled = Math.floor(scrolled % facing.arenaDepth());
            double ox = facing.vx(travelled, 0);
            double oy = facing.vy(travelled, 0);
            // Two copies chase each other across the screen so a layer never shows a seam.
            gc.drawImage(image, ox - backX, oy - backY, GameConfig.WIDTH, GameConfig.HEIGHT);
            gc.drawImage(image, ox, oy, GameConfig.WIDTH, GameConfig.HEIGHT);
        }
    }

    /**
     * Bosses run an idle animation, so they are the one entity whose image comes from a frame
     * sequence rather than from {@code entity.sprite()}. Driving the frame off the world tick rather
     * than off a counter on the ship keeps every boss on screen in step and needs no per-entity state.
     */
    private void drawBoss(EnemyShip boss, int tick) {
        if (boss instanceof BossHead head) {
            // Under the head, so the neck disappears behind the skull rather than crossing it.
            drawNeck(head);
        } else if (boss instanceof BurrowingWorm worm) {
            drawWormBody(worm);
        }
        BossArt art = boss.bossArt();
        List<Image> frames = Assets.bossFrames(art);
        Image frame = frames.get(art.frameIndexAt(tick));
        gc.drawImage(frame, boss.x(), boss.y(), boss.width(), boss.height());
    }

    /**
     * A neck, as two stroked passes of one curve: dark outline, then flesh over it.
     *
     * Drawn live rather than baked into the boss frames, so it follows the head at sixty steps a
     * second instead of the eight frames the torso animates through. The control point is pushed
     * out sideways from the midpoint so the neck bows instead of reading as a stick.
     *
     * ponytail: no taper. If it looks like plumbing, stroke it as four segments of falling width.
     */
    private void drawNeck(BossHead head) {
        double rootX = head.neckRootX();
        double rootY = head.neckRootY();
        double tipX = head.centerX();
        double tipY = head.centerY();
        double controlX = (rootX + tipX) / 2 + (tipY - rootY) * 0.18;
        double controlY = (rootY + tipY) / 2 + (rootX - tipX) * 0.18;

        gc.save();
        gc.setLineCap(StrokeLineCap.ROUND);
        for (int pass = 0; pass < 2; pass++) {
            gc.setStroke(pass == 0 ? NECK_OUTLINE : NECK_HIDE);
            gc.setLineWidth(pass == 0 ? 30 : 22);
            gc.beginPath();
            gc.moveTo(rootX, rootY);
            gc.quadraticCurveTo(controlX, controlY, tipX, tipY);
            gc.stroke();
        }
        gc.restore();
    }

    /**
     * The worm's body, trailing back into its burrow.
     *
     * Furthest ring first so each overlaps the one behind it, and tapering toward the tail. The
     * positions are a pure function of the worm's age, so none of this is simulated or stored.
     */
    private void drawWormBody(BurrowingWorm worm) {
        Image ring = Assets.image(Sprite.WORM_SEGMENT);
        double widest = Sprite.WORM_SEGMENT.width();
        for (int k = BurrowingWorm.SEGMENTS; k >= 1; k--) {
            // Tapering toward the tail, but never so far that the last rings read as pebbles.
            double size = widest * (1 - 0.055 * k);
            gc.drawImage(ring, worm.trailX(k) - size / 2, worm.trailY(k) - size / 2, size, size);
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
        // Under the hull rather than over it, so the flash frames the ship instead of hiding it.
        if (player.justHit()) {
            double flash = Math.max(player.width(), player.height()) * 1.6;
            gc.save();
            gc.setGlobalBlendMode(BlendMode.SCREEN);
            gc.setFill(HIT_HALO);
            gc.fillOval(player.centerX() - flash / 2, player.centerY() - flash / 2, flash, flash);
            gc.restore();
        }
        drawSprite(player, player.facing());
        Sprite kit = player.kitOverlay();
        if (kit != null) {
            // Drawn at the hull's own size and rotation so the decal tracks the pose and, in
            // battle mode, turns with a player two who is facing the other way.
            drawSprite(kit, player, player.facing());
        }
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
        // ponytail: keyed off the sprite. If a third hostile projectile colour turns up, put the
        // halo on Sprite itself rather than growing this chain.
        Paint halo = bullet.firedByPlayer() ? PLAYER_HALO
                : bullet.sprite() == Sprite.ACID_BALL ? ACID_HALO
                : ENEMY_HALO;
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
        drawSprite(entity.sprite(), entity, facing);
    }

    /** Draws any sprite at an entity's box, so a hull and its kit decal share one transform. */
    private void drawSprite(Sprite sprite, Entity entity, Facing facing) {
        Image image = Assets.image(sprite);
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
