import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
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
 * Three rules keep that true, and CI only catches a breach of them once someone builds on a
 * different machine:
 *
 *   1. Trig goes through {@link StrictMath}, never {@code Math}. Math.sin, cos and pow are allowed
 *      one ulp of error and may differ between JVM implementations; only StrictMath is specified
 *      bit for bit. Math.min/max/abs/sqrt are exact and fine.
 *   2. Never draw text. Graphics2D.drawString depends on which fonts the host has installed, so a
 *      rendered glyph is not reproducible. Every mark here is a shape -- see insignia() for how to
 *      draw a symbol without a font.
 *   3. One Random per image, seeded from its own constant. Sharing a Random across images makes
 *      every image depend on the order the others were drawn in.
 *
 * Deliberately NOT generated, because they are the author's own work and better than anything here
 * would produce: the projectiles, the explosion frame sequences, and the music. The player ships are
 * the author's own too, but arrive as one spritesheet in {@code tools/art} and are cut up here.
 */
public final class GenerateAssets {

    private static final Path SPRITES = Path.of("src/main/resources/sprites");
    private static final Path SOUNDS = Path.of("src/main/resources/sounds");
    private static final Path ART = Path.of("tools/art");

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

        players();
        asteroids();
        enemies();
        bosses();
        monsters();
        creatures();
        mech();
        pickups();
        insignia();
        backgrounds();

        laser();
        explosionSound();
        thud();
        gameOverSting();
        levelClearSting();
        lowHealthAlarm();

        System.out.println("done");
    }

    // ---------------------------------------------------------- player ships

    /**
     * Bank poses in the spritesheet's column order, which runs hardest *right* to hardest left.
     *
     * The sheet's leftmost column is a ship rolled to its right, not to the left of the frame. Naming
     * the columns the other way round is what made holding D bank the ship left.
     */
    private static final String[] POSES =
            {"bank-right", "right", "straight", "left", "bank-left"};

    /** Rows in the spritesheet, top to bottom. */
    private static final int SHEET_ROWS = 5;

    /**
     * Which spritesheet row supplies each player's hull, player one first.
     *
     * Row 3 is green and row 1 red: green reads as friendly against the red hostile palette, so
     * player one flies it. ponytail: rows 0 (engines off), 2 (rolled) and 4 (second green) go
     * unused -- wire them up if the ships ever need an idle or a barrel roll.
     */
    private static final int[] PLAYER_ROWS = {3, 1};

    /**
     * Common canvas every cut frame is padded to.
     *
     * The sheet's cells differ in size by up to 30px. Decoding at a fixed on-screen size preserves
     * each image's own aspect ratio, so unpadded frames would draw at visibly different sizes and
     * the ship would appear to grow as it banked. Padding to one canvas makes every pose scale
     * identically.
     */
    private static final int PLAYER_FRAME_WIDTH = 180;
    private static final int PLAYER_FRAME_HEIGHT = 192;

    /** Ignores stray antialiasing specks when hunting for ships; the smallest real one is ~12,700px. */
    private static final int MIN_SPRITE_PIXELS = 800;

    /** The checkerboard is two near-white greys; every ship pixel is tinted or much darker. */
    private static final int CHECKER_MIN_BRIGHTNESS = 238;

    /**
     * Garage paint jobs, as a hue applied to player one's cut frames.
     *
     * A hue rotation rather than a repaint: the sheet's shading and its black outlines survive
     * untouched, since outlines are unsaturated and a hue swap cannot move them. Chrome is the odd
     * one out -- it desaturates rather than recolours, so its hue is arbitrary.
     */
    private enum Paintwork {
        AZURE(0.55f, 1.0f),
        AMBER(0.10f, 1.0f),
        VIOLET(0.76f, 1.0f),
        CHROME(0.00f, 0.15f);

        private final float hue;
        private final float saturationScale;

        Paintwork(float hue, float saturationScale) {
            this.hue = hue;
            this.saturationScale = saturationScale;
        }
    }

    /** Body kits, drawn as transparent decals so one set serves every paint job. */
    private static final String[] KITS = {"fins", "armour", "lance"};

    private static void players() throws IOException {
        BufferedImage sheet = ImageIO.read(ART.resolve("spritesheet.png").toFile());
        boolean[] background = keyOutCheckerboard(sheet);
        List<Rectangle> cells = findShips(sheet, background);

        for (int player = 0; player < PLAYER_ROWS.length; player++) {
            for (int pose = 0; pose < POSES.length; pose++) {
                Rectangle cell = cells.get(PLAYER_ROWS[player] * POSES.length + pose);
                BufferedImage frame = cut(sheet, background, cell);
                String name = "player/p" + (player + 1) + "-" + POSES[pose];
                writeHull(frame, name);
            }
        }

        // Everything below is bought in the garage. Both sets derive from player one's row, so the
        // whole cosmetic catalogue costs one extra cut per pose rather than new hand-drawn art.
        for (int pose = 0; pose < POSES.length; pose++) {
            Rectangle cell = cells.get(PLAYER_ROWS[0] * POSES.length + pose);
            BufferedImage hull = cut(sheet, background, cell);

            for (Paintwork paint : Paintwork.values()) {
                BufferedImage repainted = recolour(hull, paint);
                String name = "player/" + paint.name().toLowerCase() + "-" + POSES[pose];
                writeHull(repainted, name);
            }
            for (int kit = 0; kit < KITS.length; kit++) {
                BufferedImage decal = kitOverlay(hull, kit);
                String name = "player/kit-" + KITS[kit] + "-" + POSES[pose];
                write(decal, SPRITES.resolve(name + ".png"));
                write(quarterTurnLeft(decal), SPRITES.resolve(name + "-side.png"));
            }
        }
    }

    /**
     * A hull pose in all four cuts it is flown in: clean and scorched, nose-up and turned.
     *
     * The turned pair exists because the side-view level draws its ships without a render-time
     * rotation, so the frame and the collision box can be the same shape -- the same bargain the
     * level-9 hostiles make. {@link #quarterTurnLeft} takes a nose-up hull to a nose-right one,
     * which is the way a pilot faces down a right-to-left arena.
     *
     * Note this is the same rotation the renderer used to apply per frame, so the side cuts are
     * pixel-identical to what was on screen before. What they buy is the box, not the look.
     */
    private static void writeHull(BufferedImage frame, String name) throws IOException {
        write(frame, SPRITES.resolve(name + ".png"));
        write(hitFlash(frame), SPRITES.resolve(name + "-hit.png"));
        BufferedImage side = quarterTurnLeft(frame);
        write(side, SPRITES.resolve(name + "-side.png"));
        // "-hit-side" rather than "-side-hit": the Sprite constants derive a turned frame's name by
        // suffixing "_SIDE", so the turn has to be the last thing in the name.
        write(hitFlash(side), SPRITES.resolve(name + "-hit-side.png"));
    }

    /** Rotates every opaque pixel's hue, preserving its brightness, shading and alpha. */
    private static BufferedImage recolour(BufferedImage frame, Paintwork paint) {
        BufferedImage painted = blank(frame.getWidth(), frame.getHeight());
        float[] hsb = new float[3];
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                int argb = frame.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha == 0) {
                    continue;
                }
                Color.RGBtoHSB((argb >> 16) & 0xff, (argb >> 8) & 0xff, argb & 0xff, hsb);
                float saturation = Math.min(1f, hsb[1] * paint.saturationScale);
                int rgb = Color.HSBtoRGB(paint.hue, saturation, hsb[2]);
                painted.setRGB(x, y, (alpha << 24) | (rgb & 0xffffff));
            }
        }
        return painted;
    }

    /**
     * A body kit decal for one pose, sized and placed from the hull it will sit on.
     *
     * Measured off the hull's alpha bounding box rather than drawn at fixed canvas coordinates: the
     * bank poses are narrower and offset, so a decal at fixed coordinates would float off the wing
     * on everything except the straight-ahead frame.
     */
    private static BufferedImage kitOverlay(BufferedImage hull, int kit) {
        Rectangle box = opaqueBounds(hull);
        BufferedImage decal = blank(hull.getWidth(), hull.getHeight());
        if (box == null) {
            return decal;
        }
        Graphics2D g = paint(decal);
        switch (kit) {
            case 0 -> {
                // Delta fins: swept blades off the trailing corners.
                g.setColor(HULL_LIGHT);
                g.fill(finAt(box, -1));
                g.fill(finAt(box, 1));
                g.setColor(BRAND);
                g.fill(new Ellipse2D.Double(box.getCenterX() - box.width * 0.03,
                        box.getMaxY() - box.height * 0.10, box.width * 0.06, box.height * 0.05));
            }
            case 1 -> {
                // Ablative plates: slabs bolted along both flanks.
                g.setColor(HULL_MID);
                double plateW = box.width * 0.13;
                double plateH = box.height * 0.30;
                double plateY = box.getCenterY() - plateH / 2;
                g.fill(new Rectangle2D.Double(box.x + box.width * 0.04, plateY, plateW, plateH));
                g.fill(new Rectangle2D.Double(box.getMaxX() - box.width * 0.04 - plateW, plateY,
                        plateW, plateH));
                g.setColor(HULL_LIGHT);
                g.setStroke(new BasicStroke((float) Math.max(1, box.width * 0.012)));
                g.draw(new Rectangle2D.Double(box.x + box.width * 0.04, plateY, plateW, plateH));
                g.draw(new Rectangle2D.Double(box.getMaxX() - box.width * 0.04 - plateW, plateY,
                        plateW, plateH));
            }
            default -> {
                // Nose lance: a spike off the prow with a hot tip.
                g.setColor(HULL_LIGHT);
                g.fill(path(1, 1, new double[][]{
                        {box.getCenterX() - box.width * 0.045, box.y + box.height * 0.12},
                        {box.getCenterX() + box.width * 0.045, box.y + box.height * 0.12},
                        {box.getCenterX(), box.y - box.height * 0.10}}));
                g.setColor(BRAND);
                g.fill(new Ellipse2D.Double(box.getCenterX() - box.width * 0.022,
                        box.y - box.height * 0.09, box.width * 0.044, box.height * 0.045));
            }
        }
        g.dispose();
        return decal;
    }

    /** One swept fin off the hull's trailing edge; side is -1 for left, 1 for right. */
    private static Path2D finAt(Rectangle box, int side) {
        double rootX = side < 0 ? box.x + box.width * 0.16 : box.getMaxX() - box.width * 0.16;
        double tipX = rootX + side * box.width * 0.20;
        return path(1, 1, new double[][]{
                {rootX, box.getMaxY() - box.height * 0.30},
                {rootX, box.getMaxY() - box.height * 0.06},
                {tipX, box.getMaxY() + box.height * 0.02}});
    }

    /** Bounding box of everything non-transparent, or null for an empty frame. */
    private static Rectangle opaqueBounds(BufferedImage frame) {
        int minX = frame.getWidth();
        int minY = frame.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                if ((frame.getRGB(x, y) >>> 24) == 0) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < 0) {
            return null;
        }
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /**
     * Marks the sheet's transparent pixels.
     *
     * The supplied sheet has no alpha channel -- its transparency is a baked-in near-white
     * checkerboard. Flooding inward from the border keys that out without punching holes in the
     * ships' own white highlights, which a plain colour test would; the black outlines stop the fill.
     */
    private static boolean[] keyOutCheckerboard(BufferedImage sheet) {
        int w = sheet.getWidth();
        int h = sheet.getHeight();
        boolean[] background = new boolean[w * h];
        Deque<int[]> pending = new ArrayDeque<>();

        for (int x = 0; x < w; x++) {
            spreadInto(sheet, background, pending, x, 0);
            spreadInto(sheet, background, pending, x, h - 1);
        }
        for (int y = 0; y < h; y++) {
            spreadInto(sheet, background, pending, 0, y);
            spreadInto(sheet, background, pending, w - 1, y);
        }
        while (!pending.isEmpty()) {
            int[] at = pending.remove();
            spreadInto(sheet, background, pending, at[0] + 1, at[1]);
            spreadInto(sheet, background, pending, at[0] - 1, at[1]);
            spreadInto(sheet, background, pending, at[0], at[1] + 1);
            spreadInto(sheet, background, pending, at[0], at[1] - 1);
        }
        return background;
    }

    private static void spreadInto(BufferedImage sheet, boolean[] background, Deque<int[]> pending,
                                   int x, int y) {
        if (x < 0 || y < 0 || x >= sheet.getWidth() || y >= sheet.getHeight()) {
            return;
        }
        int index = y * sheet.getWidth() + x;
        if (background[index] || !isChecker(sheet.getRGB(x, y))) {
            return;
        }
        background[index] = true;
        pending.add(new int[]{x, y});
    }

    private static boolean isChecker(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        boolean grey = Math.abs(r - g) < 4 && Math.abs(g - b) < 4;
        boolean checker = grey && r >= CHECKER_MIN_BRIGHTNESS;
        return checker;
    }

    /**
     * Bounding boxes of the sheet's ships in reading order.
     *
     * Once the checkerboard is keyed out the ships are the only islands left, so labelling connected
     * regions finds them without needing the grid pitch -- which is just as well, since the cells are
     * neither evenly spaced nor a whole number of pixels apart.
     */
    private static List<Rectangle> findShips(BufferedImage sheet, boolean[] background) {
        int w = sheet.getWidth();
        int h = sheet.getHeight();
        boolean[] visited = new boolean[w * h];
        List<Rectangle> ships = new ArrayList<>();
        Deque<int[]> pending = new ArrayDeque<>();

        for (int startY = 0; startY < h; startY++) {
            for (int startX = 0; startX < w; startX++) {
                if (visited[startY * w + startX] || background[startY * w + startX]) {
                    continue;
                }
                visited[startY * w + startX] = true;
                pending.add(new int[]{startX, startY});
                int minX = startX;
                int maxX = startX;
                int minY = startY;
                int maxY = startY;
                int pixels = 0;

                while (!pending.isEmpty()) {
                    int[] at = pending.remove();
                    pixels++;
                    minX = Math.min(minX, at[0]);
                    maxX = Math.max(maxX, at[0]);
                    minY = Math.min(minY, at[1]);
                    maxY = Math.max(maxY, at[1]);
                    // Eight-connected: antialiased outlines meet only diagonally in places.
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            int nx = at[0] + dx;
                            int ny = at[1] + dy;
                            if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                                continue;
                            }
                            int index = ny * w + nx;
                            if (visited[index] || background[index]) {
                                continue;
                            }
                            visited[index] = true;
                            pending.add(new int[]{nx, ny});
                        }
                    }
                }
                if (pixels >= MIN_SPRITE_PIXELS) {
                    ships.add(new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1));
                }
            }
        }

        int expected = SHEET_ROWS * POSES.length;
        if (ships.size() != expected) {
            throw new IllegalStateException(
                    "expected " + expected + " ships in the spritesheet, found " + ships.size());
        }
        // Rows are far enough apart to sort on centre, then each row left to right.
        ships.sort(Comparator.comparingInt(ship -> ship.y + ship.height / 2));
        for (int row = 0; row < ships.size(); row += POSES.length) {
            ships.subList(row, row + POSES.length).sort(Comparator.comparingInt(ship -> ship.x));
        }
        return ships;
    }

    /** Cuts one ship out of the sheet, centred on the common canvas, checkerboard keyed to alpha 0. */
    private static BufferedImage cut(BufferedImage sheet, boolean[] background, Rectangle cell) {
        if (cell.width > PLAYER_FRAME_WIDTH || cell.height > PLAYER_FRAME_HEIGHT) {
            throw new IllegalStateException("ship " + cell + " does not fit the padded frame");
        }
        BufferedImage frame = blank(PLAYER_FRAME_WIDTH, PLAYER_FRAME_HEIGHT);
        int offsetX = (PLAYER_FRAME_WIDTH - cell.width) / 2;
        int offsetY = (PLAYER_FRAME_HEIGHT - cell.height) / 2;

        for (int y = 0; y < cell.height; y++) {
            for (int x = 0; x < cell.width; x++) {
                if (background[(cell.y + y) * sheet.getWidth() + cell.x + x]) {
                    continue;
                }
                int rgb = sheet.getRGB(cell.x + x, cell.y + y);
                frame.setRGB(offsetX + x, offsetY + y, 0xff000000 | rgb);
            }
        }
        return frame;
    }

    /**
     * The damage frame for a pose.
     *
     * Baked here rather than blended at runtime because a JavaFX Canvas has no per-draw tint, so the
     * renderer would otherwise need an effect pass per ship per frame.
     */
    private static BufferedImage hitFlash(BufferedImage frame) {
        BufferedImage flashed = blank(frame.getWidth(), frame.getHeight());
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                int argb = frame.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha == 0) {
                    continue;
                }
                int r = mixToward((argb >> 16) & 0xff, HOSTILE_GLOW.getRed());
                int g = mixToward((argb >> 8) & 0xff, HOSTILE_GLOW.getGreen());
                int b = mixToward(argb & 0xff, HOSTILE_GLOW.getBlue());
                flashed.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return flashed;
    }

    private static int mixToward(int value, int target) {
        int mixed = channel((int) Math.round(value * 0.45 + target * 0.55));
        return mixed;
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
                double x = size / 2.0 + StrictMath.cos(angle) * wobble;
                double y = size / 2.0 + StrictMath.sin(angle) * wobble;
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

    /**
     * A level's inhabitants: the palette and build of the ships that defend it.
     *
     * Every level fields its own faction, so arriving somewhere new looks like meeting a different
     * enemy rather than the same three ships under a new sky.
     *
     * @param directory resource directory, one per level
     * @param hull      structural colour: wings and plating
     * @param accent    fuselage colour, the ship's dominant read
     * @param glow      cockpit and core colour
     * @param style     mechanical navies get barrels and spines, organic ones chitin and segments
     */
    private record Faction(String directory, Color hull, Color accent, Color glow, HullStyle style,
                           boolean sideways) {

        /** A faction whose level runs top-down, which is all of them but the side-view leg. */
        Faction(String directory, Color hull, Color accent, Color glow, HullStyle style) {
            this(directory, hull, accent, glow, style, false);
        }
    }

    private enum HullStyle { MECHANICAL, ORGANIC }

    /**
     * Ashfall: fire, ash and industry.
     *
     * One palette for the whole galaxy, worn by its grunts and its flagships alike. That shared
     * accent is the strongest cue that a boss belongs to the galaxy it is fought in, and it costs
     * nothing -- the same three colours appear in the Faction rows and the BossProfile rows.
     */
    private static final Color ASH_HULL = new Color(0x3a1c10);
    private static final Color ASH_ACCENT = new Color(0xe8641c);
    private static final Color ASH_GLOW = new Color(0xffd07a);

    /**
     * Cryonis: ice and water.
     *
     * The same one-palette-per-galaxy trick as Ashfall, turned cold. A pale cyan accent against a
     * deep blue hull, and a glow that is cold but still saturated. A near-white glow was tried
     * first and the core came out as a flare that swallowed the hull -- the same way Ashfall's two
     * palest flagships are its least readable.
     */
    private static final Color CRYO_HULL = new Color(0x1b2f42);
    private static final Color CRYO_ACCENT = new Color(0x4fd0e8);
    private static final Color CRYO_GLOW = new Color(0x7fe4f5);

    /**
     * Tempest: storm and gas giant.
     *
     * One palette again, and the same warning heeded twice over -- the glow is a saturated pale
     * blue rather than a near-white, because this galaxy's hulls are already light and a white core
     * on a light hull is the flare that swallows it.
     */
    private static final Color STORM_HULL = new Color(0x1e2740);
    private static final Color STORM_ACCENT = new Color(0x7ea8ff);
    private static final Color STORM_GLOW = new Color(0x9ec2ff);

    /**
     * Null: void, gravity, and the thing at the bottom of it.
     *
     * One palette again, at the dark end of the register. These are the darkest hulls in the game,
     * so the glow is a mid-saturated violet rather than a pale one -- for the third galaxy running,
     * because a near-white core on any hull is the flare that swallows it, and that is the one
     * mistake this palette has managed to repeat in every galaxy that tried it.
     *
     * The accent is worn by the ships and nowhere else. It stays out of Theme.tintB, which means a
     * lit surface to rocks() and ground(); see the note on the Null theme rows.
     */
    private static final Color VOID_HULL = new Color(0x1d1830);
    private static final Color VOID_ACCENT = new Color(0x9a6bff);
    private static final Color VOID_GLOW = new Color(0xa274f0);

    private static final Faction[] FACTIONS = {
            // Level 1 keeps the original palette exactly, so the opening minutes stay tuned.
            new Faction("level-1", HULL_MID, HOSTILE, HOSTILE_GLOW, HullStyle.MECHANICAL),
            new Faction("level-2", new Color(0x2f5a33), new Color(0x7fae2a), new Color(0xffd24a),
                    HullStyle.ORGANIC),
            new Faction("level-3", new Color(0x2a4a44), new Color(0x4fae7a), new Color(0xc07aff),
                    HullStyle.ORGANIC),
            new Faction("level-4", new Color(0x4a3a2c), new Color(0xb0692a), new Color(0xffcb5a),
                    HullStyle.MECHANICAL),
            new Faction("level-5", new Color(0x2c3f4a), new Color(0x4fa8c9), new Color(0xe8faff),
                    HullStyle.MECHANICAL),
            new Faction("level-6", new Color(0x33294a), new Color(0x7a3fc9), new Color(0xd9a6ff),
                    HullStyle.MECHANICAL),
            new Faction("level-7", new Color(0x4a2c22), new Color(0xd6321a), new Color(0xffb04a),
                    HullStyle.MECHANICAL),
            new Faction("level-8", new Color(0x34383f), new Color(0x8d94a0), new Color(0x9fd0ff),
                    HullStyle.MECHANICAL),
            // Level 9 runs sideways, so its hulls are cut pointing left. Organic, to sit with the
            // burrowing thing that ends the level.
            new Faction("level-9", new Color(0x3b2f22), new Color(0xc08a2e), new Color(0xffe07a),
                    HullStyle.ORGANIC, true),
            new Faction("level-10", new Color(0x241a2e), new Color(0x8a2f5a), new Color(0xff5ea8),
                    HullStyle.ORGANIC),
            // ---- Galaxy 2: Ashfall (levels 11-20) ----------------------------------------
            // Mechanical almost throughout: this is a worked, industrial galaxy. Levels 13 and 19
            // are the exceptions, where whatever lives in the ash has started fighting back.
            new Faction("level-11", ASH_HULL, ASH_ACCENT, ASH_GLOW, HullStyle.MECHANICAL),
            new Faction("level-12", new Color(0x3f2214), ASH_ACCENT, ASH_GLOW, HullStyle.MECHANICAL),
            new Faction("level-13", new Color(0x45301a), new Color(0xd87a24), ASH_GLOW,
                    HullStyle.ORGANIC),
            new Faction("level-14", new Color(0x33170d), new Color(0xf0731a), ASH_GLOW,
                    HullStyle.MECHANICAL),
            new Faction("level-15", new Color(0x2c1a14), new Color(0xff8a2a), ASH_GLOW,
                    HullStyle.MECHANICAL),
            new Faction("level-16", new Color(0x3a2018), new Color(0xe05a1c), ASH_GLOW,
                    HullStyle.MECHANICAL),
            // Sunward Dive: the side-on leg. The hulls are cut pointing left to match, which is
            // what this flag does -- see the note on Theme.sideways for the other half of the pair.
            new Faction("level-17", new Color(0x4a2a12), new Color(0xffa32a),
                    new Color(0xfff0c0), HullStyle.MECHANICAL, true),
            new Faction("level-18", new Color(0x452414), new Color(0xff9420),
                    new Color(0xfff0c0), HullStyle.MECHANICAL),
            new Faction("level-19", new Color(0x3c2418), new Color(0xc85a1e), ASH_GLOW,
                    HullStyle.ORGANIC),
            new Faction("level-20", new Color(0x2a1108), new Color(0xff5a10), ASH_GLOW,
                    HullStyle.MECHANICAL),
            // ---- Galaxy 3: Cryonis (levels 21-30) ----------------------------------------
            // Mechanical where the ice is worked and organic where it is inhabited: 24, 26 and 27
            // are the levels with something living in them, and they field the creatures too.
            // Frost Ring: side-on, so the hulls are cut pointing left. See Theme.sideways.
            new Faction("level-21", CRYO_HULL, CRYO_ACCENT, CRYO_GLOW, HullStyle.MECHANICAL, true),
            new Faction("level-22", new Color(0x223a52), new Color(0x63d8ec), CRYO_GLOW,
                    HullStyle.MECHANICAL),
            new Faction("level-23", new Color(0x2a4258), new Color(0x7ae0f0), CRYO_GLOW,
                    HullStyle.MECHANICAL),
            new Faction("level-24", new Color(0x14324a), new Color(0x3fc0dc), new Color(0xc8eeff),
                    HullStyle.ORGANIC),
            new Faction("level-25", new Color(0x1d2c3e), new Color(0x4fd0e8), CRYO_GLOW,
                    HullStyle.MECHANICAL),
            new Faction("level-26", new Color(0x101d2c), new Color(0x2fa8c8), new Color(0xb0e4ff),
                    HullStyle.ORGANIC),
            new Faction("level-27", new Color(0x24485a), new Color(0x8ae8f4), CRYO_GLOW,
                    HullStyle.ORGANIC),
            new Faction("level-28", new Color(0x1f3648), new Color(0x5cd4ea), CRYO_GLOW,
                    HullStyle.MECHANICAL),
            // Shatter Drift: the galaxy's second side-on leg, and the only galaxy to have two.
            new Faction("level-29", new Color(0x263c50), new Color(0x9af0ff),
                    new Color(0xa8eeff), HullStyle.MECHANICAL, true),
            new Faction("level-30", new Color(0x0e1a28), new Color(0x4fd0e8),
                    new Color(0xa8eeff), HullStyle.MECHANICAL),
            // ---- Galaxy 4: Tempest (levels 31-40) ----------------------------------------
            // Six of the ten levels are ATMOSPHERE, so the factions carry more of the work of
            // telling one leg from the next than in any galaxy before this one. Organic on 32, 35
            // and 36, which are the three levels with something living in them.
            new Faction("level-31", STORM_HULL, STORM_ACCENT, STORM_GLOW, HullStyle.MECHANICAL),
            new Faction("level-32", new Color(0x1a2238), new Color(0x6f9cf5), new Color(0xc4dbff),
                    HullStyle.ORGANIC),
            new Faction("level-33", new Color(0x2c3a5c), new Color(0x92b8ff), STORM_GLOW,
                    HullStyle.MECHANICAL),
            new Faction("level-34", new Color(0x2a3450), new Color(0x8ab0ff), STORM_GLOW,
                    HullStyle.MECHANICAL),
            new Faction("level-35", new Color(0x202a44), new Color(0x6890e8), new Color(0xb8d0ff),
                    HullStyle.ORGANIC),
            new Faction("level-36", new Color(0x141c30), new Color(0x5f86e0), new Color(0xaac8ff),
                    HullStyle.ORGANIC),
            new Faction("level-37", new Color(0x1c2640), new Color(0x7aa4fb), STORM_GLOW,
                    HullStyle.MECHANICAL),
            new Faction("level-38", new Color(0x2e3a58), new Color(0x9cc0ff), STORM_GLOW,
                    HullStyle.MECHANICAL),
            // Lightning Reach: the galaxy's one side-on leg, so its hulls are cut pointing left.
            new Faction("level-39", new Color(0x263254), new Color(0xaecdff),
                    new Color(0xd6e6ff), HullStyle.MECHANICAL, true),
            new Faction("level-40", new Color(0x121a2e), STORM_ACCENT, new Color(0xb4d0ff),
                    HullStyle.MECHANICAL),
            // ---- Galaxy 5: Null (levels 41-50) -------------------------------------------
            // The darkest navy in the game, so the separation between legs is carried by the
            // accent rather than the hull -- there is not much room left below 0x1d1830. Accents
            // run from 0x6f46cc to 0xc0a0ff, brightest at the Photon Ring and dimmest in the
            // Shell, which is the one level with rock instead of sky around it.
            //
            // Organic on 42, 43 and 46, the three levels with something living in them.
            new Faction("level-41", VOID_HULL, VOID_ACCENT, VOID_GLOW, HullStyle.MECHANICAL),
            new Faction("level-42", new Color(0x241c3c), new Color(0x8a5ef0),
                    new Color(0xc4a8ff), HullStyle.ORGANIC),
            new Faction("level-43", new Color(0x1a1530), new Color(0x7d52e0),
                    new Color(0xb493ff), HullStyle.ORGANIC),
            new Faction("level-44", new Color(0x272040), new Color(0xa87eff), VOID_GLOW,
                    HullStyle.MECHANICAL),
            // Tidal Shear: the galaxy's side-on leg, so its hulls are cut pointing left.
            new Faction("level-45", new Color(0x2b2348), new Color(0xb490ff),
                    new Color(0xd0bcff), HullStyle.MECHANICAL, true),
            new Faction("level-46", new Color(0x120e22), new Color(0x6f46cc),
                    new Color(0xa87eff), HullStyle.ORGANIC),
            new Faction("level-47", new Color(0x211a38), VOID_ACCENT, VOID_GLOW,
                    HullStyle.MECHANICAL),
            // Photon Ring: the brightest thing in the galaxy is the backdrop, so its navy is the
            // one that has to compete with it.
            new Faction("level-48", new Color(0x2e2652), new Color(0xc0a0ff),
                    new Color(0xdcccff), HullStyle.MECHANICAL),
            new Faction("level-49", new Color(0x100c1e), new Color(0x8257e8),
                    new Color(0xab86ff), HullStyle.MECHANICAL),
            new Faction("level-50", new Color(0x0d0a18), VOID_ACCENT,
                    new Color(0xc8b0ff), HullStyle.MECHANICAL),
    };

    /** Three hostile silhouettes per level, angular and pointing down the arena at the player. */
    private static void enemies() throws IOException {
        for (Faction faction : FACTIONS) {
            Path directory = SPRITES.resolve(faction.directory());
            write(pointed(faction, scout(faction)), directory.resolve("enemy-scout.png"));
            write(pointed(faction, fighter(faction)), directory.resolve("enemy-fighter.png"));
            write(pointed(faction, cruiser(faction)), directory.resolve("enemy-cruiser.png"));
        }
    }

    /**
     * Turns a hull to point the way its level's hostiles travel.
     *
     * Baked into the art rather than rotated at draw time, so the sprite and the collision box
     * agree to the pixel -- the engine has no facing for enemies and does not need one.
     */
    private static BufferedImage pointed(Faction faction, BufferedImage noseDown) {
        return faction.sideways() ? quarterTurnLeft(noseDown) : noseDown;
    }

    /**
     * A quarter turn clockwise: nose-down becomes nose-left, and nose-up becomes nose-right.
     *
     * A pixel copy rather than a rotated draw: no interpolation and no dependence on rendering
     * hints, so the output is byte-identical on every JDK the build might run on. That matters
     * because CI regenerates these and fails if anything moved.
     */
    private static BufferedImage quarterTurnLeft(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage turned = blank(h, w);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                turned.setRGB(h - 1 - y, x, source.getRGB(x, y));
            }
        }
        return turned;
    }

    private static final double[][] SCOUT_MECHANICAL = {
            {0.50, 0.96}, {0.34, 0.60}, {0.05, 0.28}, {0.17, 0.17}, {0.40, 0.32},
            {0.44, 0.05}, {0.56, 0.05}, {0.60, 0.32}, {0.83, 0.17}, {0.95, 0.28},
            {0.66, 0.60}};

    /** Forward-swept mandibles where the mechanical dart has wingtips. */
    private static final double[][] SCOUT_ORGANIC = {
            {0.50, 0.97}, {0.38, 0.68}, {0.10, 0.46}, {0.03, 0.20}, {0.19, 0.31},
            {0.42, 0.27}, {0.46, 0.04}, {0.54, 0.04}, {0.58, 0.27}, {0.81, 0.31},
            {0.97, 0.20}, {0.90, 0.46}, {0.62, 0.68}};

    /** A dart with swept-back wings, so the fastest enemy reads as an interceptor. */
    private static BufferedImage scout(Faction faction) {
        int w = 104;
        int h = 92;
        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);
        boolean organic = faction.style() == HullStyle.ORGANIC;

        Path2D body = path(w, h, organic ? SCOUT_ORGANIC : SCOUT_MECHANICAL);
        g.setColor(faction.accent());
        g.fill(body);
        g.setColor(HULL_DARK);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(body);

        g.setColor(HULL_DARK);
        g.fill(new Ellipse2D.Double(w * 0.43, h * 0.40, w * 0.14, h * 0.20));
        g.setColor(faction.glow());
        if (organic) {
            // An eye cluster rather than a canopy.
            g.fill(new Ellipse2D.Double(w * 0.445, h * 0.43, w * 0.045, h * 0.06));
            g.fill(new Ellipse2D.Double(w * 0.510, h * 0.43, w * 0.045, h * 0.06));
            g.fill(new Ellipse2D.Double(w * 0.478, h * 0.51, w * 0.045, h * 0.06));
        } else {
            g.fill(new Ellipse2D.Double(w * 0.455, h * 0.44, w * 0.09, h * 0.12));
        }
        g.dispose();
        return image;
    }

    private static final double[][] FIGHTER_WINGS_MECHANICAL = {
            {0.50, 0.20}, {0.04, 0.62}, {0.24, 0.66}, {0.50, 0.44}, {0.76, 0.66}, {0.96, 0.62}};

    /** Broad moth wings: more area, none of the hard sweep. */
    private static final double[][] FIGHTER_WINGS_ORGANIC = {
            {0.50, 0.24}, {0.06, 0.42}, {0.02, 0.70}, {0.22, 0.82}, {0.50, 0.54},
            {0.78, 0.82}, {0.98, 0.70}, {0.94, 0.42}};

    private static final double[][] FIGHTER_BODY = {
            {0.50, 0.96}, {0.36, 0.42}, {0.44, 0.08}, {0.56, 0.08}, {0.64, 0.42}};

    private static BufferedImage fighter(Faction faction) {
        int w = 128;
        int h = 120;
        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);
        boolean organic = faction.style() == HullStyle.ORGANIC;

        Path2D wings = path(w, h, organic ? FIGHTER_WINGS_ORGANIC : FIGHTER_WINGS_MECHANICAL);
        g.setColor(faction.hull());
        g.fill(wings);

        Path2D fuselage = path(w, h, FIGHTER_BODY);
        g.setColor(faction.accent());
        g.fill(fuselage);
        g.setColor(HULL_DARK);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(fuselage);
        g.draw(wings);

        if (organic) {
            // Veins, so the broad panels do not read as flat plate.
            g.setColor(HULL_DARK);
            g.setStroke(new BasicStroke(1.6f));
            for (int side = -1; side <= 1; side += 2) {
                for (double reach = 0.16; reach <= 0.45; reach += 0.14) {
                    g.draw(new java.awt.geom.Line2D.Double(
                            w * 0.5, h * 0.34, w * (0.5 + side * reach), h * 0.76));
                }
            }
        }

        g.setColor(faction.glow());
        g.fill(new Ellipse2D.Double(w * 0.44, h * 0.6, w * 0.12, h * 0.12));
        g.dispose();
        return image;
    }

    private static final double[][] CRUISER_HULL_MECHANICAL = {
            {0.50, 0.98}, {0.33, 0.80}, {0.15, 0.70}, {0.11, 0.44}, {0.24, 0.36},
            {0.30, 0.10}, {0.43, 0.03}, {0.57, 0.03}, {0.70, 0.10}, {0.76, 0.36},
            {0.89, 0.44}, {0.85, 0.70}, {0.67, 0.80}};

    /** A rounder carapace, with none of the mechanical hull's steps. */
    private static final double[][] CRUISER_HULL_ORGANIC = {
            {0.50, 0.99}, {0.30, 0.84}, {0.13, 0.66}, {0.09, 0.42}, {0.20, 0.28},
            {0.34, 0.08}, {0.50, 0.02}, {0.66, 0.08}, {0.80, 0.28},
            {0.91, 0.42}, {0.87, 0.66}, {0.70, 0.84}};

    /** The heavy archetype: a stepped hull with outboard guns, or a segmented body with claws. */
    private static BufferedImage cruiser(Faction faction) {
        int w = 150;
        int h = 168;
        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);
        boolean organic = faction.style() == HullStyle.ORGANIC;

        Path2D hull = path(w, h, organic ? CRUISER_HULL_ORGANIC : CRUISER_HULL_MECHANICAL);
        g.setColor(faction.hull());
        g.fill(hull);
        g.setColor(HULL_DARK);
        g.setStroke(new BasicStroke(3f));
        g.draw(hull);

        if (organic) {
            for (int segment = 0; segment < 3; segment++) {
                var plate = new Ellipse2D.Double(w * 0.35, h * (0.16 + segment * 0.17),
                        w * 0.30, h * 0.15);
                g.setColor(faction.accent());
                g.fill(plate);
                g.setColor(HULL_DARK);
                g.setStroke(new BasicStroke(2f));
                g.draw(plate);
            }
            // Claws where the mechanical hull carries barrels.
            for (int side = -1; side <= 1; side += 2) {
                Path2D claw = new Path2D.Double();
                claw.moveTo(w * (0.5 + side * 0.17), h * 0.66);
                claw.quadTo(w * (0.5 + side * 0.36), h * 0.72,
                        w * (0.5 + side * 0.28), h * 0.94);
                claw.quadTo(w * (0.5 + side * 0.20), h * 0.80,
                        w * (0.5 + side * 0.17), h * 0.66);
                claw.closePath();
                g.setColor(faction.accent());
                g.fill(claw);
                g.setColor(HULL_DARK);
                g.setStroke(new BasicStroke(2f));
                g.draw(claw);
            }
        } else {
            // Raised spine.
            g.setColor(HULL_LIGHT);
            g.fill(new java.awt.geom.Rectangle2D.Double(w * 0.42, h * 0.14, w * 0.16, h * 0.52));
            g.setColor(HULL_DARK);
            g.fill(new java.awt.geom.Rectangle2D.Double(w * 0.455, h * 0.18, w * 0.09, h * 0.42));

            // Outboard gun barrels reaching past the hull toward the player.
            for (int side = -1; side <= 1; side += 2) {
                double bx = w * (0.5 + side * 0.30);
                var barrel = new java.awt.geom.Rectangle2D.Double(
                        bx - w * 0.045, h * 0.56, w * 0.09, h * 0.30);
                g.setColor(faction.accent());
                g.fill(barrel);
                g.setColor(HULL_DARK);
                g.setStroke(new BasicStroke(2f));
                g.draw(barrel);
            }
        }

        // Prow and core.
        Path2D prow = path(w, h, new double[][]{{0.50, 0.99}, {0.43, 0.82}, {0.57, 0.82}});
        g.setColor(faction.glow());
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

    /** Frames in every boss animation. One full throb cycle, so the loop is seamless. */
    private static final int BOSS_FRAMES = 8;

    /**
     * One level boss.
     *
     * All eight share a construction — swept wings ending in turret pods, a central hull over an
     * armoured spine, a hot core, an engine bank behind, and a prow blade at the bottom because
     * enemies face down — and differ by silhouette, palette and armament.
     *
     * @param directory resource directory holding the numbered frames
     * @param width     source canvas width; the on-screen size lives in asset/BossArt
     * @param accent    hull accent, used for the spine and the turret barrels
     * @param glow      hot colour, used for the core, the prow and the turret eyes
     * @param engines   thrusters across the rear
     * @param coreScale core radius as a fraction of the width
     * @param hull      fractional outline of the central hull
     * @param wing      fractional outline of one wing, mirrored for the other side
     * @param turrets   one {distance from centre, y, radius} triple per mirrored pod pair
     */
    private record BossProfile(String directory, int width, int height, Color accent, Color glow,
                               int engines, double coreScale, double[][] hull, double[][] wing,
                               double[][] turrets, boolean sideways, Color plate) {

        /** A flagship for a level that runs top-down, which is most of them. */
        BossProfile(String directory, int width, int height, Color accent, Color glow,
                    int engines, double coreScale, double[][] hull, double[][] wing,
                    double[][] turrets) {
            this(directory, width, height, accent, glow, engines, coreScale, hull, wing, turrets,
                    false, HULL_MID);
        }

        BossProfile(String directory, int width, int height, Color accent, Color glow,
                    int engines, double coreScale, double[][] hull, double[][] wing,
                    double[][] turrets, boolean sideways) {
            this(directory, width, height, accent, glow, engines, coreScale, hull, wing, turrets,
                    sideways, HULL_MID);
        }
    }

    /**
     * Ashfall's navy, as one hull and one wing.
     *
     * Three hulls and three wings, paired up into six classes.
     *
     * One hull for all six was tried first, on the theory that turret and engine counts would carry
     * the difference. Drawn side by side, five of them were the same ship: at two hundred pixels a
     * turret is a dot, and the eye reads silhouette and colour long before it counts anything. So
     * the family is three shapes, each flown by two classes with different plate, aspect and
     * fittings -- which is enough to tell them apart while still reading as one navy.
     *
     * All wide and blunt-prowed: these are industrial hulls, not sleek ones.
     */
    private static final double[][] ASHFALL_HULL = {
            {0.50, 0.99}, {0.36, 0.90}, {0.29, 0.70}, {0.32, 0.44}, {0.40, 0.22},
            {0.46, 0.06}, {0.54, 0.06}, {0.60, 0.22}, {0.68, 0.44}, {0.71, 0.70}, {0.64, 0.90},
    };

    private static final double[][] ASHFALL_WING = {
            {0.34, 0.52}, {0.17, 0.33}, {0.05, 0.41}, {0.02, 0.62}, {0.12, 0.81},
            {0.27, 0.87}, {0.32, 0.71},
    };

    /** Hammerhead: the mass carried at the prow instead of amidships. */
    private static final double[][] ASHFALL_HULL_HAMMER = {
            {0.50, 0.99}, {0.26, 0.95}, {0.20, 0.79}, {0.30, 0.61}, {0.38, 0.40},
            {0.44, 0.08}, {0.56, 0.08}, {0.62, 0.40}, {0.70, 0.61}, {0.80, 0.79}, {0.74, 0.95},
    };

    /** Lance: narrow, with the prow drawn out into a ram. */
    private static final double[][] ASHFALL_HULL_LANCE = {
            {0.50, 0.99}, {0.43, 0.72}, {0.39, 0.50}, {0.41, 0.26},
            {0.47, 0.03}, {0.53, 0.03}, {0.59, 0.26}, {0.61, 0.50}, {0.57, 0.72},
    };

    /** Swept: long and raked back toward the engines. */
    private static final double[][] ASHFALL_WING_SWEPT = {
            {0.36, 0.44}, {0.11, 0.20}, {0.01, 0.29}, {0.05, 0.51}, {0.19, 0.71}, {0.33, 0.64},
    };

    /** Stub: barely a wing, just a hardpoint to hang a pod off. */
    private static final double[][] ASHFALL_WING_STUB = {
            {0.33, 0.55}, {0.20, 0.45}, {0.12, 0.56}, {0.16, 0.71}, {0.29, 0.74},
    };

    /**
     * Cryonis's navy, as three hulls and three fins.
     *
     * Same budget as Ashfall and for the same reason -- six classes off one shape read as one ship
     * -- but the shapes themselves are the opposite argument. Ashfall's hulls are wide and blunt
     * because they were built to work. These are narrow and edged, because they were built to cut
     * through something. Named for what they are rather than after the galaxy's colour: the fins
     * pair across all three hulls, so the names have to survive being mixed.
     */
    private static final double[][] CRYO_PROW = {
            {0.50, 0.99}, {0.38, 0.88}, {0.34, 0.64}, {0.37, 0.38}, {0.44, 0.14},
            {0.50, 0.01}, {0.56, 0.14}, {0.63, 0.38}, {0.66, 0.64}, {0.62, 0.88},
    };

    /** Spindle: the narrowest hull in the game, barely wider than its own core. */
    private static final double[][] CRYO_SPINE = {
            {0.50, 0.99}, {0.44, 0.78}, {0.42, 0.52}, {0.44, 0.28},
            {0.48, 0.04}, {0.52, 0.04}, {0.56, 0.28}, {0.58, 0.52}, {0.56, 0.78},
    };

    /** Shelf: broad and flat-topped, an icebreaker's deck rather than a warship's nose. */
    private static final double[][] CRYO_SHELF = {
            {0.50, 0.98}, {0.30, 0.90}, {0.24, 0.68}, {0.27, 0.42}, {0.34, 0.20},
            {0.42, 0.10}, {0.58, 0.10}, {0.66, 0.20}, {0.73, 0.42}, {0.76, 0.68}, {0.70, 0.90},
    };

    private static final double[][] CRYO_FIN = {
            {0.35, 0.50}, {0.16, 0.28}, {0.03, 0.38}, {0.01, 0.60}, {0.13, 0.80},
            {0.28, 0.88}, {0.33, 0.68},
    };

    /** Raked: swept hard back, so the class reads as the fast one at a glance. */
    private static final double[][] CRYO_FIN_RAKED = {
            {0.37, 0.40}, {0.09, 0.14}, {0.00, 0.24}, {0.04, 0.48}, {0.18, 0.72}, {0.34, 0.62},
    };

    /** Stub: a hardpoint with just enough fin around it to look intentional. */
    private static final double[][] CRYO_FIN_STUB = {
            {0.34, 0.52}, {0.22, 0.42}, {0.13, 0.54}, {0.17, 0.70}, {0.30, 0.73},
    };

    /**
     * Tempest's navy, as three hulls and three vanes.
     *
     * Same budget as the two galaxies before it. Cryonis's lesson was that plate colour does not
     * separate classes and aspect does, so these span 0.75 to 1.92 -- wider at the narrow end than
     * Ashfall's 0.95 to 1.96, because the Mast is this galaxy's tall-narrow outlier and there is
     * only one of it.
     *
     * Shaped for air rather than for ice: the Delta and the Keel are lifting bodies with the volume
     * carried low, where Cryonis's hulls put their mass at the nose to cut with.
     */
    // A hull runs nose-down: y = 0.99 is the nose, where bossFrame puts the prow blade, and
    // y = 0.02 is the tail, where it puts the engine bank. Worth stating, because the first pass
    // of these three had them the other way round -- widest at the nose, tapering to the tail --
    // and all three came out as the same wide dome whatever their vertices said. Cryonis's
    // CRYO_PROW is the reference: a lens, pointed at both ends, widest below the middle.
    private static final double[][] STORM_DELTA = {
            {0.50, 0.99}, {0.42, 0.66}, {0.22, 0.26}, {0.12, 0.06}, {0.38, 0.10},
            {0.50, 0.20}, {0.62, 0.10}, {0.88, 0.06}, {0.78, 0.26}, {0.58, 0.66},
    };

    /** Mast: the tall-narrow outlier. Slab-sided through the body, flared and notched at the tail. */
    private static final double[][] STORM_MAST = {
            {0.50, 0.99}, {0.45, 0.70}, {0.42, 0.44}, {0.34, 0.24}, {0.30, 0.06},
            {0.44, 0.12}, {0.50, 0.06}, {0.56, 0.12}, {0.70, 0.06}, {0.66, 0.24},
            {0.58, 0.44}, {0.55, 0.70},
    };

    /** Keel: a plank. Blunt nose, parallel sides for most of its length, square tail. */
    private static final double[][] STORM_KEEL = {
            {0.50, 0.99}, {0.32, 0.94}, {0.22, 0.80}, {0.20, 0.30}, {0.26, 0.08},
            {0.74, 0.08}, {0.80, 0.30}, {0.78, 0.80}, {0.68, 0.94},
    };

    private static final double[][] STORM_VANE = {
            {0.36, 0.46}, {0.14, 0.24}, {0.02, 0.34}, {0.00, 0.62}, {0.10, 0.84},
            {0.26, 0.90}, {0.32, 0.66},
    };

    /** Swept: notched on the trailing edge, so it is not Cryonis's raked fin in a new colour. */
    private static final double[][] STORM_VANE_SWEPT = {
            {0.38, 0.34}, {0.20, 0.16}, {0.06, 0.06}, {0.00, 0.22}, {0.12, 0.40},
            {0.05, 0.56}, {0.22, 0.70}, {0.34, 0.58},
    };

    /** Slat: two short blades that read as one stepped shape at two hundred pixels. */
    private static final double[][] STORM_VANE_SLAT = {
            {0.35, 0.44}, {0.18, 0.36}, {0.06, 0.46}, {0.12, 0.56}, {0.02, 0.64},
            {0.14, 0.76}, {0.30, 0.72},
    };

    /**
     * Null's navy, as three hulls and three vanes.
     *
     * Aspects run 0.82, 0.95, 1.50, 1.87 and 2.10, plus the turned one -- the widest band any
     * galaxy has used, because six warships is the most any galaxy has fielded since Ashfall proved
     * six on one hull read as one ship. Each hull is flown twice and each vane worn twice, with no
     * pair repeated.
     *
     * Every hull is flown twice, so what matters is how far apart its two classes sit. The first
     * pass had both Waists at 1.17 and 1.51 and they read as one ship on the sheet, which is the
     * failure Ashfall recorded, Cryonis recorded again and Tempest recorded a third time. They are
     * now 0.95 and 1.87 -- upright and wide -- and the two Forks are 1.50 and 2.10.
     *
     * The two Shards are 0.82 and, drawn, 0.69. They are the pair that cannot be confused anyway:
     * one guards the side-on leg and is turned once by the generator, so what the game decodes is
     * 340 wide against the other's 272 tall. Tempest made the same call for the same reason -- see
     * the note on its tall Mast and turned Delta.
     *
     * Nose-down, like every hull: y = 0.99 is the nose where bossFrame puts the prow blade, and
     * y = 0.04 is the tail. None of the three is widest at the nose, which is the shape that
     * collapsed three of Tempest's hulls into one dome.
     *
     * Deliberately none of the silhouettes any earlier galaxy used: not Ashfall's blunt wedges,
     * not Cryonis's lens, spindle and shelf, not Tempest's delta, slab and plank.
     */
    /** Waist: pinched amidships, shouldered at the nose and flared again at the tail. */
    private static final double[][] VOID_WAIST = {
            {0.50, 0.99}, {0.32, 0.91}, {0.24, 0.74}, {0.39, 0.56}, {0.35, 0.36},
            {0.28, 0.14}, {0.43, 0.05}, {0.57, 0.05}, {0.72, 0.14}, {0.65, 0.36},
            {0.61, 0.56}, {0.76, 0.74}, {0.68, 0.91},
    };

    /** Fork: a deep notch cut clean to amidships, so the tail reads as two prongs. */
    private static final double[][] VOID_FORK = {
            {0.50, 0.99}, {0.36, 0.84}, {0.26, 0.60}, {0.20, 0.34}, {0.14, 0.06},
            {0.34, 0.14}, {0.50, 0.46}, {0.66, 0.14}, {0.86, 0.06}, {0.80, 0.34},
            {0.74, 0.60}, {0.64, 0.84},
    };

    /** Shard: the tall-narrow outlier. A dagger -- pointed nose, straight taper, square tail. */
    private static final double[][] VOID_SHARD = {
            {0.50, 0.99}, {0.43, 0.76}, {0.39, 0.50}, {0.33, 0.22}, {0.31, 0.04},
            {0.69, 0.04}, {0.67, 0.22}, {0.61, 0.50}, {0.57, 0.76},
    };

    /** Rib: a long blade, notched on the leading edge so it reads as two points rather than a lobe. */
    private static final double[][] VOID_RIB = {
            {0.35, 0.40}, {0.16, 0.28}, {0.00, 0.24}, {0.08, 0.42}, {0.01, 0.56},
            {0.15, 0.70}, {0.30, 0.64},
    };

    /** Claw: three points off one root, jagged enough to read as a grasping thing. */
    private static final double[][] VOID_CLAW = {
            {0.36, 0.40}, {0.18, 0.22}, {0.06, 0.10}, {0.10, 0.30}, {0.00, 0.28},
            {0.06, 0.46}, {0.02, 0.58}, {0.14, 0.66}, {0.30, 0.62},
    };

    /** Lobe: a rounded paddle, the only vane in the game with no point on it at all. */
    private static final double[][] VOID_LOBE = {
            {0.34, 0.46}, {0.24, 0.30}, {0.12, 0.24}, {0.03, 0.32}, {0.00, 0.46},
            {0.04, 0.60}, {0.14, 0.70}, {0.26, 0.68}, {0.32, 0.58},
    };

    private static final BossProfile[] BOSSES = {
            // Sentinel: the level 1 fight, unchanged, so the opening minutes stay tuned.
            new BossProfile("boss-sentinel", 440, 340, HOSTILE, HOSTILE_GLOW, 3, 0.11,
                    new double[][]{{0.50, 0.99}, {0.40, 0.72}, {0.385, 0.30},
                            {0.44, 0.05}, {0.56, 0.05}, {0.615, 0.30}, {0.60, 0.72}},
                    new double[][]{{0.41, 0.60}, {0.10, 0.40}, {0.03, 0.50},
                            {0.09, 0.72}, {0.26, 0.90}, {0.38, 0.80}},
                    new double[][]{{0.29, 0.735, 0.042}, {0.15, 0.80, 0.032}}),

            // Hive Matriarch: wide and low, membranous wings, a swollen abdomen full of core.
            new BossProfile("boss-hive-matriarch", 470, 330,
                    new Color(0x7fae2a), new Color(0xffd24a), 3, 0.14,
                    new double[][]{{0.50, 0.98}, {0.33, 0.88}, {0.26, 0.62}, {0.31, 0.28},
                            {0.43, 0.04}, {0.57, 0.04}, {0.69, 0.28}, {0.74, 0.62}, {0.67, 0.88}},
                    new double[][]{{0.38, 0.46}, {0.18, 0.20}, {0.02, 0.30}, {0.00, 0.56},
                            {0.14, 0.78}, {0.32, 0.86}, {0.37, 0.66}},
                    new double[][]{{0.30, 0.66, 0.042}, {0.18, 0.44, 0.032}, {0.24, 0.80, 0.030}}),

            // Bloom Colossus: tall rather than wide, wings opening like petals around the core.
            new BossProfile("boss-bloom-colossus", 460, 390,
                    new Color(0x4fae7a), new Color(0xc07aff), 3, 0.16,
                    new double[][]{{0.50, 0.98}, {0.36, 0.86}, {0.30, 0.58}, {0.34, 0.24},
                            {0.44, 0.03}, {0.56, 0.03}, {0.66, 0.24}, {0.70, 0.58}, {0.64, 0.86}},
                    new double[][]{{0.36, 0.50}, {0.20, 0.20}, {0.04, 0.30}, {0.00, 0.56},
                            {0.10, 0.80}, {0.28, 0.90}, {0.35, 0.70}},
                    new double[][]{{0.30, 0.72, 0.044}, {0.19, 0.48, 0.034},
                            {0.23, 0.86, 0.030}, {0.12, 0.66, 0.026}}),

            // Scrap Hive: a bulbous carrier that vents fighters, so the pods read as bays.
            new BossProfile("boss-scrap-hive", 470, 350,
                    new Color(0x9a7b2a), new Color(0xffd66b), 4, 0.13,
                    new double[][]{{0.50, 0.98}, {0.36, 0.84}, {0.30, 0.58}, {0.34, 0.28},
                            {0.44, 0.06}, {0.56, 0.06}, {0.66, 0.28}, {0.70, 0.58}, {0.64, 0.84}},
                    new double[][]{{0.38, 0.56}, {0.18, 0.44}, {0.06, 0.56}, {0.10, 0.74},
                            {0.24, 0.84}, {0.36, 0.74}},
                    new double[][]{{0.32, 0.70, 0.046}, {0.20, 0.82, 0.038}, {0.26, 0.64, 0.030}}),

            // Foundry Warden: slab-sided industrial plant, the widest engine bank in the run.
            new BossProfile("boss-foundry-warden", 480, 340,
                    new Color(0x4fa8c9), new Color(0xe8faff), 6, 0.11,
                    new double[][]{{0.50, 0.97}, {0.32, 0.88}, {0.28, 0.62}, {0.30, 0.30},
                            {0.40, 0.05}, {0.60, 0.05}, {0.70, 0.30}, {0.72, 0.62}, {0.68, 0.88}},
                    new double[][]{{0.38, 0.42}, {0.14, 0.36}, {0.02, 0.46}, {0.02, 0.70},
                            {0.16, 0.82}, {0.36, 0.76}},
                    new double[][]{{0.32, 0.56, 0.042}, {0.32, 0.76, 0.042}, {0.16, 0.62, 0.030}}),

            // Void Weaver: narrow and long-limbed, a spider of a ship.
            new BossProfile("boss-void-weaver", 430, 360,
                    new Color(0x7a3fc9), new Color(0xd9a6ff), 2, 0.09,
                    new double[][]{{0.50, 0.99}, {0.43, 0.76}, {0.41, 0.40}, {0.45, 0.06},
                            {0.55, 0.06}, {0.59, 0.40}, {0.57, 0.76}},
                    new double[][]{{0.44, 0.62}, {0.20, 0.28}, {0.04, 0.34}, {0.02, 0.52},
                            {0.16, 0.86}, {0.34, 0.94}, {0.42, 0.80}},
                    new double[][]{{0.36, 0.86, 0.034}, {0.26, 0.52, 0.028}}),

            // Core Tyrant: the heaviest hull and the most guns, for the last level.
            new BossProfile("boss-core-tyrant", 500, 370,
                    new Color(0xd6321a), new Color(0xffb04a), 5, 0.14,
                    new double[][]{{0.50, 0.98}, {0.34, 0.80}, {0.28, 0.52}, {0.32, 0.24},
                            {0.42, 0.03}, {0.58, 0.03}, {0.68, 0.24}, {0.72, 0.52}, {0.66, 0.80}},
                    new double[][]{{0.36, 0.58}, {0.14, 0.36}, {0.01, 0.48}, {0.04, 0.68},
                            {0.16, 0.84}, {0.30, 0.90}, {0.34, 0.76}},
                    new double[][]{{0.38, 0.66, 0.048}, {0.28, 0.82, 0.040},
                            {0.17, 0.90, 0.032}, {0.23, 0.74, 0.030}}),

            // Exodus Dreadnought: the longest hull and the most pods, guarding the way out.
            new BossProfile("boss-exodus-dreadnought", 520, 390,
                    new Color(0x8d94a0), new Color(0x9fd0ff), 6, 0.12,
                    new double[][]{{0.50, 0.99}, {0.38, 0.84}, {0.33, 0.54}, {0.36, 0.22},
                            {0.44, 0.02}, {0.56, 0.02}, {0.64, 0.22}, {0.67, 0.54}, {0.62, 0.84}},
                    new double[][]{{0.38, 0.56}, {0.16, 0.30}, {0.02, 0.40}, {0.01, 0.60},
                            {0.12, 0.80}, {0.30, 0.92}, {0.36, 0.74}},
                    new double[][]{{0.36, 0.78, 0.046}, {0.26, 0.58, 0.038},
                            {0.16, 0.86, 0.032}, {0.30, 0.90, 0.028}}),
            // ---- Galaxy 2: Ashfall ------------------------------------------------------------
            // One hull family, six classes. Drawn on canvases about 1.5x their on-screen size
            // rather than the 2.1x the originals use: the extra pixels only ever fed antialiasing
            // Java2D was already doing, and at four hundred frames a galaxy they are real bytes.
            //
            // Engine count is the galaxy's signature -- five and six, heavy thrust for heavy hulls
            // -- and the turret count is how a class escalates within the galaxy, two on the first
            // flagship up to four on the last. The accent and glow are the same pair its grunts
            // wear, which is the strongest cue that a boss belongs where it is fought.

            // Slab hull, stub wings: a wide low freighter with guns bolted to it.
            new BossProfile("boss-cinder-warden", 396, 202, ASH_ACCENT, ASH_GLOW, 5, 0.13,
                    ASHFALL_HULL, ASHFALL_WING_STUB,
                    new double[][]{{0.28, 0.62, 0.044}, {0.17, 0.68, 0.032}},
                    false, new Color(0x4a3226)),

            // Slab hull again, but tall and narrow with full wings -- the same yard, a different
            // class, and the aspect is what tells them apart at a glance.
            new BossProfile("boss-slag-baron", 286, 300,
                    new Color(0xd87a24), ASH_GLOW, 6, 0.15,
                    ASHFALL_HULL, ASHFALL_WING,
                    new double[][]{{0.30, 0.66, 0.042}, {0.18, 0.46, 0.032},
                            {0.24, 0.80, 0.030}},
                    false, new Color(0x5f3d20)),

            // Hammerhead: everything forward, so it reads as something that rams.
            new BossProfile("boss-forge-overseer", 352, 244,
                    new Color(0xff8a2a), ASH_GLOW, 6, 0.16,
                    ASHFALL_HULL_HAMMER, ASHFALL_WING,
                    new double[][]{{0.32, 0.60, 0.044}, {0.21, 0.42, 0.034},
                            {0.25, 0.82, 0.032}},
                    false, new Color(0x36282a)),

            // Hammerhead with swept wings and a redder plate: the same prow, a faster ship.
            new BossProfile("boss-pyre-sovereign", 380, 226,
                    new Color(0xe05a1c), ASH_GLOW, 5, 0.14,
                    ASHFALL_HULL_HAMMER, ASHFALL_WING_SWEPT,
                    new double[][]{{0.33, 0.56, 0.044}, {0.22, 0.38, 0.034},
                            {0.26, 0.74, 0.032}, {0.14, 0.64, 0.028}},
                    false, new Color(0x55302a)),

            // Sunward Lance guards the side-on leg, so its art is turned once here rather than
            // rotated every frame at render time -- which is what keeps its collision box the same
            // shape as the ship. The BossArt constant declares the transposed size to match.
            new BossProfile("boss-sunward-lance", 268, 316,
                    new Color(0xffa32a), new Color(0xfff0c0), 6, 0.15,
                    ASHFALL_HULL_LANCE, ASHFALL_WING_STUB,
                    new double[][]{{0.28, 0.54, 0.046}, {0.19, 0.36, 0.034},
                            {0.22, 0.72, 0.032}},
                    true, new Color(0x6d4a28)),

            // Lance hull on full wings, the widest thing in the galaxy's navy.
            new BossProfile("boss-corona-herald", 408, 238,
                    new Color(0xff9420), new Color(0xfff0c0), 6, 0.17,
                    ASHFALL_HULL_LANCE, ASHFALL_WING_SWEPT,
                    new double[][]{{0.34, 0.54, 0.046}, {0.23, 0.36, 0.036},
                            {0.26, 0.72, 0.034}, {0.14, 0.62, 0.030}},
                    false, new Color(0x46352c)),
            // ---- Galaxy 3: Cryonis ------------------------------------------------------------
            // Three hulls and three fins again, on Ashfall's 1.5x canvases. Two things differ.
            //
            // Engines are three and four where Ashfall ran five and six: cold, quiet ships against
            // that galaxy's heavy thrust, and a step on the way to the thrusterless navy Phase 4
            // wants. Turrets still escalate two to four across the galaxy, which is how a class
            // reads as later without needing a bigger hull.
            //
            // This is the only galaxy with two side-on legs, so two of the six are turned here.

            // Shard Cutter guards the first side-on leg. Turned once here rather than rotated per
            // frame, so the collision box keeps the shape of the picture -- and the BossArt
            // constant declares the transposed size to match. See Theme.sideways.
            new BossProfile("boss-shard-cutter", 260, 310, CRYO_ACCENT, CRYO_GLOW, 3, 0.12,
                    CRYO_PROW, CRYO_FIN_STUB,
                    new double[][]{{0.27, 0.60, 0.044}, {0.17, 0.70, 0.032}},
                    true, new Color(0x24384c)),

            // Spindle on raked fins: tall and narrow, the one class in the galaxy that is taller
            // than it is wide. Aspect is what separates classes here -- the plate colours are near
            // enough to each other on purpose, because a galaxy should read as one navy.
            new BossProfile("boss-frost-harrier", 268, 310,
                    new Color(0x63d8ec), CRYO_GLOW, 4, 0.11,
                    CRYO_SPINE, CRYO_FIN_RAKED,
                    new double[][]{{0.29, 0.58, 0.042}, {0.18, 0.40, 0.032}},
                    false, new Color(0x2b3f55)),

            // The same prow as the Cutter on full fins, flown flat: an icebreaker, not a knife.
            new BossProfile("boss-glacier-breaker", 384, 214,
                    new Color(0x7ae0f0), CRYO_GLOW, 4, 0.14,
                    CRYO_PROW, CRYO_FIN,
                    new double[][]{{0.31, 0.62, 0.046}, {0.20, 0.44, 0.034},
                            {0.24, 0.80, 0.030}},
                    false, new Color(0x32485e)),

            // Shelf hull, full fins: the widest deck in the galaxy, and the tunnel fight.
            new BossProfile("boss-cryo-marshal", 344, 248,
                    CRYO_ACCENT, CRYO_GLOW, 3, 0.15,
                    CRYO_SHELF, CRYO_FIN,
                    new double[][]{{0.32, 0.64, 0.046}, {0.21, 0.46, 0.036},
                            {0.25, 0.82, 0.030}},
                    false, new Color(0x1f3346)),

            // Shelf again on stubs -- same yard, stripped for weight and given a fourth pod.
            new BossProfile("boss-hail-bastion", 404, 230,
                    new Color(0x5cd4ea), CRYO_GLOW, 4, 0.13,
                    CRYO_SHELF, CRYO_FIN_STUB,
                    new double[][]{{0.33, 0.60, 0.048}, {0.22, 0.42, 0.036},
                            {0.26, 0.76, 0.032}, {0.14, 0.66, 0.028}},
                    false, new Color(0x2a4058)),

            // Shatter Prow guards the second side-on leg, and is turned for the same reason the
            // Cutter is. Spindle hull, so the two side-on flagships do not read as one ship.
            new BossProfile("boss-shatter-prow", 276, 330,
                    new Color(0x9af0ff), new Color(0xa8eeff), 4, 0.14,
                    CRYO_SPINE, CRYO_FIN_STUB,
                    new double[][]{{0.30, 0.56, 0.048}, {0.20, 0.38, 0.036},
                            {0.24, 0.74, 0.032}, {0.13, 0.64, 0.028}},
                    true, new Color(0x35506b)),

            // ---- Galaxy 4: Tempest ------------------------------------------------------------
            // Five warships rather than six, because this galaxy fields two set pieces instead of
            // one. Three hulls and three vanes still, paired five ways with no pair repeated.
            //
            // Engines are two and three, against Cryonis's three and four and Ashfall's five and
            // six. The comment on the Cryonis block calls that a step toward the thrusterless navy
            // Phase 4 wants, and this is the next one -- a navy that rides the weather rather than
            // pushing through it. Turrets still escalate two to four across the galaxy.

            // Aspects run 0.75, 1.05, 1.20, 1.68 and 2.06 -- a wider band than Ashfall's 0.95 to
            // 1.96, and deliberately so. The first pass put three of the five between 1.3 and 1.9
            // on two hulls and they read as one wide ship on the boss sheet, which is the failure
            // Ashfall recorded and Cryonis recorded again. No two of the three broad classes now
            // share a hull, and the two that repeat one are the tall Mast and the turned Delta,
            // which cannot be confused with anything.
            new BossProfile("boss-squall-warden", 372, 222,
                    STORM_ACCENT, STORM_GLOW, 2, 0.13,
                    STORM_DELTA, STORM_VANE,
                    new double[][]{{0.30, 0.62, 0.046}, {0.19, 0.44, 0.034}},
                    false, new Color(0x28324e)),

            // The Mast on swept vanes: the one class here taller than it is wide, and the only
            // thing keeping five ships off one aspect. See the note on the shape constants.
            new BossProfile("boss-eyewall-lance", 254, 340,
                    new Color(0x92b8ff), STORM_GLOW, 3, 0.11,
                    STORM_MAST, STORM_VANE_SWEPT,
                    new double[][]{{0.28, 0.58, 0.042}, {0.17, 0.38, 0.032}},
                    false, new Color(0x2d3a58)),

            // The plank: widest and flattest in the galaxy, on the only hull with parallel sides.
            new BossProfile("boss-ring-reaver", 404, 196,
                    new Color(0x8ab0ff), STORM_GLOW, 2, 0.14,
                    STORM_KEEL, STORM_VANE_SLAT,
                    new double[][]{{0.32, 0.64, 0.048}, {0.21, 0.46, 0.036},
                            {0.25, 0.82, 0.030}},
                    false, new Color(0x243050)),

            // The Mast again, flown square rather than tall: a fat spire, and nothing like either
            // the Warden's delta or the Reaver's plank.
            new BossProfile("boss-downdraft-prow", 300, 286,
                    new Color(0x7aa4fb), STORM_GLOW, 3, 0.15,
                    STORM_MAST, STORM_VANE,
                    new double[][]{{0.33, 0.60, 0.048}, {0.22, 0.42, 0.036},
                            {0.26, 0.78, 0.032}},
                    false, new Color(0x1f2942)),

            // Arc Lance guards the galaxy's side-on leg, and is turned once here rather than
            // rotated per frame, so its collision box keeps the shape of the picture. Drawn
            // nose-down at 268x322, so what the game decodes is 322 wide -- the BossArt constant
            // declares that transposed size. See Theme.sideways and the note on SUNWARD_LANCE.
            new BossProfile("boss-arc-lance", 268, 322,
                    new Color(0xaecdff), new Color(0xd6e6ff), 3, 0.14,
                    STORM_DELTA, STORM_VANE_SWEPT,
                    new double[][]{{0.31, 0.56, 0.048}, {0.21, 0.38, 0.036},
                            {0.25, 0.74, 0.032}, {0.14, 0.64, 0.028}},
                    true, new Color(0x30406a)),

            // ---- Galaxy 5: Null ---------------------------------------------------------------
            // Six warships, the most since Ashfall, because this galaxy fields one set piece
            // rather than Tempest's two. Three hulls and three vanes, paired six ways with no
            // pair repeated. See the note on the shape constants for the aspect band.
            //
            // Engines are zero on all six, and that is the point of them. Ashfall ran five and
            // six, Cryonis three and four, Tempest two and three; the comment on each of those
            // blocks calls the next one a step toward the thrusterless navy, and this is it. A
            // fleet with nothing to push against, in the galaxy about gravity. The engine loop in
            // bossFrame simply does not run at zero, so it costs nothing but the reading.
            //
            // Turrets still escalate two to four across the galaxy.

            // The Bonepicker: what works a dead belt. The Fork on claws, and the only flagship
            // here whose silhouette is mostly gap. Squarer than the Photon Halo on the same hull.
            new BossProfile("boss-bonepicker", 372, 248,
                    VOID_ACCENT, VOID_GLOW, 0, 0.12,
                    VOID_FORK, VOID_CLAW,
                    new double[][]{{0.31, 0.60, 0.046}, {0.20, 0.42, 0.034}},
                    false, new Color(0x2a2246)),

            // Lensbreaker: guards the first sight of the hole. The Waist flown upright, on ribs --
            // the only warship here taller than it is wide that is not turned.
            new BossProfile("boss-lensbreaker", 300, 316,
                    new Color(0xa87eff), VOID_GLOW, 0, 0.12,
                    VOID_WAIST, VOID_RIB,
                    new double[][]{{0.29, 0.58, 0.044}, {0.19, 0.40, 0.034},
                            {0.23, 0.76, 0.030}},
                    false, new Color(0x332a52)),

            // Tidewrack guards the galaxy's side-on leg, and is turned once here rather than
            // rotated per frame, so its collision box keeps the shape of the picture. Drawn
            // nose-down at 236x340, so what the game decodes is 340 wide -- the BossArt constant
            // declares that transposed size. See Theme.sideways and the note on boss-arc-lance.
            new BossProfile("boss-tidewrack", 236, 340,
                    new Color(0xb490ff), new Color(0xae86f4), 0, 0.11,
                    VOID_SHARD, VOID_CLAW,
                    new double[][]{{0.28, 0.58, 0.044}, {0.18, 0.40, 0.034},
                            {0.22, 0.76, 0.030}, {0.12, 0.66, 0.026}},
                    true, new Color(0x3a2f60)),

            // Frame-Drag: the Waist again on lobes, flown wide where the Lensbreaker is upright.
            // Nearly a square of difference in aspect between the two, which is what it took.
            new BossProfile("boss-frame-drag", 400, 214,
                    VOID_ACCENT, VOID_GLOW, 0, 0.13,
                    VOID_WAIST, VOID_LOBE,
                    new double[][]{{0.30, 0.62, 0.046}, {0.20, 0.44, 0.034},
                            {0.24, 0.78, 0.030}},
                    false, new Color(0x261f40)),

            // Photon Halo: the widest and flattest thing in the game, against the brightest
            // backdrop in the game. The Fork on lobes, so it shares no fitting with the Bonepicker.
            new BossProfile("boss-photon-halo", 424, 202,
                    new Color(0xc0a0ff), new Color(0xbe9cf8), 0, 0.13,
                    VOID_FORK, VOID_LOBE,
                    new double[][]{{0.33, 0.64, 0.048}, {0.22, 0.46, 0.036},
                            {0.26, 0.82, 0.030}, {0.15, 0.72, 0.026}},
                    false, new Color(0x413466)),

            // The Gullet: set in the throat, and the tall one that is not turned. The Shard on
            // ribs -- the darkest plate here, on the darkest level.
            new BossProfile("boss-gullet", 272, 330,
                    new Color(0x8257e8), new Color(0x9068e0), 0, 0.10,
                    VOID_SHARD, VOID_RIB,
                    new double[][]{{0.27, 0.56, 0.044}, {0.18, 0.38, 0.034},
                            {0.21, 0.74, 0.030}, {0.12, 0.64, 0.026}},
                    false, new Color(0x1b1630)),
    };

    private static void bosses() throws IOException {
        for (BossProfile profile : BOSSES) {
            for (int frame = 1; frame <= BOSS_FRAMES; frame++) {
                BufferedImage image = bossFrame(profile, frame);
                if (profile.sideways()) {
                    // The same turn pointed() gives a sideways faction's hulls, for the same
                    // reason: the side-view arena draws without a render-time rotation.
                    image = quarterTurnLeft(image);
                }
                write(image, SPRITES.resolve(profile.directory()).resolve(frame + ".png"));
            }
        }
    }

    // ------------------------------------------------------------------ monsters

    /** Diseased hide, bone and bile: the palette the two monsters share. */
    private static final Color HIDE = new Color(0x24361f);
    private static final Color HIDE_DARK = new Color(0x131c10);
    private static final Color BONE = new Color(0xcfc7a8);
    private static final Color BILE = new Color(0xb6e24a);
    private static final Color CHITIN = new Color(0x6a5334);
    private static final Color CHITIN_DARK = new Color(0x3a2c1a);

    /**
     * The two organic bosses, which share nothing with the warships above.
     *
     * Written as their own methods rather than as another {@code BossProfile}: that record is all
     * wings, turrets, spine, engines and core, and a monster has none of the five. A style flag
     * would leave half the record dead and branch {@code bossFrame} through its whole draw order.
     *
     * The hydra's necks are deliberately absent from these frames. They are drawn at runtime from
     * the live head positions, so they move at sixty steps a second instead of eight, and the
     * eight frames here only have to carry a breath.
     */
    /**
     * A thing that lives somewhere, rather than a thing that was built.
     *
     * Written beside {@link #hydraTorsoFrame} rather than by generalising it, and that is a
     * deliberate trade. Reworking the hydra's method into a parameterised one would have risked
     * changing its committed frames by a pixel, and the byte-identical check in CI is the only thing
     * standing between this repository and art that drifts. A little duplication is the cheaper
     * side of that bargain.
     *
     * Everything is optional: {@code legs} or {@code ribs} at zero simply draws none, so one method
     * covers a many-legged crawler and a limbless mass.
     */
    private record CreatureProfile(String directory, int width, int height,
                                   Color hide, Color hideDark, Color bone, Color sac,
                                   int legs, int ribs, double sacScale, double[][] body) {
    }

    private static final CreatureProfile[] CREATURES = {
            // Ash Revenant: something the ashfall buried and the heat woke up. Upright, few limbs.
            new CreatureProfile("boss-ash-revenant", 340, 250,
                    new Color(0x3a2e26), new Color(0x1d1612), new Color(0xd8cbb0),
                    new Color(0xff8a2a), 4, 4, 0.17,
                    new double[][]{{0.50, 0.94}, {0.36, 0.86}, {0.28, 0.68}, {0.26, 0.48},
                            {0.32, 0.30}, {0.40, 0.22}, {0.50, 0.26}, {0.60, 0.22},
                            {0.68, 0.30}, {0.74, 0.48}, {0.72, 0.68}, {0.64, 0.86}}),

            // Vent Crawler: low, wide and many-legged, built to hold onto a hot wall.
            new CreatureProfile("boss-vent-crawler", 380, 216,
                    new Color(0x33241c), new Color(0x180f0b), new Color(0xc9bda2),
                    new Color(0xff6a14), 6, 6, 0.12,
                    new double[][]{{0.50, 0.96}, {0.32, 0.90}, {0.22, 0.74}, {0.20, 0.58},
                            {0.28, 0.44}, {0.38, 0.36}, {0.50, 0.40}, {0.62, 0.36},
                            {0.72, 0.44}, {0.80, 0.58}, {0.78, 0.74}, {0.68, 0.90}}),

            // Ember Titan: barely legged, mostly furnace. The biggest thing in the galaxy that is
            // not a machine.
            new CreatureProfile("boss-ember-titan", 356, 300,
                    new Color(0x40291d), new Color(0x1f120c), new Color(0xe0d2b4),
                    new Color(0xff5a10), 2, 5, 0.26,
                    new double[][]{{0.50, 0.97}, {0.34, 0.88}, {0.24, 0.70}, {0.21, 0.46},
                            {0.29, 0.24}, {0.39, 0.12}, {0.50, 0.16}, {0.61, 0.12},
                            {0.71, 0.24}, {0.79, 0.46}, {0.76, 0.70}, {0.66, 0.88}}),

            // ---- Galaxy 3: Cryonis ------------------------------------------------------------
            // Three again, and all three on levels with no side-on flag -- CreatureProfile has no
            // sideways field, so a creature cannot guard a side-on leg. Levels 21 and 29 field
            // warships for that reason rather than by preference.

            // Ice Wraith: whatever swims under the shelf. Long, thin-limbed, and mostly translucent
            // sac -- the bone is the only part of it that reads at distance.
            new CreatureProfile("boss-ice-wraith", 348, 268,
                    new Color(0x2a4457), new Color(0x12222f), new Color(0xdff0f6),
                    new Color(0x6fe0f4), 4, 3, 0.22,
                    new double[][]{{0.50, 0.95}, {0.38, 0.86}, {0.30, 0.66}, {0.28, 0.44},
                            {0.34, 0.26}, {0.42, 0.16}, {0.50, 0.20}, {0.58, 0.16},
                            {0.66, 0.26}, {0.72, 0.44}, {0.70, 0.66}, {0.62, 0.86}}),

            // Trench Horror: the black-trench thing. Widest and most-legged of the three, and the
            // darkest hide in the game -- it is meant to be hard to see against its own level.
            new CreatureProfile("boss-trench-horror", 392, 224,
                    new Color(0x16252f), new Color(0x080e13), new Color(0xb8ccd6),
                    new Color(0x3fb8d8), 8, 6, 0.13,
                    new double[][]{{0.50, 0.96}, {0.30, 0.90}, {0.20, 0.74}, {0.18, 0.56},
                            {0.26, 0.42}, {0.37, 0.34}, {0.50, 0.38}, {0.63, 0.34},
                            {0.74, 0.42}, {0.82, 0.56}, {0.80, 0.74}, {0.70, 0.90}}),

            // Geyser Maw: rooted in the flats rather than walking over them. Two stub legs and a
            // sac that takes a quarter of the body -- it is a vent that grew teeth.
            new CreatureProfile("boss-geyser-maw", 336, 292,
                    new Color(0x1f3a4a), new Color(0x0d1a24), new Color(0xd0e4ec),
                    new Color(0x8af0ff), 2, 5, 0.27,
                    new double[][]{{0.50, 0.98}, {0.33, 0.90}, {0.23, 0.72}, {0.20, 0.48},
                            {0.28, 0.26}, {0.38, 0.14}, {0.50, 0.18}, {0.62, 0.14},
                            {0.72, 0.26}, {0.80, 0.48}, {0.77, 0.72}, {0.67, 0.90}}),

            // ---- Galaxy 4: Tempest ------------------------------------------------------------
            // Three again, on 32, 35 and 36 -- none of them the side-on leg, for the reason the
            // Cryonis block gives. Hides are storm-dark and the sacs carry the charge, so all
            // three read as lit from inside rather than lit from above like the warships.

            // Thunder Brood: what nests in an anvil cloud. Most legs and the largest sac here --
            // it is mostly discharge with an animal around it.
            new CreatureProfile("boss-thunder-brood", 358, 262,
                    new Color(0x25304c), new Color(0x0e1422), new Color(0xdfe8fb),
                    new Color(0x8fb4ff), 6, 4, 0.29,
                    new double[][]{{0.50, 0.95}, {0.36, 0.87}, {0.28, 0.68}, {0.26, 0.46},
                            {0.33, 0.26}, {0.42, 0.15}, {0.50, 0.19}, {0.58, 0.15},
                            {0.67, 0.26}, {0.74, 0.46}, {0.72, 0.68}, {0.64, 0.87}}),

            // Static Crawler: the ground level's own, and the only one of the three that walks on
            // something. Widest and flattest, with the smallest sac -- it earths itself.
            new CreatureProfile("boss-static-crawler", 386, 220,
                    new Color(0x1a2134), new Color(0x090d16), new Color(0xc4cee0),
                    new Color(0x6f96ec), 8, 6, 0.12,
                    new double[][]{{0.50, 0.96}, {0.31, 0.90}, {0.21, 0.74}, {0.19, 0.56},
                            {0.27, 0.42}, {0.38, 0.33}, {0.50, 0.37}, {0.62, 0.33},
                            {0.73, 0.42}, {0.81, 0.56}, {0.79, 0.74}, {0.69, 0.90}}),

            // Magnetar Maw: set into the cavern wall rather than crossing it. Two stub legs and
            // the most ribs in the galaxy, so it reads as a structure that opened.
            new CreatureProfile("boss-magnetar-maw", 330, 286,
                    new Color(0x141b2e), new Color(0x070a12), new Color(0xcdd8ea),
                    new Color(0x7ea8ff), 2, 7, 0.24,
                    new double[][]{{0.50, 0.98}, {0.34, 0.91}, {0.24, 0.73}, {0.21, 0.49},
                            {0.29, 0.27}, {0.39, 0.14}, {0.50, 0.18}, {0.61, 0.14},
                            {0.71, 0.27}, {0.79, 0.49}, {0.76, 0.73}, {0.66, 0.91}}),

            // ---- Galaxy 5: Null ---------------------------------------------------------------
            // Three again, on 42, 43 and 46 -- none of them the side-on leg, for the reason the
            // Cryonis block gives: CreatureProfile has no sideways field, so a creature cannot
            // guard one.
            //
            // These three are the widest spread of the record's own knobs any galaxy has used,
            // because Null's palette has less room to separate them than any galaxy before it --
            // there is not much daylight between three hides this dark. So the separation is
            // structural instead: eight legs, four legs and none at all.

            // Aspects run 0.97, 1.25 and 1.94, which is the widest spread of three creatures the
            // game has had. The first pass put them at 1.09, 1.28 and 1.59 and all three read as
            // the same dark rounded mass with pale ribs on it -- Ashfall's lesson about hulls, one
            // record over. Leg counts of 4, 0 and 8 were never going to carry it on their own: at
            // two hundred pixels a leg is a stub and the eye reads the outline first.

            // Hulk Choir: what took up residence in a dead fleet. Upright and gathered, the sac
            // carried high -- it sings out of the wrecks. The first creature in the game taller
            // than it is wide, which is most of what separates it here.
            new CreatureProfile("boss-hulk-choir", 300, 310,
                    new Color(0x241c3c), new Color(0x0d0a18), new Color(0xded4f4),
                    new Color(0xa878ff), 4, 6, 0.26,
                    new double[][]{{0.50, 0.96}, {0.38, 0.88}, {0.31, 0.70}, {0.29, 0.48},
                            {0.34, 0.28}, {0.42, 0.14}, {0.50, 0.18}, {0.58, 0.14},
                            {0.66, 0.28}, {0.71, 0.48}, {0.69, 0.70}, {0.62, 0.88}}),

            // Shroudmaw: the shroud itself, with a mouth in it. No legs at all -- the first
            // creature in the game that does not stand on anything, which the record supports
            // without a branch: legs at zero simply draws none. Fewest ribs and the largest sac,
            // so what reads is a spread of hide with a light behind it -- and the flattest outline
            // in the galaxy, warships included.
            new CreatureProfile("boss-shroudmaw", 400, 206,
                    new Color(0x1a1530), new Color(0x07050e), new Color(0xcfc2ec),
                    new Color(0x7d52e0), 0, 3, 0.31,
                    new double[][]{{0.50, 0.97}, {0.28, 0.92}, {0.16, 0.78}, {0.12, 0.58},
                            {0.20, 0.40}, {0.34, 0.28}, {0.50, 0.32}, {0.66, 0.28},
                            {0.80, 0.40}, {0.88, 0.58}, {0.84, 0.78}, {0.72, 0.92}}),

            // Shellborn: grown into the dead structure it guards. The most legs and the most ribs
            // in the game against the smallest sac -- all carapace and almost no light, which is
            // the opposite corner of this record from the Shroudmaw two levels earlier.
            new CreatureProfile("boss-shellborn", 336, 268,
                    new Color(0x120e22), new Color(0x050409), new Color(0xb9abd8),
                    new Color(0x6f46cc), 8, 8, 0.14,
                    new double[][]{{0.50, 0.98}, {0.33, 0.92}, {0.22, 0.76}, {0.19, 0.54},
                            {0.26, 0.34}, {0.38, 0.20}, {0.50, 0.24}, {0.62, 0.20},
                            {0.74, 0.34}, {0.81, 0.54}, {0.78, 0.76}, {0.67, 0.92}}),
    };

    private static void creatures() throws IOException {
        for (CreatureProfile profile : CREATURES) {
            for (int frame = 1; frame <= BOSS_FRAMES; frame++) {
                write(creatureFrame(profile, frame),
                        SPRITES.resolve(profile.directory()).resolve(frame + ".png"));
            }
        }
    }

    private static BufferedImage creatureFrame(CreatureProfile profile, int oneBasedFrame) {
        int w = profile.width();
        int h = profile.height();
        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);
        double phase = (oneBasedFrame - 1) / (double) BOSS_FRAMES;
        double pulse = StrictMath.sin(2 * Math.PI * phase);

        // Limbs first, so the mass sits over where they join it. Paired outward from the centre,
        // and each pair a little further back, so a six-legged thing reads as a crawler rather than
        // as a spider with everything at one height.
        g.setColor(darken(profile.hide(), 30));
        int pairs = Math.max(0, profile.legs() / 2);
        for (int side = -1; side <= 1; side += 2) {
            for (int pair = 0; pair < pairs; pair++) {
                double rootX = 0.5 + side * (0.14 + pair * 0.08);
                double rootY = 0.40 + pair * 0.09;
                // The stride alternates by pair, so the whole animal does not step at once.
                double stride = 0.05 * StrictMath.sin(2 * Math.PI * (phase + pair * 0.5));
                // Reaching well outside the body: drawn tucked in they sat behind it and only the
                // tips showed, which read as a rock with chips off it rather than as a thing with
                // legs. A limb has to break the outline to be seen at all.
                g.fill(path(w, h, new double[][]{
                        {rootX, rootY},
                        {rootX + side * (0.26 + stride), rootY + 0.16},
                        {rootX + side * (0.30 + stride), rootY + 0.30},
                        {rootX + side * (0.22 + stride), rootY + 0.31},
                        {rootX + side * (0.17 + stride), rootY + 0.18},
                        {rootX, rootY + 0.13}}));
            }
        }

        // Body: many vertices rather than curves, for the reason the hydra's torso records -- an
        // irregular outline reads as grown and a smooth one reads as moulded.
        g.setColor(profile.hide());
        Path2D body = path(w, h, profile.body());
        g.fill(body);
        g.setColor(profile.hideDark());
        g.setStroke(new BasicStroke(5f));
        g.draw(body);

        // The furnace it carries instead of a reactor, breathing.
        if (profile.sacScale() > 0) {
            softBlob(g, w * 0.5, h * 0.60,
                    w * profile.sacScale() * (1 + 0.13 * pulse),
                    h * profile.sacScale() * (1 + 0.13 * pulse), profile.sac(), 160);
        }

        // Ribs pushing through the hide.
        // Under the hide rather than painted on it: at full strength the bone read as white stripes
        // across a brown mass, which is the one thing ribs must not look like.
        g.setColor(alpha(profile.bone(), 120));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int rib = 0; rib < profile.ribs(); rib++) {
            double y = 0.42 + rib * (0.36 / Math.max(1, profile.ribs()));
            double reach = 0.20 - rib * 0.012;
            Path2D arc = new Path2D.Double();
            arc.moveTo(w * (0.5 - reach), h * y);
            arc.quadTo(w * 0.50, h * (y + 0.06), w * (0.5 + reach), h * y);
            g.draw(arc);
        }

        // A cluster of eyes on the leading edge. Without a front these were shapes rather than
        // animals -- the hydra gets the same read for free from its neck sockets.
        int eyes = 3 + profile.legs() / 2;
        for (int eye = 0; eye < eyes; eye++) {
            double across = 0.5 + (eye - (eyes - 1) / 2.0) * 0.062;
            double down = 0.84 - Math.abs(eye - (eyes - 1) / 2.0) * 0.022;
            double er = w * 0.017;
            g.setColor(profile.hideDark());
            g.fill(new Ellipse2D.Double(w * across - er * 1.6, h * down - er * 1.6,
                    er * 3.2, er * 3.2));
            g.setColor(alpha(profile.sac(), 200 + (int) (40 * pulse)));
            g.fill(new Ellipse2D.Double(w * across - er, h * down - er, er * 2, er * 2));
        }
        g.dispose();
        return image;
    }

    /**
     * Vaunt's rig: a walking machine with a man visible in the front of it.
     *
     * The one boss in the galaxy that is neither a warship nor an animal, and the art has to say so
     * before the fight explains it -- so the legs carry a real walk cycle and the cockpit is lit
     * from inside. A player should be able to see there is somebody in there, because shooting him
     * is how the fight ends.
     *
     * The cockpit is drawn here as part of the rig <em>and</em> written out separately by
     * {@link #cockpitFrame}, because it is also its own target with its own health.
     */
    private static void mech() throws IOException {
        for (int frame = 1; frame <= BOSS_FRAMES; frame++) {
            write(mechFrame(frame, 400, 300, RIG_PLATE, RIG_DARK),
                    SPRITES.resolve("boss-forge-rig").resolve(frame + ".png"));
            write(cockpitFrame(frame, 96),
                    SPRITES.resolve("boss-forge-rig-cockpit").resolve(frame + ".png"));
            write(armFrame(frame, 104, RIG_PLATE, RIG_DARK),
                    SPRITES.resolve("boss-forge-rig-arm").resolve(frame + ".png"));

            // Vaunt again, in Tempest. The three methods above take their canvas and their plate
            // colours as arguments so this could exist without touching what they draw: at the
            // literals Ashfall always used, every expression inside them is unchanged and the
            // committed frames stay byte-identical, which is what CI checks. Rule 2's escape
            // hatch, the same one BossProfile.plate is.
            //
            // Deliberately the same machine rather than a new one -- same silhouette, same pilot
            // behind the same glass, the furnace and cockpit ring still Ashfall orange, refitted
            // in storm plate. That recognition is the whole payoff for having built him once.
            //
            // 480x360 decodes to 340 on screen. PilotedMech strides to 0.8 of the arena breadth
            // and adds half its own width, so a body past 398 walks its shoulder off the edge --
            // and PilotedMechTest's x() <= 996 cannot see it happen.
            write(mechFrame(frame, 480, 360, STORM_PLATE, STORM_PLATE_DARK),
                    SPRITES.resolve("boss-storm-rig").resolve(frame + ".png"));
            write(cockpitFrame(frame, 112),
                    SPRITES.resolve("boss-storm-rig-cockpit").resolve(frame + ".png"));
            write(armFrame(frame, 136, STORM_PLATE, STORM_PLATE_DARK),
                    SPRITES.resolve("boss-storm-rig-arm").resolve(frame + ".png"));
        }
    }

    private static final Color RIG_PLATE = new Color(0x4a3a30);
    private static final Color RIG_DARK = new Color(0x201814);
    private static final Color RIG_HOT = new Color(0xff5a10);
    private static final Color GLASS = new Color(0x9adcff);

    /** The Storm-Rig's plate. Only the armour is repainted; the man and his furnace are not. */
    private static final Color STORM_PLATE = new Color(0x33405e);
    private static final Color STORM_PLATE_DARK = new Color(0x141a2a);

    /**
     * The rig's body.
     *
     * Canvas and plate colours are arguments rather than constants so a second, larger rig can be
     * drawn without editing a line of what this method draws. Pass Ashfall's 400x300 with
     * RIG_PLATE and RIG_DARK and the output is the frame that is already committed.
     */
    private static BufferedImage mechFrame(int oneBasedFrame, int w, int h, Color plate,
                                          Color dark) {
        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);
        double phase = (oneBasedFrame - 1) / (double) BOSS_FRAMES;
        double step = StrictMath.sin(2 * Math.PI * phase);

        // Legs. Two, out of phase, so the rig rocks as it walks -- a pure function of the frame,
        // exactly as the worm's lunge is, so nothing here needs state or a random.
        for (int side = -1; side <= 1; side += 2) {
            double swing = 0.045 * (side > 0 ? step : -step);
            double hipX = 0.5 + side * 0.17;
            g.setColor(darken(plate, 40));
            g.fill(path(w, h, new double[][]{
                    {hipX - 0.035, 0.44}, {hipX + 0.035, 0.44},
                    {hipX + 0.055 + swing, 0.70}, {hipX + 0.03 + swing, 0.72},
                    {hipX - 0.03 + swing, 0.72}, {hipX - 0.055 + swing, 0.70}}));
            // Foot, planted flat.
            g.setColor(dark);
            g.fill(new java.awt.geom.Rectangle2D.Double(
                    w * (hipX - 0.07 + swing), h * 0.70, w * 0.14, h * 0.055));
        }

        // Torso: a squat slab, counter-bobbing against the legs.
        double bob = 0.012 * step;
        Path2D torso = path(w, h, new double[][]{
                {0.50, 0.06 + bob}, {0.28, 0.13 + bob}, {0.22, 0.28 + bob}, {0.26, 0.45 + bob},
                {0.74, 0.45 + bob}, {0.78, 0.28 + bob}, {0.72, 0.13 + bob}});
        g.setColor(plate);
        g.fill(torso);
        g.setColor(dark);
        g.setStroke(new BasicStroke(5f));
        g.draw(torso);

        // Shoulder stubs only. The arm pods themselves are separate targets with their own frames,
        // so they cannot be drawn here -- an arm that has been shot off has to stop appearing, and
        // it cannot if it lives in the body's art.
        for (int side = -1; side <= 1; side += 2) {
            double stubX = 0.5 + side * 0.24;
            g.setColor(darken(plate, 30));
            g.fill(path(w, h, new double[][]{
                    {stubX - 0.045, 0.19 + bob}, {stubX + 0.045, 0.19 + bob},
                    {stubX + 0.035, 0.33 + bob}, {stubX - 0.035, 0.33 + bob}}));
        }

        // Furnace in the chest, breathing with the walk.
        softBlob(g, w * 0.5, h * 0.34, w * 0.13 * (1 + 0.10 * step),
                h * 0.11 * (1 + 0.10 * step), RIG_HOT, 170);

        drawCockpit(g, w, h, w * 0.5, h * (0.20 + bob), w * 0.085, step);
        g.dispose();
        return image;
    }

    /**
     * The cockpit alone, for the part that is shot separately.
     *
     * Drawn on its own small canvas rather than cropped out of the rig, so its box is the glass and
     * nothing else -- a crop would carry the shoulders with it and a shot that missed the man would
     * still register.
     */
    private static BufferedImage cockpitFrame(int oneBasedFrame, int size) {
        BufferedImage image = blank(size, size);
        Graphics2D g = paint(image);
        double phase = (oneBasedFrame - 1) / (double) BOSS_FRAMES;
        drawCockpit(g, size, size, size * 0.5, size * 0.5, size * 0.34,
                StrictMath.sin(2 * Math.PI * phase));
        g.dispose();
        return image;
    }

    /** One arm pod: a gun on a mount, and the thing standing between the player and the pilot. */
    private static BufferedImage armFrame(int oneBasedFrame, int size, Color plate, Color dark) {
        BufferedImage image = blank(size, size);
        Graphics2D g = paint(image);
        double phase = (oneBasedFrame - 1) / (double) BOSS_FRAMES;
        double pulse = StrictMath.sin(2 * Math.PI * phase);

        g.setColor(darken(plate, 16));
        g.fill(path(size, size, new double[][]{
                {0.24, 0.10}, {0.76, 0.10}, {0.84, 0.62}, {0.66, 0.90}, {0.34, 0.90}, {0.16, 0.62}}));
        g.setColor(dark);
        g.setStroke(new BasicStroke(4f));
        g.draw(path(size, size, new double[][]{
                {0.24, 0.10}, {0.76, 0.10}, {0.84, 0.62}, {0.66, 0.90}, {0.34, 0.90}, {0.16, 0.62}}));

        // Barrel down-arena, so the pod reads as pointing at the player rather than sideways.
        g.setColor(dark);
        g.fill(new java.awt.geom.Rectangle2D.Double(size * 0.43, size * 0.78, size * 0.14,
                size * 0.20));
        turret(g, size * 0.5, size * 0.46, size * 0.15, RIG_HOT, 1 + 0.24 * pulse);
        g.dispose();
        return image;
    }

    /** Glass, a frame, and the shape of somebody behind it. Shared so the two agree exactly. */
    private static void drawCockpit(Graphics2D g, int w, int h, double cx, double cy, double r,
                                    double step) {
        g.setColor(RIG_DARK);
        g.fill(new Ellipse2D.Double(cx - r * 1.18, cy - r * 1.18, r * 2.36, r * 2.36));
        g.setColor(alpha(GLASS, 210));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));

        // The pilot: a head and shoulders, dark against the lit glass. Deliberately a silhouette --
        // legible at two hundred pixels, where a face would be four brown smears.
        g.setColor(alpha(RIG_DARK, 235));
        g.fill(new Ellipse2D.Double(cx - r * 0.30, cy - r * 0.52, r * 0.60, r * 0.60));
        g.fill(path(w, h, new double[][]{
                {(cx - r * 0.62) / w, (cy + r * 0.62) / h},
                {(cx - r * 0.34) / w, (cy + r * 0.02) / h},
                {(cx + r * 0.34) / w, (cy + r * 0.02) / h},
                {(cx + r * 0.62) / w, (cy + r * 0.62) / h}}));

        // A highlight on the glass, sliding as the rig rocks, so it reads as a curved surface.
        g.setColor(alpha(Color.WHITE, 90));
        g.fill(new Ellipse2D.Double(cx - r * 0.66 + r * 0.10 * step, cy - r * 0.72,
                r * 0.40, r * 0.26));

        g.setColor(RIG_HOT);
        g.setStroke(new BasicStroke(3f));
        g.draw(new Ellipse2D.Double(cx - r * 1.06, cy - r * 1.06, r * 2.12, r * 2.12));
    }

    private static void monsters() throws IOException {
        for (int frame = 1; frame <= BOSS_FRAMES; frame++) {
            write(hydraTorsoFrame(frame), SPRITES.resolve("boss-hydra").resolve(frame + ".png"));
            write(hydraHeadFrame(frame), SPRITES.resolve("boss-hydra-head").resolve(frame + ".png"));
            write(wormMawFrame(frame),
                    SPRITES.resolve("boss-dune-leviathan").resolve(frame + ".png"));
            write(frozenTorsoFrame(frame),
                    SPRITES.resolve("boss-frozen-empress").resolve(frame + ".png"));
            write(empressHeadFrame(frame),
                    SPRITES.resolve("boss-frozen-empress-head").resolve(frame + ".png"));
            write(stormMawFrame(frame),
                    SPRITES.resolve("boss-storm-serpent").resolve(frame + ".png"));
            write(aeonFrame(frame), SPRITES.resolve("boss-aeon").resolve(frame + ".png"));
            write(aeonEyeFrame(frame),
                    SPRITES.resolve("boss-aeon-eye").resolve(frame + ".png"));
        }
        write(wormSegment(), SPRITES.resolve("worm-segment.png"));
        write(stormSegment(), SPRITES.resolve("storm-segment.png"));
        write(acidBall(), SPRITES.resolve("acid-ball.png"));
    }

    /**
     * Where each neck leaves the torso, as fractions of its width and height.
     *
     * Must match {@code entity.BossHead}, which roots its necks at the same fractions. If one
     * moves, the necks detach from their sockets. Low on the body rather than high: the heads
     * reach down-arena toward the player, so the sockets belong on that edge.
     */
    private static final double[] NECK_SOCKETS = {0.30, 0.50, 0.70};
    private static final double NECK_SOCKET_DEPTH = 0.78;

    /** The hydra's body: a squat sac of a thing on stubby legs, necks rising from three sockets. */
    private static BufferedImage hydraTorsoFrame(int oneBasedFrame) {
        int w = 460;
        int h = 300;
        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);
        double phase = (oneBasedFrame - 1) / (double) BOSS_FRAMES;
        double pulse = StrictMath.sin(2 * Math.PI * phase);

        // Legs first, so the mass sits over them.
        g.setColor(darken(HIDE, 30));
        for (int side = -1; side <= 1; side += 2) {
            for (int leg = 0; leg < 3; leg++) {
                double rootX = 0.5 + side * (0.20 + leg * 0.09);
                double rootY = 0.46 + leg * 0.10;
                g.fill(path(w, h, new double[][]{
                        {rootX, rootY}, {rootX + side * 0.10, rootY + 0.30},
                        {rootX + side * 0.05, rootY + 0.34}, {rootX, rootY + 0.14}}));
            }
        }

        // Body. Many vertices rather than curves: an irregular outline reads as organic where a
        // smooth one reads as moulded plastic.
        g.setColor(HIDE);
        java.awt.geom.Path2D body = path(w, h, new double[][]{
                {0.50, 0.94}, {0.31, 0.87}, {0.19, 0.71}, {0.14, 0.55}, {0.18, 0.40},
                {0.26, 0.31}, {0.34, 0.26}, {0.42, 0.30}, {0.50, 0.27}, {0.58, 0.30},
                {0.66, 0.26}, {0.74, 0.31}, {0.82, 0.40}, {0.86, 0.55}, {0.81, 0.71},
                {0.69, 0.87}});
        g.fill(body);
        g.setColor(HIDE_DARK);
        g.setStroke(new BasicStroke(5f));
        g.draw(body);

        // Gut sac, breathing. The organic answer to the warships' reactor core.
        softBlob(g, w * 0.5, h * 0.62, w * 0.20 * (1 + 0.12 * pulse),
                h * 0.20 * (1 + 0.12 * pulse), BILE, 150);

        // Ribs pushing through the hide.
        g.setColor(BONE);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int rib = 0; rib < 5; rib++) {
            double y = 0.44 + rib * 0.10;
            java.awt.geom.Path2D arc = new java.awt.geom.Path2D.Double();
            arc.moveTo(w * 0.26, h * y);
            arc.quadTo(w * 0.50, h * (y + 0.07), w * 0.74, h * y);
            g.draw(arc);
        }

        // Neck sockets: a dark ring each, so a neck emerges from the body rather than off it.
        for (double socket : NECK_SOCKETS) {
            double cx = w * socket;
            double cy = h * NECK_SOCKET_DEPTH;
            g.setColor(HIDE_DARK);
            g.fill(new Ellipse2D.Double(cx - 30, cy - 22, 60, 44));
            g.setColor(darken(HIDE, 10));
            g.fill(new Ellipse2D.Double(cx - 22, cy - 16, 44, 32));
        }
        g.dispose();
        return image;
    }

    /**
     * One head, pointing down the arena, jaw working on the breath.
     *
     * The eyes are the whole trick: black sockets with a pinprick of fire in them. A large glowing
     * eye reads as a machine, and this is not supposed to be a machine.
     */
    private static BufferedImage hydraHeadFrame(int oneBasedFrame) {
        int size = 150;
        BufferedImage image = blank(size, size);
        Graphics2D g = paint(image);
        double phase = (oneBasedFrame - 1) / (double) BOSS_FRAMES;
        double pulse = StrictMath.sin(2 * Math.PI * phase);
        double gape = 0.30 * (0.5 + 0.5 * pulse);

        // Throat glow behind the jaws, brightest at full gape: the tell before it spits.
        softBlob(g, size * 0.5, size * 0.72, size * 0.20, size * 0.18, BILE,
                (int) (60 + 150 * (0.5 + 0.5 * pulse)));

        // Lower jaw, swung open about the hinge. Same path as drawn, then transformed -- the
        // cheapest possible articulation and it never drifts out of register.
        java.awt.geom.Path2D jaw = path(size, size, new double[][]{
                {0.30, 0.58}, {0.50, 0.94}, {0.70, 0.58}, {0.58, 0.66}, {0.42, 0.66}});
        jaw.transform(java.awt.geom.AffineTransform.getRotateInstance(
                gape, size * 0.5, size * 0.58));
        g.setColor(darken(HIDE, 20));
        g.fill(jaw);
        g.setColor(HIDE_DARK);
        g.setStroke(new BasicStroke(4f));
        g.draw(jaw);

        // Skull.
        java.awt.geom.Path2D skull = path(size, size, new double[][]{
                {0.50, 0.72}, {0.26, 0.58}, {0.18, 0.36}, {0.28, 0.14}, {0.42, 0.06},
                {0.58, 0.06}, {0.72, 0.14}, {0.82, 0.36}, {0.74, 0.58}});
        g.setColor(HIDE);
        g.fill(skull);
        g.setColor(HIDE_DARK);
        g.setStroke(new BasicStroke(4.5f));
        g.draw(skull);

        // Teeth along the upper jaw.
        g.setColor(BONE);
        for (int tooth = 0; tooth < 5; tooth++) {
            double tx = 0.32 + tooth * 0.09;
            g.fill(path(size, size, new double[][]{
                    {tx, 0.60}, {tx + 0.045, 0.60}, {tx + 0.022, 0.70}}));
        }

        // Eyes: holes first, then a small hot pupil inside each.
        for (int side = -1; side <= 1; side += 2) {
            double cx = size * (0.5 + side * 0.14);
            double cy = size * 0.30;
            g.setColor(Color.BLACK);
            g.fill(new Ellipse2D.Double(cx - 15, cy - 13, 30, 26));
            softBlob(g, cx, cy, 7 * (0.6 + 0.4 * (0.5 + 0.5 * pulse)),
                    6 * (0.6 + 0.4 * (0.5 + 0.5 * pulse)), new Color(0xffb020), 230);
        }
        g.dispose();
        return image;
    }

    // ------------------------------------------------------------ the Frozen Empress

    /** Cryonis's organic palette: blue hide, frost-white bone, and a cold core instead of bile. */
    private static final Color ICE_HIDE = new Color(0x2b4a63);
    private static final Color ICE_HIDE_DARK = new Color(0x101f2c);
    private static final Color ICE_BONE = new Color(0xe4f4fb);
    private static final Color ICE_CORE = new Color(0x4fd0e8);

    /**
     * How many necks the Empress fields. Must match {@code Boss.FROZEN_EMPRESS}'s head count.
     *
     * The span is {@code entity.BossHead.SOCKET_SPAN}, repeated here rather than shared because the
     * generator is a standalone script and cannot see the game's classes. The positions are
     * computed from it rather than written out, so a torso drawn for four heads cannot silently
     * disagree with the arc the engine spreads four necks across.
     */
    private static final int EMPRESS_HEADS = 4;
    private static final double EMPRESS_SOCKET_SPAN = 0.40;

    /**
     * The Empress's body: a mass frozen into its own throne, four necks rising from it.
     *
     * Written beside {@link #hydraTorsoFrame} rather than by generalising it. That method draws
     * three sockets from a fixed array and its committed frames are what CI's byte-identical check
     * is protecting; parameterising it would have put twenty levels of art at risk to save forty
     * lines. The same trade {@link CreatureProfile} documents.
     *
     * Deliberately not a hydra recolour. No legs -- it does not walk, it is set into the ice -- and
     * shards where the hydra has ribs, so the two multi-headed bosses read as different things.
     */
    private static BufferedImage frozenTorsoFrame(int oneBasedFrame) {
        int w = 470;
        int h = 310;
        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);
        double phase = (oneBasedFrame - 1) / (double) BOSS_FRAMES;
        double pulse = StrictMath.sin(2 * Math.PI * phase);

        // The ice it is set into, drawn first so the body sits in it rather than on it. Angular on
        // purpose: every curve in this frame belongs to the creature, every edge to the ice.
        g.setColor(darken(ICE_HIDE_DARK, 10));
        for (int side = -1; side <= 1; side += 2) {
            for (int shelf = 0; shelf < 3; shelf++) {
                double rootX = 0.5 + side * (0.16 + shelf * 0.11);
                double rootY = 0.58 + shelf * 0.09;
                g.fill(path(w, h, new double[][]{
                        {rootX, rootY}, {rootX + side * 0.13, rootY + 0.26},
                        {rootX + side * 0.04, rootY + 0.34}, {rootX - side * 0.03, rootY + 0.12}}));
            }
        }

        // Body. Fewer vertices than the hydra's and flatter across the top: this one is broad and
        // low, a thing that settled rather than a thing that stands.
        g.setColor(ICE_HIDE);
        java.awt.geom.Path2D body = path(w, h, new double[][]{
                {0.50, 0.93}, {0.29, 0.86}, {0.17, 0.70}, {0.13, 0.52}, {0.19, 0.36},
                {0.30, 0.26}, {0.42, 0.22}, {0.58, 0.22}, {0.70, 0.26}, {0.81, 0.36},
                {0.87, 0.52}, {0.83, 0.70}, {0.71, 0.86}});
        g.fill(body);
        g.setColor(ICE_HIDE_DARK);
        g.setStroke(new BasicStroke(5f));
        g.draw(body);

        // The core, breathing. Cold where the hydra's gut is hot.
        softBlob(g, w * 0.5, h * 0.58, w * 0.21 * (1 + 0.10 * pulse),
                h * 0.21 * (1 + 0.10 * pulse), ICE_CORE, 150);

        // Shards pushing out through the hide, longest at the centre.
        g.setColor(ICE_BONE);
        for (int side = -1; side <= 1; side += 2) {
            for (int shard = 0; shard < 4; shard++) {
                double baseX = 0.5 + side * (0.06 + shard * 0.09);
                double reach = 0.20 - shard * 0.035;
                g.fill(path(w, h, new double[][]{
                        {baseX, 0.42}, {baseX + side * 0.035, 0.42},
                        {baseX + side * 0.012, 0.42 - reach}}));
            }
        }

        // Neck sockets. Positions computed from the same span the engine spreads its necks across,
        // so four heads land on four sockets. Sized as fractions of the canvas rather than the
        // hydra's absolute pixels: four sockets across the same span sit closer together, and at
        // that method's 60px rings the outer two would have overlapped their neighbours.
        double outerX = w * 0.095;
        double outerY = h * 0.105;
        double innerX = w * 0.068;
        double innerY = h * 0.076;
        for (int head = 0; head < EMPRESS_HEADS; head++) {
            double spread = head / (double) (EMPRESS_HEADS - 1);
            double cx = w * (0.5 + EMPRESS_SOCKET_SPAN * (spread - 0.5));
            double cy = h * NECK_SOCKET_DEPTH;
            g.setColor(ICE_HIDE_DARK);
            g.fill(new Ellipse2D.Double(cx - outerX / 2, cy - outerY / 2, outerX, outerY));
            g.setColor(darken(ICE_HIDE, 10));
            g.fill(new Ellipse2D.Double(cx - innerX / 2, cy - innerY / 2, innerX, innerY));
        }
        g.dispose();
        return image;
    }

    /**
     * One of the Empress's heads: a crystal skull rather than a jawed one.
     *
     * Its own method rather than a recolour of {@link #hydraHeadFrame}, for the reason the Ashfall
     * notes give about hulls -- at a hundred pixels the eye reads silhouette, and an ice boss
     * wearing the hydra's heads is the hydra in a different palette.
     */
    private static BufferedImage empressHeadFrame(int oneBasedFrame) {
        int size = 156;
        BufferedImage image = blank(size, size);
        Graphics2D g = paint(image);
        double phase = (oneBasedFrame - 1) / (double) BOSS_FRAMES;
        double pulse = StrictMath.sin(2 * Math.PI * phase);
        double gape = 0.26 * (0.5 + 0.5 * pulse);

        // The cold behind the mouth, brightest just before it fires.
        softBlob(g, size * 0.5, size * 0.70, size * 0.19, size * 0.17, ICE_CORE,
                (int) (60 + 150 * (0.5 + 0.5 * pulse)));

        // Lower jaw: a single wedge, hinged like the hydra's but angular rather than toothed.
        java.awt.geom.Path2D jaw = path(size, size, new double[][]{
                {0.32, 0.58}, {0.50, 0.96}, {0.68, 0.58}, {0.50, 0.64}});
        jaw.transform(java.awt.geom.AffineTransform.getRotateInstance(
                gape, size * 0.5, size * 0.58));
        g.setColor(darken(ICE_HIDE, 20));
        g.fill(jaw);
        g.setColor(ICE_HIDE_DARK);
        g.setStroke(new BasicStroke(4f));
        g.draw(jaw);

        // Skull: faceted, six flat planes rather than the hydra's rounded nine.
        java.awt.geom.Path2D skull = path(size, size, new double[][]{
                {0.50, 0.70}, {0.24, 0.54}, {0.20, 0.30}, {0.38, 0.08},
                {0.62, 0.08}, {0.80, 0.30}, {0.76, 0.54}});
        g.setColor(ICE_HIDE);
        g.fill(skull);
        g.setColor(ICE_HIDE_DARK);
        g.setStroke(new BasicStroke(4.5f));
        g.draw(skull);

        // A crown of shards along the top, which is where the name comes from.
        g.setColor(ICE_BONE);
        for (int spike = 0; spike < 5; spike++) {
            double sx = 0.30 + spike * 0.10;
            double reach = spike == 2 ? 0.16 : 0.09;
            g.fill(path(size, size, new double[][]{
                    {sx, 0.16}, {sx + 0.05, 0.16}, {sx + 0.025, 0.16 - reach}}));
        }

        // Eyes: pale rather than hot. Nothing behind them is burning.
        for (int side = -1; side <= 1; side += 2) {
            double cx = size * (0.5 + side * 0.15);
            double cy = size * 0.34;
            g.setColor(Color.BLACK);
            g.fill(new Ellipse2D.Double(cx - 15, cy - 12, 30, 24));
            softBlob(g, cx, cy, 7 * (0.6 + 0.4 * (0.5 + 0.5 * pulse)),
                    6 * (0.6 + 0.4 * (0.5 + 0.5 * pulse)), ICE_BONE, 230);
        }
        g.dispose();
        return image;
    }

    /** The worm's maw, seen from the side and opening left, into the player. */
    /**
     * Aeon's eyes ride the same span the engine spreads its necks across.
     *
     * Its own constant rather than the Empress's, even though the number is the same one. What both
     * must match is {@code entity.BossHead.SOCKET_SPAN}; they do not have to match each other, and
     * naming this one after her would tie two unrelated bosses together for the sake of a literal.
     */
    private static final int AEON_EYES = 4;
    private static final double AEON_SOCKET_SPAN = 0.40;

    /** Void violet, and the only palette in the game whose light is entirely outside its body. */
    private static final Color AEON_HIDE = new Color(0x140f26);
    private static final Color AEON_HIDE_DARK = new Color(0x06040c);
    private static final Color AEON_BONE = new Color(0xd8ccf8);
    private static final Color AEON_CORE = new Color(0x9a6bff);

    /**
     * Aeon, the Hollow Star: the campaign's last boss, and a star with its middle gone.
     *
     * A third method beside {@link #hydraTorsoFrame} and {@link #frozenTorsoFrame} rather than a
     * generalisation of either, for the third time and the same reason -- their committed frames are
     * what CI's byte-identical check protects, and forty saved lines is not worth thirty levels of
     * art. The trade {@link CreatureProfile} documents.
     *
     * The construction is the inverse of every other boss in the game. Every one of them is a lit
     * body: hull or hide with a core burning inside it. This one is a hole with the light outside
     * it, which is the whole reason it does not reuse a torso.
     */
    private static BufferedImage aeonFrame(int oneBasedFrame) {
        int size = 450;
        BufferedImage image = blank(size, size);
        Graphics2D g = paint(image);
        double phase = (oneBasedFrame - 1) / (double) BOSS_FRAMES;
        double pulse = StrictMath.sin(2 * Math.PI * phase);

        double cx = size * 0.5;
        double cy = size * 0.48;
        double shell = size * 0.29 * (1 + 0.04 * pulse);

        // Filaments first, so they read as coming out from behind the body rather than over it.
        // Alternating thickness: sixteen identical spokes read as a mechanical gear rather than as
        // something being pulled apart.
        g.setColor(alpha(AEON_CORE, 70));
        for (int spoke = 0; spoke < 16; spoke++) {
            double angle = 2 * Math.PI * spoke / 16.0;
            double reach = shell * (1.20 + 0.24 * StrictMath.cos(angle * 3 + 2 * Math.PI * phase));
            double half = size * (spoke % 2 == 0 ? 0.011 : 0.006);
            double ax = StrictMath.cos(angle);
            double ay = StrictMath.sin(angle);
            Path2D filament = new Path2D.Double();
            filament.moveTo(cx + ay * half, cy - ax * half);
            filament.lineTo(cx + ax * reach, cy + ay * reach);
            filament.lineTo(cx - ay * half, cy + ax * half);
            filament.closePath();
            g.fill(filament);
        }

        // The corona, as two annuli. One gradient from the centre would put its brightest pixel
        // exactly where this thing is emptiest, which is the opposite of the read.
        for (int ring = 0; ring < 2; ring++) {
            double outer = shell * (1.32 + ring * 0.26);
            float inner = (float) (shell * (0.96 + ring * 0.18) / outer);
            Color tint = ring == 0 ? brighten(AEON_CORE, 40) : AEON_CORE;
            g.setPaint(new RadialGradientPaint(
                    (float) cx, (float) cy, (float) outer,
                    new float[]{0f, inner, inner + 0.06f, 1f},
                    new Color[]{alpha(tint, 0), alpha(tint, 0),
                            alpha(tint, ring == 0 ? 150 : 92), alpha(tint, 0)}));
            g.fill(new Ellipse2D.Double(cx - outer, cy - outer, outer * 2, outer * 2));
        }

        // The hollow itself.
        g.setColor(AEON_HIDE_DARK);
        g.fill(new Ellipse2D.Double(cx - shell, cy - shell, shell * 2, shell * 2));

        // Shells inside it, dimming inward, so the hole has depth instead of being a flat disc.
        for (int inner = 1; inner <= 3; inner++) {
            double r = shell * (1 - inner * 0.22);
            g.setColor(alpha(AEON_CORE, 44 - inner * 11));
            g.setStroke(new BasicStroke((float) (size * 0.004)));
            g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        }

        // The rim, where the hollow stops and the light starts. The brightest edge in the frame.
        g.setColor(AEON_BONE);
        g.setStroke(new BasicStroke((float) (size * 0.008)));
        g.draw(new Ellipse2D.Double(cx - shell, cy - shell, shell * 2, shell * 2));

        // A collar of hide across the lower body, so the sockets sit in something solid rather
        // than in open light.
        g.setColor(AEON_HIDE);
        g.fill(path(size, size, new double[][]{
                {0.20, 0.62}, {0.30, 0.86}, {0.50, 0.93}, {0.70, 0.86}, {0.80, 0.62},
                {0.66, 0.72}, {0.50, 0.76}, {0.34, 0.72}}));

        // Neck sockets, at the span and depth the engine spreads its necks across, so four eyes
        // land on four sockets. Same arithmetic as frozenTorsoFrame and it must stay that way:
        // BossHead.socket() is 0.5 + SOCKET_SPAN * (spread - 0.5), and a body drawn with the wrong
        // count grows a neck out of blank hide.
        double socketOuter = size * 0.075;
        double socketInner = size * 0.052;
        for (int eye = 0; eye < AEON_EYES; eye++) {
            double spread = eye / (double) (AEON_EYES - 1);
            double sx = size * (0.5 + AEON_SOCKET_SPAN * (spread - 0.5));
            double sy = size * NECK_SOCKET_DEPTH;
            g.setColor(AEON_HIDE_DARK);
            g.fill(new Ellipse2D.Double(sx - socketOuter / 2, sy - socketOuter / 2,
                    socketOuter, socketOuter));
            g.setColor(alpha(AEON_CORE, 120));
            g.fill(new Ellipse2D.Double(sx - socketInner / 2, sy - socketInner / 2,
                    socketInner, socketInner));
        }
        g.dispose();
        return image;
    }

    /**
     * One of Aeon's four eyes: hollow like the thing it belongs to.
     *
     * Its own method rather than a recolour of {@link #empressHeadFrame} or
     * {@link #hydraHeadFrame}, for the reason the Ashfall notes give about hulls -- at a hundred
     * pixels the eye reads silhouette, and a third multi-headed boss wearing the second one's
     * skulls is the second one in a different palette.
     */
    private static BufferedImage aeonEyeFrame(int oneBasedFrame) {
        int size = 156;
        BufferedImage image = blank(size, size);
        Graphics2D g = paint(image);
        double phase = (oneBasedFrame - 1) / (double) BOSS_FRAMES;
        double pulse = StrictMath.sin(2 * Math.PI * phase);
        double cx = size * 0.5;
        double cy = size * 0.5;
        double r = size * 0.40;

        g.setColor(AEON_HIDE);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setColor(AEON_HIDE_DARK);
        g.setStroke(new BasicStroke(size * 0.045f));
        g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));

        // The iris is an annulus, not a disc -- the body's construction at one twelfth the size.
        double iris = r * (0.62 + 0.06 * pulse);
        g.setPaint(new RadialGradientPaint(
                (float) cx, (float) cy, (float) iris,
                new float[]{0f, 0.55f, 0.82f, 1f},
                new Color[]{alpha(AEON_CORE, 0), alpha(AEON_CORE, 30),
                        brighten(AEON_CORE, 50), alpha(AEON_CORE, 0)}));
        g.fill(new Ellipse2D.Double(cx - iris, cy - iris, iris * 2, iris * 2));

        // The pupil, in bone rather than core, so there is exactly one warm point in the frame.
        double pupil = r * 0.14;
        g.setColor(AEON_BONE);
        g.fill(new Ellipse2D.Double(cx - pupil, cy - pupil, pupil * 2, pupil * 2));
        g.dispose();
        return image;
    }

    private static BufferedImage wormMawFrame(int oneBasedFrame) {
        int w = 420;
        int h = 300;
        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);
        double phase = (oneBasedFrame - 1) / (double) BOSS_FRAMES;
        double pulse = StrictMath.sin(2 * Math.PI * phase);
        double flare = 0.34 + 0.20 * (0.5 + 0.5 * pulse);

        // Body plates behind the head, receding to the right.
        g.setColor(CHITIN);
        for (int plate = 0; plate < 4; plate++) {
            double x = 0.52 + plate * 0.12;
            java.awt.geom.Path2D arc = new java.awt.geom.Path2D.Double();
            arc.moveTo(w * x, h * 0.18);
            arc.quadTo(w * (x + 0.10), h * 0.50, w * x, h * 0.82);
            arc.quadTo(w * (x + 0.02), h * 0.50, w * x, h * 0.18);
            g.fill(arc);
            g.setColor(CHITIN_DARK);
            g.setStroke(new BasicStroke(3f));
            g.draw(arc);
            g.setColor(CHITIN);
        }

        // Throat: a black hole the petals open around.
        g.setColor(Color.BLACK);
        g.fill(new Ellipse2D.Double(w * 0.24, h * 0.30, w * 0.30, h * 0.40));

        // Four mandible petals, flowering open on the breath.
        for (int petal = 0; petal < 4; petal++) {
            double angle = -flare + petal * (2 * flare / 3);
            java.awt.geom.Path2D blade = path(w, h, new double[][]{
                    {0.44, 0.50}, {0.06, 0.42}, {0.02, 0.50}, {0.06, 0.58}});
            blade.transform(java.awt.geom.AffineTransform.getRotateInstance(
                    angle, w * 0.44, h * 0.50));
            g.setColor(CHITIN);
            g.fill(blade);
            g.setColor(CHITIN_DARK);
            g.setStroke(new BasicStroke(3.5f));
            g.draw(blade);
        }

        // Two rings of teeth inside the throat.
        g.setColor(BONE);
        for (int ring = 0; ring < 2; ring++) {
            double radius = 0.10 - ring * 0.035;
            for (int tooth = 0; tooth < 12; tooth++) {
                double angle = tooth * 2 * Math.PI / 12;
                double tx = 0.39 + StrictMath.cos(angle) * radius * 0.7;
                double ty = 0.50 + StrictMath.sin(angle) * radius;
                g.fill(new Ellipse2D.Double(w * tx - 4, h * ty - 4, 8, 8));
            }
        }
        g.dispose();
        return image;
    }

    // ------------------------------------------------------------ the Storm Serpent

    /** The Storm Serpent's plate, and a bone pale enough to read against it. */
    private static final Color STORM_CHITIN = new Color(0x2c3550);
    private static final Color STORM_CHITIN_DARK = new Color(0x121828);
    private static final Color STORM_BONE = new Color(0xdce6f8);

    /**
     * The Storm Serpent's maw, seen from above and opening downward, out of the cloud deck.
     *
     * A new method beside {@link #wormMawFrame} rather than that one turned or parameterised, for
     * two reasons. The Leviathan's frames are drawn opening left, so reusing them here would need a
     * quarter turn; and a galaxy-four finale that is visibly galaxy one's boss rotated and
     * repainted reads as reused, which is the thing Cryonis recorded about not letting the Empress
     * become a hydra recolour.
     *
     * So: the same species, not the same animal. Three long petals against the Leviathan's four
     * short ones, arcs jumping the gaps between plates where that one has ribbed chitin, and a
     * throat lit from inside rather than flat black.
     */
    private static BufferedImage stormMawFrame(int oneBasedFrame) {
        int w = 480;
        int h = 360;
        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);
        double phase = (oneBasedFrame - 1) / (double) BOSS_FRAMES;
        double pulse = StrictMath.sin(2 * Math.PI * phase);
        double flare = 0.26 + 0.20 * (0.5 + 0.5 * pulse);

        // Body plates above the head, receding up into the deck it came out of. Thick lenses
        // rather than thin arcs: the first pass drew these 0.08 of the canvas deep and they read
        // as four scratches at on-screen size.
        for (int plate = 0; plate < 4; plate++) {
            double y = 0.38 - plate * 0.11;
            double half = 0.34 - plate * 0.05;
            Path2D arc = new Path2D.Double();
            arc.moveTo(w * (0.50 - half), h * y);
            arc.quadTo(w * 0.50, h * (y - 0.15), w * (0.50 + half), h * y);
            arc.quadTo(w * 0.50, h * (y + 0.03), w * (0.50 - half), h * y);
            g.setColor(brighten(STORM_CHITIN, 14 - plate * 6));
            g.fill(arc);
            g.setColor(STORM_CHITIN_DARK);
            g.setStroke(new BasicStroke(3.5f));
            g.draw(arc);
        }

        // Arcs jumping the gaps between plates, which is what makes it this galaxy's animal.
        g.setColor(alpha(STORM_ACCENT, 150 + (int) (60 * (0.5 + 0.5 * pulse))));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int gap = 0; gap < 3; gap++) {
            double y = 0.335 - gap * 0.11;
            double kink = 0.05 * (gap % 2 == 0 ? pulse : -pulse);
            g.draw(new java.awt.geom.Line2D.Double(w * (0.30 + kink), h * y,
                    w * 0.50, h * (y - 0.05)));
            g.draw(new java.awt.geom.Line2D.Double(w * 0.50, h * (y - 0.05),
                    w * (0.70 - kink), h * y));
        }

        // The head itself: a broad shell, so what the player shoots at is an animal rather than a
        // mouth floating under some rings.
        Path2D skull = path(w, h, new double[][]{
                {0.50, 0.40}, {0.26, 0.46}, {0.18, 0.60}, {0.22, 0.76}, {0.34, 0.86},
                {0.66, 0.86}, {0.78, 0.76}, {0.82, 0.60}, {0.74, 0.46}});
        g.setColor(STORM_CHITIN);
        g.fill(skull);
        g.setColor(STORM_CHITIN_DARK);
        g.setStroke(new BasicStroke(4.5f));
        g.draw(skull);

        // Throat, lit from inside rather than the Leviathan's flat black.
        softBlob(g, w * 0.50, h * 0.68, w * 0.17, h * 0.19, STORM_ACCENT, 150);
        g.setColor(STORM_CHITIN_DARK);
        g.fill(new Ellipse2D.Double(w * 0.36, h * 0.56, w * 0.28, h * 0.26));

        // Three long mandible petals, flowering open down-arena on the breath.
        for (int petal = 0; petal < 3; petal++) {
            double angle = -flare + petal * flare;
            Path2D blade = path(w, h, new double[][]{
                    {0.50, 0.62}, {0.38, 0.95}, {0.50, 1.00}, {0.62, 0.95}});
            blade.transform(java.awt.geom.AffineTransform.getRotateInstance(
                    angle, w * 0.50, h * 0.62));
            g.setColor(brighten(STORM_CHITIN, 20));
            g.fill(blade);
            g.setColor(STORM_CHITIN_DARK);
            g.setStroke(new BasicStroke(3.5f));
            g.draw(blade);
        }

        // One ring of teeth, fewer and longer than the Leviathan's two rows.
        g.setColor(STORM_BONE);
        for (int tooth = 0; tooth < 9; tooth++) {
            double angle = tooth * 2 * Math.PI / 9;
            double tx = 0.50 + StrictMath.cos(angle) * 0.095;
            double ty = 0.69 + StrictMath.sin(angle) * 0.105;
            g.fill(new Ellipse2D.Double(w * tx - 6, h * ty - 6, 12, 12));
        }
        g.dispose();
        return image;
    }

    /**
     * One ring of the Storm Serpent's body.
     *
     * Its own sprite rather than the Leviathan's, because {@link #wormSegment} is not rotationally
     * symmetric -- it draws its bristles around one side only, which is right for a worm crossing
     * the screen and wrong for one striking down it. This ring has no up.
     */
    private static BufferedImage stormSegment() {
        int size = 120;
        BufferedImage image = blank(size, size);
        Graphics2D g = paint(image);

        g.setColor(STORM_CHITIN);
        g.fill(new Ellipse2D.Double(size * 0.12, size * 0.12, size * 0.76, size * 0.76));
        g.setColor(STORM_CHITIN_DARK);
        g.setStroke(new BasicStroke(4f));
        g.draw(new Ellipse2D.Double(size * 0.12, size * 0.12, size * 0.76, size * 0.76));
        g.setColor(brighten(STORM_CHITIN, 30));
        g.setStroke(new BasicStroke(3f));
        g.draw(new Ellipse2D.Double(size * 0.26, size * 0.26, size * 0.48, size * 0.48));

        // Plates the whole way round, evenly spaced, so no orientation is implied.
        g.setColor(STORM_BONE);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int plate = 0; plate < 12; plate++) {
            double angle = plate * 2 * Math.PI / 12;
            double cx = size * 0.5 + StrictMath.cos(angle) * size * 0.38;
            double cy = size * 0.5 + StrictMath.sin(angle) * size * 0.38;
            g.draw(new java.awt.geom.Line2D.Double(cx, cy,
                    cx + StrictMath.cos(angle) * 9, cy + StrictMath.sin(angle) * 9));
        }
        g.dispose();
        return image;
    }

    /** One armoured ring of the worm's body. The renderer trails several of these behind the maw. */
    private static BufferedImage wormSegment() {
        int size = 120;
        BufferedImage image = blank(size, size);
        Graphics2D g = paint(image);

        g.setColor(CHITIN);
        g.fill(new Ellipse2D.Double(size * 0.10, size * 0.16, size * 0.80, size * 0.68));
        g.setColor(CHITIN_DARK);
        g.setStroke(new BasicStroke(4f));
        g.draw(new Ellipse2D.Double(size * 0.10, size * 0.16, size * 0.80, size * 0.68));
        g.setColor(brighten(CHITIN, 30));
        g.setStroke(new BasicStroke(3f));
        g.draw(new Ellipse2D.Double(size * 0.24, size * 0.28, size * 0.52, size * 0.44));

        // Bristles around the underside, so a ring reads as something that grips.
        g.setColor(BONE);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int bristle = 0; bristle < 8; bristle++) {
            double angle = Math.PI * (0.15 + bristle * 0.10);
            double cx = size * 0.5 + StrictMath.cos(angle) * size * 0.40;
            double cy = size * 0.5 + StrictMath.sin(angle) * size * 0.34;
            g.draw(new java.awt.geom.Line2D.Double(cx, cy,
                    cx + StrictMath.cos(angle) * 12, cy + StrictMath.sin(angle) * 12));
        }
        g.dispose();
        return image;
    }

    /**
     * A ball of acid.
     *
     * Deliberately not the bright green of an ordinary enemy bolt, which is what it would collide
     * with visually -- that one is a hot white core inside a green glow. This is the opposite
     * read: matte, murky yellow-green with a dark olive rim and no highlight, so it looks like a
     * thrown liquid rather than an energy shot, and a player can tell at a glance which is which.
     */
    private static BufferedImage acidBall() {
        int size = 68;
        BufferedImage image = blank(size, size);
        Graphics2D g = paint(image);

        Color rim = new Color(0x3f4a10);
        Color body = new Color(0x9aa81c);
        Color sheen = new Color(0xc8d24a);

        softBlob(g, size * 0.5, size * 0.5, size * 0.48, size * 0.48, new Color(0x8a9a18), 110);
        g.setColor(rim);
        g.fill(new Ellipse2D.Double(size * 0.12, size * 0.12, size * 0.76, size * 0.76));
        g.setColor(body);
        g.fill(new Ellipse2D.Double(size * 0.19, size * 0.19, size * 0.62, size * 0.62));
        // Off-centre sheen rather than a centred hotspot: a wet surface, not a light source.
        g.setColor(sheen);
        g.fill(new Ellipse2D.Double(size * 0.30, size * 0.26, size * 0.24, size * 0.20));

        // A couple of drips coming off it, which no bolt in the game has.
        g.setColor(body);
        g.fill(new Ellipse2D.Double(size * 0.70, size * 0.62, size * 0.14, size * 0.18));
        g.fill(new Ellipse2D.Double(size * 0.22, size * 0.68, size * 0.11, size * 0.14));
        g.dispose();
        return image;
    }

    /**
     * One frame of a boss's idle animation.
     *
     * The whole sequence is pure geometry with no random draw: the frame index only shifts a phase,
     * so the frames reproduce byte for byte and the last one leads back into the first.
     */
    private static BufferedImage bossFrame(BossProfile profile, int oneBasedFrame) {
        int w = profile.width();
        int h = profile.height();
        double phase = (oneBasedFrame - 1) / (double) BOSS_FRAMES;
        double pulse = StrictMath.sin(2 * Math.PI * phase);

        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);

        // Wings first, so the central hull overlaps them.
        for (int side = -1; side <= 1; side += 2) {
            Path2D wing = mirrored(side, w, h, profile.wing());
            g.setColor(profile.plate().darker());
            g.fill(wing);
            g.setColor(HULL_DARK);
            g.setStroke(new BasicStroke(4f));
            g.draw(wing);

            // The sign matches `mirrored`, which flips for side -1, so pods land on the wing just
            // drawn rather than the opposite one.
            for (double[] pod : profile.turrets()) {
                double cx = w * (0.5 - side * pod[0]);
                turret(g, cx, h * pod[1], w * pod[2], profile.glow(), 1 + 0.22 * pulse);
            }
        }

        Path2D hull = path(w, h, profile.hull());
        g.setColor(profile.plate());
        g.fill(hull);
        g.setColor(HULL_DARK);
        g.setStroke(new BasicStroke(4.5f));
        g.draw(hull);

        // Armoured spine with a hot core.
        g.setColor(HULL_LIGHT);
        g.fill(new java.awt.geom.Rectangle2D.Double(w * 0.455, h * 0.12, w * 0.09, h * 0.56));
        g.setColor(profile.accent());
        g.fill(new java.awt.geom.Rectangle2D.Double(w * 0.472, h * 0.16, w * 0.056, h * 0.46));

        double coreRadius = w * profile.coreScale() * (1 + 0.18 * pulse);
        Color glow = profile.glow();
        g.setPaint(new RadialGradientPaint(
                (float) (w * 0.5), (float) (h * 0.44), (float) coreRadius,
                new float[]{0f, 0.4f, 1f},
                new Color[]{new Color(255, 240, 200, 255),
                        new Color(glow.getRed(), glow.getGreen(), glow.getBlue(),
                                channel((int) (170 + 60 * pulse))),
                        new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 0)}));
        g.fill(new Ellipse2D.Double(w * 0.5 - coreRadius, h * 0.44 - coreRadius,
                coreRadius * 2, coreRadius * 2));

        // Engine bank at the rear, flaring on the same cycle as the core.
        double spacing = 0.055;
        double first = -(profile.engines() - 1) / 2.0;
        double flare = w * 0.035 * (1 + 0.28 * pulse);
        for (int i = 0; i < profile.engines(); i++) {
            double ex = w * (0.5 + (first + i) * spacing);
            g.setColor(HULL_DARK);
            g.fill(new java.awt.geom.Rectangle2D.Double(ex - w * 0.018, h * 0.02, w * 0.036, h * 0.07));
            g.setPaint(new RadialGradientPaint(
                    (float) ex, (float) (h * 0.045), (float) flare,
                    new float[]{0f, 1f},
                    new Color[]{new Color(140, 210, 255, 220), new Color(140, 210, 255, 0)}));
            g.fill(new Ellipse2D.Double(ex - flare, h * 0.01, flare * 2, flare * 2));
        }

        // Prow blade.
        Path2D prow = path(w, h, new double[][]{{0.50, 0.99}, {0.455, 0.80}, {0.545, 0.80}});
        g.setColor(profile.glow());
        g.fill(prow);
        g.dispose();
        return image;
    }

    private static void turret(Graphics2D g, double cx, double cy, double r, Color glow,
                               double glowScale) {
        g.setColor(HULL_DARK);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setColor(HULL_LIGHT);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        double eye = r * 0.42 * glowScale;
        g.setColor(glow);
        g.fill(new Ellipse2D.Double(cx - eye, cy - eye, eye * 2, eye * 2));
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


    // ------------------------------------------------------------- rank insignia

    /** The four insignia tiers, in {@code prefs.Rank.Insignia} declaration order. */
    private static final String[] INSIGNIA_TIERS = {"chevrons", "rods", "bars", "stars"};

    /** Drawn at 4x the size the debrief shows them at, so the marks stay crisp when scaled down. */
    private static final int BADGE_WIDTH = 192;
    private static final int BADGE_HEIGHT = 88;

    /**
     * Rank insignia: four tiers, one to four marks each.
     *
     * Sixteen files rather than one per rank. {@code prefs.Rank} already folds twenty-six ranks
     * onto this grid -- a tier from where you are on the ladder, a count of marks within it -- so
     * a badge per rank would be ten copies of the same picture.
     *
     * Marks on a plate rather than alone on the backdrop, which is the whole difference between
     * this and the strokes it replaces: the plate is what makes them read as pinned to a uniform.
     */
    private static void insignia() throws IOException {
        for (int tier = 0; tier < INSIGNIA_TIERS.length; tier++) {
            for (int marks = 1; marks <= 4; marks++) {
                write(badge(tier, marks),
                        SPRITES.resolve("insignia/" + INSIGNIA_TIERS[tier] + "-" + marks + ".png"));
            }
        }
    }

    private static BufferedImage badge(int tier, int marks) {
        BufferedImage image = blank(BADGE_WIDTH, BADGE_HEIGHT);
        Graphics2D g = paint(image);

        // Plate: a dark field with a lit edge, and a highlight along the top that gives it a face.
        RoundRectangle2D plate =
                new RoundRectangle2D.Double(4, 4, BADGE_WIDTH - 8, BADGE_HEIGHT - 8, 18, 18);
        g.setColor(new Color(0x0f1626));
        g.fill(plate);
        g.setColor(BRAND.darker());
        g.setStroke(new BasicStroke(5f));
        g.draw(plate);
        g.setColor(new Color(255, 255, 255, 26));
        g.setStroke(new BasicStroke(3f));
        g.draw(new RoundRectangle2D.Double(12, 12, BADGE_WIDTH - 24, BADGE_HEIGHT - 24, 12, 12));

        // Marks twice: a dark pass offset down and right, then the bright one over it. Cheaper than
        // a blur and it is what stops the marks reading as flat against the plate.
        g.translate(2, 3);
        g.setColor(new Color(0, 0, 0, 128));
        marks(g, tier, marks);
        g.translate(-2, -3);
        g.setColor(BRAND);
        marks(g, tier, marks);

        g.dispose();
        return image;
    }

    /** The marks themselves, centred on the plate. Shapes follow prefs.Rank.Insignia's four tiers. */
    private static void marks(Graphics2D g, int tier, int count) {
        double midX = BADGE_WIDTH / 2.0;
        double midY = BADGE_HEIGHT / 2.0;
        switch (INSIGNIA_TIERS[tier]) {
            case "chevrons" -> {
                double pitch = 17;
                double top = midY - (count - 1) * pitch / 2 - 8;
                g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < count; i++) {
                    double y = top + i * pitch;
                    Path2D chevron = new Path2D.Double();
                    chevron.moveTo(midX - 30, y + 16);
                    chevron.lineTo(midX, y);
                    chevron.lineTo(midX + 30, y + 16);
                    g.draw(chevron);
                }
            }
            case "rods" -> forEachMark(g, count, 24, midX, x ->
                    g.fill(new RoundRectangle2D.Double(x - 6, midY - 26, 12, 52, 8, 8)));
            case "bars" -> forEachMark(g, count, 26, midX, x ->
                    g.fill(new Rectangle2D.Double(x - 8, midY - 22, 16, 44)));
            default -> forEachMark(g, count, 40, midX, x -> star(g, x, midY, 19));
        }
    }

    /** Lays marks out in a row centred on {@code midX}, so one, three and four all sit balanced. */
    private static void forEachMark(Graphics2D g, int count, double pitch, double midX,
                                    java.util.function.DoubleConsumer mark) {
        double first = midX - (count - 1) * pitch / 2;
        for (int i = 0; i < count; i++) {
            mark.accept(first + i * pitch);
        }
    }

    /** A five-pointed star, alternating outer and inner radius from the top. */
    private static void star(Graphics2D g, double cx, double cy, double radius) {
        Path2D path = new Path2D.Double();
        for (int point = 0; point < 10; point++) {
            double reach = point % 2 == 0 ? radius : radius * 0.42;
            double angle = -Math.PI / 2 + point * Math.PI / 5;
            double x = cx + StrictMath.cos(angle) * reach;
            double y = cy + StrictMath.sin(angle) * reach;
            if (point == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.closePath();
        g.fill(path);
    }

    /** Pickup icons: readable at 36px, and none of them a trademark. */
    private static void pickups() throws IOException {
        write(icon(g -> {
            // Three chevrons: speed.
            capsule(g, BRAND);
            g.setColor(brighten(BRAND, 90));
            g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 3; i++) {
                Path2D chevron = new Path2D.Double();
                double y = 38 + i * 22;
                chevron.moveTo(38, y + 16);
                chevron.lineTo(64, y);
                chevron.lineTo(90, y + 16);
                g.draw(chevron);
            }
        }), SPRITES.resolve("pickup-speed.png"));

        write(icon(g -> {
            // Medkit cross.
            Color red = new Color(0xd6263c);
            capsule(g, red);
            g.setColor(new Color(0xe8f1f2));
            g.fillRoundRect(56, 34, 16, 60, 8, 8);
            g.fillRoundRect(34, 56, 60, 16, 8, 8);
            g.setColor(alpha(red, 200));
            g.setStroke(new BasicStroke(3f));
            g.drawRoundRect(56, 34, 16, 60, 8, 8);
            g.drawRoundRect(34, 56, 60, 16, 8, 8);
        }), SPRITES.resolve("pickup-health.png"));

        write(icon(g -> {
            // Shield outline.
            Color blue = new Color(0x5aa6ff);
            capsule(g, blue);
            Path2D shield = new Path2D.Double();
            shield.moveTo(64, 26);
            shield.lineTo(100, 42);
            shield.curveTo(100, 84, 84, 98, 64, 106);
            shield.curveTo(44, 98, 28, 84, 28, 42);
            shield.closePath();
            g.setColor(new Color(0x2a4f8f));
            g.fill(shield);
            g.setColor(new Color(0x9fd0ff));
            g.setStroke(new BasicStroke(6f));
            g.draw(shield);
            // A band across it, so the plate is not a flat silhouette at 36px.
            g.setColor(alpha(new Color(0xdfefff), 120));
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new java.awt.geom.Line2D.Double(40, 56, 88, 56));
        }), SPRITES.resolve("pickup-shield.png"));

        write(icon(g -> {
            // Three bolts fanning upward, with heads, so it reads as outgoing fire rather than a
            // download arrow.
            Color cyan = new Color(0x7ce8ff);
            capsule(g, cyan);
            g.setColor(cyan);
            double[][] tips = {{38, 32}, {64, 24}, {90, 32}};
            for (double[] tip : tips) {
                g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new java.awt.geom.Line2D.Double(64, 102, tip[0], tip[1] + 16));
                Path2D head = new Path2D.Double();
                head.moveTo(tip[0], tip[1]);
                head.lineTo(tip[0] - 10, tip[1] + 18);
                head.lineTo(tip[0] + 10, tip[1] + 18);
                head.closePath();
                g.fill(head);
            }
        }), SPRITES.resolve("pickup-tri-shot.png"));

        write(icon(g -> {
            // A red column with a white-hot core: the beam this pickup actually fires. Red, not
            // pink, because the drawn beam is red and the icon has to promise the right weapon.
            Color red = new Color(0xff2a2a);
            capsule(g, red);
            g.setColor(alpha(red, 200));
            g.fillRoundRect(50, 22, 28, 66, 12, 12);
            g.setColor(new Color(0xff8a6a));
            g.fillRoundRect(57, 24, 14, 62, 7, 7);
            g.setColor(new Color(0xffffff));
            g.fillRoundRect(61, 26, 6, 58, 3, 3);
            // The muzzle flare beneath, so the column reads as leaving something rather than
            // floating.
            g.setColor(alpha(red, 235));
            Path2D flare = new Path2D.Double();
            flare.moveTo(28, 108);
            flare.lineTo(64, 84);
            flare.lineTo(100, 108);
            flare.closePath();
            g.fill(flare);
        }), SPRITES.resolve("pickup-mega-laser.png"));

        write(icon(g -> {
            // A missile: nose cone, body, fins, exhaust. The pickup that inherited the old mega
            // laser's projectile art.
            Color orange = new Color(0xff9a3c);
            capsule(g, orange);
            // Fins first, so the body is drawn over their roots.
            g.setColor(new Color(0xc7411f));
            Path2D fins = new Path2D.Double();
            fins.moveTo(52, 66);
            fins.lineTo(36, 92);
            fins.lineTo(52, 88);
            fins.closePath();
            g.fill(fins);
            Path2D right = new Path2D.Double();
            right.moveTo(76, 66);
            right.lineTo(92, 92);
            right.lineTo(76, 88);
            right.closePath();
            g.fill(right);

            g.setColor(new Color(0xe8eef5));
            g.fillRoundRect(52, 40, 24, 50, 10, 10);
            Path2D nose = new Path2D.Double();
            nose.moveTo(64, 18);
            nose.lineTo(78, 46);
            nose.lineTo(50, 46);
            nose.closePath();
            g.setColor(new Color(0xd6263c));
            g.fill(nose);
            // A band, which is what separates a missile from a plain capsule at icon size.
            g.setColor(new Color(0x9aa7b8));
            g.fillRect(52, 58, 24, 8);

            g.setPaint(new RadialGradientPaint(64f, 100f, 20f,
                    new float[]{0f, 0.5f, 1f},
                    new Color[]{new Color(255, 244, 214, 255), alpha(orange, 200),
                            alpha(orange, 0)}));
            g.fill(new Ellipse2D.Double(44, 80, 40, 40));
        }), SPRITES.resolve("pickup-rocket.png"));

        write(icon(g -> {
            // A small ship outline plus a plus sign: an extra life.
            capsule(g, BRAND);
            g.setColor(brighten(BRAND, 80));
            Path2D ship = new Path2D.Double();
            ship.moveTo(64, 26);
            ship.lineTo(80, 66);
            ship.lineTo(64, 58);
            ship.lineTo(48, 66);
            ship.closePath();
            g.fill(ship);
            g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(64, 80, 64, 106);
            g.drawLine(51, 93, 77, 93);
        }), SPRITES.resolve("pickup-extra-life.png"));
    }

    /**
     * The housing every pickup glyph sits in: bloom, dark plate, tinted rim, top highlight.
     *
     * The glyphs on their own were flat marks on nothing, which at 36px in a busy arena read as
     * clip-art rather than as objects worth flying into. One shared body makes them a set -- and
     * the tint carries which pickup it is even when the glyph is too small to resolve.
     */
    private static void capsule(Graphics2D g, Color tint) {
        g.setPaint(new RadialGradientPaint(64f, 64f, 64f,
                new float[]{0f, 0.6f, 1f},
                new Color[]{alpha(tint, 130), alpha(tint, 55), alpha(tint, 0)}));
        g.fill(new Ellipse2D.Double(0, 0, 128, 128));

        RoundRectangle2D body = new RoundRectangle2D.Double(14, 14, 100, 100, 34, 34);
        g.setPaint(new java.awt.GradientPaint(0f, 14f, new Color(0x1d2537), 0f, 114f,
                new Color(0x090d17)));
        g.fill(body);
        g.setPaint(alpha(tint, 235));
        g.setStroke(new BasicStroke(5f));
        g.draw(body);

        // A sheen along the top edge. Without it the plate is a hole rather than a surface. Hugging
        // the rim rather than floating inside it, or it reads as a smudge on the glyph.
        g.setPaint(new Color(255, 255, 255, 55));
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.QuadCurve2D.Double(28, 34, 64, 20, 100, 34));
    }

    private static BufferedImage icon(java.util.function.Consumer<Graphics2D> body) {
        BufferedImage image = blank(128, 128);
        Graphics2D g = paint(image);
        body.accept(g);
        g.dispose();
        return image;
    }

    /**
     * A level's sky: what makes one place look unlike the next.
     *
     * @param directory resource directory, one per level
     * @param kind      which backdrop recipe draws the three layers
     * @param seed      base seed; each layer adds its index, so seeds must be at least 3 apart
     * @param tintA     dominant tint -- nebula wash, high sky, soil or rock, depending on kind
     * @param tintB     secondary tint, alternated or blended with the first
     * @param blobs     large features: nebula washes, cloud decks, hills, light strips
     * @param density   count multiplier for the small scattered detail, whatever that detail is
     * @param starRed   added to each star's red channel, and likewise green and blue
     */
    private record Theme(String directory, Backdrop kind, long seed, Color tintA, Color tintB,
                         int blobs, double density, int starRed, int starGreen, int starBlue,
                         boolean sideways, double disc) {

        /**
         * The disc radius EVENT_HORIZON uses when a row does not say, as a fraction of the canvas
         * height.
         *
         * Equal to the factor {@code planet()} has always used for the one other disc in the game,
         * so the default is not an invented number. Only EVENT_HORIZON reads this field; every
         * other recipe ignores it, which is why forty existing rows can leave it unsaid and stay
         * byte-identical.
         */
        private static final double DEFAULT_DISC = 0.26;

        /** A level that scrolls top to bottom, which is all of them but the side-view legs. */
        Theme(String directory, Backdrop kind, long seed, Color tintA, Color tintB,
              int blobs, double density, int starRed, int starGreen, int starBlue) {
            this(directory, kind, seed, tintA, tintB, blobs, density,
                    starRed, starGreen, starBlue, false, DEFAULT_DISC);
        }

        /** A level that scrolls the other way. */
        Theme(String directory, Backdrop kind, long seed, Color tintA, Color tintB,
              int blobs, double density, int starRed, int starGreen, int starBlue,
              boolean sideways) {
            this(directory, kind, seed, tintA, tintB, blobs, density,
                    starRed, starGreen, starBlue, sideways, DEFAULT_DISC);
        }
    }

    /**
     * How a level's three layers are drawn.
     *
     * STARFIELD is open space. PLANET_RISE adds a world to look at. ATMOSPHERE is inside the air of
     * one, SURFACE is low over its ground, and CAVERN is enclosed by rock on both sides.
     * EVENT_HORIZON is open space with a black hole in it, and is the only one of the seven whose
     * subject sits on a different parallax layer from its detail.
     */
    private enum Backdrop {
        STARFIELD, PLANET_RISE, ATMOSPHERE, SURFACE, CAVERN, BELT, EVENT_HORIZON
    }

    private static final int BACKDROP_WIDTH = 996;
    private static final int BACKDROP_HEIGHT = 864;
    private static final String[] LAYER_NAMES = {"far", "mid", "near"};

    private static final Theme[] THEMES = {
            // Orbital Approach: open space with the target world hanging in it.
            new Theme("level-1", Backdrop.PLANET_RISE, 4200,
                    new Color(0x35, 0x2a, 0x6a, 26), new Color(0x10, 0x3a, 0x4a, 22),
                    5, 1.0, 0, 0, 18),
            // Verdant Airspace: inside the atmosphere, cloud decks lit from above.
            new Theme("level-2", Backdrop.ATMOSPHERE, 4210,
                    new Color(0x24, 0x54, 0x6b), new Color(0x9a, 0xc9, 0xa4),
                    7, 1.2, 0, 0, 0),
            // Canopy Descent: low over jungle, treetops crowding the frame.
            new Theme("level-3", Backdrop.SURFACE, 4220,
                    new Color(0x18, 0x33, 0x20), new Color(0x36, 0x6b, 0x38),
                    9, 1.7, 0, 0, 0),
            // Rust Canyon: oxide rock and dust, barer and bigger-boned than the jungle.
            new Theme("level-4", Backdrop.SURFACE, 4230,
                    new Color(0x36, 0x20, 0x16), new Color(0x7a, 0x44, 0x24),
                    5, 0.6, 0, 0, 0),
            // Undercity: a foundry tunnel, walls closing in on both sides.
            new Theme("level-5", Backdrop.CAVERN, 4240,
                    new Color(0x22, 0x28, 0x30), new Color(0x4f, 0xa8, 0xc9),
                    7, 1.0, 0, 0, 0),
            // Void Rift: the emptiest sky in the run.
            new Theme("level-6", Backdrop.STARFIELD, 4250,
                    new Color(0x2a, 0x10, 0x50, 34), new Color(0x12, 0x07, 0x30, 28),
                    3, 0.6, 8, -10, 20),
            // Star Core: too close to a star, everything washed red.
            new Theme("level-7", Backdrop.STARFIELD, 4260,
                    new Color(0x6b, 0x1c, 0x10, 34), new Color(0x7a, 0x2a, 0x0a, 26),
                    7, 1.0, 28, -4, -22),
            // Escape Vector: the world falling away astern.
            new Theme("level-8", Backdrop.PLANET_RISE, 4270,
                    new Color(0x25, 0x1c, 0x2c, 30), new Color(0x2f, 0x1a, 0x14, 24),
                    4, 0.8, 10, 4, 10),
            // Dust Reach: the side-view leg, flown along a dead world's terminator.
            // Open space on purpose -- ATMOSPHERE, SURFACE and CAVERN all have a built-in up
            // (lit sky bands, ground along the bottom edge, walls left and right), and scrolled
            // sideways they read as nonsense. Stars look the same lying on their side.
            new Theme("level-9", Backdrop.STARFIELD, 4280,
                    new Color(0x4a, 0x33, 0x14, 32), new Color(0x2a, 0x1d, 0x0c, 24),
                    5, 0.9, 22, 8, -14, true),
            // Hollow Womb: where the thing with three heads lives.
            new Theme("level-10", Backdrop.CAVERN, 4290,
                    new Color(0x28, 0x14, 0x1e), new Color(0x8a, 0x2f, 0x5a),
                    8, 1.3, 0, 0, 0),
            // ---- Galaxy 2: Ashfall (levels 11-20) ----------------------------------------
            // Seeds run 4300 to 4390, ten apart, extending the 4190 + 10 x level pattern. Each
            // image adds its layer index to the seed, so the gap has to leave room for three.
            //
            // The galaxy with a ceiling: it opens on a belt, spends its middle underground or
            // hugging the ground, and closes in a caldera. Only level 17 leaves the planet.

            // Cinder Belt: the first rocks, still warm from whatever broke them.
            new Theme("level-11", Backdrop.BELT, 4300,
                    new Color(0x50, 0x36, 0x28, 30), new Color(0x9a, 0x4a, 0x1e, 24),
                    4, 1.0, 22, 4, -16),
            // Ashfall Sky: soot decks lit from beneath by what is burning below them.
            new Theme("level-12", Backdrop.ATMOSPHERE, 4310,
                    new Color(0x2a, 0x18, 0x12), new Color(0x8a, 0x4a, 0x2a),
                    7, 1.1, 0, 0, 0),
            // Slagfields: cooling flows with crusted lobes standing out of them.
            new Theme("level-13", Backdrop.SURFACE, 4320,
                    new Color(0x24, 0x12, 0x0c), new Color(0x7a, 0x30, 0x14),
                    8, 1.5, 0, 0, 0),
            // Magma Vents: the first tunnel, and the first level with rock that hurts.
            new Theme("level-14", Backdrop.CAVERN, 4330,
                    new Color(0x1c, 0x0e, 0x0a), new Color(0x9a, 0x3a, 0x12),
                    5, 1.2, 0, 0, 0),
            // The Forgeworks: worked stone rather than raw, light strips down the walls.
            new Theme("level-15", Backdrop.CAVERN, 4340,
                    new Color(0x18, 0x12, 0x10), new Color(0xb0, 0x5a, 0x1c),
                    6, 1.4, 0, 0, 0),
            // Pyroclast: the sky full of what the vents threw up.
            new Theme("level-16", Backdrop.ATMOSPHERE, 4350,
                    new Color(0x30, 0x1a, 0x14), new Color(0xa0, 0x52, 0x24),
                    9, 1.6, 0, 0, 0),
            // Sunward Dive: flown side-on, so the sky tiles horizontally. Atmosphere, surface and
            // cavern all have a built-in up and cannot be used; starfield and belt are
            // axis-agnostic and can, which is what Cryonis's two side-on legs use.
            new Theme("level-17", Backdrop.STARFIELD, 4360,
                    new Color(0x3a, 0x1e, 0x0c, 30), new Color(0xff, 0x9a, 0x30, 26),
                    5, 1.0, 30, 12, -20, true),
            // Coronal Arc: the star itself hanging in the frame. A plain starfield only implies
            // one, and an implied star is an empty screen; the planet disc lit hot reads as the
            // real thing and costs no new drawing code.
            new Theme("level-18", Backdrop.PLANET_RISE, 4370,
                    new Color(0xff, 0x8a, 0x24, 30), new Color(0xff, 0xd0, 0x7a, 26),
                    6, 1.0, 28, 10, -18),
            // Ember Canyon: back on the ground, walls of banked cinder.
            new Theme("level-19", Backdrop.SURFACE, 4380,
                    new Color(0x22, 0x14, 0x10), new Color(0x8e, 0x3c, 0x18),
                    9, 1.8, 0, 0, 0),
            // Caldera Heart: the deepest tunnel, and what is sitting at the bottom of it.
            new Theme("level-20", Backdrop.CAVERN, 4390,
                    new Color(0x16, 0x0a, 0x08), new Color(0xd0, 0x4a, 0x10),
                    6, 1.5, 0, 0, 0),
            // ---- Galaxy 3: Cryonis (levels 21-30) ----------------------------------------
            // Seeds 4400 to 4490, ten apart, continuing the pattern. Cold tints throughout, and
            // the star channels pushed blue rather than red wherever stars are visible at all.
            //
            // The galaxy accent does not appear in these rows. tintB means a lit surface to rocks()
            // and ground() -- a rock's sunward face, a mound's top -- and painting those in a vivid
            // cyan turned every shard into a glowing ball and every hill into a bubble. Ashfall
            // runs 0x9a4a1e and 0x7a3014 there, mid-dark and desaturated, and this galaxy matches
            // that weight in blue. The accent is worn by the ships, which is where it reads.
            //
            // Two side-on legs, 21 and 29, which no other galaxy has. Both are BELT: rocks() draws
            // through wrapped(), which shifts by the canvas width instead of its height when the
            // theme is sideways, so a belt tiles correctly along either axis.
            new Theme("level-21", Backdrop.BELT, 4400,
                    new Color(0x28, 0x40, 0x52, 30), new Color(0x3c, 0x86, 0xa2, 24),
                    4, 1.0, -18, 2, 26, true),
            new Theme("level-22", Backdrop.ATMOSPHERE, 4410,
                    new Color(0x12, 0x28, 0x3c), new Color(0x6a, 0xc4, 0xdc),
                    7, 1.1, 0, 0, 0),
            new Theme("level-23", Backdrop.SURFACE, 4420,
                    new Color(0x14, 0x22, 0x2a), new Color(0x30, 0x6e, 0x82),
                    5, 1.0, 0, 0, 0),
            // Under-Ice: the water level. Same ATMOSPHERE recipe as a sky, tinted cold and dark,
            // so the cloud decks read as light shafts coming down through the shelf instead.
            new Theme("level-24", Backdrop.ATMOSPHERE, 4430,
                    new Color(0x05, 0x12, 0x1e), new Color(0x2f, 0x84, 0xa0),
                    8, 1.2, 0, 0, 0),
            new Theme("level-25", Backdrop.CAVERN, 4440,
                    new Color(0x0e, 0x1c, 0x28), new Color(0x4f, 0xd0, 0xe8),
                    5, 1.2, 0, 0, 0),
            // Black Trench: the darkest backdrop in the galaxy, and the fewest light strips.
            new Theme("level-26", Backdrop.CAVERN, 4450,
                    new Color(0x06, 0x0e, 0x16), new Color(0x2f, 0x90, 0xb0),
                    3, 1.3, 0, 0, 0),
            new Theme("level-27", Backdrop.SURFACE, 4460,
                    new Color(0x12, 0x24, 0x2c), new Color(0x3a, 0x7e, 0x92),
                    6, 1.1, 0, 0, 0),
            new Theme("level-28", Backdrop.ATMOSPHERE, 4470,
                    new Color(0x1a, 0x30, 0x44), new Color(0xbc, 0xe8, 0xf4),
                    9, 1.4, 0, 0, 0),
            // Shatter Drift: the second side-on leg. Denser and brighter than Frost Ring -- the
            // ring has broken up by the time the player gets back out to it.
            new Theme("level-29", Backdrop.BELT, 4480,
                    new Color(0x30, 0x4a, 0x5e, 32), new Color(0x4e, 0x9e, 0xb8, 26),
                    5, 1.3, -14, 6, 30, true),
            new Theme("level-30", Backdrop.CAVERN, 4490,
                    new Color(0x04, 0x0c, 0x18), new Color(0x4f, 0xd0, 0xe8),
                    6, 1.5, 0, 0, 0),
            // ---- Galaxy 4: Tempest (levels 31-40) ----------------------------------------
            // Seeds 4500 to 4590, ten apart, continuing the pattern.
            //
            // This galaxy has no floor: six of the ten run the ATMOSPHERE recipe, one is ground,
            // and nothing else repeats. Six levels off one drawing method is the hard part of
            // these rows, not the palette -- Ashfall learned that six flagships off one hull read
            // as one ship, and this is the same mistake one layer down.
            //
            // What separates them is tintB, and only tintB. The first pass set blobs to 10, 4, 3,
            // 6, 7, 9 to spread them and it changed nothing: sky() never reads blobs. It reads
            // density for the deck count, and paints every deck brighten(tintB, 78) over a
            // high-low-high gradient of tintA and tintB. So a pale tintB is a white deck on a
            // white band whatever else the row says, and the first six rows here were six of
            // those. Rendered side by side they were one sky.
            //
            // They now span the whole register instead: saturated mid blue at 31, dark saturated
            // at 32, near-white at 33, near-black at 37, desaturated overcast grey at 38 and
            // saturated indigo at 40, with density from 0.45 to 1.8 under it. blobs is left at
            // plausible values on those rows and is inert; it does real work only in stars(),
            // ground() and tunnel().
            //
            // The accent stays off tintB on the ground and belt rows for the reason Cryonis
            // records: it means a lit surface to rocks() and ground(). 0x46587e and 0x4e689a are
            // Ashfall's mid-dark weight in storm blue. The CAVERN row is the exception, where
            // tintB becomes wall light strips and the full 0x7ea8ff belongs.
            // Cloudwall: a wall of it. Saturated mid blue and the second-densest deck count, so
            // it reads as weather with colour in it rather than as haze.
            new Theme("level-31", Backdrop.ATMOSPHERE, 4500,
                    new Color(0x1a, 0x24, 0x40), new Color(0x3f, 0x6a, 0xc4),
                    10, 1.6, 0, 0, 0),
            // Thunderhead: the anvil. Dark and sparse, the opposite corner of the register from
            // Cloudwall on both axes at once.
            new Theme("level-32", Backdrop.ATMOSPHERE, 4510,
                    new Color(0x0c, 0x12, 0x20), new Color(0x24, 0x40, 0x7e),
                    4, 0.7, 0, 0, 0),
            // The Eye: the calm inside it. The one bright sky in the galaxy, and the emptiest --
            // near-white on a light ground, with barely half the usual decks.
            new Theme("level-33", Backdrop.ATMOSPHERE, 4520,
                    new Color(0x33, 0x45, 0x6e), new Color(0xdf, 0xe8, 0xff),
                    3, 0.45, 0, 0, 0),
            new Theme("level-34", Backdrop.BELT, 4530,
                    new Color(0x2c, 0x38, 0x54, 30), new Color(0x4e, 0x68, 0x9a, 24),
                    5, 1.2, -10, 0, 28),
            // Static Canyon: the one level in the galaxy with ground under it.
            new Theme("level-35", Backdrop.SURFACE, 4540,
                    new Color(0x14, 0x1a, 0x28), new Color(0x46, 0x58, 0x7e),
                    7, 1.3, 0, 0, 0),
            new Theme("level-36", Backdrop.CAVERN, 4550,
                    new Color(0x0a, 0x10, 0x20), new Color(0x7e, 0xa8, 0xff),
                    5, 1.2, 0, 0, 0),
            // Deep Descent: below the cloud base. Near-black on both tints, so the decks are
            // barely there -- the darkest backdrop in the galaxy by a wide margin.
            new Theme("level-37", Backdrop.ATMOSPHERE, 4560,
                    new Color(0x05, 0x08, 0x0f), new Color(0x1b, 0x2c, 0x4e),
                    6, 1.2, 0, 0, 0),
            // Upper Deck: on top of the weather, lit from above for once. Desaturated on purpose,
            // so it reads as overcast grey against five blue skies rather than as a sixth.
            new Theme("level-38", Backdrop.ATMOSPHERE, 4570,
                    new Color(0x2a, 0x35, 0x50), new Color(0xa8, 0xb4, 0xc8),
                    7, 0.9, 0, 0, 0),
            // Lightning Reach: the galaxy's side-on leg. Open space for the reason levels 9 and 17
            // are -- stars look the same lying on their side, and sky, ground and cavern do not.
            new Theme("level-39", Backdrop.STARFIELD, 4580,
                    new Color(0x1c, 0x26, 0x44, 32), new Color(0x3a, 0x54, 0x8e, 26),
                    4, 0.9, -12, 0, 30, true),
            // Storm Crown: the densest deck in the galaxy, and the only one that leans off blue
            // toward indigo -- the finale should not share a hue with the level that opened it.
            new Theme("level-40", Backdrop.ATMOSPHERE, 4590,
                    new Color(0x0e, 0x10, 0x24), new Color(0x6f, 0x7c, 0xe8),
                    9, 1.8, 0, 0, 0),
            // ---- Galaxy 5: Null (levels 41-50) ----------------------------------------------
            // Seeds 4600 to 4690, ten apart, finishing the pattern.
            //
            // No sky and no ground: not one ATMOSPHERE or SURFACE row, which rules out the two
            // recipes four galaxies leaned on hardest. What is left is five recipes over ten
            // levels -- the widest spread the game has had, and the answer to the failure Tempest
            // recorded, which was ten levels sharing two.
            //
            // Four of the ten run EVENT_HORIZON, and those four are not four skies with the same
            // object in them. They are one approach, authored on the disc field: 0.10 at the
            // corridor where the hole is first sighted, 0.20 in the ergosphere, 0.26 at the photon
            // ring where the ring is the whole image, and 0.32 at the finale where it fills the
            // frame. The ring and streak counts move with it, so the galaxy tells its story in the
            // backdrops rather than only in the level names.
            //
            // 0.32 is near the ceiling, not a round number: the ring reaches RING_REACH past the
            // horizon, so at 0.32 the whole feature is about 746 across against an 864 canvas, and
            // a feature taller than the canvas cannot wrap without overlapping itself.
            //
            // The accent stays off tintB on the belt rows for the reason Cryonis records -- there
            // it means a lit surface to rocks(). 0x50, 0x40, 0x76 and 0x5e, 0x4a, 0x88 are
            // Ashfall's mid-dark weight in void violet. The two CAVERN rows are the exception,
            // where tintB becomes wall light strips and the full 0x9a6bff belongs.

            // Dead Belt: a graveyard, and the last ordinary sky in the game. Separated from Tidal
            // Shear on blobs and density, which are the two fields stars() and rocks() both read.
            new Theme("level-41", Backdrop.BELT, 4600,
                    new Color(0x2a, 0x22, 0x42, 30), new Color(0x50, 0x40, 0x76, 24),
                    6, 1.1, 18, -8, 30),
            // Hulk Drift: the emptiest sky in the galaxy and the most washed -- barely any stars
            // over the most nebula in the game. A dead fleet in dust.
            new Theme("level-42", Backdrop.STARFIELD, 4610,
                    new Color(0x24, 0x18, 0x40, 34), new Color(0x46, 0x2c, 0x74, 26),
                    8, 0.55, 16, -10, 34),
            // Shroud: a dead world, shrouded. The galaxy's one PLANET_RISE, and the only body in
            // it that is not a hole -- near-black with a violet limb, so it reads as a planet that
            // went out rather than one hanging in the light.
            new Theme("level-43", Backdrop.PLANET_RISE, 4620,
                    new Color(0x1e, 0x18, 0x30, 30), new Color(0x8a, 0x62, 0xd8, 22),
                    7, 0.8, 14, -12, 30),
            // Lens Corridor: first sighting. The smallest disc, the tightest ring and the fewest
            // streaks -- it is a long way off and it is the only thing out here.
            new Theme("level-44", Backdrop.EVENT_HORIZON, 4630,
                    new Color(0x6e, 0x48, 0xc8, 24), new Color(0x9a, 0x6b, 0xff, 20),
                    3, 0.9, 12, -10, 28, false, 0.10),
            // Tidal Shear: the galaxy's side-on leg. A belt for the reason levels 9, 17 and 39 are
            // open space -- rock and stars look the same lying on their side, and this galaxy has
            // no sky or ground to get wrong anyway. Densest debris in the galaxy, sheared fine.
            new Theme("level-45", Backdrop.BELT, 4640,
                    new Color(0x32, 0x28, 0x50, 32), new Color(0x5e, 0x4a, 0x88, 26),
                    3, 1.6, 22, -6, 34, true),
            // The Shell: inside a dead structure. Sparse strips on near-black -- the enclosure is
            // a hull, not a cave, so there is less of it lit than in any tunnel so far.
            new Theme("level-46", Backdrop.CAVERN, 4650,
                    new Color(0x08, 0x06, 0x10), new Color(0x9a, 0x6b, 0xff),
                    5, 1.0, 0, 0, 0),
            // Ergosphere: close enough that spacetime is visibly turning. Twice the disc, a bright
            // ring and the most streaks so far.
            new Theme("level-47", Backdrop.EVENT_HORIZON, 4660,
                    new Color(0x7e, 0x52, 0xe0, 26), new Color(0xb4, 0x8c, 0xff, 22),
                    9, 1.2, 10, -8, 26, false, 0.20),
            // Photon Ring: the brightest backdrop in the game, and the only one where the ring is
            // the subject. Palest tints and fewest streaks -- nothing else in frame competes.
            new Theme("level-48", Backdrop.EVENT_HORIZON, 4670,
                    new Color(0xa0, 0x80, 0xe0, 28), new Color(0xc8, 0xb4, 0xf4, 20),
                    2, 0.7, 8, 0, 22, false, 0.26),
            // The Throat: the second dead structure, and the opposite corner of tunnel() from the
            // Shell -- most strips in the galaxy, densest detail, dimmer light.
            new Theme("level-49", Backdrop.CAVERN, 4680,
                    new Color(0x0c, 0x08, 0x14), new Color(0x6f, 0x46, 0xcc),
                    8, 1.6, 0, 0, 0),
            // Event Horizon: the last level in the campaign. Largest disc, most streaks, and the
            // dimmest ring of the four -- this close, the light is behind you.
            new Theme("level-50", Backdrop.EVENT_HORIZON, 4690,
                    new Color(0x4a, 0x2e, 0x88, 30), new Color(0x74, 0x50, 0xc0, 24),
                    12, 1.5, 6, -12, 24, false, 0.32),
    };

    /** Three parallax layers per level, scrolled at different rates by the renderer. */
    private static void backgrounds() throws IOException {
        for (Theme theme : THEMES) {
            for (int layer = 0; layer < LAYER_NAMES.length; layer++) {
                BufferedImage image = blank(BACKDROP_WIDTH, BACKDROP_HEIGHT);
                Graphics2D g = paint(image);
                // One Random per image, so adding a shape to one layer cannot shift any other file.
                Random random = new Random(theme.seed() + layer);

                switch (theme.kind()) {
                    case STARFIELD -> stars(g, theme, layer, random);
                    case PLANET_RISE -> {
                        stars(g, theme, layer, random);
                        if (layer == 0) {
                            planet(g, theme, random);
                        }
                    }
                    case ATMOSPHERE -> sky(g, theme, layer, random);
                    case SURFACE -> ground(g, theme, layer, random);
                    case CAVERN -> tunnel(g, theme, layer, random);
                    case BELT -> {
                        stars(g, theme, layer, random);
                        rocks(g, theme, layer, random);
                    }
                    // Draws its own stars, because it has to clip them; see eventHorizon.
                    case EVENT_HORIZON -> eventHorizon(g, theme, layer, random);
                }
                g.dispose();
                write(image, SPRITES.resolve(theme.directory())
                        .resolve(LAYER_NAMES[layer] + ".png"));
            }
        }
    }

    private static final int[] STAR_COUNTS = {520, 240, 90};
    private static final float[] STAR_MAX_RADIUS = {0.9f, 1.5f, 2.4f};

    private static void stars(Graphics2D g, Theme theme, int layer, Random random) {
        int w = BACKDROP_WIDTH;
        int h = BACKDROP_HEIGHT;

        if (layer == 0) {
            // Nebula wash, only on the deepest layer.
            for (int i = 0; i < theme.blobs(); i++) {
                double cx = random.nextDouble() * w;
                double cy = random.nextDouble() * h;
                double r = 180 + random.nextDouble() * 260;
                Color tint = i % 2 == 0 ? theme.tintA() : theme.tintB();
                g.setPaint(new RadialGradientPaint(
                        (float) cx, (float) cy, (float) r,
                        new float[]{0f, 1f},
                        new Color[]{tint, new Color(0, 0, 0, 0)}));
                g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            }
        }

        int count = (int) Math.round(STAR_COUNTS[layer] * theme.density());
        for (int i = 0; i < count; i++) {
            double x = random.nextDouble() * w;
            double y = random.nextDouble() * h;
            double r = 0.35 + random.nextDouble() * STAR_MAX_RADIUS[layer];
            int brightness = 150 + random.nextInt(106);
            int starAlpha = layer == 0 ? 150 : 210 + random.nextInt(46);
            g.setColor(new Color(
                    channel(brightness + theme.starRed()),
                    channel(brightness + theme.starGreen()),
                    channel(brightness + theme.starBlue()),
                    starAlpha));
            g.fill(new Ellipse2D.Double(x, y, r * 2, r * 2));
        }
    }

    /**
     * A world hanging in the far layer.
     *
     * Kept smaller than the canvas on purpose: layers tile by wrapping at the image height, and a
     * feature taller than that cannot repeat without overlapping itself.
     */
    private static void planet(Graphics2D g, Theme theme, Random random) {
        int w = BACKDROP_WIDTH;
        int h = BACKDROP_HEIGHT;
        double radius = h * 0.26;
        // Kept clear of the left and right edges too: only the vertical axis wraps, so a limb cut
        // flat by a side edge just looks like a mistake.
        double cx = radius + random.nextDouble() * (w - radius * 2);
        double cy = h * (0.2 + random.nextDouble() * 0.6);
        Color body = opaque(theme.tintA());
        Color rim = opaque(theme.tintB());

        wrapped(g, theme, copy -> {
            // Lit from the upper left, so the terminator reads as a sphere. Kept dim on purpose: the
            // planet covers a third of the arena, and at full brightness bullets crossing it stop
            // reading.
            copy.setPaint(new RadialGradientPaint(
                    (float) (cx - radius * 0.45), (float) (cy - radius * 0.5), (float) (radius * 1.7),
                    new float[]{0f, 0.45f, 1f},
                    new Color[]{brighten(body, 26), darken(body, 6), darken(body, 40)}));
            copy.fill(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));

            // Atmosphere: one gradient hugging the limb. Concentric strokes banded visibly instead.
            double halo = radius * 1.22;
            float limb = (float) (radius / halo);
            copy.setPaint(new RadialGradientPaint(
                    (float) cx, (float) cy, (float) halo,
                    new float[]{0f, limb * 0.98f, limb, 1f},
                    new Color[]{alpha(rim, 0), alpha(rim, 0), alpha(rim, 96), alpha(rim, 0)}));
            copy.fill(new Ellipse2D.Double(cx - halo, cy - halo, halo * 2, halo * 2));
        });
    }

    /** How far past the horizon the outer accretion ring reaches, as a multiple of the radius. */
    private static final double RING_REACH = 1.35;

    /** Base lensing streaks per layer, before the theme's blob count scales them. Far layer: none. */
    private static final int[] STREAK_COUNTS = {0, 5, 3};

    /**
     * A black hole. The one image in the game worth extra time.
     *
     * Three things, and which layer each lands on is the point of it. The disc and its accretion
     * ring sit on the far layer only, as {@link #planet} does, so they hang almost still while the
     * lensing streaks on the two nearer layers rush past -- which is what makes the hole read as
     * something at the bottom of a distance rather than a circle painted on the sky. It is also
     * what stops four levels of this recipe being four copies: the nearer layers differ between
     * them too, not just the far one.
     *
     * Four levels do run it, which is more than any galaxy has put on one backdrop since Tempest
     * put six on {@code sky()} and got one sky back. The lesson recorded from that is to check
     * which fields the recipe actually consumes before tuning them, so: this one reads {@code disc}
     * for the radius, {@code blobs} for the streak count, {@code density} through {@code stars()},
     * and both tints for the ring. All four vary across the four rows, and {@code disc} exists
     * precisely so the radius is not welded to something else that is already doing a job.
     *
     * No trigonometry, deliberately -- {@code Arc2D} takes degrees and {@code Math.toDegrees} is a
     * multiply. There is nothing here for {@code StrictMath} to protect.
     */
    private static void eventHorizon(Graphics2D g, Theme theme, int layer, Random random) {
        int w = BACKDROP_WIDTH;
        int h = BACKDROP_HEIGHT;
        double radius = h * theme.disc();

        // Kept clear of the side edges for planet()'s reason: on a top-down level only the vertical
        // axis wraps, so a limb cut flat by a side edge just looks like a mistake. It is the ring
        // that has to clear them, not the disc, because the ring reaches further.
        double margin = radius * RING_REACH;
        double span = Math.max(1, w - margin * 2);

        // Derived from the seed by arithmetic rather than drawn from the Random, and that is not a
        // style choice. backgrounds() seeds one Random per image as seed + layer, and stars() draws
        // from it a different number of times on each layer, so two draws taken here would put the
        // hole somewhere different on all three layers. Every layer has to agree on where it is:
        // the far layer paints it and the two nearer ones have to keep out of it.
        double cx = margin + (theme.seed() % 97) / 96.0 * span;
        double cy = h * (0.25 + (theme.seed() / 97 % 89) / 88.0 * 0.5);

        // Nothing on a nearer layer may be drawn inside the horizon. The renderer stacks far, mid
        // then near with ordinary alpha, so one opaque star on the near layer lands on top of the
        // hole painted on the far one, and a hole with stars in it stops reading as a hole at all.
        // Excluded rather than painted over, because on these layers there is nothing to paint with.
        // All three wrap copies come out, since wrapped() will draw the streaks at all three.
        if (layer > 0) {
            java.awt.geom.Area allowed =
                    new java.awt.geom.Area(new Rectangle2D.Double(0, 0, w, h));
            for (int copy = -1; copy <= 1; copy++) {
                double ox = theme.sideways() ? copy * w : 0;
                double oy = theme.sideways() ? 0 : copy * h;
                allowed.subtract(new java.awt.geom.Area(new Ellipse2D.Double(
                        cx - radius + ox, cy - radius + oy, radius * 2, radius * 2)));
            }
            g.setClip(allowed);
        }

        // Behind the hole on the far layer, and outside it on the two nearer ones.
        stars(g, theme, layer, random);

        if (layer == 0) {
            Color inner = opaque(theme.tintA());
            Color outer = opaque(theme.tintB());
            wrapped(g, theme, copy -> {
                // The accretion ring, as two annuli: hot and tight against the horizon, cooler and
                // wider outside it. One gradient from the centre would put its brightest pixel
                // inside the event horizon, where by definition there is nothing to light.
                for (int ring = 0; ring < 2; ring++) {
                    double reach = radius * (1.12 + ring * (RING_REACH - 1.12));
                    float edge = (float) (radius / reach);
                    Color tint = ring == 0 ? brighten(inner, 30) : outer;
                    int peak = ring == 0 ? 190 : 110;
                    copy.setPaint(new RadialGradientPaint(
                            (float) cx, (float) cy, (float) reach,
                            new float[]{0f, edge * 0.97f, edge, 1f},
                            new Color[]{alpha(tint, 0), alpha(tint, 0), alpha(tint, peak),
                                    alpha(tint, 0)}));
                    copy.fill(new Ellipse2D.Double(cx - reach, cy - reach, reach * 2, reach * 2));
                }

                // The hole. Flat black and fully opaque: the one shape in the game that is not
                // allowed to have anything behind it showing through.
                copy.setColor(Color.BLACK);
                copy.fill(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));

                // The photon ring: one bright hairline hugging the horizon. This is the detail that
                // stops the disc reading as a hole punched in the picture.
                copy.setColor(alpha(brighten(inner, 70), 210));
                copy.setStroke(new BasicStroke((float) Math.max(1.5, radius * 0.018)));
                copy.draw(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
            });
            return;
        }

        // Lensing streaks: arcs of light dragged round the hole. Struck about the same centre the
        // far layer painted the disc at -- which is what the seed-derived placement above buys --
        // so they read as light being pulled round it rather than as scratches near it.
        int streaks = (int) Math.round(STREAK_COUNTS[layer] * Math.max(1, theme.blobs()) / 3.0);
        Color tint = opaque(theme.tintB());
        for (int i = 0; i < streaks; i++) {
            double arcRadius = radius * (1.5 + random.nextDouble() * 2.4);
            double start = random.nextDouble() * 360;
            double sweep = 14 + random.nextDouble() * 46;
            double thickness = 1.2 + random.nextDouble() * (layer == 2 ? 3.4 : 1.8);
            int streakAlpha = (layer == 2 ? 70 : 44) + random.nextInt(40);
            g.setColor(alpha(tint, streakAlpha));
            g.setStroke(new BasicStroke((float) thickness, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            wrapped(g, theme, copy -> copy.draw(new java.awt.geom.Arc2D.Double(
                    cx - arcRadius, cy - arcRadius, arcRadius * 2, arcRadius * 2,
                    start, sweep, java.awt.geom.Arc2D.OPEN)));
        }
        g.setClip(null);
    }

    private static final int[] DECK_COUNTS = {7, 12, 17};
    private static final double[] DECK_REACH = {0.34, 0.23, 0.15};
    private static final double[] DECK_FLATTEN = {0.22, 0.16, 0.09};
    private static final int[] DECK_ALPHAS = {64, 98, 126};

    /** Inside a planet's air: a banded sky, broad decks behind, thin wisps rushing past in front. */
    private static void sky(Graphics2D g, Theme theme, int layer, Random random) {
        int w = BACKDROP_WIDTH;
        int h = BACKDROP_HEIGHT;
        Color high = opaque(theme.tintA());
        Color low = opaque(theme.tintB());

        if (layer == 0) {
            // Symmetric gradient: the top and bottom edges match, which is what lets it tile.
            g.setPaint(new java.awt.LinearGradientPaint(
                    0, 0, 0, h,
                    new float[]{0f, 0.5f, 1f},
                    new Color[]{high, low, high}));
            g.fillRect(0, 0, w, h);
        }

        int decks = (int) Math.round(DECK_COUNTS[layer] * theme.density());
        for (int i = 0; i < decks; i++) {
            double cx = random.nextDouble() * w;
            double cy = random.nextDouble() * h;
            double rx = w * DECK_REACH[layer] * (0.55 + random.nextDouble() * 0.7);
            double ry = rx * DECK_FLATTEN[layer] * (0.7 + random.nextDouble() * 0.7);
            Color tint = i % 2 == 0 ? brighten(low, 78) : brighten(high, 58);
            int deckAlpha = DECK_ALPHAS[layer];
            wrapped(g, theme, copy -> {
                softBlob(copy, cx, cy, rx, ry, tint, deckAlpha);
                // A brighter, tighter top so the deck has a lit crown rather than one flat wash.
                softBlob(copy, cx - rx * 0.15, cy - ry * 0.45, rx * 0.6, ry * 0.55,
                        brighten(tint, 40), deckAlpha);
            });
        }
    }

    private static final double[] MOUND_REACH = {0.018, 0.027, 0.038};
    private static final int[] MOUND_COUNTS = {40, 24, 12};
    private static final int[] MOUND_ALPHAS = {255, 225, 200};

    /** Low over a world's surface: ground, then hills, then whatever nearly brushes the hull. */
    private static void ground(Graphics2D g, Theme theme, int layer, Random random) {
        int w = BACKDROP_WIDTH;
        int h = BACKDROP_HEIGHT;
        Color soil = opaque(theme.tintA());
        Color growth = opaque(theme.tintB());

        if (layer == 0) {
            g.setColor(soil);
            g.fillRect(0, 0, w, h);
            for (int i = 0; i < theme.blobs(); i++) {
                double cx = random.nextDouble() * w;
                double cy = random.nextDouble() * h;
                double rx = w * (0.16 + random.nextDouble() * 0.26);
                wrapped(g, theme, copy -> softBlob(copy, cx, cy, rx, rx * 0.7, growth, 96));
            }
        }

        // Mounds: treetops on a jungle, boulders on a canyon. Density decides which it reads as.
        int count = (int) Math.round(MOUND_COUNTS[layer] * theme.density());
        int tintAlpha = MOUND_ALPHAS[layer];
        for (int i = 0; i < count; i++) {
            double cx = random.nextDouble() * w;
            double cy = random.nextDouble() * h;
            double r = w * MOUND_REACH[layer] * (0.6 + random.nextDouble() * 0.8);
            Color tint = random.nextBoolean() ? growth : brighten(soil, 34);
            // Lobe offsets are drawn before wrapping, not inside it: consuming randoms per copy
            // would give the three copies different shapes and break the tile.
            double[][] lobes = new double[3][3];
            for (double[] lobe : lobes) {
                lobe[0] = (random.nextDouble() - 0.5) * r * 0.9;
                lobe[1] = (random.nextDouble() - 0.5) * r * 0.7;
                lobe[2] = 0.62 + random.nextDouble() * 0.5;
            }

            wrapped(g, theme, copy -> {
                copy.setColor(alpha(darken(tint, 48), tintAlpha));
                for (double[] lobe : lobes) {
                    double lr = r * lobe[2];
                    copy.fill(new Ellipse2D.Double(cx + lobe[0] - lr,
                            cy + lobe[1] - lr + r * 0.2, lr * 2, lr * 2));
                }
                copy.setColor(alpha(tint, tintAlpha));
                for (double[] lobe : lobes) {
                    double lr = r * lobe[2] * 0.88;
                    copy.fill(new Ellipse2D.Double(cx + lobe[0] - lr, cy + lobe[1] - lr,
                            lr * 2, lr * 2));
                }
                copy.setColor(alpha(brighten(tint, 48), tintAlpha));
                double lit = r * 0.32;
                copy.fill(new Ellipse2D.Double(cx - lit - r * 0.22, cy - lit - r * 0.3,
                        lit * 2, lit * 2));
            });
        }
    }

    /** Rocks per layer: a few big ones near, many small ones far. */
    private static final int[] BELT_COUNTS = {26, 15, 7};
    private static final double[] BELT_REACH = {0.014, 0.028, 0.052};
    private static final int[] BELT_ALPHAS = {150, 190, 230};

    /**
     * A debris belt: rock tumbling past on every layer.
     *
     * A starfield alone never reads as a belt however it is tinted -- there has to be something with
     * an edge going by. Each rock is a few overlapping lobes rather than a disc, so it reads as
     * broken stone, and the lobe offsets are drawn <em>before</em> {@link #wrapped} for the reason
     * {@link #ground} records: consuming randoms inside the wrap would give the three copies
     * different shapes and split the tile at the seam.
     */
    private static void rocks(Graphics2D g, Theme theme, int layer, Random random) {
        int w = BACKDROP_WIDTH;
        int h = BACKDROP_HEIGHT;
        Color stone = opaque(theme.tintA());
        Color lit = opaque(theme.tintB());

        int count = (int) Math.round(BELT_COUNTS[layer] * theme.density());
        int shade = BELT_ALPHAS[layer];
        for (int i = 0; i < count; i++) {
            double cx = random.nextDouble() * w;
            double cy = random.nextDouble() * h;
            double r = w * BELT_REACH[layer] * (0.7 + random.nextDouble() * 0.9);
            double spin = random.nextDouble() * Math.PI;
            double[][] lobes = new double[4][3];
            for (int lobe = 0; lobe < lobes.length; lobe++) {
                double angle = spin + lobe * Math.PI / 2 + random.nextDouble() * 0.4;
                lobes[lobe][0] = StrictMath.cos(angle) * r * 0.45;
                lobes[lobe][1] = StrictMath.sin(angle) * r * 0.45;
                lobes[lobe][2] = 0.55 + random.nextDouble() * 0.45;
            }

            wrapped(g, theme, copy -> {
                copy.setColor(alpha(darken(stone, 40), shade));
                for (double[] lobe : lobes) {
                    double lr = r * lobe[2];
                    copy.fill(new Ellipse2D.Double(cx + lobe[0] - lr, cy + lobe[1] - lr,
                            lr * 2, lr * 2));
                }
                // One lit face, always from the same side, so a whole belt looks like one star is
                // lighting it rather than like a field of unrelated pebbles.
                // One highlight for the whole rock, always up and to the left. Lighting each lobe
                // separately gave every rock four bright dots and a belt of stone came out looking
                // like a bowl of blackberries -- a rock has one sun on it, not one per bump.
                copy.setColor(alpha(lit, Math.min(255, shade + 20)));
                double hr = r * 0.42;
                copy.fill(new Ellipse2D.Double(cx - hr - r * 0.30, cy - hr - r * 0.32,
                        hr * 2, hr * 2));
            });
        }
    }

    /** A foundry tunnel: rock walls on both sides, light strips down them, dust in between. */
    private static void tunnel(Graphics2D g, Theme theme, int layer, Random random) {
        int w = BACKDROP_WIDTH;
        int h = BACKDROP_HEIGHT;
        Color rock = opaque(theme.tintA());
        Color lamp = opaque(theme.tintB());

        if (layer == 0) {
            g.setColor(darken(rock, 14));
            g.fillRect(0, 0, w, h);
        }

        // Walls thicken on nearer layers, so the tunnel closes in as the parallax pulls past. Kept
        // narrow on purpose: enemies still spawn across the full arena, and ships flying over solid
        // rock reads as a bug rather than as depth.
        double reach = 0.07 + layer * 0.03;
        for (int side = -1; side <= 1; side += 2) {
            Path2D wall = tunnelWall(side, reach, random);
            g.setColor(layer == 0 ? rock : darken(rock, 18 + layer * 12));
            g.fill(wall);
            g.setColor(darken(rock, 60));
            g.setStroke(new BasicStroke(3f));
            g.draw(wall);
        }

        int lamps = (int) Math.round(theme.blobs() * theme.density());
        for (int i = 0; i < lamps; i++) {
            double lx = w * (i % 2 == 0 ? reach * 0.5 : 1 - reach * 0.5);
            double ly = random.nextDouble() * h;
            double r = w * 0.007;
            wrapped(g, theme, copy -> {
                softBlob(copy, lx, ly, r * 9, r * 9, lamp, 110);
                copy.setColor(brighten(lamp, 80));
                copy.fill(new java.awt.geom.RoundRectangle2D.Double(
                        lx - r, ly - r * 5, r * 2, r * 10, r * 2, r * 2));
            });
        }

        if (layer == LAYER_NAMES.length - 1) {
            // Dust down the middle, so the nearest layer has something moving between the walls.
            int motes = (int) Math.round(140 * theme.density());
            for (int i = 0; i < motes; i++) {
                double x = w * (reach + random.nextDouble() * (1 - reach * 2));
                double y = random.nextDouble() * h;
                double r = 0.6 + random.nextDouble() * 1.8;
                g.setColor(alpha(brighten(rock, 96), 90 + random.nextInt(80)));
                g.fill(new Ellipse2D.Double(x, y, r * 2, r * 2));
            }
        }
    }

    /** Harmonic weights for the tunnel edge, coarsest first. */
    private static final double[] WALL_HARMONICS = {0.5, 0.3, 0.2};

    /**
     * One side of the tunnel.
     *
     * The edge is a sum of harmonics with random phases rather than an independent sample per step.
     * Being periodic in the layer height it closes on itself exactly, which is what lets the wall
     * tile when the layer wraps; being continuous it reads as rock, where uncorrelated samples read
     * as a sawtooth however many of them there are.
     */
    private static Path2D tunnelWall(int side, double reach, Random random) {
        int w = BACKDROP_WIDTH;
        int h = BACKDROP_HEIGHT;
        int steps = 48;

        double[] phase = new double[WALL_HARMONICS.length];
        for (int i = 0; i < phase.length; i++) {
            phase[i] = random.nextDouble() * 2 * Math.PI;
        }

        Path2D wall = new Path2D.Double();
        double edge = side < 0 ? 0 : w;
        wall.moveTo(edge, 0);
        for (int i = 0; i <= steps; i++) {
            double along = i / (double) steps;
            double wobble = 0;
            for (int harmonic = 0; harmonic < WALL_HARMONICS.length; harmonic++) {
                wobble += WALL_HARMONICS[harmonic]
                        * StrictMath.sin(2 * Math.PI * (harmonic + 1) * along + phase[harmonic]);
            }
            double inset = reach * (1 + 0.42 * wobble);
            double x = side < 0 ? w * inset : w * (1 - inset);
            wall.lineTo(x, h * along);
        }
        wall.lineTo(edge, h);
        wall.closePath();
        return wall;
    }

    /**
     * Draws the same body three times, one canvas height apart.
     *
     * The renderer scrolls each layer by wrapping at the image height, so a shape clipped by the top
     * or bottom edge has to reappear on the other side or the join shows as a hard line once per
     * cycle. Stars get away without this because they are barely a pixel across; nothing larger does.
     */
    private static void wrapped(Graphics2D g, Theme theme,
                                java.util.function.Consumer<Graphics2D> body) {
        // Along whichever axis the renderer wraps this level on. A top-down level shifts by the
        // image height exactly as it always did, so every existing backdrop is unchanged.
        int dx = theme.sideways() ? BACKDROP_WIDTH : 0;
        int dy = theme.sideways() ? 0 : BACKDROP_HEIGHT;
        for (int copy = -1; copy <= 1; copy++) {
            Graphics2D shifted = (Graphics2D) g.create();
            shifted.translate(copy * dx, copy * dy);
            body.accept(shifted);
            shifted.dispose();
        }
    }

    /**
     * A soft elliptical wash.
     *
     * Squashes a circular gradient rather than filling an ellipse with one, so the tint fades out on
     * every edge instead of being cut off flat above and below.
     */
    private static void softBlob(Graphics2D g, double cx, double cy, double rx, double ry,
                                 Color tint, int centreAlpha) {
        Graphics2D squashed = (Graphics2D) g.create();
        squashed.translate(cx, cy);
        squashed.scale(1, ry / rx);
        squashed.translate(-cx, -cy);
        squashed.setPaint(new RadialGradientPaint(
                (float) cx, (float) cy, (float) rx,
                new float[]{0f, 1f},
                new Color[]{alpha(tint, centreAlpha), alpha(tint, 0)}));
        squashed.fill(new Ellipse2D.Double(cx - rx, cy - rx, rx * 2, rx * 2));
        squashed.dispose();
    }

    private static int channel(int value) {
        int clamped = Math.max(0, Math.min(255, value));
        return clamped;
    }

    /** Drops a tint's alpha, for the places a theme colour is used as a solid fill. */
    private static Color opaque(Color tint) {
        Color solid = new Color(tint.getRed(), tint.getGreen(), tint.getBlue());
        return solid;
    }

    private static Color alpha(Color tint, int value) {
        Color tinted = new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), channel(value));
        return tinted;
    }

    /** Even per-channel lightening. Color.brighter() scales, which washes out dark tints unevenly. */
    private static Color brighten(Color base, int amount) {
        Color shifted = new Color(channel(base.getRed() + amount), channel(base.getGreen() + amount),
                channel(base.getBlue() + amount));
        return shifted;
    }

    private static Color darken(Color base, int amount) {
        Color shifted = brighten(base, -amount);
        return shifted;
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
            double rumble = StrictMath.sin(2 * Math.PI * (70 - 30 * progress) * i / SAMPLE_RATE);
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
            double body = StrictMath.sin(2 * Math.PI * (150 - 90 * progress) * i / SAMPLE_RATE);
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

    /** A short ascending arpeggio over a held top note: the level is cleared. */
    private static void levelClearSting() throws IOException {
        double duration = 1.6;
        double[] mix = new double[(int) (duration * SAMPLE_RATE)];
        int[] notes = {57, 62, 66, 69};
        for (int i = 0; i < notes.length; i++) {
            addTone(mix, i * 0.2, 0.45, pitch(notes[i]), 0.3, Wave.SQUARE);
            addTone(mix, i * 0.2, 0.45, pitch(notes[i] + 12), 0.14, Wave.TRIANGLE);
        }
        addTone(mix, 0.8, 0.7, pitch(74), 0.26, Wave.SQUARE);
        addTone(mix, 0.8, 0.7, pitch(62), 0.16, Wave.TRIANGLE);
        writeWav(mix, SOUNDS.resolve("level-clear.wav"));
    }

    /**
     * A two-note warble for a nearly-dead ship.
     *
     * Deliberately short and unlooped: the game retriggers it on a tick interval while health is
     * low, which needs no stop call and stops on its own the moment health recovers. A falling
     * interval rather than a rising one, so it reads as an alarm and not as a pickup.
     */
    private static void lowHealthAlarm() throws IOException {
        double duration = 0.5;
        double[] mix = new double[(int) (duration * SAMPLE_RATE)];
        addTone(mix, 0.00, 0.16, pitch(81), 0.28, Wave.SQUARE);
        addTone(mix, 0.20, 0.16, pitch(76), 0.28, Wave.SQUARE);
        addTone(mix, 0.20, 0.16, pitch(64), 0.12, Wave.TRIANGLE);
        writeWav(mix, SOUNDS.resolve("low-health.wav"));
    }

    private enum Wave { SQUARE, TRIANGLE, SINE }

    private static double pitch(int midiNote) {
        double frequency = 440 * StrictMath.pow(2, (midiNote - 69) / 12.0);
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
                case SINE -> StrictMath.sin(2 * Math.PI * frequency * t);
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
        Files.createDirectories(target.getParent());
        ImageIO.write(image, "png", target.toFile());
        System.out.println("wrote " + target);
    }

    private GenerateAssets() {
    }
}
