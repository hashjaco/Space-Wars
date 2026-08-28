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
import com.hashimjacobs.spacecase.prefs.Settings;
import com.hashimjacobs.spacecase.prefs.Standing;
import com.hashimjacobs.spacecase.ui.Tokens;

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

    /** The mega laser's glow, and the two hotter cores drawn inside it. */
    private static final Paint BEAM_HALO = halo(Color.web("#ff2a2a"));
    private static final Color BEAM_OUTER = Color.web("#ff2a2a");
    private static final Color BEAM_INNER = Color.web("#ff8a6a");

    /** A soft wash behind a floating pickup, so it reads as an object with power in it. */
    private static final Paint PICKUP_HALO = halo(Color.web("#ffe9a8"));

    /** Hydra necks: a dark edge under a hide-coloured core, matching the generated torso. */
    private static final Color NECK_OUTLINE = Color.web("#131c10");
    private static final Color NECK_HIDE = Color.web("#24361f");

    private static final Font PILOT_NAME_FONT = Font.font(Tokens.BODY, FontWeight.BOLD, Tokens.SIZE_CAPTION);

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

    /**
     * The corner falloff, built once because it never changes.
     *
     * Clear across the middle two thirds -- nothing that matters to a fight may be dimmed -- then
     * down to a little over half black in the corners, which is where the arena has nothing in it
     * anyway. Proportional, so it stretches to the arena's shape rather than staying circular.
     */
    private static final Paint VIGNETTE = new RadialGradient(
            0, 0, 0.5, 0.5, 0.78, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.TRANSPARENT),
            new Stop(0.62, Color.TRANSPARENT),
            new Stop(1, Color.web("#05070c", 0.55)));

    /** The falloff itself, so {@code tools/preview} can draw the real one rather than a copy. */
    static Paint vignettePaint() {
        return VIGNETTE;
    }

    /** How far past the canvas the sky is painted, so a camera throw never runs off the paint. */
    private static final double SHAKE_MARGIN = 16;

    private final GraphicsContext gc;
    private final Settings settings;
    private final Hud hud;
    private final DebriefOverlay debriefOverlay;
    private final GarageOverlay garageOverlay;

    public Renderer(GraphicsContext gc, Settings settings) {
        this.gc = gc;
        this.settings = settings;
        this.hud = new Hud(gc, settings);
        this.debriefOverlay = new DebriefOverlay(gc);
        this.garageOverlay = new GarageOverlay(gc);
    }

    public void draw(World world, SpawnDirector director) {
        // The arena is thrown about; the HUD is not, so it stays readable through a hit. Popped
        // before hud.draw and therefore before the debrief, garage and warp veil the game loop
        // draws after this method returns.
        double throwDistance = settings.reducedFlash() ? 0 : world.shakeRemaining();
        gc.save();
        if (throwDistance > 0) {
            int tick = world.tick();
            gc.translate(throwDistance * Math.sin(tick * 2.7), throwDistance * Math.cos(tick * 3.9));
        }
        drawScrollingBackground(director.level(), world.tick());
        // After the sky and before everything solid, so rock sits behind the fight but in front of
        // the parallax. Inside the shake, so it is thrown with the arena rather than against it.
        drawTerrain(world);

        for (Asteroid asteroid : world.asteroids()) {
            drawSprite(asteroid);
        }
        for (PowerUp powerUp : world.powerUps()) {
            drawPowerUp(powerUp);
        }
        for (EnemyShip enemy : world.enemies()) {
            if (enemy.isBoss()) {
                drawBoss(enemy, world.tick());
            } else {
                drawSprite(enemy);
            }
            drawHitBar(enemy);
        }
        for (Bullet bullet : world.bullets()) {
            drawBullet(bullet);
        }
        // Between the bullets and the hulls, so a ship is drawn over its own muzzle rather than
        // sitting behind the beam it is firing.
        for (PlayerShip player : world.players()) {
            drawBeam(player, world.tick());
        }
        for (PlayerShip player : world.players()) {
            drawPlayer(player, world.tick());
        }
        for (ActiveExplosion explosion : world.explosions()) {
            Image frame = explosion.currentFrame();
            gc.drawImage(frame, explosion.drawX(), explosion.drawY(), explosion.size(), explosion.size());
        }
        gc.restore();

        drawVignette();
        hud.draw(world, director);
    }

    /**
     * Darkens the corners, so the arena reads as lit rather than as a flat rectangle.
     *
     * Outside the shake and before the HUD: it belongs to the screen, not to the world. Thrown
     * with the arena it would slide its dark corners across the play area on every hit, and baked
     * into a backdrop it would scroll with that layer -- which is also the rule that an occluding
     * backdrop owns every parallax layer, and this owns none of them.
     *
     * Off with reducedFlash, which already governs everything else that dims or pulses the arena.
     */
    private void drawVignette() {
        if (settings.reducedFlash()) {
            return;
        }
        gc.setFill(VIGNETTE);
        gc.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);
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
        gc.setFill(worstMs <= FRAME_BUDGET_MS ? Tokens.BRAND : Tokens.DANGER);
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
        gc.setFill(Tokens.veil(Math.min(1, opacity)));
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
    /**
     * The tunnel walls: two filled shapes, one per side.
     *
     * The vertices are the terrain's own lattice nodes, which is the whole reason this reads
     * honestly -- the outline drawn here <em>is</em> the surface collision resolves against, so
     * there is no "looks like I cleared it but took damage anyway". If {@link Terrain#PITCH} is ever
     * read in only one of the two places, that guarantee quietly goes away.
     *
     * Opaque fill, because everything else at this depth is a dim parallax layer and the rock has to
     * read as solid at a glance. The lighter inner stroke is the important part: it is the edge the
     * player is actually flying against, so it gets the contrast.
     */
    private void drawTerrain(World world) {
        Terrain terrain = world.terrain();
        if (terrain.isEmpty()) {
            return;
        }
        Orientation facing = terrain.orientation();
        Color rock = Color.web(terrain.template().rockHex());
        Color edge = Color.web(terrain.template().edgeHex());

        // Overrun the arena on all sides so a thrown camera never shows daylight past the rock,
        // the same reason the sky fill above is drawn oversized.
        double margin = 24;
        double depthTo = facing.arenaDepth() + margin;
        double breadth = facing.arenaBreadth();
        int steps = (int) Math.ceil((depthTo + margin) / Terrain.PITCH) + 1;

        for (int side = -1; side <= 1; side += 2) {
            double[] xs = new double[steps + 2];
            double[] ys = new double[steps + 2];
            for (int i = 0; i < steps; i++) {
                double depth = -margin + i * Terrain.PITCH;
                double across = side < 0 ? terrain.laneLow(depth) : terrain.laneHigh(depth);
                xs[i] = facing.atX(depth, across, 0, 0);
                ys[i] = facing.atY(depth, across, 0, 0);
            }
            // Close the shape off along the outer edge of the arena.
            double outer = side < 0 ? -margin : breadth + margin;
            xs[steps] = facing.atX(depthTo, outer, 0, 0);
            ys[steps] = facing.atY(depthTo, outer, 0, 0);
            xs[steps + 1] = facing.atX(-margin, outer, 0, 0);
            ys[steps + 1] = facing.atY(-margin, outer, 0, 0);

            gc.setFill(rock);
            gc.fillPolygon(xs, ys, steps + 2);
            gc.setStroke(edge);
            gc.setLineWidth(3);
            gc.strokePolyline(xs, ys, steps);
        }
        gc.setLineWidth(1);
    }

    private void drawScrollingBackground(Level level, int tick) {
        // Fill first: the layers have transparent gaps, so without this the previous frame shows.
        // Overdrawn by the shake margin, because this is a fill rather than a clear -- a thrown
        // camera would otherwise drag a band of last frame's pixels in at two edges.
        gc.setFill(Tokens.SPACE);
        gc.fillRect(-SHAKE_MARGIN, -SHAKE_MARGIN,
                GameConfig.WIDTH + 2 * SHAKE_MARGIN, GameConfig.HEIGHT + 2 * SHAKE_MARGIN);

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
        Sprite segment = worm.segmentSprite();
        Image ring = Assets.image(segment);
        double widest = segment.width();
        for (int k = BurrowingWorm.SEGMENTS; k >= 1; k--) {
            // Tapering toward the tail, but never so far that the last rings read as pebbles.
            double size = widest * (1 - 0.055 * k);
            gc.drawImage(ring, worm.trailX(k) - size / 2, worm.trailY(k) - size / 2, size, size);
        }
    }

    /**
     * The mega laser: a red column from the nose to the far wall.
     *
     * Three rectangles under SCREEN rather than one -- wide and dim, narrower and warmer, then a
     * white-hot core -- because that is what makes a flat fill read as something burning. The
     * geometry is {@code PlayerShip.beamBox}, the same box the collision pass burns things in, so
     * what is drawn and what is lethal cannot drift apart.
     *
     * The width breathes on the tick, which is the one flicker here; under reduced flash it is
     * held at the middle of that breath, like {@code Hud.drawLowHealthGlow}.
     */
    private void drawBeam(PlayerShip player, int tick) {
        if (player.isOut() || !player.isFiringBeam()) {
            return;
        }
        double[] beam = player.beamBox();
        double pulse = settings.reducedFlash() ? 1 : 1 + 0.12 * Math.sin(tick * 0.9);
        // Off the facing, not off which side of the box is longer: a ship pressed against the far
        // wall fires a beam shorter than it is wide, and that must not flip the layout.
        boolean vertical = !player.facing().horizontal();

        gc.save();
        gc.setGlobalBlendMode(BlendMode.SCREEN);
        drawBeamCore(beam, vertical, 1.9 * pulse, BEAM_OUTER, 0.5);
        drawBeamCore(beam, vertical, 1.0 * pulse, BEAM_INNER, 0.75);
        drawBeamCore(beam, vertical, 0.35 * pulse, Color.WHITE, 0.95);
        // A bloom at the muzzle end, where the energy is leaving the hull.
        double bloom = GameConfig.BEAM_WIDTH * 3.4;
        gc.setGlobalAlpha(1);
        gc.setFill(BEAM_HALO);
        gc.fillOval(player.centerX() - bloom / 2, player.centerY() - bloom / 2, bloom, bloom);
        // And one where it lands, when it lands on something. This is the whole point of the beam
        // ending at a hull rather than at the wall -- without it the shortened column reads as the
        // beam having failed rather than as it burning through something.
        if (player.beamStopped()) {
            double impact = bloom * (settings.reducedFlash() ? 1.15 : 1.15 + 0.2 * Math.sin(tick * 0.6));
            double[] end = beamImpactPoint(player, beam, vertical);
            gc.fillOval(end[0] - impact / 2, end[1] - impact / 2, impact, impact);
            gc.setGlobalAlpha(0.85);
            gc.setFill(Color.WHITE);
            double core = GameConfig.BEAM_WIDTH * 0.8;
            gc.fillOval(end[0] - core / 2, end[1] - core / 2, core, core);
        }
        gc.restore();
    }

    /**
     * Where a shortened beam ends: the far end of its box, on whichever side the ship is not.
     *
     * Off {@code beamBox} rather than off the thing that stopped the beam, so the bloom sits exactly
     * where the burn does even if the target moved between the collision pass and this one.
     */
    private static double[] beamImpactPoint(PlayerShip player, double[] beam, boolean vertical) {
        if (vertical) {
            double y = player.facing().yDirection() < 0 ? beam[1] : beam[1] + beam[3];
            return new double[] {beam[0] + beam[2] / 2, y};
        }
        double x = player.facing().xDirection() < 0 ? beam[0] : beam[0] + beam[2];
        return new double[] {x, beam[1] + beam[3] / 2};
    }

    /** One layer of the beam, scaled about its own centre line so all three stay concentric. */
    private void drawBeamCore(double[] beam, boolean vertical, double scale, Color colour,
                              double alpha) {
        double across = (vertical ? beam[2] : beam[3]) * scale;
        gc.setGlobalAlpha(alpha);
        gc.setFill(colour);
        if (vertical) {
            gc.fillRect(beam[0] + beam[2] / 2 - across / 2, beam[1], across, beam[3]);
        } else {
            gc.fillRect(beam[0], beam[1] + beam[3] / 2 - across / 2, beam[2], across);
        }
    }

    /** A pickup, with a soft wash behind it so it reads as powered rather than as flat clip-art. */
    private void drawPowerUp(PowerUp powerUp) {
        double glow = Math.max(powerUp.width(), powerUp.height()) * 2.1;
        gc.save();
        gc.setGlobalBlendMode(BlendMode.SCREEN);
        gc.setFill(PICKUP_HALO);
        gc.fillOval(powerUp.centerX() - glow / 2, powerUp.centerY() - glow / 2, glow, glow);
        gc.restore();
        drawSprite(powerUp);
    }

    private void drawPlayer(PlayerShip player, int tick) {
        if (player.isOut()) {
            return;
        }
        // Blink while the respawn grace period is running. Under reduced flash the same window is
        // shown as a steady fade instead: the grace period is the only thing telling a player they
        // are briefly untouchable, so the signal has to survive even when the strobe does not.
        boolean fadeInstead = settings.reducedFlash();
        boolean blinkedOut = player.isInvulnerable() && !fadeInstead && (tick / 6) % 2 == 0;
        if (blinkedOut) {
            return;
        }
        boolean faded = player.isInvulnerable() && fadeInstead;
        if (player.hasEffect(PowerUp.Kind.SHIELD)) {
            Image aura = Assets.image(Sprite.SHIELD_AURA);
            double size = Math.max(player.width(), player.height()) * 1.5;
            // The shield art is near-opaque, so fade it to keep the ship underneath readable.
            gc.setGlobalAlpha(0.45);
            gc.drawImage(aura, player.centerX() - size / 2, player.centerY() - size / 2, size, size);
            gc.setGlobalAlpha(1.0);
            drawShieldBar(player);
        }
        // Under the hull rather than over it, so the flash frames the ship instead of hiding it.
        // Only the halo is gated: hitFlashTicks itself is the ram-damage grace window that
        // CollisionSystem reads, so the timer keeps running whatever this setting says.
        if (player.justHit() && !settings.reducedFlash()) {
            double flash = Math.max(player.width(), player.height()) * 1.6;
            gc.save();
            gc.setGlobalBlendMode(BlendMode.SCREEN);
            gc.setFill(HIT_HALO);
            gc.fillOval(player.centerX() - flash / 2, player.centerY() - flash / 2, flash, flash);
            gc.restore();
        }
        // After the aura, which sets its own alpha and puts it back.
        if (faded) {
            gc.setGlobalAlpha(0.45);
        }
        double hullDegrees = hullDegrees(player.facing());
        drawSprite(player.sprite(), player, hullDegrees);
        Sprite kit = player.kitOverlay();
        if (kit != null) {
            // Drawn at the hull's own size and rotation so the decal tracks the pose and, in
            // battle mode, turns with a player two who is facing the other way.
            drawSprite(kit, player, hullDegrees);
        }
        if (faded) {
            gc.setGlobalAlpha(1.0);
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
        gc.setFill(Tokens.TEXT_SECONDARY);
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

        drawSprite(bullet.sprite(), bullet, bullet.headingDegrees());
    }

    private void drawSprite(Entity entity) {
        drawSprite(entity.sprite(), entity, 0);
    }

    /**
     * How far a hull has to turn on screen, given the way its pilot faces.
     *
     * The horizontal pair need no quarter turn: those levels are flown on hulls the generator
     * already cut pointing right, which is what lets the collision box turn with the art. Only the
     * far seat -- battle mode's player two, at either end of either axis -- is turned about.
     */
    private static double hullDegrees(Facing facing) {
        if (!facing.horizontal()) {
            return facing.rotationDegrees();
        }
        return facing == Facing.RIGHT ? 0 : 180;
    }

    /**
     * Draws any sprite at an entity's box, so a hull and its kit decal share one transform.
     *
     * Degrees rather than a {@link Facing} because a projectile's heading is not one of the four:
     * see {@code Bullet.headingDegrees()}.
     */
    private void drawSprite(Sprite sprite, Entity entity, double degrees) {
        Image image = Assets.image(sprite);
        if (degrees == 0) {
            gc.drawImage(image, entity.x(), entity.y(), entity.width(), entity.height());
            return;
        }
        // Rotate about the sprite's centre so a downward-facing ship points at its opponent.
        gc.save();
        gc.translate(entity.centerX(), entity.centerY());
        gc.rotate(degrees);
        gc.drawImage(image, -entity.width() / 2, -entity.height() / 2, entity.width(), entity.height());
        gc.restore();
    }

    /**
     * How much more the shield will deflect, above the ship it is protecting.
     *
     * Over the arena rather than in the HUD panel because the number matters at the moment
     * something is about to hit you, and that is not where your eyes are. No pulse and no flash:
     * it is a quantity, not an alarm.
     */
    /**
     * How much is left in an enemy that was shot in the last three seconds.
     *
     * Over the hull rather than in the HUD, because the question it answers is "is this one nearly
     * dead", and that is asked while looking at the ship rather than at the panel. The same green,
     * gold and red as the player's own bar, so a bar means one thing wherever it appears.
     *
     * That is also what keeps this from reading as a second copy of the HUD's boss bar. This one
     * always describes the box underneath it; that one always describes the fight. On a multi-part
     * flagship the difference is the whole point -- the heads drain their own bars while the torso
     * sits at a full one that will not move, which teaches the guard rather than duplicating it.
     *
     * Not gated on reduced flash, and that is deliberate. Nothing here oscillates: it appears, it
     * holds, and it fades once in one direction. Every existing reduced-flash site holds an
     * animation still rather than deleting a signal, and gating this would delete the whole feature
     * for anyone who has the setting on.
     */
    private void drawHitBar(EnemyShip enemy) {
        double alpha = enemy.hitBarAlpha();
        if (alpha <= 0) {
            return;
        }
        // Capped so a 210-pixel flagship does not grow a bar half the width of the HUD's, floored
        // so a 64-pixel hydra head still gets one that can be read.
        double width = Math.min(96, Math.max(36, enemy.width()));
        double x = enemy.centerX() - width / 2;
        // Screen-horizontal above the hull in every level. Enemy hulls are drawn at zero degrees
        // even in the side-on levels, so a bar turned to the arena axis would be the only rotated
        // thing on screen -- and this is a readout, which is read the way the HUD is read.
        double y = enemy.y() - 12;
        double fraction = enemy.hullFraction();

        // ponytail: no anti-overlap pass. Two ships stacked and both shot inside the same three
        // seconds will stack their bars. If it ever shows, offset y by the enemy's list index.
        gc.setGlobalAlpha(alpha);
        gc.setFill(Tokens.TRACK);
        gc.fillRoundRect(x, y, width, 5, 3, 3);
        gc.setFill(fraction > 0.5 ? Tokens.BRAND : fraction > 0.25 ? Tokens.WARN : Tokens.DANGER_LOW);
        gc.fillRoundRect(x, y, width * fraction, 5, 3, 3);
        gc.setGlobalAlpha(1);
    }

    private void drawShieldBar(PlayerShip player) {
        double width = Math.max(player.width(), 36);
        double x = player.centerX() - width / 2;
        double y = player.centerY() - player.height() / 2 - 12;
        double fraction = Math.min(1, player.shieldRemaining() / (double) GameConfig.SHIELD_CAPACITY);

        gc.setFill(Color.web("#0d1626", 0.75));
        gc.fillRoundRect(x, y, width, 5, 3, 3);
        gc.setFill(Color.web("#9fd0ff"));
        gc.fillRoundRect(x, y, width * fraction, 5, 3, 3);
    }

    public void drawPausedVeil() {
        gc.setFill(Tokens.veil(0.55));
        gc.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);
    }
}
