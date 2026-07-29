package com.hashimjacobs.spacecase.prefs;

/**
 * A pilot's standing, earned across every run they have ever flown.
 *
 * Thresholds climb geometrically rather than evenly: the early ranks arrive inside a run or two so
 * progress is visible from the first debrief, and the last few take hundreds, so the top of the
 * ladder means something.
 *
 * Pure data, so the ladder is testable without touching Preferences or the JavaFX toolkit.
 */
public enum Rank {

    RECRUIT("Recruit", "RCT", 0),
    PRIVATE("Private", "PVT", 2_000),
    PRIVATE_FIRST_CLASS("Private First Class", "PFC", 5_000),
    SPECIALIST("Specialist", "SPC", 10_000),
    CORPORAL("Corporal", "CPL", 18_000),
    SERGEANT("Sergeant", "SGT", 30_000),
    STAFF_SERGEANT("Staff Sergeant", "SSG", 45_000),
    SERGEANT_FIRST_CLASS("Sergeant First Class", "SFC", 65_000),
    MASTER_SERGEANT("Master Sergeant", "MSG", 90_000),
    FIRST_SERGEANT("First Sergeant", "1SG", 125_000),
    SERGEANT_MAJOR("Sergeant Major", "SGM", 170_000),
    COMMAND_SERGEANT_MAJOR("Command Sergeant Major", "CSM", 225_000),
    WARRANT_OFFICER("Warrant Officer", "WO", 300_000),
    CHIEF_WARRANT_OFFICER("Chief Warrant Officer", "CWO", 400_000),
    ENSIGN("Ensign", "ENS", 525_000),
    SECOND_LIEUTENANT("Second Lieutenant", "2LT", 675_000),
    FIRST_LIEUTENANT("First Lieutenant", "1LT", 875_000),
    CAPTAIN("Captain", "CPT", 1_100_000),
    MAJOR("Major", "MAJ", 1_400_000),
    LIEUTENANT_COLONEL("Lieutenant Colonel", "LTC", 1_750_000),
    COLONEL("Colonel", "COL", 2_200_000),
    BRIGADIER_GENERAL("Brigadier General", "BG", 2_750_000),
    MAJOR_GENERAL("Major General", "MG", 3_400_000),
    LIEUTENANT_GENERAL("Lieutenant General", "LTG", 4_200_000),
    GENERAL("General", "GEN", 5_200_000),
    FLEET_MARSHAL("Fleet Marshal", "FM", 6_500_000);

    private final String label;
    private final String abbreviation;
    private final int careerScoreRequired;

    Rank(String label, String abbreviation, int careerScoreRequired) {
        this.label = label;
        this.abbreviation = abbreviation;
        this.careerScoreRequired = careerScoreRequired;
    }

    /** The highest rank the given career score has earned. */
    public static Rank forCareerScore(int careerScore) {
        Rank[] ladder = values();
        Rank earned = ladder[0];
        for (Rank candidate : ladder) {
            if (careerScore < candidate.careerScoreRequired) {
                break;
            }
            earned = candidate;
        }
        return earned;
    }

    public String label() {
        return label;
    }

    /** Short form, for where the full title will not fit. */
    public String abbreviation() {
        return abbreviation;
    }

    public int careerScoreRequired() {
        return careerScoreRequired;
    }

    /** The rank above this one, or this one at the top of the ladder. */
    public Rank next() {
        Rank[] ladder = values();
        int above = Math.min(ordinal() + 1, ladder.length - 1);
        return ladder[above];
    }

    public boolean isHighest() {
        boolean top = ordinal() == values().length - 1;
        return top;
    }

    /**
     * How far this rank is toward the next, as a 0..1 fraction of the career score between them.
     *
     * Returns 1 at the top of the ladder, where there is nothing left to progress toward.
     */
    public double progressToward(Rank above, int careerScore) {
        if (this == above) {
            return 1;
        }
        int span = above.careerScoreRequired - careerScoreRequired;
        if (span <= 0) {
            return 1;
        }
        double earned = (careerScore - careerScoreRequired) / (double) span;
        double clamped = Math.max(0, Math.min(1, earned));
        return clamped;
    }

    /**
     * Insignia tier, used to draw chevrons, bars or stars without shipping twenty-six images.
     *
     * ponytail: the shapes are drawn from this in {@code engine.DebriefOverlay}. Generate proper
     * insignia art if the drawn version ever reads as placeholder.
     */
    public Insignia insignia() {
        if (ordinal() >= BRIGADIER_GENERAL.ordinal()) {
            return Insignia.STARS;
        }
        if (ordinal() >= ENSIGN.ordinal()) {
            return Insignia.BARS;
        }
        if (ordinal() >= WARRANT_OFFICER.ordinal()) {
            return Insignia.RODS;
        }
        return Insignia.CHEVRONS;
    }

    /** How many marks the insignia carries, which is what separates ranks within a tier. */
    public int insigniaCount() {
        Rank tierStart = switch (insignia()) {
            case CHEVRONS -> RECRUIT;
            case RODS -> WARRANT_OFFICER;
            case BARS -> ENSIGN;
            case STARS -> BRIGADIER_GENERAL;
        };
        int marks = ordinal() - tierStart.ordinal() + 1;
        return marks;
    }

    public enum Insignia { CHEVRONS, RODS, BARS, STARS }
}
