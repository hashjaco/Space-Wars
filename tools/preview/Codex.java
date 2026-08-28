import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Four plates from an imaginary field guide, drawn in the Cold Taxonomy manner.
 *
 * A preview harness like everything else in this directory: it draws something and writes a PNG so
 * it can be looked at instead of reasoned about. It is <strong>not</strong> part of
 * {@code GenerateAssets} and it never writes into {@code src/} -- these are concept plates for a
 * sixth galaxy and the three new player hulls, not committed art, so none of the byte-reproducibility
 * rules in {@code docs/ROADMAP.md} bite here. The conventions are kept anyway, because a plate that
 * redraws itself differently every run is not a reference.
 *
 * <pre>
 *   java tools/preview/Codex.java /tmp/plates
 * </pre>
 *
 * Every letterform on these plates is constructed from strokes on a grid rather than typeset, so the
 * page has one hand throughout and needs no font to be installed. See {@link #GLYPHS}.
 *
 * <p><strong>What the first pass got wrong</strong>, kept here because it is the same lesson the
 * roadmap records for four galaxies running and it was learned again from scratch: twelve organisms
 * built by feeding parameters to one ellipse are one organism twelve times, however far apart their
 * aspects and leg counts are pushed. They are six construction families now -- radial, segmented,
 * faceted, annular, lobed, filamentous -- because the eye has the outline before it has anything
 * else. The same applies to the three insets on Plate IV, which were one drawing repeated.
 *
 * @see docs/COLD-TAXONOMY.md for the philosophy these are drawn from
 */
public final class Codex {

    // ------------------------------------------------------------------ page

    private static final int W = 2400;
    private static final int H = 3200;
    private static final double MARGIN = 208;

    /** How far inside the frame rule type sits. Nothing crosses the margin, ever. */
    private static final double INDENT = 46;

    // ---------------------------------------------------------------- palette
    //
    // Bone and ash on a ground close enough to black to swallow a careless mark, with exactly one
    // chromatic accent used at about two percent of the surface. The accent is the sixth galaxy's,
    // continuing the campaign's hue walk past Null's violet into the arterial reds.

    private static final Color GROUND = new Color(0x090b0e);
    private static final Color GROUND_LIFT = new Color(0x11151a);
    private static final Color BONE = new Color(0xd8d1c3);
    private static final Color BONE_DIM = new Color(0x8d8779);
    private static final Color ASH = new Color(0x4a4f57);
    private static final Color ASH_FAINT = new Color(0x272c33);
    private static final Color ACCENT = new Color(0xe0384f);
    private static final Color ACCENT_DEEP = new Color(0x7d2030);
    private static final Color FLUID = new Color(0xa8842f);

    private Codex() {
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "/tmp/plates");
        Files.createDirectories(out);
        write(plateOne(), out.resolve("plate-i-airframes.png"));
        write(plateTwo(), out.resolve("plate-ii-strata.png"));
        write(plateThree(), out.resolve("plate-iii-fauna.png"));
        write(plateFour(), out.resolve("plate-iv-holotype.png"));
        System.out.println("four plates written to " + out.toAbsolutePath());
    }

    private static void write(BufferedImage image, Path to) throws IOException {
        ImageIO.write(image, "png", to.toFile());
        System.out.println("  " + to.getFileName());
    }

    // ============================================================== PLATE I

    /**
     * Airframes. Three hulls the garage does not yet sell, as orthographic specimen studies.
     *
     * One form is allowed to run very large -- the twin-boom, because it is the strangest of the
     * three and the eye should fall a long way before it lands. The other two sit beneath it, and
     * the standing fleet runs along the foot as filled outlines, which is the one view that settles
     * whether three new ships are three ships or one ship three times. Those outlines are the same
     * paths the studies are engraved from, so the row cannot flatter a hull the plate does not have.
     */
    private static BufferedImage plateOne() {
        BufferedImage image = page();
        Graphics2D g = pen(image);
        Random random = new Random(6100);
        ground(g, random);
        frame(g);
        header(g, "PLATE  I", "AIRFRAMES", "THREE  HULLS  ·  ORTHOGRAPHIC  ·  NOSE  UP");

        double cx = W / 2.0;
        Path2D[] boom = twinBoomParts(cx, 470, 1060);
        specimenRule(g, cx, 470, 1060, 300);
        engrave(g, boom[1], random, 34, 26);
        engrave(g, boom[2], random, 90, 20);
        engrave(g, boom[3], random, 90, 20);
        engrave(g, boom[4], random, 0, 28);
        engrave(g, boom[0], random, 62, 22);
        boomDetail(g, cx, 470, 1060);
        leader(g, cx + 214, 620, cx + 486, 560, "CANARD");
        leader(g, cx + 178, 1080, cx + 486, 1170, "OUTBOARD  BOOM");
        leader(g, cx - 60, 900, cx - 486, 840, "CREW  POD");
        caption(g, cx, 1636, "VI · 01", "TWIN-BOOM", "THE  HULL  THAT  FLIES  IN  TWO  PIECES");

        double leftX = MARGIN + 480;
        double rightX = W - MARGIN - 480;
        specimenRule(g, leftX, 1790, 600, 180);
        // Fin first: over the hull it read as a slot cut down the spine rather than as a blade
        // standing behind one.
        engrave(g, interceptorFin(leftX, 1790, 600), random, 90, 0);
        engrave(g, interceptorHull(leftX, 1790, 600), random, 8, 22);
        canopy(g, leftX, 1790 + 600 * 0.24, 26, 52);
        thrusters(g, leftX, 1790 + 600, 50, 2);
        centreline(g, leftX, 1740, 2450);
        caption(g, leftX, 2440, "VI · 02", "INTERCEPTOR", "NARROW  ·  QUICK  ·  THIN-SKINNED");

        specimenRule(g, rightX, 1790, 600, 180);
        engrave(g, gunshipHull(rightX, 1790, 600), random, 152, 26);
        gunshipPods(g, rightX, 1790, 600, random);
        canopy(g, rightX, 1790 + 600 * 0.19, 42, 42);
        thrusters(g, rightX, 1790 + 600, 50, 4);
        centreline(g, rightX, 1740, 2450);
        caption(g, rightX, 2440, "VI · 03", "GUNSHIP", "BROAD  ·  SLOW  ·  ARMOURED");

        fleetRow(g, 2620);
        footer(g, "COLD  TAXONOMY  ·  PLATE  I  OF  IV", "SCALE  1:1  AT  ARENA  RESOLUTION");
        g.dispose();
        return image;
    }

    /** Pod, canard, two booms and the aft spar, in draw order back to front. */
    private static Path2D[] twinBoomParts(double cx, double top, double length) {
        double unit = length / 12.0;
        Path2D pod = symmetric(new double[][] {
                {0.00, 0.00}, {0.26, 0.11}, {0.42, 0.28}, {0.46, 0.52},
                {0.42, 0.76}, {0.50, 0.88}, {0.36, 0.97}, {0.13, 1.00},
        }, cx, top, unit * 3.1, length);
        // Swept back rather than square, so it reads as a wing and not as a shoulder on the hull.
        Path2D canard = symmetric(new double[][] {
                {0.10, 0.13}, {0.86, 0.30}, {1.02, 0.40}, {1.00, 0.46}, {0.30, 0.38},
        }, cx, top, unit * 2.6, length);
        return new Path2D[] {
            pod, canard,
            boom(cx - unit * 2.46, top + length * 0.26, length * 0.74, unit * 0.60),
            boom(cx + unit * 2.46, top + length * 0.26, length * 0.74, unit * 0.60),
            new Path2D.Double(new Rectangle2D.Double(
                    cx - unit * 2.66, top + length * 0.88, unit * 5.32, unit * 0.32)),
        };
    }

    /** Intake slots and the canopy: the only place the accent is spent on this figure. */
    private static void boomDetail(Graphics2D g, double cx, double top, double length) {
        double unit = length / 12.0;
        g.setColor(BONE_DIM);
        g.setStroke(new BasicStroke(2.1f));
        for (int side = -1; side <= 1; side += 2) {
            double x = cx + side * unit * 2.46;
            double y = top + length * 0.36;
            // Repetition with a hand in it: the interval opens by a hair as it runs aft.
            for (int slot = 0; slot < 8; slot++) {
                g.draw(new Line2D.Double(x - unit * 0.32, y, x + unit * 0.32, y));
                y += unit * 0.50 + slot * unit * 0.024;
            }
        }
        canopy(g, cx, top + length * 0.22, unit * 0.62, length * 0.085);
        centreline(g, cx, top - unit, top + length + unit);
    }

    /** Narrowed and stretched: one lens with a long tail and a single blade fin. */
    private static Path2D interceptorHull(double cx, double top, double length) {
        return symmetric(new double[][] {
                {0.00, 0.00}, {0.15, 0.15}, {0.26, 0.36}, {0.29, 0.58},
                {0.90, 0.72}, {0.96, 0.80}, {0.24, 0.82}, {0.20, 0.93}, {0.09, 1.00},
        }, cx, top, length / 12.0 * 3.3, length);
    }

    private static Path2D interceptorFin(double cx, double top, double length) {
        double unit = length / 12.0;
        Path2D fin = new Path2D.Double();
        fin.moveTo(cx - unit * 0.20, top + length * 0.58);
        fin.lineTo(cx + unit * 0.20, top + length * 0.58);
        fin.lineTo(cx + unit * 0.62, top + length * 1.04);
        fin.lineTo(cx - unit * 0.62, top + length * 1.04);
        fin.closePath();
        return fin;
    }

    /** Widened and squat: a hull built around what it carries rather than around going fast. */
    private static Path2D gunshipHull(double cx, double top, double length) {
        return symmetric(new double[][] {
                {0.00, 0.05}, {0.38, 0.00}, {0.60, 0.21}, {0.64, 0.48},
                {1.10, 0.54}, {1.16, 0.73}, {0.68, 0.77}, {0.58, 0.94}, {0.20, 1.00},
        }, cx, top, length / 12.0 * 3.4, length);
    }

    /** The two gun pods, each with its barrel run down into the hull rather than floating over it. */
    private static void gunshipPods(Graphics2D g, double cx, double top, double length,
                                    Random random) {
        double unit = length / 12.0;
        for (int side = -1; side <= 1; side += 2) {
            double x = cx + side * unit * 2.92;
            Path2D pod = new Path2D.Double(new Rectangle2D.Double(
                    x - unit * 0.50, top + length * 0.26, unit * 1.00, length * 0.36));
            engrave(g, pod, random, 90, unit * 0.30);
            g.setColor(ACCENT);
            g.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(x, top + length * 0.26, x, top + length * 0.205));
            g.setColor(BONE_DIM);
            g.setStroke(new BasicStroke(1.8f));
            g.draw(new Line2D.Double(x - unit * 0.50, top + length * 0.62,
                    cx - side * unit * 0.4, top + length * 0.70));
        }
    }

    private static void canopy(Graphics2D g, double cx, double cy, double halfW, double halfH) {
        Shape glass = new Ellipse2D.Double(cx - halfW, cy - halfH, halfW * 2, halfH * 2);
        g.setColor(GROUND);
        g.fill(glass);
        hatch(g, glass, 58, 6.5, 1.0f, ACCENT_DEEP);
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(2.4f));
        g.draw(glass);
    }

    private static Path2D boom(double cx, double top, double length, double halfWidth) {
        Path2D path = new Path2D.Double();
        path.moveTo(cx - halfWidth, top + length * 0.10);
        path.curveTo(cx - halfWidth, top, cx + halfWidth, top, cx + halfWidth, top + length * 0.10);
        path.lineTo(cx + halfWidth, top + length * 0.93);
        path.curveTo(cx + halfWidth, top + length, cx - halfWidth, top + length,
                cx - halfWidth, top + length * 0.93);
        path.closePath();
        return path;
    }

    private static void thrusters(Graphics2D g, double cx, double tail, double unit, int count) {
        g.setColor(BONE_DIM);
        g.setStroke(new BasicStroke(2.2f));
        double span = unit * 0.62 * (count - 1);
        for (int i = 0; i < count; i++) {
            double x = cx - span / 2 + i * unit * 0.62;
            g.draw(new Ellipse2D.Double(x - unit * 0.20, tail - unit * 0.34,
                    unit * 0.40, unit * 0.68));
        }
    }

    /**
     * Five silhouettes in a rank: the two hulls that exist and the three that do not.
     *
     * Filled rather than engraved, because the only question this row asks is about outline -- and
     * outline is the first thing the eye reads and the last thing a colour choice can rescue. The
     * three new ones are the very paths engraved above, scaled down: a silhouette row drawn from
     * different geometry than the studies would be a row that agrees with nothing.
     */
    private static void fleetRow(Graphics2D g, double top) {
        rule(g, MARGIN + INDENT, top - 54, W - MARGIN - INDENT, top - 54, ASH_FAINT, 1.4f);
        text(g, "THE  STANDING  FLEET", MARGIN + INDENT, top - 18, 20, 10, BONE_DIM, 2.0f);

        String[] names = {"MILITIA", "CORSAIR", "INTERCEPT", "TWIN-BOOM", "GUNSHIP"};
        double step = (W - 2 * (MARGIN + INDENT)) / 5.0;
        double figureTop = top + 30;
        double figureLength = 250;
        for (int i = 0; i < 5; i++) {
            double cx = MARGIN + INDENT + step * (i + 0.5);
            boolean standing = i < 2;
            g.setColor(standing ? ASH : BONE_DIM);
            for (Shape part : fleetShape(i, cx, figureTop, figureLength)) {
                g.fill(part);
            }
            text(g, names[i], cx - textWidth(names[i], 16, 8) / 2, figureTop + figureLength + 52,
                    16, 8, standing ? ASH : BONE_DIM, 1.7f);
        }
    }

    private static List<Shape> fleetShape(int which, double cx, double top, double length) {
        List<Shape> parts = new ArrayList<>();
        switch (which) {
            // The two standing hulls, as the sheet cuts them: a plain delta and a broader one.
            case 0 -> parts.add(symmetric(new double[][] {
                    {0.00, 0.00}, {0.20, 0.30}, {0.30, 0.52}, {0.92, 0.72}, {0.96, 0.84},
                    {0.30, 0.86}, {0.24, 1.00},
            }, cx, top, length / 12.0 * 3.0, length));
            case 1 -> parts.add(symmetric(new double[][] {
                    {0.00, 0.02}, {0.28, 0.26}, {0.46, 0.50}, {1.04, 0.66}, {1.06, 0.82},
                    {0.44, 0.84}, {0.34, 1.00},
            }, cx, top, length / 12.0 * 3.0, length));
            case 2 -> {
                parts.add(interceptorHull(cx, top, length));
                parts.add(interceptorFin(cx, top, length));
            }
            case 3 -> {
                Path2D[] boom = twinBoomParts(cx, top, length);
                for (Path2D part : boom) {
                    parts.add(part);
                }
            }
            default -> {
                parts.add(gunshipHull(cx, top, length));
                double unit = length / 12.0;
                for (int side = -1; side <= 1; side += 2) {
                    parts.add(new Rectangle2D.Double(cx + side * unit * 2.92 - unit * 0.50,
                            top + length * 0.26, unit * 1.00, length * 0.36));
                }
            }
        }
        return parts;
    }

    // ============================================================= PLATE II

    /**
     * Strata. Six places, as cores drawn from them.
     *
     * A level is a stack of layers -- far, mid, near, and whatever the floor is -- so the honest
     * portrait of one is a section rather than a view. Six of them racked side by side says more
     * about how a galaxy differs from itself than six pictures of six skies ever would, which is a
     * lesson this campaign has already learned once at some cost.
     *
     * Every core carries exactly one accent seam, at a different depth in each. Six marks on the
     * page, and they read as a horizon that is not level.
     */
    private static BufferedImage plateTwo() {
        BufferedImage image = page();
        Graphics2D g = pen(image);
        Random random = new Random(6200);
        ground(g, random);
        frame(g);
        header(g, "PLATE  II", "STRATA", "SIX  CORES  ·  FULL  DEPTH  ·  ARENA  SECTION");

        String[] names = {"BLOOM  SHAFT", "THE  WEEPING", "GLASS  ORCHARD",
                          "MEATWORKS", "THE  LONG  MOUTH", "CHOIR"};
        double[] seam = {0.22, 0.61, 0.44, 0.13, 0.78, 0.35};
        double top = 610;
        double depth = 2140;
        double coreWidth = 196;
        double gap = (W - 2 * (MARGIN + INDENT) - 6 * coreWidth) / 5.0;

        depthRule(g, MARGIN + INDENT - 6, top, depth);
        for (int i = 0; i < 6; i++) {
            double x = MARGIN + INDENT + i * (coreWidth + gap);
            core(g, x, top, coreWidth, depth, i, seam[i], new Random(6210 + i * 7));
            text(g, "VI · " + (i + 4), x, top - 30, 19, 9, ACCENT, 1.9f);
            // Vertical, centred in the gap, running the core's full depth: the label belongs to the
            // column it stands beside and must not stray into the next one.
            verticalText(g, names[i], x + coreWidth + gap / 2 + 9,
                    top + depth / 2 + textWidth(names[i], 21, 10) / 2, 21, 10, BONE, 2.0f);
        }

        rule(g, MARGIN + INDENT, top + depth + 74, W - MARGIN - INDENT, top + depth + 74,
                ASH_FAINT, 1.4f);
        text(g, "SECTIONS  TAKEN  AT  THE  LANE  CENTRE", MARGIN + INDENT, top + depth + 118,
                20, 10, BONE_DIM, 2.0f);
        text(g, "NO  SKY  IN  ANY  OF  THEM", MARGIN + INDENT, top + depth + 162, 20, 10,
                ASH, 2.0f);
        footer(g, "COLD  TAXONOMY  ·  PLATE  II  OF  IV", "DEPTH  IN  ARENA  UNITS");
        g.dispose();
        return image;
    }

    /**
     * One core: bands of texture down a narrow column, each band a different mark.
     *
     * The bands are drawn, never stamped. A stipple band and a hatch band and a void band read as
     * three materials at arm's length and as three decisions at reading distance, which is the whole
     * bargain of working this way.
     */
    private static void core(Graphics2D g, double x, double top, double width, double depth,
                             int which, double seam, Random random) {
        Rectangle2D tube = new Rectangle2D.Double(x, top, width, depth);
        g.setColor(GROUND_LIFT);
        g.fill(tube);

        double y = top;
        int bands = 8 + which % 4;
        for (int band = 0; band < bands; band++) {
            double height = depth / bands * (0.70 + random.nextDouble() * 0.60);
            height = Math.min(height, top + depth - y);
            if (height <= 2) {
                break;
            }
            Rectangle2D slab = new Rectangle2D.Double(x, y, width, height);
            switch ((band + which) % 5) {
                case 0 -> hatch(g, slab, 62 + which * 9, 8 + which, 1.1f, ASH);
                case 1 -> stipple(g, slab, (int) (height * 1.6), random, BONE_DIM, 1.9);
                case 2 -> {
                    hatch(g, slab, 118 - which * 7, 13, 1.0f, ASH_FAINT);
                    stipple(g, slab, (int) (height * 0.4), random, BONE, 1.4);
                }
                case 3 -> waveBand(g, slab, random, which);
                default -> {
                    // A void. Left as ground, so the column breathes and the eye is given somewhere
                    // to rest before the next material.
                    g.setColor(GROUND);
                    g.fill(slab);
                }
            }
            g.setColor(BONE_DIM);
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new Line2D.Double(x, y, x + width, y));
            y += height;
        }

        // The one warm mark on this column, at its own depth.
        double seamY = top + depth * seam;
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(3.4f));
        g.draw(new Line2D.Double(x, seamY, x + width, seamY));
        g.setColor(ACCENT_DEEP);
        g.setStroke(new BasicStroke(1.2f));
        for (int i = 1; i <= 3; i++) {
            g.draw(new Line2D.Double(x, seamY + i * 4.5, x + width, seamY + i * 4.5));
        }

        g.setColor(BONE);
        g.setStroke(new BasicStroke(2.4f));
        g.draw(tube);
    }

    /** Bedding planes that follow a slow curve rather than the page's axis. */
    private static void waveBand(Graphics2D g, Rectangle2D slab, Random random, int phase) {
        g.setColor(BONE_DIM);
        g.setStroke(new BasicStroke(1.4f));
        double amplitude = slab.getHeight() * 0.09;
        for (double y = slab.getMinY() + 5; y < slab.getMaxY() - 3; y += 8) {
            Path2D line = new Path2D.Double();
            for (int step = 0; step <= 24; step++) {
                double t = step / 24.0;
                double px = slab.getMinX() + t * slab.getWidth();
                double py = y + StrictMath.sin(t * StrictMath.PI * 2 + phase + y * 0.02) * amplitude;
                if (step == 0) {
                    line.moveTo(px, py);
                } else {
                    line.lineTo(px, py);
                }
            }
            g.draw(line);
        }
    }

    private static void depthRule(Graphics2D g, double x, double top, double depth) {
        rule(g, x, top, x, top + depth, BONE_DIM, 1.8f);
        for (int i = 0; i <= 10; i++) {
            double y = top + depth * i / 10.0;
            boolean major = i % 5 == 0;
            rule(g, x - (major ? 22 : 12), y, x, y, major ? BONE : ASH, major ? 1.8f : 1.3f);
        }
    }

    // ============================================================ PLATE III

    /**
     * Fauna. Twelve small hostiles, one to a cell, in the arrangement a naturalist would use.
     *
     * <strong>Six construction families, not one shape with twelve settings.</strong> The first pass
     * built every one of these as an ellipse with limbs and varied the aspect, the leg count and the
     * frill -- and produced twelve of the same animal, which is precisely the failure the roadmap
     * records against Ashfall's flagships, Cryonis's, Tempest's and Null's creatures. At the size a
     * thing is actually fought, a leg is a stub and the eye has the outline first. So: radial,
     * segmented, faceted, annular, lobed and filamentous, two of each, and every limb clipped to its
     * own cell so a specimen cannot borrow its neighbour's silhouette.
     */
    private static BufferedImage plateThree() {
        BufferedImage image = page();
        Graphics2D g = pen(image);
        Random random = new Random(6300);
        ground(g, random);
        frame(g);
        header(g, "PLATE  III", "FAUNA", "TWELVE  MINOR  FORMS  ·  DORSAL  ·  AT  REST");

        String[] names = {
            "SPOREJACK", "WEEPER", "GLASSMOTH", "TALLYMAN",
            "SIX-O'CLOCK", "THE  USHER", "CRIB", "PALE  SERGEANT",
            "KNOTWORK", "LITTLE  APPETITE", "CHORISTER", "THE  APOLOGY",
        };
        // Family, then the variation inside it. The families are what separate the outlines; the
        // numbers only stop two members of one family being the same drawing.
        int[] family = {0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 4, 5};
        double[] scale = {1.00, 0.86, 1.12, 0.92, 1.06, 0.80, 0.84, 1.10, 0.94, 1.14, 0.88, 1.02};

        double cellW = (W - 2 * (MARGIN + INDENT)) / 4.0;
        double cellH = 754;
        double top = 530;
        for (int i = 0; i < 12; i++) {
            double x = MARGIN + INDENT + (i % 4) * cellW;
            double y = top + (i / 4) * cellH;
            cell(g, x, y, cellW, cellH);
            Shape saved = g.getClip();
            g.clip(new Rectangle2D.Double(x + 8, y + 8, cellW - 16, cellH - 16));
            specimen(g, family[i], x + cellW / 2, y + cellH * 0.43, cellW * 0.335 * scale[i],
                    i, new Random(6310 + i * 13));
            g.setClip(saved);
            text(g, "VI · " + (10 + i), x + 26, y + 44, 17, 8, ACCENT, 1.7f);
            String name = names[i];
            text(g, name, x + cellW / 2 - textWidth(name, 20, 9) / 2, y + cellH - 44, 20, 9,
                    BONE, 1.9f);
        }
        footer(g, "COLD  TAXONOMY  ·  PLATE  III  OF  IV", "FIGURES  AT  UNIFORM  MAGNIFICATION");
        g.dispose();
        return image;
    }

    private static void cell(Graphics2D g, double x, double y, double w, double h) {
        g.setColor(ASH_FAINT);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(new Rectangle2D.Double(x + 14, y + 14, w - 28, h - 28));
    }

    /** Dispatch to a construction family. Six of them, and no two share a way of being built. */
    private static void specimen(Graphics2D g, int family, double cx, double cy, double size,
                                 int index, Random random) {
        switch (family) {
            case 0 -> radial(g, cx, cy, size, index, random);
            case 1 -> segmented(g, cx, cy, size, index, random);
            case 2 -> faceted(g, cx, cy, size, index, random);
            case 3 -> annular(g, cx, cy, size, index, random);
            case 4 -> lobed(g, cx, cy, size, index, random);
            default -> filamentous(g, cx, cy, size, index, random);
        }
    }

    /** A disc that has decided on symmetry, with spokes it stands on and a rim of small eyes. */
    private static void radial(Graphics2D g, double cx, double cy, double size, int index,
                               Random random) {
        int spokes = 7 + index % 4;
        g.setColor(BONE_DIM);
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < spokes; i++) {
            double angle = 2 * StrictMath.PI * i / spokes + index * 0.2;
            double reach = size * (1.30 + 0.34 * StrictMath.sin(i * 2.1));
            g.draw(new Line2D.Double(cx + StrictMath.cos(angle) * size * 0.8,
                    cy + StrictMath.sin(angle) * size * 0.8,
                    cx + StrictMath.cos(angle) * reach, cy + StrictMath.sin(angle) * reach));
        }
        Shape disc = new Ellipse2D.Double(cx - size, cy - size, size * 2, size * 2);
        g.setColor(GROUND);
        g.fill(disc);
        hatch(g, disc, 12, 6.5, 1.0f, ASH);
        stipple(g, disc, (int) (size * 1.6), random, BONE_DIM, 1.6);
        g.setColor(BONE);
        g.setStroke(new BasicStroke(2.6f));
        g.draw(disc);
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(BONE_DIM);
        for (int i = 1; i <= 3; i++) {
            double r = size * (1 - i * 0.22);
            g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        }
        // Eyes on the rim, evenly, so this family reads as having no front at all.
        g.setColor(ACCENT);
        for (int i = 0; i < spokes; i++) {
            double angle = 2 * StrictMath.PI * i / spokes + index * 0.2;
            double d = size * 0.07;
            g.fill(new Ellipse2D.Double(cx + StrictMath.cos(angle) * size * 0.62 - d,
                    cy + StrictMath.sin(angle) * size * 0.62 - d, d * 2, d * 2));
        }
    }

    /** A chain of unequal segments on a slow curve, with a paired leg off each. */
    private static void segmented(Graphics2D g, double cx, double cy, double size, int index,
                                  Random random) {
        int count = 5 + index % 3;
        double reach = size * 2.1;
        double[][] joints = new double[count][2];
        for (int i = 0; i < count; i++) {
            double t = i / (count - 1.0);
            joints[i][0] = cx + StrictMath.sin(t * 2.4 + index) * size * 0.42;
            joints[i][1] = cy - reach / 2 + t * reach;
        }
        g.setColor(BONE_DIM);
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < count; i++) {
            double r = size * (0.52 - 0.30 * i / (count - 1.0));
            for (int side = -1; side <= 1; side += 2) {
                g.draw(new Line2D.Double(joints[i][0], joints[i][1],
                        joints[i][0] + side * r * 2.6, joints[i][1] + r * 1.5));
            }
        }
        for (int i = count - 1; i >= 0; i--) {
            double r = size * (0.54 - 0.30 * i / (count - 1.0));
            Shape bead = new Ellipse2D.Double(joints[i][0] - r, joints[i][1] - r * 0.82,
                    r * 2, r * 1.64);
            g.setColor(GROUND);
            g.fill(bead);
            hatch(g, bead, 70, 5.5, 0.9f, ASH);
            g.setColor(BONE);
            g.setStroke(new BasicStroke(2.3f));
            g.draw(bead);
        }
        // One eye, on the largest segment only: this family has a head, and the head is the front.
        g.setColor(ACCENT);
        double d = size * 0.13;
        g.fill(new Ellipse2D.Double(joints[0][0] - d, joints[0][1] - d, d * 2, d * 2));
    }

    /** An irregular crystal. No limbs at all -- it does not walk, it accretes. */
    private static void faceted(Graphics2D g, double cx, double cy, double size, int index,
                                Random random) {
        int sides = 7 + index % 3;
        Path2D shell = new Path2D.Double();
        double[][] hull = new double[sides][2];
        for (int i = 0; i < sides; i++) {
            double angle = 2 * StrictMath.PI * i / sides - StrictMath.PI / 2;
            double r = size * (0.66 + ((i * 7 + index) % 5) * 0.17);
            hull[i][0] = cx + StrictMath.cos(angle) * r;
            hull[i][1] = cy + StrictMath.sin(angle) * r * 1.18;
            if (i == 0) {
                shell.moveTo(hull[i][0], hull[i][1]);
            } else {
                shell.lineTo(hull[i][0], hull[i][1]);
            }
        }
        shell.closePath();
        g.setColor(GROUND);
        g.fill(shell);
        hatch(g, shell, 40, 7, 1.0f, ASH_FAINT);
        stipple(g, shell, (int) (size * 1.2), random, BONE_DIM, 1.4);
        // Facets: every vertex to a shared interior point that is deliberately off centre.
        double fx = cx + size * 0.18;
        double fy = cy - size * 0.14;
        g.setColor(BONE_DIM);
        g.setStroke(new BasicStroke(1.6f));
        for (double[] vertex : hull) {
            g.draw(new Line2D.Double(vertex[0], vertex[1], fx, fy));
        }
        g.setColor(BONE);
        g.setStroke(new BasicStroke(2.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shell);
        g.setColor(ACCENT);
        double d = size * 0.10;
        g.fill(new Ellipse2D.Double(fx - d, fy - d, d * 2, d * 2));
    }

    /** A ring with nothing in the middle, and what it carries hanging from the inside of it. */
    private static void annular(Graphics2D g, double cx, double cy, double size, int index,
                                Random random) {
        double outer = size * 1.16;
        double inner = size * (0.66 + (index % 3) * 0.07);
        Area ring = new Area(new Ellipse2D.Double(cx - outer, cy - outer * 0.88,
                outer * 2, outer * 1.76));
        ring.subtract(new Area(new Ellipse2D.Double(cx - inner, cy - inner * 0.88,
                inner * 2, inner * 1.76)));
        g.setColor(GROUND);
        g.fill(ring);
        hatch(g, ring, 96, 6, 1.0f, ASH);
        hatch(g, ring, 6, 17, 0.9f, BONE_DIM);
        stipple(g, ring, (int) (size * 1.4), random, BONE_DIM, 1.5);
        g.setColor(BONE);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(ring);
        // Tendrils inward, unequal, and one of them much longer than the rest.
        int hangs = 6 + index % 4;
        g.setColor(BONE_DIM);
        for (int i = 0; i < hangs; i++) {
            double angle = 2 * StrictMath.PI * i / hangs + 0.4 * index;
            double drop = inner * (0.45 + (i == index % hangs ? 1.5 : random.nextDouble() * 0.5));
            g.setStroke(new BasicStroke(i == index % hangs ? 3.0f : 1.8f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(cx + StrictMath.cos(angle) * inner,
                    cy + StrictMath.sin(angle) * inner * 0.88,
                    cx + StrictMath.cos(angle) * (inner - drop),
                    cy + StrictMath.sin(angle) * (inner - drop) * 0.88));
        }
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(2.6f));
        g.draw(new Ellipse2D.Double(cx - inner, cy - inner * 0.88, inner * 2, inner * 1.76));
    }

    /** A bunch: three or four bodies that never finished separating. */
    private static void lobed(Graphics2D g, double cx, double cy, double size, int index,
                              Random random) {
        int lobes = 3 + index % 3;
        double[][] centres = new double[lobes][3];
        for (int i = 0; i < lobes; i++) {
            double angle = 2 * StrictMath.PI * i / lobes + index * 0.5;
            centres[i][0] = cx + StrictMath.cos(angle) * size * 0.52;
            centres[i][1] = cy + StrictMath.sin(angle) * size * 0.52;
            centres[i][2] = size * (0.52 + ((i + index) % 3) * 0.16);
        }
        Area body = new Area();
        for (double[] lobe : centres) {
            body.add(new Area(new Ellipse2D.Double(lobe[0] - lobe[2], lobe[1] - lobe[2],
                    lobe[2] * 2, lobe[2] * 2)));
        }
        g.setColor(GROUND);
        g.fill(body);
        hatch(g, body, 128, 6, 1.0f, ASH);
        stipple(g, body, (int) (size * 2.2), random, BONE_DIM, 1.8);
        g.setColor(BONE);
        g.setStroke(new BasicStroke(2.7f));
        g.draw(body);
        // The seams where the lobes did not part, drawn inside the merged outline.
        g.setColor(BONE_DIM);
        g.setStroke(new BasicStroke(1.5f));
        Shape saved = g.getClip();
        g.clip(body);
        for (double[] lobe : centres) {
            g.draw(new Ellipse2D.Double(lobe[0] - lobe[2], lobe[1] - lobe[2],
                    lobe[2] * 2, lobe[2] * 2));
        }
        g.setClip(saved);
        g.setColor(ACCENT);
        for (double[] lobe : centres) {
            double d = lobe[2] * 0.17;
            g.fill(new Ellipse2D.Double(lobe[0] - d, lobe[1] - d, d * 2, d * 2));
        }
    }

    /** A stalk with a bulb on it and a fan of hair. Mostly empty space, and mostly upright. */
    private static void filamentous(Graphics2D g, double cx, double cy, double size, int index,
                                    Random random) {
        double height = size * 2.5;
        double baseY = cy + height * 0.5;
        Path2D stalk = new Path2D.Double();
        stalk.moveTo(cx, baseY);
        stalk.curveTo(cx + size * 0.5, baseY - height * 0.35,
                cx - size * 0.5, baseY - height * 0.65, cx, baseY - height);
        g.setColor(BONE_DIM);
        g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(stalk);

        // The fan, off the base, at unequal lengths.
        int hairs = 15 + index % 5;
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(ASH);
        for (int i = 0; i < hairs; i++) {
            double t = i / (hairs - 1.0);
            double angle = StrictMath.PI * (0.10 + 0.80 * t);
            double reach = size * (0.7 + 0.7 * StrictMath.sin(t * StrictMath.PI));
            g.draw(new Line2D.Double(cx, baseY,
                    cx + StrictMath.cos(angle) * reach, baseY + StrictMath.sin(angle) * reach * 0.5));
        }

        double r = size * 0.62;
        Shape bulb = new Ellipse2D.Double(cx - r, baseY - height - r * 1.2, r * 2, r * 2.1);
        g.setColor(GROUND);
        g.fill(bulb);
        hatch(g, bulb, 84, 5, 0.9f, ASH);
        g.setColor(BONE);
        g.setStroke(new BasicStroke(2.4f));
        g.draw(bulb);
        g.setColor(ACCENT);
        double d = r * 0.30;
        g.fill(new Ellipse2D.Double(cx - d, baseY - height - r * 0.2 - d, d * 2, d * 2));
    }

    // ============================================================= PLATE IV

    /**
     * Holotype. One flagship, at the size a flagship deserves, with three details taken from it.
     *
     * The whole plate is one specimen and the marks around it. Everything the other three plates do
     * with arrangement, this one does with scale.
     *
     * The figure is deliberately <em>asymmetric</em>. The first pass drew it as a mirrored lozenge
     * with a row of eyes above an oval mouth, and at any distance that is a face -- on a plate whose
     * caption says it has none. Bilateral symmetry plus two features stacked vertically is a face
     * whatever the intent, so the eyes are now a cluster carried on one flank and the mouth is off
     * the centreline, which is also the more frightening drawing.
     */
    private static BufferedImage plateFour() {
        BufferedImage image = page();
        Graphics2D g = pen(image);
        Random random = new Random(6400);
        ground(g, random);
        frame(g);
        header(g, "PLATE  IV", "HOLOTYPE", "ONE  FORM  ·  VENTRAL  ·  FULLY  OPEN");

        double cx = W / 2.0 - 210;
        double top = 560;
        double span = 2020;
        specimenRule(g, cx, top, span, 470);
        chorister(g, cx, top, span, random);

        double dx = W - MARGIN - INDENT - 196;
        // Ranked in the order the figure reads, head to foot, so that no leader has to cross the
        // specimen to reach its inset. The letters follow the eye, not the alphabet.
        detailEyes(g, dx, 880, 192, new Random(6420));
        detailGrip(g, dx, 1440, 192, new Random(6430));
        detailMouth(g, dx, 2000, 192, new Random(6410));
        detailLabel(g, dx, 880, 192, "A", "NINE  EYES,  NO  FACE");
        detailLabel(g, dx, 1440, 192, "B", "IT  IS  HOLDING  ITSELF");
        detailLabel(g, dx, 2000, 192, "C", "THE  MOUTH  IS  A  DOOR");
        leader(g, cx + 300, 1230, dx - 232, 880, "A");
        leader(g, cx + 360, 1610, dx - 232, 1440, "B");
        leader(g, cx + 250, 2140, dx - 232, 2000, "C");

        caption(g, cx, top + span + 210, "VI · 22", "CHORISTER,  THE  LAST  VOICE",
                "IT  DOES  NOT  COME  TO  YOU.  YOU  ARRIVE  INSIDE  IT.");
        footer(g, "COLD  TAXONOMY  ·  PLATE  IV  OF  IV", "FIGURE  AT  ONE  THIRD");
        g.dispose();
        return image;
    }

    /**
     * A hanging column of unequal segments, gripped by its own arms and looking to one side.
     *
     * The segments are unioned into <em>one</em> outline and the joins drawn inside it, which is the
     * difference between a creature and a stack of pebbles -- the first pass drew five separate
     * lobes and produced the second thing. Same reasoning as the lobed fauna on Plate III, and the
     * same construction, at eight times the size.
     */
    private static void chorister(Graphics2D g, double cx, double top, double span, Random random) {
        double unit = span / 16.0;

        // Five segments, unequal and off-axis, merged into a single body.
        double[][] segments = {
            {0.00, 0.22, 1.9, -0.90},
            {0.10, 0.44, 4.6, 0.40},
            {0.30, 0.60, 3.1, -1.30},
            {0.38, 0.78, 5.0, 0.55},
            {0.66, 0.94, 3.2, -0.80},
            {0.82, 1.00, 2.4, 0.90},
        };
        Area body = new Area();
        List<Shape> lobes = new ArrayList<>();
        for (double[] s : segments) {
            double y0 = top + span * s[0];
            double y1 = top + span * s[1];
            Shape lobe = new Ellipse2D.Double(cx + unit * s[3] - unit * s[2], y0,
                    unit * s[2] * 2, y1 - y0);
            lobes.add(lobe);
            body.add(new Area(lobe));
        }

        // The arms, behind the body, rooted high and sweeping down past it.
        g.setColor(ASH);
        for (int i = 0; i < 11; i++) {
            double t = i / 10.0;
            double reach = span * (0.20 + 0.14 * StrictMath.sin(t * StrictMath.PI));
            double rootX = cx + (t - 0.5) * unit * 5.0;
            double rootY = top + span * (0.22 + 0.06 * StrictMath.sin(t * 3.1));
            Path2D arm = new Path2D.Double();
            arm.moveTo(rootX, rootY);
            arm.curveTo(rootX + (t - 0.5) * unit * 3.4, rootY + reach * 0.5,
                    rootX + (t - 0.5) * unit * 4.2, rootY + reach * 1.0,
                    rootX + (t - 0.5) * unit * 2.0, rootY + reach * 1.4);
            g.setStroke(new BasicStroke((float) (7.0 - 4.0 * StrictMath.abs(t - 0.5) * 2),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(arm);
        }

        engrave(g, body, random, 64, unit * 0.44);

        // The joins, clipped inside the outline, so the body has segments without being in pieces.
        g.setColor(ASH);
        g.setStroke(new BasicStroke(1.5f));
        Shape saved = g.getClip();
        g.clip(body);
        for (Shape lobe : lobes) {
            g.draw(lobe);
        }
        g.setClip(saved);

        // Two arms brought back across the front and closed on the third segment. This is the whole
        // of the C detail, at figure scale: it is holding itself.
        for (int side = -1; side <= 1; side += 2) {
            Path2D grip = new Path2D.Double();
            grip.moveTo(cx + side * unit * 5.2, top + span * 0.36);
            grip.curveTo(cx + side * unit * 4.6, top + span * 0.50,
                    cx + side * unit * 2.6, top + span * 0.56,
                    cx - side * unit * 1.2, top + span * 0.53);
            g.setColor(BONE_DIM);
            g.setStroke(new BasicStroke(13f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(grip);
            g.setColor(GROUND_LIFT);
            g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(grip);
        }

        // The eye cluster, high on the right flank and inside the outline. Small: nine of them come
        // to about one percent of the page, which is as much accent as a figure this size can take.
        double ex = cx + unit * 2.4;
        double ey = top + span * 0.33;
        for (int i = 0; i < 9; i++) {
            double angle = i * 2.399;
            double r = unit * (0.4 + 1.15 * StrictMath.sqrt(i / 9.0));
            double px = ex + StrictMath.cos(angle) * r;
            double py = ey + StrictMath.sin(angle) * r * 1.5;
            double d = unit * (0.20 - 0.011 * i);
            g.setColor(i % 3 == 0 ? FLUID : ACCENT);
            g.fill(new Ellipse2D.Double(px - d, py - d, d * 2, d * 2));
            g.setColor(BONE);
            g.setStroke(new BasicStroke(1.6f));
            g.draw(new Ellipse2D.Double(px - d * 1.8, py - d * 1.8, d * 3.6, d * 3.6));
        }

        // The mouth is an aperture rather than a shape: rings tightening into black, low and left
        // of the centreline. The same drawing as inset A, which is what makes the inset a magnified
        // passage of the figure and not a second picture.
        double mx = cx - unit * 1.6;
        double my = top + span * 0.71;
        double mr = unit * 1.7;
        Shape aperture = new Ellipse2D.Double(mx - mr, my - mr * 0.86, mr * 2, mr * 1.72);
        g.setColor(GROUND);
        g.fill(aperture);
        for (int i = 10; i >= 1; i--) {
            double t = i / 10.0;
            g.setColor(i % 4 == 0 ? ACCENT_DEEP : BONE_DIM);
            g.setStroke(new BasicStroke((float) (0.9 + t * 1.8)));
            g.draw(new Ellipse2D.Double(mx - mr * 0.94 * t, my - mr * 0.80 * t + mr * 0.12 * (1 - t),
                    mr * 1.88 * t, mr * 1.60 * t));
        }
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(2.8f));
        g.draw(aperture);
        centreline(g, cx, top - unit, top + span + unit);
    }

    /** Inset A: a throat, seen down its length. Rings tightening into black. */
    private static void detailMouth(Graphics2D g, double cx, double cy, double r, Random random) {
        Shape disc = disc(g, cx, cy, r);
        Shape saved = g.getClip();
        g.clip(disc);
        for (int i = 12; i >= 1; i--) {
            double t = i / 12.0;
            g.setColor(BONE_DIM);
            g.setStroke(new BasicStroke((float) (0.9 + t * 2.2)));
            g.draw(new Ellipse2D.Double(cx - r * 0.92 * t, cy - r * 0.78 * t + r * 0.16 * (1 - t),
                    r * 1.84 * t, r * 1.56 * t));
        }
        g.setColor(GROUND);
        g.fill(new Ellipse2D.Double(cx - r * 0.16, cy + r * 0.06, r * 0.32, r * 0.28));
        stipple(g, disc, 200, random, ASH, 1.5);
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(2.2f));
        g.draw(new Ellipse2D.Double(cx - r * 0.34, cy - r * 0.14, r * 0.68, r * 0.58));
        g.setClip(saved);
        rim(g, disc);
    }

    /** Inset B: the eye cluster magnified. Lenses, not dots, and no two the same size. */
    private static void detailEyes(Graphics2D g, double cx, double cy, double r, Random random) {
        Shape disc = disc(g, cx, cy, r);
        Shape saved = g.getClip();
        g.clip(disc);
        hatch(g, disc, 74, 9, 1.0f, ASH_FAINT);
        for (int i = 0; i < 9; i++) {
            double angle = i * 2.399;
            double reach = r * 0.72 * StrictMath.sqrt(i / 9.0);
            double px = cx + StrictMath.cos(angle) * reach;
            double py = cy + StrictMath.sin(angle) * reach;
            double d = r * (0.20 - 0.013 * i);
            g.setColor(GROUND);
            g.fill(new Ellipse2D.Double(px - d, py - d, d * 2, d * 2));
            g.setColor(BONE);
            g.setStroke(new BasicStroke(1.9f));
            g.draw(new Ellipse2D.Double(px - d, py - d, d * 2, d * 2));
            g.setColor(i % 3 == 0 ? FLUID : ACCENT);
            g.fill(new Ellipse2D.Double(px - d * 0.42, py - d * 0.42, d * 0.84, d * 0.84));
            g.setColor(BONE_DIM);
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new Line2D.Double(px - d * 1.5, py, px - d * 1.05, py));
        }
        stipple(g, disc, 150, random, BONE_DIM, 1.3);
        g.setClip(saved);
        rim(g, disc);
    }

    /** Inset C: two arms crossed over a segment, and the segment giving under them. */
    private static void detailGrip(Graphics2D g, double cx, double cy, double r, Random random) {
        Shape disc = disc(g, cx, cy, r);
        Shape saved = g.getClip();
        g.clip(disc);
        Shape flesh = new Ellipse2D.Double(cx - r * 1.1, cy - r * 0.5, r * 2.2, r * 1.6);
        g.setColor(GROUND);
        g.fill(flesh);
        hatch(g, flesh, 22, 6, 1.0f, ASH);
        g.setColor(BONE);
        g.setStroke(new BasicStroke(2.2f));
        g.draw(flesh);
        // The two arms, and the compression rules where they bite.
        for (int side = -1; side <= 1; side += 2) {
            Path2D arm = new Path2D.Double();
            arm.moveTo(cx + side * r * 1.3, cy - r * 1.2);
            arm.curveTo(cx + side * r * 0.5, cy - r * 0.4,
                    cx - side * r * 0.4, cy + r * 0.2, cx - side * r * 1.3, cy + r * 1.1);
            g.setColor(BONE_DIM);
            g.setStroke(new BasicStroke(15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(arm);
            g.setColor(GROUND_LIFT);
            g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(arm);
        }
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(2.0f));
        for (int i = -2; i <= 2; i++) {
            g.draw(new Line2D.Double(cx - r * 0.30, cy + i * r * 0.12,
                    cx + r * 0.30, cy + i * r * 0.12));
        }
        stipple(g, disc, 170, random, ASH, 1.4);
        g.setClip(saved);
        rim(g, disc);
    }

    private static Shape disc(Graphics2D g, double cx, double cy, double r) {
        Shape disc = new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
        g.setColor(GROUND_LIFT);
        g.fill(disc);
        return disc;
    }

    private static void rim(Graphics2D g, Shape disc) {
        g.setColor(BONE);
        g.setStroke(new BasicStroke(2.4f));
        g.draw(disc);
    }

    private static void detailLabel(Graphics2D g, double cx, double cy, double r, String letter,
                                    String note) {
        text(g, letter, cx - r - 52, cy - r + 26, 30, 14, ACCENT, 2.6f);
        text(g, note, cx - textWidth(note, 15, 7) / 2, cy + r + 46, 15, 7, BONE_DIM, 1.5f);
    }

    // ======================================================== plate furniture

    private static BufferedImage page() {
        return new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    }

    private static Graphics2D pen(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    /** The inside of a case: near-black, lifted a little at the centre, with a grain in it. */
    private static void ground(Graphics2D g, Random random) {
        g.setColor(GROUND);
        g.fillRect(0, 0, W, H);
        // A very slow lift toward the middle, drawn as bands rather than a gradient so it has the
        // same made-by-hand quality as everything else on the page.
        for (int i = 60; i > 0; i--) {
            double t = i / 60.0;
            int v = (int) (2 + 5 * (1 - t));
            g.setColor(new Color(9 + v, 11 + v, 14 + v));
            double inset = MARGIN * 0.4 + t * (W * 0.55);
            g.fill(new Ellipse2D.Double(W / 2.0 - inset, H / 2.0 - inset * H / (double) W,
                    inset * 2, inset * 2 * H / (double) W));
        }
        g.setColor(new Color(0x1a1f26));
        for (int i = 0; i < 52000; i++) {
            g.fillRect(random.nextInt(W), random.nextInt(H), 1, 1);
        }
    }

    /** The margin, and the marks that say the margin is deliberate. */
    private static void frame(Graphics2D g) {
        rule(g, MARGIN, MARGIN, W - MARGIN, MARGIN, ASH, 1.6f);
        rule(g, MARGIN, H - MARGIN, W - MARGIN, H - MARGIN, ASH, 1.6f);
        rule(g, MARGIN, MARGIN, MARGIN, H - MARGIN, ASH, 1.6f);
        rule(g, W - MARGIN, MARGIN, W - MARGIN, H - MARGIN, ASH, 1.6f);
        double t = 34;
        for (double[] corner : new double[][] {
                {MARGIN, MARGIN, 1, 1}, {W - MARGIN, MARGIN, -1, 1},
                {MARGIN, H - MARGIN, 1, -1}, {W - MARGIN, H - MARGIN, -1, -1}}) {
            rule(g, corner[0], corner[1] + corner[3] * t * 1.6,
                    corner[0] + corner[2] * t * 1.6, corner[1], BONE_DIM, 1.6f);
        }
    }

    private static void header(Graphics2D g, String plate, String title, String subtitle) {
        text(g, plate, MARGIN + INDENT, MARGIN - 46, 24, 12, ACCENT, 2.2f);
        text(g, title, MARGIN + INDENT, MARGIN + 128, 72, 33, BONE, 4.4f);
        text(g, subtitle, MARGIN + INDENT, MARGIN + 184, 19, 10, BONE_DIM, 1.9f);
        rule(g, MARGIN + INDENT, MARGIN + 228, W - MARGIN - INDENT, MARGIN + 228, ASH_FAINT, 1.4f);
    }

    private static void footer(Graphics2D g, String left, String right) {
        text(g, left, MARGIN + INDENT, H - MARGIN + 54, 18, 9, BONE_DIM, 1.8f);
        text(g, right, W - MARGIN - INDENT - textWidth(right, 18, 9), H - MARGIN + 54, 18, 9,
                ASH, 1.8f);
    }

    /** A specimen's catalogue mark, its name, and one line that is not an explanation. */
    private static void caption(Graphics2D g, double cx, double y, String mark, String name,
                                String line) {
        rule(g, cx - 200, y - 62, cx + 200, y - 62, ASH_FAINT, 1.4f);
        text(g, mark, cx - textWidth(mark, 18, 9) / 2, y - 8, 18, 9, ACCENT, 1.8f);
        text(g, name, cx - textWidth(name, 34, 17) / 2, y + 44, 34, 17, BONE, 2.9f);
        text(g, line, cx - textWidth(line, 15, 7) / 2, y + 90, 15, 7, ASH, 1.5f);
    }

    /** The rule a specimen is measured against: a bar with ticks, set beside the figure. */
    private static void specimenRule(Graphics2D g, double cx, double top, double length,
                                     double halfSpan) {
        double x = cx - halfSpan - 96;
        rule(g, x, top, x, top + length, ASH, 1.6f);
        for (int i = 0; i <= 8; i++) {
            double y = top + length * i / 8.0;
            boolean major = i % 4 == 0;
            rule(g, x - (major ? 20 : 11), y, x, y, major ? BONE_DIM : ASH, 1.4f);
        }
        verticalText(g, "0  —  8  UNITS", x - 64, top + length * 0.5
                + textWidth("0  —  8  UNITS", 14, 7) / 2, 14, 7, ASH, 1.4f);
    }

    /** A callout: a hairline elbow from the figure to a label that never touches it. */
    private static void leader(Graphics2D g, double fromX, double fromY, double toX, double toY,
                               String label) {
        boolean rightward = toX > fromX;
        double elbowX = rightward ? toX - 46 : toX + 46;
        g.setColor(ASH);
        g.setStroke(new BasicStroke(1.3f));
        Path2D path = new Path2D.Double();
        path.moveTo(fromX, fromY);
        path.lineTo(elbowX, fromY);
        path.lineTo(toX, toY);
        g.draw(path);
        g.setColor(BONE_DIM);
        g.fill(new Ellipse2D.Double(fromX - 3.5, fromY - 3.5, 7, 7));
        double width = textWidth(label, 16, 8);
        text(g, label, rightward ? toX + 16 : toX - 16 - width, toY + 6, 16, 8, BONE_DIM, 1.6f);
    }

    private static void centreline(Graphics2D g, double x, double top, double bottom) {
        g.setColor(ASH_FAINT);
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[] {14f, 10f}, 0f));
        g.draw(new Line2D.Double(x, top, x, bottom));
    }

    private static void rule(Graphics2D g, double x1, double y1, double x2, double y2,
                             Color colour, float width) {
        g.setColor(colour);
        g.setStroke(new BasicStroke(width));
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    // ============================================================== marks

    /**
     * Fill a form the way a burin would: parallel rule at an angle, then the outline over it.
     *
     * Fills are suspect. A form built from strokes carries a weight no flat colour counterfeits, and
     * the difference is visible at a glance to somebody who could not say why. The angle is passed
     * per form rather than fixed, because three hulls hatched identically are three hulls cut from
     * one bolt of cloth.
     */
    private static void engrave(Graphics2D g, Shape form, Random random, double angle,
                                double panelSpacing) {
        g.setColor(GROUND);
        g.fill(form);
        hatch(g, form, angle, 7.5, 1.05f, ASH);
        stipple(g, form, (int) (form.getBounds2D().getWidth() * 0.9), random, BONE_DIM, 1.6);
        panelLines(g, form, panelSpacing);
        g.setColor(BONE);
        g.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(form);
    }

    private static void hatch(Graphics2D g, Shape form, double angleDegrees, double spacing,
                              float width, Color colour) {
        Shape saved = g.getClip();
        g.clip(form);
        g.setColor(colour);
        g.setStroke(new BasicStroke(width));
        Rectangle2D box = form.getBounds2D();
        double diagonal = StrictMath.hypot(box.getWidth(), box.getHeight());
        double radians = StrictMath.toRadians(angleDegrees);
        double dx = StrictMath.cos(radians);
        double dy = StrictMath.sin(radians);
        double cx = box.getCenterX();
        double cy = box.getCenterY();
        for (double offset = -diagonal; offset <= diagonal; offset += spacing) {
            double ox = cx - dy * offset;
            double oy = cy + dx * offset;
            g.draw(new Line2D.Double(ox - dx * diagonal, oy - dy * diagonal,
                    ox + dx * diagonal, oy + dy * diagonal));
        }
        g.setClip(saved);
    }

    private static void stipple(Graphics2D g, Shape form, int count, Random random, Color colour,
                                double maxRadius) {
        Rectangle2D box = form.getBounds2D();
        g.setColor(colour);
        for (int i = 0; i < count; i++) {
            double x = box.getMinX() + random.nextDouble() * box.getWidth();
            double y = box.getMinY() + random.nextDouble() * box.getHeight();
            if (!form.contains(x, y)) {
                continue;
            }
            double r = 0.6 + random.nextDouble() * maxRadius;
            g.fill(new Ellipse2D.Double(x - r / 2, y - r / 2, r, r));
        }
    }

    /** Plating: rules laid across the form and clipped to it, so they read as seams. */
    private static void panelLines(Graphics2D g, Shape form, double spacing) {
        if (spacing <= 1) {
            return;
        }
        Shape saved = g.getClip();
        g.clip(form);
        Rectangle2D box = form.getBounds2D();
        g.setColor(BONE_DIM);
        g.setStroke(new BasicStroke(1.5f));
        int i = 0;
        for (double y = box.getMinY() + spacing; y < box.getMaxY(); y += spacing, i++) {
            double inset = box.getWidth() * (i % 3 == 0 ? 0.02 : 0.16);
            g.draw(new Line2D.Double(box.getMinX() + inset, y, box.getMaxX() - inset, y));
        }
        g.setClip(saved);
    }

    /**
     * A hull from a half-profile, mirrored about its centreline.
     *
     * @param half points as {@code {acrossHalfWidths, alongLength}}, nose first, tail last
     */
    private static Path2D symmetric(double[][] half, double cx, double top, double halfWidth,
                                    double length) {
        Path2D path = new Path2D.Double();
        List<Point2D> right = new ArrayList<>();
        for (double[] point : half) {
            right.add(new Point2D.Double(cx + point[0] * halfWidth, top + point[1] * length));
        }
        path.moveTo(right.get(0).getX(), right.get(0).getY());
        for (int i = 1; i < right.size(); i++) {
            path.lineTo(right.get(i).getX(), right.get(i).getY());
        }
        for (int i = right.size() - 1; i >= 0; i--) {
            Point2D p = right.get(i);
            path.lineTo(2 * cx - p.getX(), p.getY());
        }
        path.closePath();
        return path;
    }

    // =========================================================== letterforms

    /**
     * A stroke alphabet on a six-by-seven grid, drawn rather than typeset.
     *
     * Every glyph is polylines in grid units, x rightward and y downward from the cap line. It costs
     * more than {@code drawString} and it is worth it twice over: the page has one hand throughout,
     * and nothing here depends on which fonts a machine happens to have -- the same reason
     * {@code GenerateAssets} draws its insignia rather than setting a letter.
     */
    private static final String[][] GLYPHS = {
        {"A", "0,7 3,0 6,7", "1.2,4.6 4.8,4.6"},
        {"B", "0,0 0,7", "0,0 4.4,0 6,1.6 4.4,3.4 0,3.4", "0,3.4 4.8,3.4 6,5.2 4.8,7 0,7"},
        {"C", "6,1.5 4.4,0 1.6,0 0,1.6 0,5.4 1.6,7 4.4,7 6,5.5"},
        {"D", "0,0 0,7", "0,0 4,0 6,2 6,5 4,7 0,7"},
        {"E", "6,0 0,0 0,7 6,7", "0,3.4 4.4,3.4"},
        {"F", "6,0 0,0 0,7", "0,3.4 4.4,3.4"},
        {"G", "6,1.5 4.4,0 1.6,0 0,1.6 0,5.4 1.6,7 4.4,7 6,5.5 6,3.8 3.4,3.8"},
        {"H", "0,0 0,7", "6,0 6,7", "0,3.4 6,3.4"},
        {"I", "3,0 3,7", "1.2,0 4.8,0", "1.2,7 4.8,7"},
        {"J", "6,0 6,5.4 4.4,7 1.6,7 0,5.4"},
        {"K", "0,0 0,7", "6,0 0.4,4", "2.2,2.5 6,7"},
        {"L", "0,0 0,7 6,7"},
        {"M", "0,7 0,0 3,3.6 6,0 6,7"},
        {"N", "0,7 0,0 6,7 6,0"},
        {"O", "1.6,0 4.4,0 6,1.6 6,5.4 4.4,7 1.6,7 0,5.4 0,1.6 1.6,0"},
        {"P", "0,7 0,0 4.4,0 6,1.6 4.4,3.3 0,3.3"},
        {"Q", "1.6,0 4.4,0 6,1.6 6,5.4 4.4,7 1.6,7 0,5.4 0,1.6 1.6,0", "3.6,5 6.2,7.6"},
        {"R", "0,7 0,0 4.4,0 6,1.6 4.4,3.3 0,3.3", "3,3.3 6,7"},
        {"S", "6,1.2 4.4,0 1.6,0 0,1.4 0,2.6 1.4,3.4 4.6,3.6 6,4.6 6,5.8 4.4,7 1.6,7 0,5.8"},
        {"T", "0,0 6,0", "3,0 3,7"},
        {"U", "0,0 0,5.4 1.6,7 4.4,7 6,5.4 6,0"},
        {"V", "0,0 3,7 6,0"},
        {"W", "0,0 1.4,7 3,2.6 4.6,7 6,0"},
        {"X", "0,0 6,7", "6,0 0,7"},
        {"Y", "0,0 3,3.6 6,0", "3,3.6 3,7"},
        {"Z", "0,0 6,0 0,7 6,7"},
        {"0", "1.6,0 4.4,0 6,1.6 6,5.4 4.4,7 1.6,7 0,5.4 0,1.6 1.6,0", "0.9,5.7 5.1,1.3"},
        {"1", "1,1.5 3,0 3,7", "1.2,7 4.8,7"},
        {"2", "0,1.5 1.6,0 4.4,0 6,1.6 6,2.7 0,7 6,7"},
        {"3", "0,0 6,0 2.6,3 4.6,3 6,4.4 6,5.7 4.4,7 1.6,7 0,5.8"},
        {"4", "4.5,7 4.5,0 0,4.8 6,4.8"},
        {"5", "6,0 0,0 0,3 4.4,3 6,4.4 6,5.7 4.4,7 1.6,7 0,5.8"},
        {"6", "5.5,0.6 4,0 1.6,0 0,1.7 0,5.4 1.6,7 4.4,7 6,5.6 6,4.4 4.4,3.2 1.6,3.2 0,4.4"},
        {"7", "0,0 6,0 2.4,7"},
        {"8", "1.6,3.4 0,2.1 0,1.3 1.6,0 4.4,0 6,1.3 6,2.1 4.4,3.4 1.6,3.4 0,4.8 0,5.7 1.6,7 "
              + "4.4,7 6,5.7 6,4.8 4.4,3.4"},
        {"9", "0.5,6.4 2,7 4.4,7 6,5.4 6,1.6 4.4,0 1.6,0 0,1.4 0,2.7 1.6,3.9 4.4,3.9 6,2.7"},
        {"-", "0.8,3.6 5.2,3.6"},
        {".", "2.6,6.4 3.4,6.4 3.4,7.1 2.6,7.1 2.6,6.4"},
        {",", "3.4,6.4 3.4,7.1 2.4,7.9"},
        {":", "2.7,2.2 3.3,2.2", "2.7,5.2 3.3,5.2"},
        {"/", "6,0 0,7"},
        {"(", "4.6,0 2,2 2,5 4.6,7"},
        {")", "1.4,0 4,2 4,5 1.4,7"},
        {"'", "3,0 3,1.8"},
        {"·", "2.6,3.2 3.4,3.2 3.4,4 2.6,4 2.6,3.2"},
        {"—", "0,3.6 6,3.6"},
    };

    /** Grid units across one glyph cell, before tracking. */
    private static final double GLYPH_UNITS_WIDE = 6;
    private static final double GLYPH_UNITS_TALL = 7;

    /**
     * Sets a line of constructed capitals.
     *
     * @param capHeight cap line to baseline, in pixels
     * @param tracking  extra space between glyphs, in pixels. Generous everywhere on these plates:
     *                  wide tracking is most of what makes small type read as inked rather than as
     *                  set, and it is what lets a six-word line hold a whole margin.
     */
    private static void text(Graphics2D g, String line, double x, double baseline, double capHeight,
                             double tracking, Color colour, float width) {
        double scale = capHeight / GLYPH_UNITS_TALL;
        double advance = GLYPH_UNITS_WIDE * scale + tracking;
        g.setColor(colour);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double penX = x;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') {
                penX += advance * 0.62;
                continue;
            }
            String[] glyph = glyphFor(c);
            if (glyph != null) {
                for (int stroke = 1; stroke < glyph.length; stroke++) {
                    g.draw(polyline(glyph[stroke], penX, baseline - capHeight, scale));
                }
            }
            penX += advance;
        }
    }

    /** The same, turned a quarter left, for labels that run up a column. */
    private static void verticalText(Graphics2D g, String line, double x, double baseline,
                                     double capHeight, double tracking, Color colour, float width) {
        AffineTransform saved = g.getTransform();
        g.translate(x, baseline);
        g.rotate(-StrictMath.PI / 2);
        text(g, line, 0, 0, capHeight, tracking, colour, width);
        g.setTransform(saved);
    }

    private static double textWidth(String line, double capHeight, double tracking) {
        double advance = GLYPH_UNITS_WIDE * (capHeight / GLYPH_UNITS_TALL) + tracking;
        double width = 0;
        for (int i = 0; i < line.length(); i++) {
            width += line.charAt(i) == ' ' ? advance * 0.62 : advance;
        }
        return width - tracking;
    }

    private static String[] glyphFor(char c) {
        char upper = Character.toUpperCase(c);
        for (String[] glyph : GLYPHS) {
            if (glyph[0].charAt(0) == upper) {
                return glyph;
            }
        }
        return null;
    }

    private static Path2D polyline(String points, double x, double top, double scale) {
        Path2D path = new Path2D.Double();
        boolean first = true;
        for (String point : points.split(" ")) {
            String[] pair = point.split(",");
            double px = x + Double.parseDouble(pair[0]) * scale;
            double py = top + Double.parseDouble(pair[1]) * scale;
            if (first) {
                path.moveTo(px, py);
                first = false;
            } else {
                path.lineTo(px, py);
            }
        }
        return path;
    }
}
