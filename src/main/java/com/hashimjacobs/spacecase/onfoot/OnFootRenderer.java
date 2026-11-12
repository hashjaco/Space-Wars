package com.hashimjacobs.spacecase.onfoot;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.ChassisArt;
import com.hashimjacobs.spacecase.garage.Livery;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.engine.Renderer;
import com.hashimjacobs.spacecase.prefs.Settings;
import com.hashimjacobs.spacecase.ui.Tokens;
import com.hashimjacobs.spacecase.ui.CanvasFx;

/**
 * Draws the on-foot stage: a lit, layered undercity with the fight in it.
 *
 * Depth comes from three planes at different parallax -- the painted cavern, the floor the fight
 * stands on, and chains hung in front of the camera -- with furnace light
 * pooled additively on top. The screen-space finish (vignette, grain) is {@link Renderer}'s, so the
 * two modes share one look.
 *
 * Character art is Sprixen moveset frames under {@code /sprites/onfoot/<who>/<move>/<n>.png}, found by
 * probing for the next number rather than by a table. A character with only a still is puppeted from
 * it (the Foreman, for now), and one with nothing is drawn as a silhouette.
 */
public final class OnFootRenderer {

    private static final Color TEAL = Color.web("#5ff2ff");
    private static final Color VIOLET = Color.web("#c08bff");
    private static final Color MOLTEN = Color.web("#ff7a2a");
    private static final Color FURNACE = Color.web("#ff8c3b");
    private static final Color KEY_COLD = Color.web("#64b2ff");
    private static final Paint FURNACE_POOL = Renderer.halo(FURNACE);
    private static final Paint KEY_HALO = Renderer.halo(VIOLET);
    private static final Paint TEAL_HALO = Renderer.halo(TEAL);
    private static final Paint MOLTEN_HALO = Renderer.halo(MOLTEN);
    /** Second seat: same pilot, re-dyed, so two players are told apart at a glance and art costs nothing. */
    private static final ColorAdjust SECOND_SEAT = new ColorAdjust(-0.55, 0.1, 0, 0);
    /**
     * A frame's hit flash: the same shape in white, laid over the frame. Baked once per frame rather
     * than a ColorAdjust under ADD, which flashed the frame's whole rectangle, not the body.
     */
    private static final Map<Image, Image> WHITE = new java.util.IdentityHashMap<>();

    private static Image whiteOf(Image frame) {
        return WHITE.computeIfAbsent(frame, f -> {
            int w = (int) f.getWidth(), h = (int) f.getHeight();
            javafx.scene.image.WritableImage white = new javafx.scene.image.WritableImage(w, h);
            javafx.scene.image.PixelReader in = f.getPixelReader();
            javafx.scene.image.PixelWriter out = white.getPixelWriter();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int alpha = in.getArgb(x, y) >>> 24;
                    out.setArgb(x, y, alpha == 0 ? 0 : alpha << 24 | 0x00ffffff);
                }
            }
            return white;
        });
    }
    private static final Font TITLE = Font.font(Tokens.BODY, FontWeight.BOLD, 46);
    private static final Font CAPTION = Font.font(Tokens.BODY, FontWeight.BOLD, Tokens.SIZE_LABEL);
    private static final double FLOOR = OnFootStage.FLOOR;

    /** On-screen height of a character's frame relative to its body height; frames include reach and air. */
    private static final double PILOT_ART = 1.102, THRALL_ART = 1.434, FOREMAN_ART = 1.141,
            RIVETEER_ART = 1.008, BRUTE_ART = 1.033,  // canvas / standing body, printed by onfoot.py import
            WARDEN_ART = 1.477, MATRIARCH_ART = 1.138, TYRANT_ART = 1.122, REVENANT_ART = 1.326;

    private static final Map<String, List<Image>> FRAMES = new HashMap<>();
    private static final Map<String, Image> STILLS = new HashMap<>();

    private final GraphicsContext gc;
    private final Settings settings;

    public OnFootRenderer(GraphicsContext gc, Settings settings) {
        this.gc = gc;
        this.settings = settings;
    }

    /** The stage being drawn; set once per {@link #draw}. */
    private Mission mission = Mission.UNDERCITY;

    /** The mission's hue over art it borrowed from the Undercity, or null where none is needed. */
    private ColorAdjust tint() {
        return mission.hue() == 0 ? null : new ColorAdjust(mission.hue(), 0, 0, 0);
    }

    /** The mission's own still if it has one, otherwise the Undercity's, which then wants the tint. */
    private Image missionStill(String name, boolean[] borrowed) {
        Image own = still(mission.artFolder() + "/" + name);
        borrowed[0] = own == null;
        return own != null ? own : still(name);
    }

    public void draw(OnFootStage s) {
        mission = s.mission();
        double shake = settings.reducedFlash() ? 0 : s.shake();
        double zoom = 1 + (settings.reducedFlash() ? 0 : s.zoomKick() * 0.006);
        int tick = s.tick();
        double cam = Math.round(s.camera());

        gc.save();
        gc.translate(GameConfig.WIDTH / 2, GameConfig.HEIGHT * 0.6);
        gc.scale(zoom, zoom);
        gc.translate(-GameConfig.WIDTH / 2 + shake * Math.sin(tick * 2.7), -GameConfig.HEIGHT * 0.6 + shake * Math.cos(tick * 3.9));

        drawCavern(cam, tick);

        gc.save();
        gc.translate(-cam, 0);
        drawFloor(cam);
        drawWalls(s, tick);
        drawShip(s, tick);
        for (Fighter f : s.enemies()) {
            shadow(f);
        }
        for (Fighter f : s.pilots()) {
            if (s.beat() != OnFootStage.Beat.LANDING && s.beat() != OnFootStage.Beat.LIFTOFF && visible(f, s)) {
                shadow(f);
            }
        }
        for (OnFootStage.Shock shock : s.shocks()) {
            drawShock(shock, tick);
        }
        for (OnFootStage.Hazard hazard : s.hazards()) {
            drawHazard(hazard, tick);
        }
        drawPull(s.boss(), tick);
        for (Fighter f : s.enemies()) {
            drawFighter(f, tick);
            drawRivetAim(f, tick);
        }
        for (Fighter f : s.pilots()) {
            if (visible(f, s)) {
                drawFighter(f, tick);
            }
        }
        for (OnFootStage.Beam beam : s.beams()) {
            drawBeam(beam, tick);
        }
        drawCharge(s, tick);
        drawParticles(s);
        if (s.hasKey()) {
            drawKey(s, tick);
        }
        drawLight(cam, tick);
        gc.restore();

        drawChains(cam);
        gc.restore();

        drawHud(s, tick);
        drawCinema(s);
    }

    /** A pilot is in the cockpit until EXIT puts them on the floor, and back in it for LIFTOFF. */
    private static boolean visible(Fighter f, OnFootStage s) {
        return switch (s.beat()) {
            case LANDING, LIFTOFF, DONE -> false;
            case EXIT -> s.beatTicks() >= 12;
            default -> true;
        };
    }

    // ---- planes

    private void drawCavern(double cam, int tick) {
        gc.setFill(Tokens.SPACE);
        gc.fillRect(-40, -40, GameConfig.WIDTH + 80, GameConfig.HEIGHT + 80);
        boolean[] borrowed = new boolean[1];
        Image back = missionStill("backdrop", borrowed);
        double h = FLOOR + 30;
        if (back != null) {
            CanvasFx.effect(gc, borrowed[0] ? tint() : null);
            double w = back.getWidth() * h / back.getHeight();
            double off = -(cam * 0.25) % (2 * w);
            // Every other copy mirrored, so a painting never meant to tile still has no seam.
            for (double x = off - 2 * w; x < GameConfig.WIDTH + 2 * w; x += 2 * w) {
                gc.drawImage(back, x, 0, w, h);
                gc.drawImage(back, x + 2 * w, 0, -w, h);
            }
            CanvasFx.effect(gc, null);
        } else {
            gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#06080d")), new Stop(1, Color.web("#1e1518"))));
            gc.fillRect(-40, 0, GameConfig.WIDTH + 80, h);
        }
        // Cold light shafts from the city above, slowly breathing.
        gc.save();
        CanvasFx.blend(gc, BlendMode.ADD);
        for (int i = 0; i < 4; i++) {
            double x = ((i * 410 - cam * 0.32) % 1640 + 1640) % 1640 - 200;
            gc.setGlobalAlpha(0.05 + 0.025 * Math.sin(tick * 0.013 + i));
            gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, KEY_COLD), new Stop(1, Color.TRANSPARENT)));
            gc.fillPolygon(new double[] {x, x + 70, x + 260, x + 120}, new double[] {0, 0, FLOOR, FLOOR}, 4);
        }
        gc.restore();
        // Haze: the far plane sits in fog, which is what pushes it back.
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(8, 10, 14, 0.15)), new Stop(0.7, Color.rgb(8, 10, 14, 0.45)),
                new Stop(1, Color.rgb(30, 14, 10, 0.6))));
        gc.fillRect(-40, 0, GameConfig.WIDTH + 80, FLOOR + 30);
    }

    private void drawFloor(double cam) {
        boolean[] borrowed = new boolean[1];
        Image floor = missionStill("floor", borrowed);
        double top = FLOOR - 6;
        double h = GameConfig.HEIGHT - top + 60;
        if (floor != null) {
            CanvasFx.effect(gc, borrowed[0] ? tint() : null);
            double w = floor.getWidth() * h / floor.getHeight();
            double start = Math.floor(cam / (2 * w)) * 2 * w;
            for (double x = start - 2 * w; x < cam + GameConfig.WIDTH + 2 * w; x += 2 * w) {
                gc.drawImage(floor, x, top, w, h);
                gc.drawImage(floor, x + 2 * w, top, -w, h);
            }
            CanvasFx.effect(gc, null);
        } else {
            gc.setFill(Color.web("#14161c"));
            gc.fillRect(cam - 60, top, GameConfig.WIDTH + 120, h);
            gc.setStroke(Color.web("#2b3140"));
            gc.setLineWidth(2);
            for (double x = Math.floor(cam / 120) * 120; x < cam + GameConfig.WIDTH + 120; x += 120) {
                gc.strokeLine(x, top, x - 30, GameConfig.HEIGHT);
            }
        }
        // The walkway's lit lip, and the floor falling away into dark below it.
        gc.setFill(Color.rgb(255, 160, 90, 0.35));
        gc.fillRect(cam - 60, top, GameConfig.WIDTH + 120, 2);
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT), new Stop(1, Color.rgb(3, 4, 7, 0.85))));
        gc.fillRect(cam - 60, top + 20, GameConfig.WIDTH + 120, h);
        // Untinted: a hue-shifted crate went magenta. Iron reads as iron in any galaxy.
        Image crate = missionStill("crate", borrowed);
        if (crate != null) {
            for (double x : new double[] {420, 1180, 1960, 2700, 3560}) {
                if (x > cam - 200 && x < cam + GameConfig.WIDTH + 200) {
                    double ch = 96, cw = crate.getWidth() * ch / crate.getHeight();
                    gc.drawImage(crate, x, FLOOR - ch + 8, cw, ch);
                }
            }
        }
    }

    /**
     * Pulsing pools of furnace light, laid over everything in the world plane.
     *
     * Plain alpha, not ADD. Under ADD these 520x300 gradient ovals came out as a blocky dark-navy
     * band across the lower half of the screen on some frames -- which ones shifted with whatever
     * else the frame drew (a hit flash, a beam), so it read as the enemy laser blacking out the
     * room. At this alpha the warm wash looks the same either way.
     */
    private void drawLight(double cam, int tick) {
        gc.save();
        for (int i = 0; i < 12; i++) {
            double x = 180 + i * 360;
            if (x < cam - 400 || x > cam + GameConfig.WIDTH + 400) {
                continue;
            }
            double flicker = settings.reducedFlash() ? 0.8 : 0.7 + 0.15 * Math.sin(tick * 0.07 + i * 1.7)
                    + 0.08 * Math.sin(tick * 0.31 + i);
            gc.setGlobalAlpha(0.32 * flicker);
            gc.setFill(FURNACE_POOL);
            gc.fillOval(x - 260, FLOOR - 220, 520, 300);
        }
        gc.restore();
    }

    /** Chains hung close to the lens, moving faster than the fight: the cheapest depth there is. */
    private void drawChains(double cam) {
        gc.setStroke(Color.web("#050608"));
        for (int i = 0; i < 20; i++) {
            double x = i * 520 + 130 - cam * 1.35;
            if (x < -60 || x > GameConfig.WIDTH + 60) {
                continue;
            }
            double len = 120 + Math.floorMod(i * 31, 5) * 60;
            gc.setLineWidth(9);
            gc.strokeLine(x, -10, x + 6, len);
            gc.setLineWidth(5);
            for (double y = 0; y < len; y += 22) {
                gc.strokeOval(x - 6 + y * 0.05, y, 12, 20);
            }
        }
    }

    private void drawWalls(OnFootStage s, int tick) {
        if (!s.locked()) {
            return;
        }
        gc.save();
        CanvasFx.blend(gc, BlendMode.ADD);
        for (double x : new double[] {s.wallLeft(), s.wallRight()}) {
            double pulse = 0.5 + 0.2 * Math.sin(tick * 0.2);
            gc.setGlobalAlpha(pulse);
            gc.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.TRANSPARENT), new Stop(0.5, MOLTEN), new Stop(1, Color.TRANSPARENT)));
            gc.fillRect(x - 14, FLOOR - 420, 28, 420);
            gc.setGlobalAlpha(0.8);
            gc.setFill(Color.WHITE);
            gc.fillRect(x - 1, FLOOR - 420, 2, 420);
        }
        gc.restore();
    }

    private void drawShip(OnFootStage s, int tick) {
        ChassisArt stock = ChassisArt.named("STOCK");
        Image ship = stock == null ? Assets.image(Sprite.P1_STRAIGHT)
                : Assets.chassisFrames(stock, null, ChassisArt.View.SIDE, Livery.MILITIA_GREEN.hue(),
                        Livery.MILITIA_GREEN.saturation(), 2, false).get(ChassisArt.View.SIDE.frameIndexAt(tick));
        boolean profile = stock != null;
        double w = 190, h = w * ship.getHeight() / ship.getWidth();
        double x = s.shipX(), y = s.shipY();
        if (y < -150) {
            return;
        }
        boolean burning = s.beat() == OnFootStage.Beat.LANDING || s.beat() == OnFootStage.Beat.PICKUP
                || s.beat() == OnFootStage.Beat.LIFTOFF;
        if (burning) {
            gc.save();
            CanvasFx.blend(gc, BlendMode.ADD);
            double flame = 70 + 20 * Math.sin(tick * 0.9);
            gc.setFill(TEAL_HALO);
            gc.fillOval(x - flame, y + 10, flame * 2, flame * 1.6);
            gc.restore();
        }
        // Seen from the side: the hull's true profile, nose right; the drawn fallback is the
        // top-down hull turned nose-right, which reads as a ship on its skids.
        gc.save();
        gc.translate(x, y - 20);
        if (profile) {
            gc.drawImage(ship, -w / 2, -h / 2, w, h);
        } else {
            gc.rotate(90);
            gc.drawImage(ship, -h / 2, -w / 2, h, w);
        }
        gc.restore();
        double above = Math.max(0, FLOOR - (y + 40));
        double spread = 220 * Math.max(0.3, 1 - above / 600);
        gc.setFill(Color.rgb(0, 0, 0, 0.5 * Math.max(0.2, 1 - above / 600)));
        gc.fillOval(x - spread / 2, FLOOR - 10, spread, 18);
    }

    // ---- the fight

    private void shadow(Fighter f) {
        if (!f.alive() && f.stateTicks() > 40) {
            return;
        }
        double above = FLOOR - f.y();
        double w = f.kind.width() * 1.7 * Math.max(0.35, 1 - above / 400);
        gc.setFill(Color.rgb(0, 0, 0, 0.55 * Math.max(0.25, 1 - above / 400)));
        gc.fillOval(f.x() - w / 2, FLOOR - 8, w, 14);
    }

    /**
     * A Riveteer's tell: a thin red line down its aim that brightens as the wind-up runs out, so the
     * shot is read and jumped rather than eaten.
     */
    private void drawRivetAim(Fighter f, int tick) {
        if (f.kind != Fighter.Kind.RIVETEER || f.state() != Fighter.State.ATTACK || f.move() != Move.RIVET
                || f.stateTicks() >= Move.RIVET.startup) {
            return;
        }
        double p = f.stateTicks() / (double) Move.RIVET.startup;
        double y = f.y() - f.kind.height() * 0.6;
        double x = f.x() + f.facing() * f.kind.width() * 0.6;
        boolean blink = !settings.reducedFlash() && p > 0.7 && (tick / 3) % 2 == 0;
        gc.setStroke(Color.color(1, 0.2, 0.15, blink ? 0.9 : 0.25 + 0.5 * p));
        gc.setLineWidth(1.5);
        gc.strokeLine(x, y, x + f.facing() * 900, y);
    }

    private void drawFighter(Fighter f, int tick) {
        String who = switch (f.kind) {
            case PILOT -> "pilot";
            case THRALL -> "thrall";
            case FOREMAN -> "foreman";
            case RIVETEER -> "riveteer";
            case BRUTE -> "brute";
            case WARDEN -> "warden";
            case MATRIARCH -> "matriarch";
            case TYRANT -> "tyrant";
            case REVENANT -> "revenant";
        };
        double art = switch (f.kind) {
            case PILOT -> PILOT_ART;
            case THRALL -> THRALL_ART;
            case FOREMAN -> FOREMAN_ART;
            case RIVETEER -> RIVETEER_ART;
            case BRUTE -> BRUTE_ART;
            case WARDEN -> WARDEN_ART;
            case MATRIARCH -> MATRIARCH_ART;
            case TYRANT -> TYRANT_ART;
            case REVENANT -> REVENANT_ART;
        };
        String[] anim = animFor(f);
        List<Image> frames = frames(who, anim[0]);
        for (int i = 1; frames.isEmpty() && i < anim.length; i++) {
            frames = frames(who, anim[i]);
        }
        // No moveset, but a still: one pose, animated by hand (see puppet).
        boolean puppet = frames.isEmpty() && still(who) != null;
        if (puppet) {
            frames = List.of(still(who));
        }

        double fade = f.alive() ? 1 : Math.max(0, 1 - Math.max(0, f.stateTicks() - 30) / 40.0);
        if (f.player > 0) {
            fade = 1;
        }
        if (fade <= 0) {
            return;
        }
        double sx = 1, sy = 1;
        if (f.landed() > 0) {
            double k = f.landed() / 8.0;
            sx = 1 + 0.08 * k;
            sy = 1 - 0.08 * k;
        } else if (!f.grounded() && f.vy() < -6) {
            sx = 0.95;
            sy = 1.06;
        }

        gc.save();
        gc.setGlobalAlpha(fade);
        gc.translate(f.x(), f.y());
        if (f.state() == Fighter.State.LAUNCHED) {
            gc.rotate(-f.facing() * Math.max(-40, Math.min(40, f.vy() * 3)));
        }
        gc.scale(f.facing() * sx, sy);
        if (puppet) {
            puppet(f, tick);
        }
        if (frames.isEmpty()) {
            silhouette(f);
        } else {
            Image frame = frames.get(frameIndex(f, anim[0], frames.size(), tick));
            double h = f.kind.height() * art;
            double w = frame.getWidth() * h / frame.getHeight();
            if (f.player == 2) {
                CanvasFx.effect(gc, SECOND_SEAT);
            } else if (f.player == 0 && !f.kind.boss()) {
                CanvasFx.effect(gc, tint());    // the borrowed crowd, dyed to the galaxy
            }
            if (f.mercy() > 0 && (tick / 4) % 2 == 0) {
                gc.setGlobalAlpha(fade * 0.45);    // protected after a hit: blinks until it can be hit again
            }
            gc.drawImage(frame, -w / 2, -h, w, h);
            if (f.hurtFlash() > 0 && !settings.reducedFlash()) {
                // Plain alpha, not ADD: an image drawn with ADD lightens its whole rectangle here, clear
                // pixels included, which is the box that used to flash around a struck enemy.
                CanvasFx.effect(gc, null);
                gc.setGlobalAlpha(fade * f.hurtFlash() / 12.0);
                gc.drawImage(whiteOf(frame), -w / 2, -h, w, h);
            }
        }
        gc.restore();
        if (f.kind != Fighter.Kind.PILOT && f.alive() && f.healthFraction() < 1 && !f.kind.boss()) {
            double w = 54;
            gc.setFill(Color.rgb(0, 0, 0, 0.6));
            gc.fillRect(f.x() - w / 2, f.y() - f.kind.height() - 18, w, 5);
            gc.setFill(Tokens.DANGER_BAR);
            gc.fillRect(f.x() - w / 2, f.y() - f.kind.height() - 18, w * f.healthFraction(), 5);
        }
    }

    /**
     * Body language for a character drawn from a single still: a breathing bob, a stride bounce, a
     * wind-up lean back then a lunge forward, a flinch, a fall. Applied after the facing flip, so
     * a positive angle always leans into the direction it faces.
     */
    private void puppet(Fighter f, int tick) {
        double h = f.kind.height();
        switch (f.state()) {
            case ATTACK -> {
                Move m = f.move();
                double t = f.stateTicks();
                double lean;
                if (t < m.startup) {
                    lean = -7 * t / m.startup;                          // wind up
                } else if (t < m.startup + Math.max(6, m.active)) {
                    lean = m == Move.POUND ? 3 : 11;                    // commit
                    if (m == Move.POUND) {
                        gc.scale(1.06, 0.9);
                    }
                    gc.translate(16, 0);
                } else {
                    lean = 11 * Math.max(0, 1 - (t - m.startup - m.active) / 14.0);
                }
                if (m == Move.MOLTEN_BEAM && t < m.startup) {
                    gc.translate(Math.sin(t * 2.1) * 2, 0);            // the furnace building pressure
                }
                gc.rotate(lean);
            }
            case HITSTUN -> {
                gc.translate(-6 + Math.sin(f.stateTicks() * 2.5) * 2, 0);
                gc.rotate(-5);
            }
            case DOWN, DEAD -> {
                double fall = Math.min(1, f.stateTicks() / 22.0);
                gc.translate(-h * 0.25 * fall, 0);
                gc.rotate(-80 * fall);
            }
            case GETUP -> gc.rotate(-80 * (1 - f.stateTicks() / (double) Fighter.GETUP_TICKS));
            default -> {
                if (f.grounded() && Math.abs(f.vx()) > 0.5) {
                    double phase = f.stride() / 34.0;
                    gc.translate(0, -Math.abs(Math.sin(phase)) * 7);
                    gc.rotate(2 + Math.sin(phase) * 2);
                } else {
                    gc.scale(1, 1 + 0.012 * Math.sin(tick * 0.07));
                }
            }
        }
    }

    /** Which moveset key to show, with fallbacks for a character whose set lacks it. */
    private static String[] animFor(Fighter f) {
        return switch (f.state()) {
            case ATTACK -> new String[] {f.move().anim(), "combo_1"};
            case CHARGE -> new String[] {"special", "combat_idle", "idle"};
            case GUARD -> new String[] {"victory", "combat_idle", "idle"};
            case HITSTUN, LAUNCHED -> new String[] {"hurt", "idle", "combat_idle"};
            case DOWN, DEAD, GETUP -> new String[] {"death", "hurt"};
            default -> !f.grounded()
                    ? new String[] {"jump", "idle", "combat_idle"}
                    : Math.abs(f.vx()) > 4.8 && f.heldTicks() > 0
                    ? new String[] {"run", "walk"}
                    : Math.abs(f.vx()) > 0.6
                    ? new String[] {"walk", "combat_idle", "idle"}
                    : new String[] {f.kind == Fighter.Kind.PILOT ? "idle" : "combat_idle", "idle", "combat_idle"};
        };
    }

    private static int frameIndex(Fighter f, String anim, int count, int tick) {
        double t;
        switch (f.state()) {
            case ATTACK -> {
                Move m = f.move();
                if (m == Move.SOLAR_LANCE || m == Move.MOLTEN_BEAM) {
                    // Wind up, then hold the firing frame while the beam lives: the pilot's arms-forward
                    // release, the Foreman's flame.
                    double from = m == Move.SOLAR_LANCE ? 0.65 : 0, fire = m == Move.SOLAR_LANCE ? 0.85 : 0.55;
                    t = from + (fire - from) * Math.min(1, f.stateTicks() / (double) Math.max(1, m.startup + 1));
                } else {
                    t = f.stateTicks() / (double) m.length();
                }
            }
            case CHARGE -> t = 0.15 + 0.5 * Math.min(1, f.charge() / (double) Fighter.CHARGE_MAX);
            case GUARD -> t = Math.min(0.5, f.stateTicks() / 16.0);
            case HITSTUN -> t = Math.min(0.6, f.stateTicks() / 20.0);
            case LAUNCHED -> t = 0.4;
            case DOWN, DEAD -> t = Math.min(0.999, f.stateTicks() / 18.0);
            case GETUP -> t = 1 - f.stateTicks() / (double) Fighter.GETUP_TICKS;
            default -> {
                if (!f.grounded()) {
                    // The jump clip is airborne frames only (see onfoot.py), indexed by vertical speed:
                    // take-off stretch at full rise, the tuck at the apex, reaching down on the way in.
                    t = Math.max(0, Math.min(0.999, (f.vy() + 14) / 31));
                } else if (anim.equals("walk") || anim.equals("run")) {
                    return (int) (f.stride() / (anim.equals("run") ? 26 : 18)) % count;
                } else {
                    return (tick / 6) % count;
                }
            }
        }
        return Math.max(0, Math.min(count - 1, (int) (t * count)));
    }

    private void silhouette(Fighter f) {
        double w = f.kind.width(), h = f.kind.height();
        gc.setFill(f.kind == Fighter.Kind.PILOT ? (f.player == 2 ? Color.web("#d8a24a") : Color.web("#2f6f7a"))
                : Color.web("#1d2b1a"));
        gc.fillRoundRect(-w / 2, -h, w, h * 0.62, w * 0.6, w * 0.6);
        gc.fillOval(-w * 0.32, -h - w * 0.5, w * 0.64, w * 0.64);
        gc.fillRect(-w * 0.4, -h * 0.4, w * 0.3, h * 0.4);
        gc.fillRect(w * 0.1, -h * 0.4, w * 0.3, h * 0.4);
        if (f.state() == Fighter.State.ATTACK && f.move() != null) {
            gc.fillRect(w * 0.3, -h * 0.75, f.move().reach * Math.min(1, f.stateTicks() / (double) f.move().startup), 12);
        }
    }

    private void drawBeam(OnFootStage.Beam b, int tick) {
        // Any enemy's beam is molten; only a pilot's is teal.
        boolean hostile = b.owner().kind != Fighter.Kind.PILOT;
        Color core = b.needle() ? VIOLET : hostile ? MOLTEN : TEAL;
        Paint halo = b.needle() ? KEY_HALO : hostile ? MOLTEN_HALO : TEAL_HALO;
        double life = b.life() / (double) b.maxLife();
        double swell = Math.min(1, (1 - life) * 6) * Math.min(1, life * 4);
        double wobble = settings.reducedFlash() ? 1 : 1 + 0.1 * Math.sin(tick * 1.3);
        double t = b.thickness() * Math.max(0.15, swell) * wobble;
        gc.save();
        CanvasFx.blend(gc, BlendMode.ADD);
        for (double[] layer : new double[][] {{2.4, 0.25}, {1.4, 0.5}, {0.8, 0.85}, {0.3, 1}}) {
            gc.setGlobalAlpha(layer[1]);
            gc.setFill(layer[0] < 0.5 ? Color.WHITE : core);
            double th = t * layer[0];
            gc.fillRoundRect(b.left(), b.y() - th / 2, b.length(), th, th, th);
        }
        double bloom = 40 + t * 2.2;
        gc.setGlobalAlpha(1);
        gc.setFill(halo);
        gc.fillOval(b.x() - bloom / 2, b.y() - bloom / 2, bloom, bloom);
        double end = b.dir() > 0 ? b.right() : b.left();
        gc.fillOval(end - bloom * 0.6, b.y() - bloom * 0.6, bloom * 1.2, bloom * 1.2);
        // Light thrown down onto the floor under the beam.
        gc.setGlobalAlpha(0.35 * swell);
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, core), new Stop(1, Color.TRANSPARENT)));
        gc.fillRect(b.left(), FLOOR - 4, b.length(), 26);
        gc.restore();
    }

    private void drawCharge(OnFootStage s, int tick) {
        gc.save();
        CanvasFx.blend(gc, BlendMode.ADD);
        for (Fighter f : s.pilots()) {
            if (f.state() != Fighter.State.CHARGE) {
                continue;
            }
            double p = f.charge() / (double) Fighter.CHARGE_MAX;
            double r = 18 + 70 * p + (settings.reducedFlash() ? 0 : 6 * Math.sin(tick * 0.8));
            double hx = f.x() - f.facing() * f.kind.width() * 0.2, hy = f.y() - f.kind.height() * 0.5;
            gc.setGlobalAlpha(0.6 + 0.4 * p);
            gc.setFill(TEAL_HALO);
            gc.fillOval(hx - r, hy - r, 2 * r, 2 * r);
            gc.setFill(Color.WHITE);
            gc.fillOval(hx - r * 0.18, hy - r * 0.18, r * 0.36, r * 0.36);
            // An aura around the whole body, the ki made visible.
            gc.setGlobalAlpha(0.25 * p);
            gc.fillOval(f.x() - 60, f.y() - f.kind.height() - 30, 120, f.kind.height() + 40);
        }
        gc.restore();
    }

    private void drawShock(OnFootStage.Shock s, int tick) {
        gc.save();
        CanvasFx.blend(gc, BlendMode.ADD);
        gc.setFill(MOLTEN_HALO);
        gc.fillOval(s.x() - 50, FLOOR - 70, 100, 90);
        gc.setFill(MOLTEN);
        gc.fillPolygon(new double[] {s.x() - 22, s.x(), s.x() + 22},
                new double[] {FLOOR, FLOOR - 46 - 8 * Math.sin(tick * 0.9), FLOOR}, 3);
        gc.restore();
    }

    /**
     * A signature's footprint. While it only warns it is an outline that tightens; once it bites it
     * fills: slag glowing on the floor, spikes standing up out of the mark, the dash line lit hot.
     */
    private void drawHazard(OnFootStage.Hazard h, int tick) {
        double half = h.width() / 2;
        double left = h.x() - half;
        double warn = h.armed() ? 1 : 0.35 + 0.35 * Math.abs(Math.sin(tick * 0.25));
        gc.save();
        switch (h.kind()) {
            case OnFootStage.Hazard.SLAG -> {
                double fade = Math.min(1, h.live() / 30.0);
                CanvasFx.blend(gc, BlendMode.ADD);
                gc.setFill(MOLTEN_HALO);
                gc.setGlobalAlpha(warn * fade);
                gc.fillOval(left - 30, FLOOR - 40, h.width() + 60, 70);
                CanvasFx.blend(gc, BlendMode.SRC_OVER);
                gc.setFill(Color.web("#ff5a1a", (h.armed() ? 0.85 : 0.35) * fade));
                gc.fillOval(left, FLOOR - 8, h.width(), 16);
                gc.setFill(Color.web("#ffd27a", 0.7 * fade));
                gc.fillOval(left + h.width() * 0.25, FLOOR - 4, h.width() * 0.5, 7);
            }
            case OnFootStage.Hazard.SPIKE -> {
                Color ice = Color.web("#bfefff");
                if (!h.armed()) {
                    // Dark under a bright ring: the Under-Ice floor is nearly white, so a pale mark alone vanished.
                    double shrink = 1 - 0.5 * (1 - h.arm() / 45.0);
                    gc.setFill(Color.web("#0b2a55", 0.75));
                    gc.fillOval(left, FLOOR - 12, h.width(), 24);
                    gc.setStroke(Color.WHITE.deriveColor(0, 1, 1, warn));
                    gc.setLineWidth(3);
                    gc.strokeOval(h.x() - half * shrink, FLOOR - 10 * shrink, h.width() * shrink, 20 * shrink);
                } else {
                    double rise = Math.min(1, (h.maxLive() - h.live() + 1) / 4.0);
                    gc.setFill(ice);
                    for (int i = 0; i < 5; i++) {
                        double cx = left + h.width() * (i + 0.5) / 5;
                        double tall = (60 + 30 * ((i * 37) % 3)) * rise;
                        gc.fillPolygon(new double[] {cx - 11, cx, cx + 11},
                                new double[] {FLOOR, FLOOR - tall, FLOOR}, 3);
                    }
                }
            }
            default -> {
                double y = FLOOR - 50;
                CanvasFx.blend(gc, BlendMode.ADD);
                gc.setStroke(Color.web("#9fd8ff", h.armed() ? 0.95 : warn));
                gc.setLineWidth(h.armed() ? 8 : 3);
                gc.setLineDashes(h.armed() ? null : new double[] {18, 12});
                gc.strokeLine(left, y, left + h.width(), y);
                gc.setLineDashes(null);
            }
        }
        gc.restore();
    }

    /**
     * The Null Revenant's pull, made visible: violet rings closing in on it for as long as it drags,
     * so a pilot sliding toward it knows why, and which way to run.
     */
    private void drawPull(Fighter boss, int tick) {
        if (boss == null || boss.move() != Move.GRAVITY_WELL || boss.state() != Fighter.State.ATTACK) {
            return;
        }
        double cx = boss.x(), cy = boss.y() - boss.kind.height() * 0.55;
        gc.save();
        CanvasFx.blend(gc, BlendMode.ADD);
        gc.setLineWidth(3);
        for (int i = 0; i < 4; i++) {
            double phase = ((tick * 0.012 + i * 0.25) % 1.0);
            double r = 60 + 520 * (1 - phase);
            gc.setStroke(Color.web("#b07cff", 0.55 * phase));
            gc.strokeOval(cx - r, cy - r * 0.45, 2 * r, r * 0.9);
        }
        gc.setFill(KEY_HALO);
        gc.fillOval(cx - 70, cy - 70, 140, 140);
        gc.restore();
    }

    private void drawParticles(OnFootStage s) {
        for (OnFootStage.Particle p : s.particles()) {
            double a = p.life() / (double) p.maxLife();
            switch (p.kind()) {
                case OnFootStage.Particle.SPARK -> {
                    gc.save();
                    CanvasFx.blend(gc, BlendMode.ADD);
                    gc.setStroke(Color.web("#ffd27a", a));
                    gc.setLineWidth(2.5);
                    gc.strokeLine(p.x(), p.y(), p.x() - p.vx() * 2.2, p.y() - p.vy() * 2.2);
                    gc.restore();
                }
                case OnFootStage.Particle.DUST -> {
                    double r = 10 + (1 - a) * 26;
                    gc.setFill(Color.rgb(90, 84, 80, 0.28 * a));
                    gc.fillOval(p.x() - r, p.y() - r, 2 * r, 2 * r);
                }
                case OnFootStage.Particle.DEBRIS -> {
                    gc.setFill(Color.rgb(30, 26, 24, Math.min(1, a * 2)));
                    gc.fillRect(p.x() - 3, p.y() - 3, 6, 5);
                    gc.setFill(Color.rgb(255, 120, 40, 0.6 * a));
                    gc.fillRect(p.x() - 1, p.y() - 2, 2, 2);
                }
                default -> {
                    gc.save();
                    CanvasFx.blend(gc, BlendMode.ADD);
                    gc.setFill(Color.web("#9ffcff", a));
                    gc.fillOval(p.x() - 2.5, p.y() - 2.5, 5, 5);
                    gc.restore();
                }
            }
        }
    }

    private void drawKey(OnFootStage s, int tick) {
        double bob = Math.sin(tick * 0.08) * 6;
        double x = s.keyX(), y = s.keyY() + bob;
        gc.save();
        CanvasFx.blend(gc, BlendMode.ADD);
        double r = 80 + 10 * Math.sin(tick * 0.15);
        gc.setFill(KEY_HALO);
        gc.fillOval(x - r, y - r, 2 * r, 2 * r);
        gc.restore();
        Image key = still("rift-key");
        if (key != null) {
            double h = 64, w = key.getWidth() * h / key.getHeight();
            CanvasFx.effect(gc, tint());
            gc.drawImage(key, x - w / 2, y - h / 2, w, h);
            CanvasFx.effect(gc, null);
        } else {
            gc.setFill(VIOLET);
            gc.fillPolygon(new double[] {x, x + 16, x, x - 16}, new double[] {y - 26, y, y + 26, y}, 4);
        }
    }

    // ---- screen space

    private void drawHud(OnFootStage s, int tick) {
        if (s.beat() == OnFootStage.Beat.LANDING || s.beat() == OnFootStage.Beat.LIFTOFF) {
            return;
        }
        for (Fighter p : s.pilots()) {
            boolean right = p.player == 2;
            double x = right ? GameConfig.WIDTH - 24 - 260 : 24, y = 24;
            gc.setFill(Color.rgb(0, 0, 0, 0.5));
            gc.fillRoundRect(x - 8, y - 8, 276, 54, 10, 10);
            gc.setFill(Tokens.TRACK);
            gc.fillRect(x, y + 14, 260, 12);
            gc.setFill(p.healthFraction() > 0.25 ? Tokens.BRAND : Tokens.DANGER_BAR);
            gc.fillRect(right ? x + 260 * (1 - p.healthFraction()) : x, y + 14, 260 * p.healthFraction(), 12);
            gc.setFill(Tokens.TRACK);
            gc.fillRect(x, y + 32, 260, 6);
            double ki = p.ki() / Fighter.MAX_KI;
            gc.setFill(ki >= Fighter.NEEDLE_COST / (double) Fighter.MAX_KI ? TEAL : Color.web("#2d6a72"));
            gc.fillRect(right ? x + 260 * (1 - ki) : x, y + 32, 260 * ki, 6);
            gc.setFont(CAPTION);
            gc.setFill(Tokens.TEXT);
            gc.setTextAlign(right ? TextAlignment.RIGHT : TextAlignment.LEFT);
            gc.setTextBaseline(VPos.TOP);
            gc.fillText("P" + p.player + (p.alive() ? "" : "  DOWN"), right ? x + 260 : x, y - 4);
        }
        Fighter boss = s.boss();
        if (boss != null && boss.alive()) {
            double w = 560, x = (GameConfig.WIDTH - w) / 2, y = GameConfig.HEIGHT - 58;
            gc.setFill(Color.rgb(0, 0, 0, 0.6));
            gc.fillRoundRect(x - 10, y - 26, w + 20, 46, 10, 10);
            gc.setFill(Tokens.TRACK_DANGER);
            gc.fillRect(x, y, w, 12);
            gc.setFill(boss.rage() ? MOLTEN : Tokens.DANGER_BAR);
            gc.fillRect(x, y, w * boss.healthFraction(), 12);
            gc.setFont(CAPTION);
            gc.setFill(Tokens.TEXT);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.BOTTOM);
            gc.fillText(mission.bossName(), GameConfig.WIDTH / 2, y - 4);
        }
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        if (s.goPrompt() && (tick / 20) % 2 == 0) {
            gc.setFont(TITLE);
            gc.setFill(FURNACE);
            gc.fillText("GO  →", GameConfig.WIDTH - 130, GameConfig.HEIGHT * 0.42);
        }
        if (s.retrying() > 0) {
            gc.setFont(TITLE);
            gc.setFill(Tokens.DANGER_SOFT);
            gc.fillText("PILOTS DOWN", GameConfig.WIDTH / 2, GameConfig.HEIGHT * 0.42);
        }
        if (s.beat() == OnFootStage.Beat.KEY) {
            gc.setFont(CAPTION);
            gc.setFill(VIOLET);
            gc.fillText("TAKE " + mission.prize(), GameConfig.WIDTH / 2, GameConfig.HEIGHT * 0.3);
        }
        gc.setTextBaseline(VPos.BASELINE);
    }

    /** Letterbox and title card for the arrival, and again on the way out. */
    private void drawCinema(OnFootStage s) {
        double bars = switch (s.beat()) {
            case LANDING -> 1;
            case EXIT -> Math.max(0, 1 - s.beatTicks() / (double) OnFootStage.EXIT_TICKS);
            case PICKUP -> Math.min(1, s.beatTicks() / 40.0);
            case LIFTOFF, DONE -> 1;
            default -> 0;
        };
        if (bars > 0) {
            double bar = 72 * bars;
            gc.setFill(Color.BLACK);
            gc.fillRect(0, 0, GameConfig.WIDTH, bar);
            gc.fillRect(0, GameConfig.HEIGHT - bar, GameConfig.WIDTH, bar);
        }
        if (s.beat() == OnFootStage.Beat.LANDING || s.beat() == OnFootStage.Beat.EXIT) {
            double t = s.beat() == OnFootStage.Beat.LANDING ? s.beatTicks() : OnFootStage.LANDING_TICKS + s.beatTicks();
            double in = Math.min(1, t / 30.0), out = Math.min(1, (OnFootStage.LANDING_TICKS + OnFootStage.EXIT_TICKS - t) / 30.0);
            gc.setGlobalAlpha(Math.max(0, Math.min(in, out)));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
            gc.setFont(Font.font(Assets.displayFontFamily(), FontWeight.BOLD, 54));
            gc.setFill(Color.rgb(0, 0, 0, 0.6));
            gc.fillText(mission.title(), GameConfig.WIDTH / 2 + 3, GameConfig.HEIGHT * 0.3 + 4);
            gc.setFill(Color.WHITE);
            gc.fillText(mission.title(), GameConfig.WIDTH / 2, GameConfig.HEIGHT * 0.3);
            gc.setFont(Font.font(Assets.displayFontFamily(), FontWeight.NORMAL, 18));
            gc.setFill(VIOLET);
            gc.fillText(mission.objective(), GameConfig.WIDTH / 2, GameConfig.HEIGHT * 0.3 + 46);
            gc.setGlobalAlpha(1);
            gc.setTextBaseline(VPos.BASELINE);
        }
    }

    // ---- art

    private static List<Image> frames(String who, String anim) {
        return FRAMES.computeIfAbsent(who + "/" + anim, key -> {
            List<Image> found = new ArrayList<>();
            for (int i = 1; ; i++) {
                InputStream in = OnFootRenderer.class.getResourceAsStream("/sprites/onfoot/" + key + "/" + i + ".png");
                if (in == null) {
                    return found;
                }
                found.add(new Image(in));
            }
        });
    }

    private static Image still(String name) {
        // Not computeIfAbsent: a missing still is remembered as null rather than probed for every frame.
        if (!STILLS.containsKey(name)) {
            InputStream in = OnFootRenderer.class.getResourceAsStream("/sprites/onfoot/" + name + ".png");
            STILLS.put(name, in == null ? null : new Image(in));
        }
        return STILLS.get(name);
    }
}
