package com.hashimjacobs.spacecase.prefs;

import com.hashimjacobs.spacecase.mode.Galaxy;

/** Spawn pressure presets. Chances are per-thousand rolls per tick. */
public enum Difficulty {

    EASY("Easy", 7, 3, 130, 3, 0.85, 0.90, 0),
    NORMAL("Normal", 11, 6, 84, 5, 1.0, 1.0, 0),
    HARD("Hard", 17, 10, 40, 9, 1.20, 1.15, 0),

    /**
     * The two joke-name presets, which are not a joke.
     *
     * They move every lever at once rather than one -- rocks, waves, the enemy cap, how fast the
     * ordinary hostiles shoot, how much hull they carry, how many of them are the heavy archetype,
     * and the flagship on top. Cranking a single one of those gives a mode that is annoying in one
     * direction and trivially exploitable in the others; cranking them together is what makes the
     * arena itself the opponent.
     *
     * DIE is deliberately past what is fair: twice the flagship, near twice the hull on everything
     * else, a fifth of NORMAL's gap between enemy shots, and four times as many ships allowed on
     * the field. Nobody is expected to clear a galaxy on it.
     */
    SUFFER("I Want To Suffer", 26, 17, 26, 14, 1.75, 1.45, 12),
    DIE("I Want To Die", 38, 26, 16, 20, 2.50, 1.90, 25);

    /** How much tougher a flagship gets for each level into its galaxy. */
    private static final double BOSS_LEVEL_STEP = 0.04;

    /**
     * How much tougher a flagship gets for each completed pass through the whole campaign.
     *
     * Has to exceed nine level steps (0.36), or coming back round would be easier than the galaxy
     * finale you just beat and {@link #bossScale} would stop being monotonic where it matters.
     */
    private static final double BOSS_LOOP_STEP = 0.40;

    private final String label;
    private final int asteroidChance;
    private final int enemyChance;
    private final int enemyFireCooldown;
    private final int maxEnemies;
    private final double bossFactor;
    private final double enemyScale;
    private final int heavyBias;

    Difficulty(String label, int asteroidChance, int enemyChance, int enemyFireCooldown,
               int maxEnemies, double bossFactor, double enemyScale, int heavyBias) {
        this.label = label;
        this.asteroidChance = asteroidChance;
        this.enemyChance = enemyChance;
        this.enemyFireCooldown = enemyFireCooldown;
        this.maxEnemies = maxEnemies;
        this.bossFactor = bossFactor;
        this.enemyScale = enemyScale;
        this.heavyBias = heavyBias;
    }

    /**
     * The single boss-difficulty knob: how much harder a flagship is than its authored numbers.
     *
     * Health and projectile damage are multiplied by it, fire cooldowns divided by it. Level one on
     * the first loop at NORMAL comes out at exactly 1.0, so the opening fight stays tuned as it was.
     *
     * The loop term is what stops a repeated campaign going flat: {@code mode.Level} wraps past the
     * last level back to the first, and without this the same flagships come round again with
     * identical health and identical cooldowns. This is the multiplier sibling of
     * {@code SpawnDirector.escalated}, which raises spawn pressure per loop; the two are deliberately
     * separate because that one is in per-thousand roll chances and this one is a scale factor.
     *
     * The level term counts within a galaxy, not across the campaign, so it is deliberately NOT
     * monotonic in {@code levelNumber} -- the first flagship of each galaxy scales the same as the
     * very first one. Escalation across galaxies lives in the bosses' authored health instead, which
     * is where a designer can see and tune it. Ramping across all fifty here would compound with
     * that authored curve and put the final flagship near three times its written numbers, which is
     * a ninety-second sponge rather than a hard fight.
     *
     * @param levelNumber position in the campaign, counting from one; reduced to a position within
     *                    its galaxy here, so passing either convention gives the same answer
     * @param loop        passes through the whole campaign, counting from one
     */
    public double bossScale(int levelNumber, int loop) {
        int levelInGalaxy = (levelNumber - 1) % Galaxy.LEVELS_PER_GALAXY;
        double byLevel = 1 + BOSS_LEVEL_STEP * levelInGalaxy;
        double byLoop = 1 + BOSS_LOOP_STEP * (loop - 1);
        double scale = bossFactor * byLevel * byLoop;
        return scale;
    }

    public String label() {
        return label;
    }

    public int asteroidChance() {
        return asteroidChance;
    }

    public int enemyChance() {
        return enemyChance;
    }

    public int enemyFireCooldown() {
        return enemyFireCooldown;
    }

    /**
     * How many ordinary hostiles the <em>filler</em> may put on the field.
     *
     * It used to be the ceiling on the whole population, back when the whole population was filler.
     * A level now fields the waves {@code mode.Waves} authors for it and this arrives on top, so it
     * has to be read as "how much noise over the fight" rather than "how big a fight may be" --
     * otherwise EASY, whose ceiling is three, could not field a six-ship wave at all and every level
     * would quietly play differently from the way it was written.
     *
     * The authored wave ignores it outright. What that means in practice is that on EASY the wave
     * lands whole and the trickle is simply off until the wave thins, which is the right reading of
     * "easy"; on DIE you get the wave and twenty more on top.
     */
    public int maxEnemies() {
        return maxEnemies;
    }

    /**
     * The cap, opened up for a crowd.
     *
     * The authored numbers were tuned against one or two ships and are left exactly alone at those
     * counts -- a couch co-op game must field what it always fielded. Beyond two the sky would
     * otherwise thin out with every player added, four guns against the same six enemies.
     *
     * A cap, deliberately, and not a spawn chance. Raising the chance would mean touching the draw
     * order in {@code SpawnDirector}, which {@code SpawnDirectorTest} pins on fixed seeds and
     * {@code docs/ROADMAP.md} rule 8 forbids; clamping the result after the draw is the move the
     * terrain lane check already makes.
     *
     * ponytail: linear in the player count. It is arithmetic, not playtesting -- four players on
     * DIE get forty enemies, and whether that is a fight or a slideshow is a question for a
     * controller in someone's hands.
     */
    public int maxEnemies(int players) {
        return players <= 2 ? maxEnemies : maxEnemies * players / 2;
    }

    /**
     * How much tougher an ordinary hostile is than its authored archetype.
     *
     * The sibling of {@link #bossScale}, for everything that is not a flagship. Multiplies hull in
     * {@code entity.EnemyShip}; speed too, but capped there, because a scout that outruns the
     * player's own top speed cannot be fought, only absorbed.
     *
     * Score is left alone on purpose, for the same reason a scaled flagship pays its authored
     * bounty: choosing a harder preset should not also be the fastest way to farm the leaderboard.
     */
    public double enemyScale() {
        return enemyScale;
    }

    /**
     * Percentage points added to the roll that picks the heavy archetype over the light one.
     *
     * Zero for the three original presets, so their wave composition is exactly what it was and the
     * seeded spawn tests still describe the game. Only the two new modes lean on it.
     */
    public int heavyBias() {
        return heavyBias;
    }

    public Difficulty next() {
        Difficulty[] all = values();
        Difficulty following = all[(ordinal() + 1) % all.length];
        return following;
    }
}
