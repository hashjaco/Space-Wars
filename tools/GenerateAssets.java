import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Generates every sprite and sound that is not original hand-drawn work, so the repository contains
 * no third-party art or audio and can be published under a single licence.
 *
 * Run with:  java tools/GenerateAssets.java
 *
 * Output is deterministic: every random draw comes from a fixed seed, so re-running reproduces the
 * same assets byte for byte, which is what CI checks.
 *
 * Deliberately NOT generated, because they are the author's own work and better than anything here
 * would produce: the player ships, the projectiles, the explosion frame sequences, and the music.
 */
public final class GenerateAssets {

    private static final Path SPRITES = Path.of("src/main/resources/sprites");
    private static final Path SOUNDS = Path.of("src/main/resources/sounds");

    private static final int SAMPLE_RATE = 44100;

    // Palette shared across the generated sprites so they read as one set.
    private static final Color HULL_DARK = new Color(0x1b2030);
    private static final Color HULL_MID = new Color(0x39415c);
    private static final Color HULL_LIGHT = new Color(0x6d7899);
    private static final Color HOSTILE = new Color(0xc2384a);
    private static final Color HOSTILE_GLOW = new Color(0xff6b5a);
    private static final Color BRAND = new Color(0x0ec417);

    public static void main(String[] args) throws IOException {
        Files.createDirectories(SPRITES);
        Files.createDirectories(SOUNDS);

        asteroids();
        enemies();
        boss();
        pickups();
        background();

        laser();
        explosionSound();
        thud();
        gameOverSting();

        System.out.println("done");
    }

    // ---------------------------------------------------------------- sprites

    /** Procedural cratered rock, lit from the upper left. Replaces a watermarked download. */
    private static void asteroids() throws IOException {
        int[] sizes = {128, 128, 128};
        String[] names = {"asteroid-small", "asteroid-big", "asteroid-huge"};
        for (int variant = 0; variant < names.length; variant++) {
            int size = sizes[variant];
            BufferedImage image = blank(size, size);
            Graphics2D g = paint(image);
            Random random = new Random(1000 + variant);

            // Irregular silhouette: a circle perturbed per vertex.
            Path2D outline = new Path2D.Double();
            int points = 15 + variant * 3;
            double radius = size * 0.42;
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points;
                double wobble = radius * (0.76 + random.nextDouble() * 0.3);
                double x = size / 2.0 + Math.cos(angle) * wobble;
                double y = size / 2.0 + Math.sin(angle) * wobble;
                if (i == 0) {
                    outline.moveTo(x, y);
                } else {
                    outline.lineTo(x, y);
                }
            }
            outline.closePath();

            g.setClip(outline);
            g.setPaint(new RadialGradientPaint(
                    (float) (size * 0.34), (float) (size * 0.3), (float) (size * 0.72),
                    new float[]{0f, 0.55f, 1f},
                    new Color[]{new Color(0xb9c0cc), new Color(0x6f7783), new Color(0x24272e)}));
            g.fillRect(0, 0, size, size);

            // Craters, darker with a lit lower rim.
            int craters = 9 + variant * 4;
            for (int i = 0; i < craters; i++) {
                double cx = size * (0.2 + random.nextDouble() * 0.6);
                double cy = size * (0.2 + random.nextDouble() * 0.6);
                double cr = size * (0.035 + random.nextDouble() * 0.075);
                g.setColor(new Color(0, 0, 0, 70));
                g.fill(new Ellipse2D.Double(cx - cr, cy - cr, cr * 2, cr * 2));
                g.setColor(new Color(255, 255, 255, 34));
                g.fill(new Ellipse2D.Double(cx - cr * 0.8, cy - cr * 0.55, cr * 1.6, cr * 1.3));
            }
            g.setClip(null);

            g.setColor(new Color(0x15171c));
            g.setStroke(new BasicStroke(2f));
            g.draw(outline);
            g.dispose();
            write(image, SPRITES.resolve(names[variant] + ".png"));
        }
    }

    /** Three hostile silhouettes, angular and pointing down the arena at the player. */
    private static void enemies() throws IOException {
        write(scout(), SPRITES.resolve("enemy-scout.png"));
        write(fighter(), SPRITES.resolve("enemy-fighter.png"));
        write(cruiser(), SPRITES.resolve("enemy-cruiser.png"));
    }

    /** A dart with swept-back wings, so the fastest enemy reads as an interceptor. */
    private static BufferedImage scout() {
        int w = 104;
        int h = 92;
        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);

        Path2D body = path(w, h, new double[][]{
                {0.50, 0.96}, {0.34, 0.60}, {0.05, 0.28}, {0.17, 0.17}, {0.40, 0.32},
                {0.44, 0.05}, {0.56, 0.05}, {0.60, 0.32}, {0.83, 0.17}, {0.95, 0.28},
                {0.66, 0.60}});
        g.setColor(HOSTILE);
        g.fill(body);
        g.setColor(HULL_DARK);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(body);

        // Cockpit.
        g.setColor(HULL_DARK);
        g.fill(new Ellipse2D.Double(w * 0.43, h * 0.40, w * 0.14, h * 0.20));
        g.setColor(HOSTILE_GLOW);
        g.fill(new Ellipse2D.Double(w * 0.455, h * 0.44, w * 0.09, h * 0.12));
        g.dispose();
        return image;
    }

    private static BufferedImage fighter() {
        int w = 128;
        int h = 120;
        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);

        // Swept wings.
        Path2D wings = new Path2D.Double();
        wings.moveTo(w * 0.5, h * 0.2);
        wings.lineTo(w * 0.04, h * 0.62);
        wings.lineTo(w * 0.24, h * 0.66);
        wings.lineTo(w * 0.5, h * 0.44);
        wings.lineTo(w * 0.76, h * 0.66);
        wings.lineTo(w * 0.96, h * 0.62);
        wings.closePath();
        g.setColor(HULL_MID);
        g.fill(wings);

        Path2D fuselage = new Path2D.Double();
        fuselage.moveTo(w * 0.5, h * 0.96);
        fuselage.lineTo(w * 0.36, h * 0.42);
        fuselage.lineTo(w * 0.44, h * 0.08);
        fuselage.lineTo(w * 0.56, h * 0.08);
        fuselage.lineTo(w * 0.64, h * 0.42);
        fuselage.closePath();
        g.setColor(HOSTILE);
        g.fill(fuselage);
        g.setColor(HULL_DARK);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(fuselage);
        g.draw(wings);

        g.setColor(HOSTILE_GLOW);
        g.fill(new Ellipse2D.Double(w * 0.44, h * 0.6, w * 0.12, h * 0.12));
        g.dispose();
        return image;
    }

    /** A stepped, heavy hull with outboard gun barrels and a pointed prow. */
    private static BufferedImage cruiser() {
        int w = 150;
        int h = 168;
        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);

        Path2D hull = path(w, h, new double[][]{
                {0.50, 0.98}, {0.33, 0.80}, {0.15, 0.70}, {0.11, 0.44}, {0.24, 0.36},
                {0.30, 0.10}, {0.43, 0.03}, {0.57, 0.03}, {0.70, 0.10}, {0.76, 0.36},
                {0.89, 0.44}, {0.85, 0.70}, {0.67, 0.80}});
        g.setColor(HULL_MID);
        g.fill(hull);
        g.setColor(HULL_DARK);
        g.setStroke(new BasicStroke(3f));
        g.draw(hull);

        // Raised spine.
        g.setColor(HULL_LIGHT);
        g.fill(new java.awt.geom.Rectangle2D.Double(w * 0.42, h * 0.14, w * 0.16, h * 0.52));
        g.setColor(HULL_DARK);
        g.fill(new java.awt.geom.Rectangle2D.Double(w * 0.455, h * 0.18, w * 0.09, h * 0.42));

        // Outboard gun barrels reaching past the hull toward the player.
        for (int side = -1; side <= 1; side += 2) {
            double bx = w * (0.5 + side * 0.30);
            g.setColor(HOSTILE);
            g.fill(new java.awt.geom.Rectangle2D.Double(bx - w * 0.045, h * 0.56, w * 0.09, h * 0.30));
            g.setColor(HULL_DARK);
            g.setStroke(new BasicStroke(2f));
            g.draw(new java.awt.geom.Rectangle2D.Double(bx - w * 0.045, h * 0.56, w * 0.09, h * 0.30));
        }

        // Prow and core.
        Path2D prow = path(w, h, new double[][]{{0.50, 0.99}, {0.43, 0.82}, {0.57, 0.82}});
        g.setColor(HOSTILE_GLOW);
        g.fill(prow);
        g.fill(new Ellipse2D.Double(w * 0.445, h * 0.66, w * 0.11, h * 0.08));
        g.dispose();
        return image;
    }

    /** Builds a closed path from fractional coordinates. */
    private static Path2D path(int w, int h, double[][] points) {
        Path2D shape = new Path2D.Double();
        for (int i = 0; i < points.length; i++) {
            double x = w * points[i][0];
            double y = h * points[i][1];
            if (i == 0) {
                shape.moveTo(x, y);
            } else {
                shape.lineTo(x, y);
            }
        }
        shape.closePath();
        return shape;
    }

    /**
     * A war machine rather than a slab: swept wings ending in turret pods, a pointed prow aimed at
     * the player, and an engine bank behind. The prow is at the bottom because enemies face down.
     */
    private static void boss() throws IOException {
        int w = 440;
        int h = 340;
        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);

        // Wings first, so the central hull overlaps them.
        for (int side = -1; side <= 1; side += 2) {
            Path2D wing = mirrored(side, w, h, new double[][]{
                    {0.41, 0.60}, {0.10, 0.40}, {0.03, 0.50},
                    {0.09, 0.72}, {0.26, 0.90}, {0.38, 0.80}});
            g.setColor(HULL_MID.darker());
            g.fill(wing);
            g.setColor(HULL_DARK);
            g.setStroke(new BasicStroke(4f));
            g.draw(wing);

            // Turret pod at the wing tip, plus an inboard one. The sign matches `mirrored`, which
            // flips for side -1, so these land on the wing just drawn rather than the opposite one.
            turret(g, w * (0.5 - side * 0.29), h * 0.735, w * 0.042);
            turret(g, w * (0.5 - side * 0.15), h * 0.80, w * 0.032);
        }

        Path2D hull = new Path2D.Double();
        hull.moveTo(w * 0.50, h * 0.99);
        hull.lineTo(w * 0.40, h * 0.72);
        hull.lineTo(w * 0.385, h * 0.30);
        hull.lineTo(w * 0.44, h * 0.05);
        hull.lineTo(w * 0.56, h * 0.05);
        hull.lineTo(w * 0.615, h * 0.30);
        hull.lineTo(w * 0.60, h * 0.72);
        hull.closePath();
        g.setColor(HULL_MID);
        g.fill(hull);
        g.setColor(HULL_DARK);
        g.setStroke(new BasicStroke(4.5f));
        g.draw(hull);

        // Armoured spine with a hot core.
        g.setColor(HULL_LIGHT);
        g.fill(new java.awt.geom.Rectangle2D.Double(w * 0.455, h * 0.12, w * 0.09, h * 0.56));
        g.setColor(HOSTILE);
        g.fill(new java.awt.geom.Rectangle2D.Double(w * 0.472, h * 0.16, w * 0.056, h * 0.46));

        g.setPaint(new RadialGradientPaint(
                (float) (w * 0.5), (float) (h * 0.44), (float) (w * 0.11),
                new float[]{0f, 0.4f, 1f},
                new Color[]{new Color(255, 240, 200, 255), new Color(255, 150, 60, 170),
                        new Color(255, 90, 40, 0)}));
        g.fill(new Ellipse2D.Double(w * 0.39, h * 0.33, w * 0.22, w * 0.22));

        // Engine bank at the rear.
        for (int i = -1; i <= 1; i++) {
            double ex = w * (0.5 + i * 0.055);
            g.setColor(HULL_DARK);
            g.fill(new java.awt.geom.Rectangle2D.Double(ex - w * 0.018, h * 0.02, w * 0.036, h * 0.07));
            g.setPaint(new RadialGradientPaint(
                    (float) ex, (float) (h * 0.045), (float) (w * 0.035),
                    new float[]{0f, 1f},
                    new Color[]{new Color(140, 210, 255, 220), new Color(140, 210, 255, 0)}));
            g.fill(new Ellipse2D.Double(ex - w * 0.035, h * 0.01, w * 0.07, w * 0.07));
        }

        // Prow blade.
        Path2D prow = new Path2D.Double();
        prow.moveTo(w * 0.50, h * 0.99);
        prow.lineTo(w * 0.455, h * 0.80);
        prow.lineTo(w * 0.545, h * 0.80);
        prow.closePath();
        g.setColor(HOSTILE_GLOW);
        g.fill(prow);
        g.dispose();
        write(image, SPRITES.resolve("boss.png"));
    }

    private static void turret(Graphics2D g, double cx, double cy, double r) {
        g.setColor(HULL_DARK);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setColor(HULL_LIGHT);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setColor(HOSTILE_GLOW);
        g.fill(new Ellipse2D.Double(cx - r * 0.42, cy - r * 0.42, r * 0.84, r * 0.84));
    }

    /** Builds a path from fractional points, mirrored about the vertical centre when side is -1. */
    private static Path2D mirrored(int side, int w, int h, double[][] points) {
        Path2D path = new Path2D.Double();
        for (int i = 0; i < points.length; i++) {
            double fx = 0.5 + side * (points[i][0] - 0.5);
            double x = w * fx;
            double y = h * points[i][1];
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.closePath();
        return path;
    }

    /** Pickup icons: readable at 36px, and none of them a trademark. */
    private static void pickups() throws IOException {
        write(icon(g -> {
            // Three chevrons: speed.
            g.setColor(BRAND);
            for (int i = 0; i < 3; i++) {
                Path2D chevron = new Path2D.Double();
                double y = 26 + i * 26;
                chevron.moveTo(28, y + 18);
                chevron.lineTo(64, y);
                chevron.lineTo(100, y + 18);
                g.setStroke(new BasicStroke(11f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(chevron);
            }
        }), SPRITES.resolve("pickup-speed.png"));

        write(icon(g -> {
            // Medkit cross.
            g.setColor(new Color(0xe8f1f2));
            g.fillRoundRect(18, 18, 92, 92, 22, 22);
            g.setColor(new Color(0xd6263c));
            g.fillRect(56, 34, 16, 60);
            g.fillRect(34, 56, 60, 16);
        }), SPRITES.resolve("pickup-health.png"));

        write(icon(g -> {
            // Shield outline.
            Path2D shield = new Path2D.Double();
            shield.moveTo(64, 14);
            shield.lineTo(110, 34);
            shield.curveTo(110, 88, 90, 106, 64, 116);
            shield.curveTo(38, 106, 18, 88, 18, 34);
            shield.closePath();
            g.setColor(new Color(0x2a4f8f));
            g.fill(shield);
            g.setColor(new Color(0x9fd0ff));
            g.setStroke(new BasicStroke(7f));
            g.draw(shield);
        }), SPRITES.resolve("pickup-shield.png"));

        write(icon(g -> {
            // Three bolts fanning upward, with heads, so it reads as outgoing fire rather than a
            // download arrow.
            g.setColor(new Color(0x7ce8ff));
            double[][] tips = {{34, 34}, {64, 20}, {94, 34}};
            for (double[] tip : tips) {
                g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new java.awt.geom.Line2D.Double(64, 108, tip[0], tip[1] + 16));
                Path2D head = new Path2D.Double();
                head.moveTo(tip[0], tip[1]);
                head.lineTo(tip[0] - 11, tip[1] + 20);
                head.lineTo(tip[0] + 11, tip[1] + 20);
                head.closePath();
                g.fill(head);
            }
        }), SPRITES.resolve("pickup-tri-shot.png"));

        write(icon(g -> {
            // Thick beam with a flared muzzle: mega laser.
            g.setColor(new Color(0xff8ae0));
            g.fillRoundRect(50, 20, 28, 78, 12, 12);
            g.setColor(new Color(0xffffff));
            g.fillRoundRect(58, 28, 12, 54, 6, 6);
            g.setColor(new Color(0xff8ae0));
            Path2D flare = new Path2D.Double();
            flare.moveTo(36, 104);
            flare.lineTo(64, 88);
            flare.lineTo(92, 104);
            flare.closePath();
            g.fill(flare);
        }), SPRITES.resolve("pickup-mega-laser.png"));

        write(icon(g -> {
            // A small ship outline plus a plus sign: an extra life.
            g.setColor(BRAND);
            Path2D ship = new Path2D.Double();
            ship.moveTo(52, 24);
            ship.lineTo(78, 70);
            ship.lineTo(64, 62);
            ship.lineTo(50, 70);
            ship.closePath();
            g.fill(ship);
            g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(64, 82, 64, 112);
            g.drawLine(49, 97, 79, 97);
        }), SPRITES.resolve("pickup-extra-life.png"));
    }

    private static BufferedImage icon(java.util.function.Consumer<Graphics2D> body) {
        BufferedImage image = blank(128, 128);
        Graphics2D g = paint(image);
        body.accept(g);
        g.dispose();
        return image;
    }

    /**
     * Three starfield layers plus a faint nebula. The renderer scrolls them at different rates, which
     * the previous single 3 KB tile could not do.
     */
    private static void background() throws IOException {
        int w = 996;
        int h = 864;
        String[] names = {"background-far", "background-mid", "background-near"};
        int[] counts = {520, 240, 90};
        float[] maxRadius = {0.9f, 1.5f, 2.4f};

        for (int layer = 0; layer < names.length; layer++) {
            BufferedImage image = blank(w, h);
            Graphics2D g = paint(image);
            Random random = new Random(4200 + layer);

            if (layer == 0) {
                // Nebula wash, only on the deepest layer.
                for (int i = 0; i < 5; i++) {
                    double cx = random.nextDouble() * w;
                    double cy = random.nextDouble() * h;
                    double r = 180 + random.nextDouble() * 260;
                    Color tint = i % 2 == 0
                            ? new Color(0x35, 0x2a, 0x6a, 26)
                            : new Color(0x10, 0x3a, 0x4a, 22);
                    g.setPaint(new RadialGradientPaint(
                            (float) cx, (float) cy, (float) r,
                            new float[]{0f, 1f},
                            new Color[]{tint, new Color(0, 0, 0, 0)}));
                    g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
                }
            }

            for (int i = 0; i < counts[layer]; i++) {
                double x = random.nextDouble() * w;
                double y = random.nextDouble() * h;
                double r = 0.35 + random.nextDouble() * maxRadius[layer];
                int brightness = 150 + random.nextInt(106);
                int alpha = layer == 0 ? 150 : 210 + random.nextInt(46);
                g.setColor(new Color(brightness, brightness, Math.min(255, brightness + 18), alpha));
                g.fill(new Ellipse2D.Double(x, y, r * 2, r * 2));
            }
            g.dispose();
            write(image, SPRITES.resolve(names[layer] + ".png"));
        }
    }

    // ----------------------------------------------------------------- audio

    private static void laser() throws IOException {
        double duration = 0.2;
        double[] mix = new double[(int) (duration * SAMPLE_RATE)];
        // Downward pitch sweep: the classic shot.
        for (int i = 0; i < mix.length; i++) {
            double t = i / (double) SAMPLE_RATE;
            double progress = t / duration;
            double freq = 1500 - 1050 * progress;
            double envelope = Math.exp(-5.5 * progress);
            mix[i] = square(freq, t) * 0.35 * envelope;
        }
        writeWav(mix, SOUNDS.resolve("laser.wav"));
    }

    private static void explosionSound() throws IOException {
        double duration = 0.75;
        double[] mix = new double[(int) (duration * SAMPLE_RATE)];
        Random random = new Random(77);
        double lowpass = 0;
        for (int i = 0; i < mix.length; i++) {
            double progress = i / (double) mix.length;
            double envelope = Math.exp(-4.2 * progress);
            double white = random.nextDouble() * 2 - 1;
            // One-pole lowpass, opening then closing, so it reads as a boom not a hiss.
            double cutoff = 0.36 - 0.26 * progress;
            lowpass += cutoff * (white - lowpass);
            double rumble = Math.sin(2 * Math.PI * (70 - 30 * progress) * i / SAMPLE_RATE);
            mix[i] = (lowpass * 0.8 + rumble * 0.35) * envelope * 0.75;
        }
        writeWav(mix, SOUNDS.resolve("explosion.wav"));
    }

    private static void thud() throws IOException {
        double duration = 0.3;
        double[] mix = new double[(int) (duration * SAMPLE_RATE)];
        Random random = new Random(21);
        for (int i = 0; i < mix.length; i++) {
            double progress = i / (double) mix.length;
            double envelope = Math.exp(-11 * progress);
            double body = Math.sin(2 * Math.PI * (150 - 90 * progress) * i / SAMPLE_RATE);
            double grit = (random.nextDouble() * 2 - 1) * 0.3;
            mix[i] = (body + grit) * envelope * 0.55;
        }
        writeWav(mix, SOUNDS.resolve("collision.wav"));
    }

    /** A short descending arpeggio. Replaces a 172-second clip ripped from a video. */
    private static void gameOverSting() throws IOException {
        double duration = 1.9;
        double[] mix = new double[(int) (duration * SAMPLE_RATE)];
        int[] notes = {57, 53, 50, 45};
        for (int i = 0; i < notes.length; i++) {
            addTone(mix, i * 0.34, 0.5, pitch(notes[i]), 0.3, Wave.SQUARE);
            addTone(mix, i * 0.34, 0.5, pitch(notes[i] - 12), 0.18, Wave.TRIANGLE);
        }
        writeWav(mix, SOUNDS.resolve("game-over.wav"));
    }

    private enum Wave { SQUARE, TRIANGLE, SINE }

    private static double pitch(int midiNote) {
        double frequency = 440 * Math.pow(2, (midiNote - 69) / 12.0);
        return frequency;
    }

    private static void addTone(double[] mix, double startSeconds, double durationSeconds,
                                double frequency, double amplitude, Wave wave) {
        int start = (int) (startSeconds * SAMPLE_RATE);
        int length = (int) (durationSeconds * SAMPLE_RATE);
        int attack = Math.max(1, length / 30);
        int release = Math.max(1, length / 3);

        for (int i = 0; i < length; i++) {
            int index = start + i;
            if (index < 0 || index >= mix.length) {
                continue;
            }
            double t = index / (double) SAMPLE_RATE;
            double sample = switch (wave) {
                case SQUARE -> square(frequency, t);
                case TRIANGLE -> triangle(frequency, t);
                case SINE -> Math.sin(2 * Math.PI * frequency * t);
            };
            // Short attack, long release, so notes do not click.
            double envelope = 1;
            if (i < attack) {
                envelope = i / (double) attack;
            } else if (i > length - release) {
                envelope = (length - i) / (double) release;
            }
            mix[index] += sample * amplitude * envelope;
        }
    }

    private static double square(double frequency, double t) {
        double phase = (t * frequency) % 1.0;
        return phase < 0.5 ? 1 : -1;
    }

    private static double triangle(double frequency, double t) {
        double phase = (t * frequency) % 1.0;
        return 4 * Math.abs(phase - 0.5) - 1;
    }

    /** 16-bit mono PCM WAV, written by hand so the tool needs no audio libraries. */
    private static void writeWav(double[] samples, Path target) throws IOException {
        // Normalise to just under full scale rather than clipping the sum of layered voices.
        double peak = 0;
        for (double sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        double gain = peak > 0 ? 0.89 / peak : 1;

        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        for (double sample : samples) {
            int value = (int) Math.round(Math.max(-1, Math.min(1, sample * gain)) * 32767);
            pcm.write(value & 0xff);
            pcm.write((value >> 8) & 0xff);
        }
        byte[] data = pcm.toByteArray();

        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes());
        header.putInt(36 + data.length);
        header.put("WAVE".getBytes());
        header.put("fmt ".getBytes());
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) 1);
        header.putInt(SAMPLE_RATE);
        header.putInt(SAMPLE_RATE * 2);
        header.putShort((short) 2);
        header.putShort((short) 16);
        header.put("data".getBytes());
        header.putInt(data.length);

        try (var out = Files.newOutputStream(target)) {
            out.write(header.array());
            out.write(data);
        }
        System.out.println("wrote " + target);
    }

    // ----------------------------------------------------------------- shared

    private static BufferedImage blank(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        return image;
    }

    private static Graphics2D paint(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    private static void write(BufferedImage image, Path target) throws IOException {
        ImageIO.write(image, "png", target.toFile());
        System.out.println("wrote " + target);
    }

    private GenerateAssets() {
    }
}
