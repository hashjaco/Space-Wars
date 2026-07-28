package com.hashimjacobs.spacecase.prefs;

/** Spawn pressure presets. Chances are per-thousand rolls per tick. */
public enum Difficulty {

    EASY("Easy", 7, 3, 100, 4),
    NORMAL("Normal", 11, 6, 66, 6),
    HARD("Hard", 17, 10, 40, 9);

    private final String label;
    private final int asteroidChance;
    private final int enemyChance;
    private final int enemyFireCooldown;
    private final int maxEnemies;

    Difficulty(String label, int asteroidChance, int enemyChance, int enemyFireCooldown, int maxEnemies) {
        this.label = label;
        this.asteroidChance = asteroidChance;
        this.enemyChance = enemyChance;
        this.enemyFireCooldown = enemyFireCooldown;
        this.maxEnemies = maxEnemies;
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
