package com.hashimjacobs.spacecase.asset;

/**
 * A player hull's CG loops, one constant per chassis, rendered from a Tripo model per chassis and
 * per body kit by {@code tools/tripo/models.py}:
 * {@code sprites/hull-cg/<chassis>[-<kit>]/<view>/<n>.png}, in militia green. Paint, bank and the
 * hit flash are derived on decode ({@link HullPaint}), so one set of loops serves all six liveries
 * and five poses.
 *
 * Three views, all of the same model: {@link View#TOP} is the hull flown top-down, nose up;
 * {@link View#SIDE} is its true profile, nose right, for the side-on levels; {@link View#TURN} is
 * the garage's turntable, a full turn seen from above and in front.
 *
 * The models have no flame. The renderer draws {@code fx-engine-flame} behind each hull at its
 * tail, which is where {@link #tailX}/{@link #tailY} (top view) and {@link #sideTailY} (profile)
 * put it, as fractions of the view's box.
 *
 * The five poses are the banks in {@code PlayerShip.Lean} order, the same order the generator's
 * {@code POSES} and {@code Chassis.Names.POSES} use; {@code ChassisArtPosesTest} pins that.
 */
public enum ChassisArt {
    // tailX, tailY: the nozzles in the nose-up top view. sideTailY: their height in the profile.
    // Measured off the first frame of each bare hull's renders; the kits keep their hull's tail.
    STOCK("stock", 0.50, 0.97, 0.47),
    INTERCEPTOR("interceptor", 0.50, 0.96, 0.45),
    GUNSHIP("gunship", 0.50, 0.86, 0.55),
    TWIN_BOOM("twin-boom", 0.50, 0.96, 0.48);   // ponytail: one flame from the pod; each boom could take one

    public static final String[] POSES = {"bank-left", "left", "straight", "right", "bank-right"};

    /** Which camera a loop was rendered from, how many frames it has and how fast it plays. */
    public enum View {
        TOP(16, 5), SIDE(16, 5), TURN(36, 6);

        private final int frameCount;
        private final int ticksPerFrame;

        View(int frameCount, int ticksPerFrame) {
            this.frameCount = frameCount;
            this.ticksPerFrame = ticksPerFrame;
        }

        public int frameCount() {
            return frameCount;
        }

        public int frameIndexAt(int tick) {
            return (tick / ticksPerFrame) % frameCount;
        }

        String directory() {
            return name().toLowerCase();
        }
    }

    private final String directory;
    private final double tailX;
    private final double tailY;
    private final double sideTailY;

    ChassisArt(String directory, double tailX, double tailY, double sideTailY) {
        this.directory = directory;
        this.tailX = tailX;
        this.tailY = tailY;
        this.sideTailY = sideTailY;
    }

    /** @param kit the kit's directory suffix ({@code Kit.slug()}), or null for the bare hull */
    public String framePath(String kit, View view, int oneBasedIndex) {
        return "/sprites/hull-cg/" + directory + (kit == null ? "" : "-" + kit) + "/" + view.directory() + "/"
                + oneBasedIndex + ".png";
    }

    public double tailX() {
        return tailX;
    }

    public double tailY() {
        return tailY;
    }

    public double sideTailY() {
        return sideTailY;
    }

    /** The constant of that name ({@code Chassis.name()}), or null while its art has not landed. */
    public static ChassisArt named(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException notYet) {
            return null;
        }
    }
}
