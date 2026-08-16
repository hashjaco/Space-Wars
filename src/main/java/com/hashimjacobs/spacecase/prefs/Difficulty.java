package com.hashimjacobs.spacecase.prefs;

/** Spawn pressure presets. Chances are per-thousand rolls per tick. */
public enum Difficulty {

    EASY("Easy", 7, 3, 100, 4, 0.85),
    NORMAL("Normal", 11, 6, 66, 6, 1.0),
    HARD("Hard", 17, 10, 40, 9, 1.20);

    /** How much tougher a flagship gets for each level into the run. */
    private static final double BOSS_LEVEL_STEP = 0.04;

    /** How much tougher a flagship gets for each completed pass through the whole run. */
    private static final double BOSS_LOOP_STEP = 0.30;

    private final String label;
    private final int asteroidChance;
    private final int enemyChance;
    private final int enemyFireCooldown;
    private final int maxEnemies;
    private final double bossFactor;

    Difficulty(String label, int asteroidChance, int enemyChance, int enemyFireCooldown,
               int maxEnemies, double bossFactor) {
        this.label = label;
        this.asteroidChance = asteroidChance;
        this.enemyChance = enemyChance;
        this.enemyFireCooldown = enemyFireCooldown;
        this.maxEnemies = maxEnemies;
        this.bossFactor = bossFactor;
    }

    /**
     * The single boss-difficulty knob: how much harder a flagship is than its authored numbers.
     *
     * Health and projectile damage are multiplied by it, fire cooldowns divided by it. Level one on
     * the first loop at NORMAL comes out at exactly 1.0, so the opening fight stays tuned as it was.
     *
     * The loop term is what stops the run going flat: {@code mode.Level} wraps past level eight back
     * to the first, and without this the same eight flagships come round again with identical health
     * and identical cooldowns. This is the multiplier sibling of {@code SpawnDirector.escalated},
     * which raises spawn pressure per loop; the two are deliberately separate because that one is in
     * per-thousand roll chances and this one is a scale factor.
     *
     * @param levelNumber position in the run, counting from one
     * @param loop        passes through the whole run, counting from one
     */
    public double bossScale(int levelNumber, int loop) {
        double byLevel = 1 + BOSS_LEVEL_STEP * (levelNumber - 1);
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

    public int maxEnemies() {
        return maxEnemies;
    }

    public Difficulty next() {
        Difficulty[] all = values();
        Difficulty following = all[(ordinal() + 1) % all.length];
        return following;
    }
}
