package com.hashimjacobs.spacecase.asset;

import java.util.EnumMap;
import java.util.Map;

import com.hashimjacobs.spacecase.GameConfig;

/**
 * Every image the game draws, with the on-screen size it is decoded at.
 *
 * Sprites used to be looked up by string out of a HashMap, which silently returned null for the
 * several keys that were never registered; those nulls reached GraphicsContext.drawImage and threw
 * on the render thread. Enum constants make a missing sprite a compile error instead.
 *
 * Provenance for every file is recorded in ASSETS.md.
 */
public enum Sprite {

    /**
     * Player hulls: five bank poses each, plus a damage frame per pose.
     *
     * All twenty are cut from one spritesheet and padded to a common canvas, so every pose decodes to
     * the same size and the ship does not appear to grow as it banks.
     */
    P1_BANK_LEFT("player/p1-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P1_LEFT("player/p1-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P1_STRAIGHT("player/p1-straight.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P1_RIGHT("player/p1-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P1_BANK_RIGHT("player/p1-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P1_BANK_LEFT_HIT("player/p1-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P1_LEFT_HIT("player/p1-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P1_STRAIGHT_HIT("player/p1-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P1_RIGHT_HIT("player/p1-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P1_BANK_RIGHT_HIT("player/p1-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),

    P2_BANK_LEFT("player/p2-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P2_LEFT("player/p2-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P2_STRAIGHT("player/p2-straight.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P2_RIGHT("player/p2-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P2_BANK_RIGHT("player/p2-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P2_BANK_LEFT_HIT("player/p2-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P2_LEFT_HIT("player/p2-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P2_STRAIGHT_HIT("player/p2-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P2_RIGHT_HIT("player/p2-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P2_BANK_RIGHT_HIT("player/p2-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),

    /**
     * Garage paint jobs, bought per pilot.
     *
     * Same twenty frames as a stock hull, hue-rotated by the generator rather than redrawn, so a
     * paint job costs ten files and no new art. Each set runs in {@code PlayerShip.Lean} order --
     * hardest left to hardest right, poses then damage frames -- because {@code garage.Livery}
     * indexes them by that enum's ordinal.
     */
    AZURE_BANK_LEFT("player/azure-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    AZURE_LEFT("player/azure-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    AZURE_STRAIGHT("player/azure-straight.png", Draw.PLAYER_W, Draw.PLAYER_H),
    AZURE_RIGHT("player/azure-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    AZURE_BANK_RIGHT("player/azure-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    AZURE_BANK_LEFT_HIT("player/azure-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    AZURE_LEFT_HIT("player/azure-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    AZURE_STRAIGHT_HIT("player/azure-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    AZURE_RIGHT_HIT("player/azure-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    AZURE_BANK_RIGHT_HIT("player/azure-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),

    AMBER_BANK_LEFT("player/amber-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    AMBER_LEFT("player/amber-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    AMBER_STRAIGHT("player/amber-straight.png", Draw.PLAYER_W, Draw.PLAYER_H),
    AMBER_RIGHT("player/amber-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    AMBER_BANK_RIGHT("player/amber-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    AMBER_BANK_LEFT_HIT("player/amber-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    AMBER_LEFT_HIT("player/amber-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    AMBER_STRAIGHT_HIT("player/amber-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    AMBER_RIGHT_HIT("player/amber-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    AMBER_BANK_RIGHT_HIT("player/amber-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),

    VIOLET_BANK_LEFT("player/violet-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    VIOLET_LEFT("player/violet-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    VIOLET_STRAIGHT("player/violet-straight.png", Draw.PLAYER_W, Draw.PLAYER_H),
    VIOLET_RIGHT("player/violet-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    VIOLET_BANK_RIGHT("player/violet-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    VIOLET_BANK_LEFT_HIT("player/violet-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    VIOLET_LEFT_HIT("player/violet-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    VIOLET_STRAIGHT_HIT("player/violet-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    VIOLET_RIGHT_HIT("player/violet-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    VIOLET_BANK_RIGHT_HIT("player/violet-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),

    CHROME_BANK_LEFT("player/chrome-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    CHROME_LEFT("player/chrome-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    CHROME_STRAIGHT("player/chrome-straight.png", Draw.PLAYER_W, Draw.PLAYER_H),
    CHROME_RIGHT("player/chrome-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    CHROME_BANK_RIGHT("player/chrome-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    CHROME_BANK_LEFT_HIT("player/chrome-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    CHROME_LEFT_HIT("player/chrome-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    CHROME_STRAIGHT_HIT("player/chrome-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    CHROME_RIGHT_HIT("player/chrome-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    CHROME_BANK_RIGHT_HIT("player/chrome-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),

    /**
     * Garage body kits: transparent decals drawn over whatever hull is underneath.
     *
     * Overlays rather than whole hulls, which is what stops the catalogue exploding -- otherwise
     * every kit would need one copy per paint job per damage state. There is no damage variant for
     * the same reason: the hull beneath already flashes.
     */
    KIT_FINS_BANK_LEFT("player/kit-fins-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_FINS_LEFT("player/kit-fins-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_FINS_STRAIGHT("player/kit-fins-straight.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_FINS_RIGHT("player/kit-fins-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_FINS_BANK_RIGHT("player/kit-fins-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H),

    KIT_ARMOUR_BANK_LEFT("player/kit-armour-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_ARMOUR_LEFT("player/kit-armour-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_ARMOUR_STRAIGHT("player/kit-armour-straight.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_ARMOUR_RIGHT("player/kit-armour-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_ARMOUR_BANK_RIGHT("player/kit-armour-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H),

    KIT_LANCE_BANK_LEFT("player/kit-lance-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_LANCE_LEFT("player/kit-lance-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_LANCE_STRAIGHT("player/kit-lance-straight.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_LANCE_RIGHT("player/kit-lance-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_LANCE_BANK_RIGHT("player/kit-lance-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H),

    /**
     * The bought chassis: three silhouettes, each in all six paints.
     *
     * Faulted in on first use rather than decoded with everything else. {@code Assets.load} warms
     * every other sprite up front, and three hundred and sixty frames for ships a pilot is not
     * flying is a startup cost paid by everybody for nobody. The first draw is the garage showcase,
     * which is the right moment to pay it.
     *
     * A chassis is a shape and a paint is a hue, which is why the two stock ships appear here as
     * "militia" and "corsair" -- on the sheet they are rows, and a bought chassis has no row.
     */

    INTERCEPTOR_MILITIA_BANK_LEFT("player/interceptor-militia-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_MILITIA_LEFT("player/interceptor-militia-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_MILITIA_STRAIGHT("player/interceptor-militia-straight.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_MILITIA_RIGHT("player/interceptor-militia-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_MILITIA_BANK_RIGHT("player/interceptor-militia-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_MILITIA_BANK_LEFT_HIT("player/interceptor-militia-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_MILITIA_LEFT_HIT("player/interceptor-militia-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_MILITIA_STRAIGHT_HIT("player/interceptor-militia-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_MILITIA_RIGHT_HIT("player/interceptor-militia-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_MILITIA_BANK_RIGHT_HIT("player/interceptor-militia-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),

    INTERCEPTOR_CORSAIR_BANK_LEFT("player/interceptor-corsair-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_CORSAIR_LEFT("player/interceptor-corsair-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_CORSAIR_STRAIGHT("player/interceptor-corsair-straight.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_CORSAIR_RIGHT("player/interceptor-corsair-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_CORSAIR_BANK_RIGHT("player/interceptor-corsair-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_CORSAIR_BANK_LEFT_HIT("player/interceptor-corsair-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_CORSAIR_LEFT_HIT("player/interceptor-corsair-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_CORSAIR_STRAIGHT_HIT("player/interceptor-corsair-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_CORSAIR_RIGHT_HIT("player/interceptor-corsair-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_CORSAIR_BANK_RIGHT_HIT("player/interceptor-corsair-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),

    INTERCEPTOR_AZURE_BANK_LEFT("player/interceptor-azure-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_AZURE_LEFT("player/interceptor-azure-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_AZURE_STRAIGHT("player/interceptor-azure-straight.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_AZURE_RIGHT("player/interceptor-azure-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_AZURE_BANK_RIGHT("player/interceptor-azure-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_AZURE_BANK_LEFT_HIT("player/interceptor-azure-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_AZURE_LEFT_HIT("player/interceptor-azure-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_AZURE_STRAIGHT_HIT("player/interceptor-azure-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_AZURE_RIGHT_HIT("player/interceptor-azure-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_AZURE_BANK_RIGHT_HIT("player/interceptor-azure-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),

    INTERCEPTOR_AMBER_BANK_LEFT("player/interceptor-amber-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_AMBER_LEFT("player/interceptor-amber-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_AMBER_STRAIGHT("player/interceptor-amber-straight.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_AMBER_RIGHT("player/interceptor-amber-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_AMBER_BANK_RIGHT("player/interceptor-amber-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_AMBER_BANK_LEFT_HIT("player/interceptor-amber-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_AMBER_LEFT_HIT("player/interceptor-amber-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_AMBER_STRAIGHT_HIT("player/interceptor-amber-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_AMBER_RIGHT_HIT("player/interceptor-amber-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_AMBER_BANK_RIGHT_HIT("player/interceptor-amber-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),

    INTERCEPTOR_VIOLET_BANK_LEFT("player/interceptor-violet-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_VIOLET_LEFT("player/interceptor-violet-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_VIOLET_STRAIGHT("player/interceptor-violet-straight.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_VIOLET_RIGHT("player/interceptor-violet-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_VIOLET_BANK_RIGHT("player/interceptor-violet-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_VIOLET_BANK_LEFT_HIT("player/interceptor-violet-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_VIOLET_LEFT_HIT("player/interceptor-violet-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_VIOLET_STRAIGHT_HIT("player/interceptor-violet-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_VIOLET_RIGHT_HIT("player/interceptor-violet-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_VIOLET_BANK_RIGHT_HIT("player/interceptor-violet-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),

    INTERCEPTOR_CHROME_BANK_LEFT("player/interceptor-chrome-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_CHROME_LEFT("player/interceptor-chrome-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_CHROME_STRAIGHT("player/interceptor-chrome-straight.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_CHROME_RIGHT("player/interceptor-chrome-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_CHROME_BANK_RIGHT("player/interceptor-chrome-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_CHROME_BANK_LEFT_HIT("player/interceptor-chrome-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_CHROME_LEFT_HIT("player/interceptor-chrome-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_CHROME_STRAIGHT_HIT("player/interceptor-chrome-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_CHROME_RIGHT_HIT("player/interceptor-chrome-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    INTERCEPTOR_CHROME_BANK_RIGHT_HIT("player/interceptor-chrome-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),

    GUNSHIP_MILITIA_BANK_LEFT("player/gunship-militia-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_MILITIA_LEFT("player/gunship-militia-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_MILITIA_STRAIGHT("player/gunship-militia-straight.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_MILITIA_RIGHT("player/gunship-militia-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_MILITIA_BANK_RIGHT("player/gunship-militia-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_MILITIA_BANK_LEFT_HIT("player/gunship-militia-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_MILITIA_LEFT_HIT("player/gunship-militia-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_MILITIA_STRAIGHT_HIT("player/gunship-militia-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_MILITIA_RIGHT_HIT("player/gunship-militia-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_MILITIA_BANK_RIGHT_HIT("player/gunship-militia-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),

    GUNSHIP_CORSAIR_BANK_LEFT("player/gunship-corsair-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_CORSAIR_LEFT("player/gunship-corsair-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_CORSAIR_STRAIGHT("player/gunship-corsair-straight.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_CORSAIR_RIGHT("player/gunship-corsair-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_CORSAIR_BANK_RIGHT("player/gunship-corsair-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_CORSAIR_BANK_LEFT_HIT("player/gunship-corsair-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_CORSAIR_LEFT_HIT("player/gunship-corsair-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_CORSAIR_STRAIGHT_HIT("player/gunship-corsair-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_CORSAIR_RIGHT_HIT("player/gunship-corsair-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_CORSAIR_BANK_RIGHT_HIT("player/gunship-corsair-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),

    GUNSHIP_AZURE_BANK_LEFT("player/gunship-azure-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_AZURE_LEFT("player/gunship-azure-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_AZURE_STRAIGHT("player/gunship-azure-straight.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_AZURE_RIGHT("player/gunship-azure-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_AZURE_BANK_RIGHT("player/gunship-azure-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_AZURE_BANK_LEFT_HIT("player/gunship-azure-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_AZURE_LEFT_HIT("player/gunship-azure-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_AZURE_STRAIGHT_HIT("player/gunship-azure-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_AZURE_RIGHT_HIT("player/gunship-azure-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_AZURE_BANK_RIGHT_HIT("player/gunship-azure-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),

    GUNSHIP_AMBER_BANK_LEFT("player/gunship-amber-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_AMBER_LEFT("player/gunship-amber-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_AMBER_STRAIGHT("player/gunship-amber-straight.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_AMBER_RIGHT("player/gunship-amber-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_AMBER_BANK_RIGHT("player/gunship-amber-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_AMBER_BANK_LEFT_HIT("player/gunship-amber-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_AMBER_LEFT_HIT("player/gunship-amber-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_AMBER_STRAIGHT_HIT("player/gunship-amber-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_AMBER_RIGHT_HIT("player/gunship-amber-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_AMBER_BANK_RIGHT_HIT("player/gunship-amber-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),

    GUNSHIP_VIOLET_BANK_LEFT("player/gunship-violet-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_VIOLET_LEFT("player/gunship-violet-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_VIOLET_STRAIGHT("player/gunship-violet-straight.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_VIOLET_RIGHT("player/gunship-violet-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_VIOLET_BANK_RIGHT("player/gunship-violet-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_VIOLET_BANK_LEFT_HIT("player/gunship-violet-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_VIOLET_LEFT_HIT("player/gunship-violet-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_VIOLET_STRAIGHT_HIT("player/gunship-violet-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_VIOLET_RIGHT_HIT("player/gunship-violet-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_VIOLET_BANK_RIGHT_HIT("player/gunship-violet-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),

    GUNSHIP_CHROME_BANK_LEFT("player/gunship-chrome-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_CHROME_LEFT("player/gunship-chrome-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_CHROME_STRAIGHT("player/gunship-chrome-straight.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_CHROME_RIGHT("player/gunship-chrome-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_CHROME_BANK_RIGHT("player/gunship-chrome-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_CHROME_BANK_LEFT_HIT("player/gunship-chrome-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_CHROME_LEFT_HIT("player/gunship-chrome-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_CHROME_STRAIGHT_HIT("player/gunship-chrome-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_CHROME_RIGHT_HIT("player/gunship-chrome-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    GUNSHIP_CHROME_BANK_RIGHT_HIT("player/gunship-chrome-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),

    TWIN_BOOM_MILITIA_BANK_LEFT("player/twin-boom-militia-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_MILITIA_LEFT("player/twin-boom-militia-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_MILITIA_STRAIGHT("player/twin-boom-militia-straight.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_MILITIA_RIGHT("player/twin-boom-militia-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_MILITIA_BANK_RIGHT("player/twin-boom-militia-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_MILITIA_BANK_LEFT_HIT("player/twin-boom-militia-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_MILITIA_LEFT_HIT("player/twin-boom-militia-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_MILITIA_STRAIGHT_HIT("player/twin-boom-militia-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_MILITIA_RIGHT_HIT("player/twin-boom-militia-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_MILITIA_BANK_RIGHT_HIT("player/twin-boom-militia-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),

    TWIN_BOOM_CORSAIR_BANK_LEFT("player/twin-boom-corsair-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_CORSAIR_LEFT("player/twin-boom-corsair-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_CORSAIR_STRAIGHT("player/twin-boom-corsair-straight.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_CORSAIR_RIGHT("player/twin-boom-corsair-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_CORSAIR_BANK_RIGHT("player/twin-boom-corsair-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_CORSAIR_BANK_LEFT_HIT("player/twin-boom-corsair-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_CORSAIR_LEFT_HIT("player/twin-boom-corsair-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_CORSAIR_STRAIGHT_HIT("player/twin-boom-corsair-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_CORSAIR_RIGHT_HIT("player/twin-boom-corsair-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_CORSAIR_BANK_RIGHT_HIT("player/twin-boom-corsair-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),

    TWIN_BOOM_AZURE_BANK_LEFT("player/twin-boom-azure-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_AZURE_LEFT("player/twin-boom-azure-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_AZURE_STRAIGHT("player/twin-boom-azure-straight.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_AZURE_RIGHT("player/twin-boom-azure-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_AZURE_BANK_RIGHT("player/twin-boom-azure-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_AZURE_BANK_LEFT_HIT("player/twin-boom-azure-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_AZURE_LEFT_HIT("player/twin-boom-azure-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_AZURE_STRAIGHT_HIT("player/twin-boom-azure-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_AZURE_RIGHT_HIT("player/twin-boom-azure-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_AZURE_BANK_RIGHT_HIT("player/twin-boom-azure-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),

    TWIN_BOOM_AMBER_BANK_LEFT("player/twin-boom-amber-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_AMBER_LEFT("player/twin-boom-amber-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_AMBER_STRAIGHT("player/twin-boom-amber-straight.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_AMBER_RIGHT("player/twin-boom-amber-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_AMBER_BANK_RIGHT("player/twin-boom-amber-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_AMBER_BANK_LEFT_HIT("player/twin-boom-amber-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_AMBER_LEFT_HIT("player/twin-boom-amber-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_AMBER_STRAIGHT_HIT("player/twin-boom-amber-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_AMBER_RIGHT_HIT("player/twin-boom-amber-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_AMBER_BANK_RIGHT_HIT("player/twin-boom-amber-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),

    TWIN_BOOM_VIOLET_BANK_LEFT("player/twin-boom-violet-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_VIOLET_LEFT("player/twin-boom-violet-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_VIOLET_STRAIGHT("player/twin-boom-violet-straight.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_VIOLET_RIGHT("player/twin-boom-violet-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_VIOLET_BANK_RIGHT("player/twin-boom-violet-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_VIOLET_BANK_LEFT_HIT("player/twin-boom-violet-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_VIOLET_LEFT_HIT("player/twin-boom-violet-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_VIOLET_STRAIGHT_HIT("player/twin-boom-violet-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_VIOLET_RIGHT_HIT("player/twin-boom-violet-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_VIOLET_BANK_RIGHT_HIT("player/twin-boom-violet-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),

    TWIN_BOOM_CHROME_BANK_LEFT("player/twin-boom-chrome-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_CHROME_LEFT("player/twin-boom-chrome-left.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_CHROME_STRAIGHT("player/twin-boom-chrome-straight.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_CHROME_RIGHT("player/twin-boom-chrome-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_CHROME_BANK_RIGHT("player/twin-boom-chrome-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_CHROME_BANK_LEFT_HIT("player/twin-boom-chrome-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_CHROME_LEFT_HIT("player/twin-boom-chrome-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_CHROME_STRAIGHT_HIT("player/twin-boom-chrome-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_CHROME_RIGHT_HIT("player/twin-boom-chrome-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),
    TWIN_BOOM_CHROME_BANK_RIGHT_HIT("player/twin-boom-chrome-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H, true),


    /** The four kits added beside the original three. Same decals, drawn over any hull. */

    KIT_CANARDS_BANK_LEFT("player/kit-canards-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_CANARDS_LEFT("player/kit-canards-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_CANARDS_STRAIGHT("player/kit-canards-straight.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_CANARDS_RIGHT("player/kit-canards-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_CANARDS_BANK_RIGHT("player/kit-canards-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H),

    KIT_SCOOP_BANK_LEFT("player/kit-scoop-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_SCOOP_LEFT("player/kit-scoop-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_SCOOP_STRAIGHT("player/kit-scoop-straight.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_SCOOP_RIGHT("player/kit-scoop-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_SCOOP_BANK_RIGHT("player/kit-scoop-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H),

    KIT_MAST_BANK_LEFT("player/kit-mast-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_MAST_LEFT("player/kit-mast-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_MAST_STRAIGHT("player/kit-mast-straight.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_MAST_RIGHT("player/kit-mast-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_MAST_BANK_RIGHT("player/kit-mast-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H),

    KIT_RACK_BANK_LEFT("player/kit-rack-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_RACK_LEFT("player/kit-rack-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_RACK_STRAIGHT("player/kit-rack-straight.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_RACK_RIGHT("player/kit-rack-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    KIT_RACK_BANK_RIGHT("player/kit-rack-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H),


    /**
     * The same hulls cut for a level flown side-on.
     *
     * A quarter turn of the frames above, baked by the generator rather than applied at draw time,
     * so the art and the collision box are the same shape -- see the transposed dimensions, and
     * {@code L9_SCOUT} below for the hostiles that made the same bargain first. Pixel-identical to
     * what the renderer used to produce by rotating; what changes is that nothing has to rotate.
     */
    P1_BANK_LEFT_SIDE("player/p1-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    P1_LEFT_SIDE("player/p1-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    P1_STRAIGHT_SIDE("player/p1-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    P1_RIGHT_SIDE("player/p1-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    P1_BANK_RIGHT_SIDE("player/p1-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    P1_BANK_LEFT_HIT_SIDE("player/p1-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    P1_LEFT_HIT_SIDE("player/p1-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    P1_STRAIGHT_HIT_SIDE("player/p1-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    P1_RIGHT_HIT_SIDE("player/p1-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    P1_BANK_RIGHT_HIT_SIDE("player/p1-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),

    P2_BANK_LEFT_SIDE("player/p2-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    P2_LEFT_SIDE("player/p2-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    P2_STRAIGHT_SIDE("player/p2-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    P2_RIGHT_SIDE("player/p2-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    P2_BANK_RIGHT_SIDE("player/p2-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    P2_BANK_LEFT_HIT_SIDE("player/p2-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    P2_LEFT_HIT_SIDE("player/p2-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    P2_STRAIGHT_HIT_SIDE("player/p2-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    P2_RIGHT_HIT_SIDE("player/p2-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    P2_BANK_RIGHT_HIT_SIDE("player/p2-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),

    AZURE_BANK_LEFT_SIDE("player/azure-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    AZURE_LEFT_SIDE("player/azure-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    AZURE_STRAIGHT_SIDE("player/azure-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    AZURE_RIGHT_SIDE("player/azure-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    AZURE_BANK_RIGHT_SIDE("player/azure-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    AZURE_BANK_LEFT_HIT_SIDE("player/azure-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    AZURE_LEFT_HIT_SIDE("player/azure-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    AZURE_STRAIGHT_HIT_SIDE("player/azure-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    AZURE_RIGHT_HIT_SIDE("player/azure-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    AZURE_BANK_RIGHT_HIT_SIDE("player/azure-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),

    AMBER_BANK_LEFT_SIDE("player/amber-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    AMBER_LEFT_SIDE("player/amber-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    AMBER_STRAIGHT_SIDE("player/amber-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    AMBER_RIGHT_SIDE("player/amber-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    AMBER_BANK_RIGHT_SIDE("player/amber-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    AMBER_BANK_LEFT_HIT_SIDE("player/amber-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    AMBER_LEFT_HIT_SIDE("player/amber-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    AMBER_STRAIGHT_HIT_SIDE("player/amber-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    AMBER_RIGHT_HIT_SIDE("player/amber-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    AMBER_BANK_RIGHT_HIT_SIDE("player/amber-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),

    VIOLET_BANK_LEFT_SIDE("player/violet-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    VIOLET_LEFT_SIDE("player/violet-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    VIOLET_STRAIGHT_SIDE("player/violet-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    VIOLET_RIGHT_SIDE("player/violet-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    VIOLET_BANK_RIGHT_SIDE("player/violet-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    VIOLET_BANK_LEFT_HIT_SIDE("player/violet-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    VIOLET_LEFT_HIT_SIDE("player/violet-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    VIOLET_STRAIGHT_HIT_SIDE("player/violet-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    VIOLET_RIGHT_HIT_SIDE("player/violet-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    VIOLET_BANK_RIGHT_HIT_SIDE("player/violet-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),

    CHROME_BANK_LEFT_SIDE("player/chrome-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    CHROME_LEFT_SIDE("player/chrome-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    CHROME_STRAIGHT_SIDE("player/chrome-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    CHROME_RIGHT_SIDE("player/chrome-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    CHROME_BANK_RIGHT_SIDE("player/chrome-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    CHROME_BANK_LEFT_HIT_SIDE("player/chrome-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    CHROME_LEFT_HIT_SIDE("player/chrome-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    CHROME_STRAIGHT_HIT_SIDE("player/chrome-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    CHROME_RIGHT_HIT_SIDE("player/chrome-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    CHROME_BANK_RIGHT_HIT_SIDE("player/chrome-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W),

    KIT_FINS_BANK_LEFT_SIDE("player/kit-fins-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_FINS_LEFT_SIDE("player/kit-fins-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_FINS_STRAIGHT_SIDE("player/kit-fins-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_FINS_RIGHT_SIDE("player/kit-fins-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_FINS_BANK_RIGHT_SIDE("player/kit-fins-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),

    KIT_ARMOUR_BANK_LEFT_SIDE("player/kit-armour-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_ARMOUR_LEFT_SIDE("player/kit-armour-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_ARMOUR_STRAIGHT_SIDE("player/kit-armour-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_ARMOUR_RIGHT_SIDE("player/kit-armour-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_ARMOUR_BANK_RIGHT_SIDE("player/kit-armour-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),

    KIT_LANCE_BANK_LEFT_SIDE("player/kit-lance-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_LANCE_LEFT_SIDE("player/kit-lance-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_LANCE_STRAIGHT_SIDE("player/kit-lance-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_LANCE_RIGHT_SIDE("player/kit-lance-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_LANCE_BANK_RIGHT_SIDE("player/kit-lance-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),

    /** Every bought chassis frame again, turned, for the levels flown side-on. */

    INTERCEPTOR_MILITIA_BANK_LEFT_SIDE("player/interceptor-militia-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_MILITIA_LEFT_SIDE("player/interceptor-militia-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_MILITIA_STRAIGHT_SIDE("player/interceptor-militia-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_MILITIA_RIGHT_SIDE("player/interceptor-militia-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_MILITIA_BANK_RIGHT_SIDE("player/interceptor-militia-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_MILITIA_BANK_LEFT_HIT_SIDE("player/interceptor-militia-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_MILITIA_LEFT_HIT_SIDE("player/interceptor-militia-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_MILITIA_STRAIGHT_HIT_SIDE("player/interceptor-militia-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_MILITIA_RIGHT_HIT_SIDE("player/interceptor-militia-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_MILITIA_BANK_RIGHT_HIT_SIDE("player/interceptor-militia-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),

    INTERCEPTOR_CORSAIR_BANK_LEFT_SIDE("player/interceptor-corsair-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_CORSAIR_LEFT_SIDE("player/interceptor-corsair-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_CORSAIR_STRAIGHT_SIDE("player/interceptor-corsair-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_CORSAIR_RIGHT_SIDE("player/interceptor-corsair-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_CORSAIR_BANK_RIGHT_SIDE("player/interceptor-corsair-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_CORSAIR_BANK_LEFT_HIT_SIDE("player/interceptor-corsair-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_CORSAIR_LEFT_HIT_SIDE("player/interceptor-corsair-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_CORSAIR_STRAIGHT_HIT_SIDE("player/interceptor-corsair-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_CORSAIR_RIGHT_HIT_SIDE("player/interceptor-corsair-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_CORSAIR_BANK_RIGHT_HIT_SIDE("player/interceptor-corsair-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),

    INTERCEPTOR_AZURE_BANK_LEFT_SIDE("player/interceptor-azure-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_AZURE_LEFT_SIDE("player/interceptor-azure-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_AZURE_STRAIGHT_SIDE("player/interceptor-azure-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_AZURE_RIGHT_SIDE("player/interceptor-azure-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_AZURE_BANK_RIGHT_SIDE("player/interceptor-azure-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_AZURE_BANK_LEFT_HIT_SIDE("player/interceptor-azure-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_AZURE_LEFT_HIT_SIDE("player/interceptor-azure-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_AZURE_STRAIGHT_HIT_SIDE("player/interceptor-azure-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_AZURE_RIGHT_HIT_SIDE("player/interceptor-azure-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_AZURE_BANK_RIGHT_HIT_SIDE("player/interceptor-azure-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),

    INTERCEPTOR_AMBER_BANK_LEFT_SIDE("player/interceptor-amber-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_AMBER_LEFT_SIDE("player/interceptor-amber-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_AMBER_STRAIGHT_SIDE("player/interceptor-amber-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_AMBER_RIGHT_SIDE("player/interceptor-amber-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_AMBER_BANK_RIGHT_SIDE("player/interceptor-amber-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_AMBER_BANK_LEFT_HIT_SIDE("player/interceptor-amber-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_AMBER_LEFT_HIT_SIDE("player/interceptor-amber-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_AMBER_STRAIGHT_HIT_SIDE("player/interceptor-amber-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_AMBER_RIGHT_HIT_SIDE("player/interceptor-amber-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_AMBER_BANK_RIGHT_HIT_SIDE("player/interceptor-amber-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),

    INTERCEPTOR_VIOLET_BANK_LEFT_SIDE("player/interceptor-violet-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_VIOLET_LEFT_SIDE("player/interceptor-violet-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_VIOLET_STRAIGHT_SIDE("player/interceptor-violet-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_VIOLET_RIGHT_SIDE("player/interceptor-violet-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_VIOLET_BANK_RIGHT_SIDE("player/interceptor-violet-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_VIOLET_BANK_LEFT_HIT_SIDE("player/interceptor-violet-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_VIOLET_LEFT_HIT_SIDE("player/interceptor-violet-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_VIOLET_STRAIGHT_HIT_SIDE("player/interceptor-violet-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_VIOLET_RIGHT_HIT_SIDE("player/interceptor-violet-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_VIOLET_BANK_RIGHT_HIT_SIDE("player/interceptor-violet-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),

    INTERCEPTOR_CHROME_BANK_LEFT_SIDE("player/interceptor-chrome-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_CHROME_LEFT_SIDE("player/interceptor-chrome-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_CHROME_STRAIGHT_SIDE("player/interceptor-chrome-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_CHROME_RIGHT_SIDE("player/interceptor-chrome-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_CHROME_BANK_RIGHT_SIDE("player/interceptor-chrome-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_CHROME_BANK_LEFT_HIT_SIDE("player/interceptor-chrome-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_CHROME_LEFT_HIT_SIDE("player/interceptor-chrome-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_CHROME_STRAIGHT_HIT_SIDE("player/interceptor-chrome-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_CHROME_RIGHT_HIT_SIDE("player/interceptor-chrome-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    INTERCEPTOR_CHROME_BANK_RIGHT_HIT_SIDE("player/interceptor-chrome-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),

    GUNSHIP_MILITIA_BANK_LEFT_SIDE("player/gunship-militia-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_MILITIA_LEFT_SIDE("player/gunship-militia-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_MILITIA_STRAIGHT_SIDE("player/gunship-militia-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_MILITIA_RIGHT_SIDE("player/gunship-militia-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_MILITIA_BANK_RIGHT_SIDE("player/gunship-militia-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_MILITIA_BANK_LEFT_HIT_SIDE("player/gunship-militia-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_MILITIA_LEFT_HIT_SIDE("player/gunship-militia-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_MILITIA_STRAIGHT_HIT_SIDE("player/gunship-militia-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_MILITIA_RIGHT_HIT_SIDE("player/gunship-militia-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_MILITIA_BANK_RIGHT_HIT_SIDE("player/gunship-militia-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),

    GUNSHIP_CORSAIR_BANK_LEFT_SIDE("player/gunship-corsair-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_CORSAIR_LEFT_SIDE("player/gunship-corsair-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_CORSAIR_STRAIGHT_SIDE("player/gunship-corsair-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_CORSAIR_RIGHT_SIDE("player/gunship-corsair-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_CORSAIR_BANK_RIGHT_SIDE("player/gunship-corsair-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_CORSAIR_BANK_LEFT_HIT_SIDE("player/gunship-corsair-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_CORSAIR_LEFT_HIT_SIDE("player/gunship-corsair-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_CORSAIR_STRAIGHT_HIT_SIDE("player/gunship-corsair-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_CORSAIR_RIGHT_HIT_SIDE("player/gunship-corsair-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_CORSAIR_BANK_RIGHT_HIT_SIDE("player/gunship-corsair-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),

    GUNSHIP_AZURE_BANK_LEFT_SIDE("player/gunship-azure-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_AZURE_LEFT_SIDE("player/gunship-azure-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_AZURE_STRAIGHT_SIDE("player/gunship-azure-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_AZURE_RIGHT_SIDE("player/gunship-azure-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_AZURE_BANK_RIGHT_SIDE("player/gunship-azure-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_AZURE_BANK_LEFT_HIT_SIDE("player/gunship-azure-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_AZURE_LEFT_HIT_SIDE("player/gunship-azure-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_AZURE_STRAIGHT_HIT_SIDE("player/gunship-azure-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_AZURE_RIGHT_HIT_SIDE("player/gunship-azure-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_AZURE_BANK_RIGHT_HIT_SIDE("player/gunship-azure-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),

    GUNSHIP_AMBER_BANK_LEFT_SIDE("player/gunship-amber-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_AMBER_LEFT_SIDE("player/gunship-amber-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_AMBER_STRAIGHT_SIDE("player/gunship-amber-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_AMBER_RIGHT_SIDE("player/gunship-amber-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_AMBER_BANK_RIGHT_SIDE("player/gunship-amber-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_AMBER_BANK_LEFT_HIT_SIDE("player/gunship-amber-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_AMBER_LEFT_HIT_SIDE("player/gunship-amber-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_AMBER_STRAIGHT_HIT_SIDE("player/gunship-amber-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_AMBER_RIGHT_HIT_SIDE("player/gunship-amber-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_AMBER_BANK_RIGHT_HIT_SIDE("player/gunship-amber-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),

    GUNSHIP_VIOLET_BANK_LEFT_SIDE("player/gunship-violet-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_VIOLET_LEFT_SIDE("player/gunship-violet-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_VIOLET_STRAIGHT_SIDE("player/gunship-violet-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_VIOLET_RIGHT_SIDE("player/gunship-violet-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_VIOLET_BANK_RIGHT_SIDE("player/gunship-violet-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_VIOLET_BANK_LEFT_HIT_SIDE("player/gunship-violet-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_VIOLET_LEFT_HIT_SIDE("player/gunship-violet-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_VIOLET_STRAIGHT_HIT_SIDE("player/gunship-violet-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_VIOLET_RIGHT_HIT_SIDE("player/gunship-violet-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_VIOLET_BANK_RIGHT_HIT_SIDE("player/gunship-violet-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),

    GUNSHIP_CHROME_BANK_LEFT_SIDE("player/gunship-chrome-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_CHROME_LEFT_SIDE("player/gunship-chrome-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_CHROME_STRAIGHT_SIDE("player/gunship-chrome-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_CHROME_RIGHT_SIDE("player/gunship-chrome-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_CHROME_BANK_RIGHT_SIDE("player/gunship-chrome-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_CHROME_BANK_LEFT_HIT_SIDE("player/gunship-chrome-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_CHROME_LEFT_HIT_SIDE("player/gunship-chrome-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_CHROME_STRAIGHT_HIT_SIDE("player/gunship-chrome-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_CHROME_RIGHT_HIT_SIDE("player/gunship-chrome-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    GUNSHIP_CHROME_BANK_RIGHT_HIT_SIDE("player/gunship-chrome-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),

    TWIN_BOOM_MILITIA_BANK_LEFT_SIDE("player/twin-boom-militia-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_MILITIA_LEFT_SIDE("player/twin-boom-militia-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_MILITIA_STRAIGHT_SIDE("player/twin-boom-militia-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_MILITIA_RIGHT_SIDE("player/twin-boom-militia-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_MILITIA_BANK_RIGHT_SIDE("player/twin-boom-militia-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_MILITIA_BANK_LEFT_HIT_SIDE("player/twin-boom-militia-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_MILITIA_LEFT_HIT_SIDE("player/twin-boom-militia-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_MILITIA_STRAIGHT_HIT_SIDE("player/twin-boom-militia-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_MILITIA_RIGHT_HIT_SIDE("player/twin-boom-militia-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_MILITIA_BANK_RIGHT_HIT_SIDE("player/twin-boom-militia-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),

    TWIN_BOOM_CORSAIR_BANK_LEFT_SIDE("player/twin-boom-corsair-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_CORSAIR_LEFT_SIDE("player/twin-boom-corsair-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_CORSAIR_STRAIGHT_SIDE("player/twin-boom-corsair-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_CORSAIR_RIGHT_SIDE("player/twin-boom-corsair-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_CORSAIR_BANK_RIGHT_SIDE("player/twin-boom-corsair-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_CORSAIR_BANK_LEFT_HIT_SIDE("player/twin-boom-corsair-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_CORSAIR_LEFT_HIT_SIDE("player/twin-boom-corsair-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_CORSAIR_STRAIGHT_HIT_SIDE("player/twin-boom-corsair-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_CORSAIR_RIGHT_HIT_SIDE("player/twin-boom-corsair-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_CORSAIR_BANK_RIGHT_HIT_SIDE("player/twin-boom-corsair-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),

    TWIN_BOOM_AZURE_BANK_LEFT_SIDE("player/twin-boom-azure-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_AZURE_LEFT_SIDE("player/twin-boom-azure-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_AZURE_STRAIGHT_SIDE("player/twin-boom-azure-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_AZURE_RIGHT_SIDE("player/twin-boom-azure-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_AZURE_BANK_RIGHT_SIDE("player/twin-boom-azure-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_AZURE_BANK_LEFT_HIT_SIDE("player/twin-boom-azure-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_AZURE_LEFT_HIT_SIDE("player/twin-boom-azure-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_AZURE_STRAIGHT_HIT_SIDE("player/twin-boom-azure-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_AZURE_RIGHT_HIT_SIDE("player/twin-boom-azure-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_AZURE_BANK_RIGHT_HIT_SIDE("player/twin-boom-azure-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),

    TWIN_BOOM_AMBER_BANK_LEFT_SIDE("player/twin-boom-amber-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_AMBER_LEFT_SIDE("player/twin-boom-amber-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_AMBER_STRAIGHT_SIDE("player/twin-boom-amber-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_AMBER_RIGHT_SIDE("player/twin-boom-amber-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_AMBER_BANK_RIGHT_SIDE("player/twin-boom-amber-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_AMBER_BANK_LEFT_HIT_SIDE("player/twin-boom-amber-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_AMBER_LEFT_HIT_SIDE("player/twin-boom-amber-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_AMBER_STRAIGHT_HIT_SIDE("player/twin-boom-amber-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_AMBER_RIGHT_HIT_SIDE("player/twin-boom-amber-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_AMBER_BANK_RIGHT_HIT_SIDE("player/twin-boom-amber-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),

    TWIN_BOOM_VIOLET_BANK_LEFT_SIDE("player/twin-boom-violet-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_VIOLET_LEFT_SIDE("player/twin-boom-violet-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_VIOLET_STRAIGHT_SIDE("player/twin-boom-violet-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_VIOLET_RIGHT_SIDE("player/twin-boom-violet-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_VIOLET_BANK_RIGHT_SIDE("player/twin-boom-violet-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_VIOLET_BANK_LEFT_HIT_SIDE("player/twin-boom-violet-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_VIOLET_LEFT_HIT_SIDE("player/twin-boom-violet-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_VIOLET_STRAIGHT_HIT_SIDE("player/twin-boom-violet-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_VIOLET_RIGHT_HIT_SIDE("player/twin-boom-violet-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_VIOLET_BANK_RIGHT_HIT_SIDE("player/twin-boom-violet-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),

    TWIN_BOOM_CHROME_BANK_LEFT_SIDE("player/twin-boom-chrome-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_CHROME_LEFT_SIDE("player/twin-boom-chrome-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_CHROME_STRAIGHT_SIDE("player/twin-boom-chrome-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_CHROME_RIGHT_SIDE("player/twin-boom-chrome-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_CHROME_BANK_RIGHT_SIDE("player/twin-boom-chrome-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_CHROME_BANK_LEFT_HIT_SIDE("player/twin-boom-chrome-bank-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_CHROME_LEFT_HIT_SIDE("player/twin-boom-chrome-left-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_CHROME_STRAIGHT_HIT_SIDE("player/twin-boom-chrome-straight-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_CHROME_RIGHT_HIT_SIDE("player/twin-boom-chrome-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),
    TWIN_BOOM_CHROME_BANK_RIGHT_HIT_SIDE("player/twin-boom-chrome-bank-right-hit-side.png", Draw.PLAYER_H, Draw.PLAYER_W, true),


    /** The four new kits, turned. */

    KIT_CANARDS_BANK_LEFT_SIDE("player/kit-canards-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_CANARDS_LEFT_SIDE("player/kit-canards-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_CANARDS_STRAIGHT_SIDE("player/kit-canards-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_CANARDS_RIGHT_SIDE("player/kit-canards-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_CANARDS_BANK_RIGHT_SIDE("player/kit-canards-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),

    KIT_SCOOP_BANK_LEFT_SIDE("player/kit-scoop-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_SCOOP_LEFT_SIDE("player/kit-scoop-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_SCOOP_STRAIGHT_SIDE("player/kit-scoop-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_SCOOP_RIGHT_SIDE("player/kit-scoop-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_SCOOP_BANK_RIGHT_SIDE("player/kit-scoop-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),

    KIT_MAST_BANK_LEFT_SIDE("player/kit-mast-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_MAST_LEFT_SIDE("player/kit-mast-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_MAST_STRAIGHT_SIDE("player/kit-mast-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_MAST_RIGHT_SIDE("player/kit-mast-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_MAST_BANK_RIGHT_SIDE("player/kit-mast-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),

    KIT_RACK_BANK_LEFT_SIDE("player/kit-rack-bank-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_RACK_LEFT_SIDE("player/kit-rack-left-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_RACK_STRAIGHT_SIDE("player/kit-rack-straight-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_RACK_RIGHT_SIDE("player/kit-rack-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),
    KIT_RACK_BANK_RIGHT_SIDE("player/kit-rack-bank-right-side.png", Draw.PLAYER_H, Draw.PLAYER_W),



    /**
     * Enemy hulls, one faction per level.
     *
     * The three archetypes keep their sizes across every level, so a scout is the same target
     * wherever it is met; only the palette and the build change. Which set spawns comes from
     * {@code mode.Level}.
     */
    L1_SCOUT("level-1/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L1_FIGHTER("level-1/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L1_CRUISER("level-1/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L2_SCOUT("level-2/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L2_FIGHTER("level-2/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L2_CRUISER("level-2/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L3_SCOUT("level-3/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L3_FIGHTER("level-3/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L3_CRUISER("level-3/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L4_SCOUT("level-4/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L4_FIGHTER("level-4/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L4_CRUISER("level-4/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L5_SCOUT("level-5/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L5_FIGHTER("level-5/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L5_CRUISER("level-5/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L6_SCOUT("level-6/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L6_FIGHTER("level-6/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L6_CRUISER("level-6/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L7_SCOUT("level-7/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L7_FIGHTER("level-7/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L7_CRUISER("level-7/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L8_SCOUT("level-8/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L8_FIGHTER("level-8/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L8_CRUISER("level-8/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    /**
     * Level 9's hostiles, drawn pointing left because the level runs sideways.
     *
     * Dimensions transposed to match: the generator turns the art a quarter turn, so the box has
     * to turn with it or the hitbox and the hull disagree. {@code entity.Orientation} then picks
     * the right axis off these for spawning and culling.
     */
    L9_SCOUT("level-9/enemy-scout.png", Draw.SCOUT_H, Draw.SCOUT_W),
    L9_FIGHTER("level-9/enemy-fighter.png", Draw.FIGHTER_H, Draw.FIGHTER_W),
    L9_CRUISER("level-9/enemy-cruiser.png", Draw.CRUISER_H, Draw.CRUISER_W),

    L10_SCOUT("level-10/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L10_FIGHTER("level-10/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L10_CRUISER("level-10/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    // ---- Galaxy 2: Ashfall (levels 11-20) --------------------------------------------
    L11_SCOUT("level-11/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L11_FIGHTER("level-11/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L11_CRUISER("level-11/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L12_SCOUT("level-12/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L12_FIGHTER("level-12/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L12_CRUISER("level-12/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L13_SCOUT("level-13/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L13_FIGHTER("level-13/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L13_CRUISER("level-13/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L14_SCOUT("level-14/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L14_FIGHTER("level-14/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L14_CRUISER("level-14/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L15_SCOUT("level-15/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L15_FIGHTER("level-15/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L15_CRUISER("level-15/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L16_SCOUT("level-16/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L16_FIGHTER("level-16/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L16_CRUISER("level-16/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),


    /** Sunward Dive's hostiles are cut pointing left, so width and height swap. */
    L17_SCOUT("level-17/enemy-scout.png", Draw.SCOUT_H, Draw.SCOUT_W),
    L17_FIGHTER("level-17/enemy-fighter.png", Draw.FIGHTER_H, Draw.FIGHTER_W),
    L17_CRUISER("level-17/enemy-cruiser.png", Draw.CRUISER_H, Draw.CRUISER_W),

    L18_SCOUT("level-18/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L18_FIGHTER("level-18/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L18_CRUISER("level-18/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L19_SCOUT("level-19/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L19_FIGHTER("level-19/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L19_CRUISER("level-19/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L20_SCOUT("level-20/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L20_FIGHTER("level-20/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L20_CRUISER("level-20/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    /** Frost Ring's hostiles are cut pointing left, so width and height swap. */
    L21_SCOUT("level-21/enemy-scout.png", Draw.SCOUT_H, Draw.SCOUT_W),
    L21_FIGHTER("level-21/enemy-fighter.png", Draw.FIGHTER_H, Draw.FIGHTER_W),
    L21_CRUISER("level-21/enemy-cruiser.png", Draw.CRUISER_H, Draw.CRUISER_W),

    L22_SCOUT("level-22/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L22_FIGHTER("level-22/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L22_CRUISER("level-22/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L23_SCOUT("level-23/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L23_FIGHTER("level-23/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L23_CRUISER("level-23/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L24_SCOUT("level-24/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L24_FIGHTER("level-24/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L24_CRUISER("level-24/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L25_SCOUT("level-25/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L25_FIGHTER("level-25/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L25_CRUISER("level-25/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L26_SCOUT("level-26/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L26_FIGHTER("level-26/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L26_CRUISER("level-26/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L27_SCOUT("level-27/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L27_FIGHTER("level-27/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L27_CRUISER("level-27/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L28_SCOUT("level-28/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L28_FIGHTER("level-28/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L28_CRUISER("level-28/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    /** Shatter Drift is the galaxy's second side-on leg, so its hulls turn too. */
    L29_SCOUT("level-29/enemy-scout.png", Draw.SCOUT_H, Draw.SCOUT_W),
    L29_FIGHTER("level-29/enemy-fighter.png", Draw.FIGHTER_H, Draw.FIGHTER_W),
    L29_CRUISER("level-29/enemy-cruiser.png", Draw.CRUISER_H, Draw.CRUISER_W),

    L30_SCOUT("level-30/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L30_FIGHTER("level-30/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L30_CRUISER("level-30/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    // ---- Galaxy 4: Tempest (levels 31-40) --------------------------------------------
    L31_SCOUT("level-31/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L31_FIGHTER("level-31/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L31_CRUISER("level-31/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L32_SCOUT("level-32/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L32_FIGHTER("level-32/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L32_CRUISER("level-32/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L33_SCOUT("level-33/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L33_FIGHTER("level-33/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L33_CRUISER("level-33/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L34_SCOUT("level-34/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L34_FIGHTER("level-34/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L34_CRUISER("level-34/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L35_SCOUT("level-35/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L35_FIGHTER("level-35/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L35_CRUISER("level-35/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L36_SCOUT("level-36/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L36_FIGHTER("level-36/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L36_CRUISER("level-36/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L37_SCOUT("level-37/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L37_FIGHTER("level-37/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L37_CRUISER("level-37/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L38_SCOUT("level-38/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L38_FIGHTER("level-38/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L38_CRUISER("level-38/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    /** Lightning Reach is the galaxy's side-on leg, so its hulls are cut pointing left. */
    L39_SCOUT("level-39/enemy-scout.png", Draw.SCOUT_H, Draw.SCOUT_W),
    L39_FIGHTER("level-39/enemy-fighter.png", Draw.FIGHTER_H, Draw.FIGHTER_W),
    L39_CRUISER("level-39/enemy-cruiser.png", Draw.CRUISER_H, Draw.CRUISER_W),

    L40_SCOUT("level-40/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L40_FIGHTER("level-40/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L40_CRUISER("level-40/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    // ---- Galaxy 5: Null (levels 41-50) -----------------------------------------------
    L41_SCOUT("level-41/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L41_FIGHTER("level-41/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L41_CRUISER("level-41/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L42_SCOUT("level-42/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L42_FIGHTER("level-42/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L42_CRUISER("level-42/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L43_SCOUT("level-43/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L43_FIGHTER("level-43/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L43_CRUISER("level-43/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L44_SCOUT("level-44/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L44_FIGHTER("level-44/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L44_CRUISER("level-44/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    /** Tidal Shear is the galaxy's side-on leg, so its hulls are cut pointing left. */
    L45_SCOUT("level-45/enemy-scout.png", Draw.SCOUT_H, Draw.SCOUT_W),
    L45_FIGHTER("level-45/enemy-fighter.png", Draw.FIGHTER_H, Draw.FIGHTER_W),
    L45_CRUISER("level-45/enemy-cruiser.png", Draw.CRUISER_H, Draw.CRUISER_W),

    L46_SCOUT("level-46/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L46_FIGHTER("level-46/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L46_CRUISER("level-46/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L47_SCOUT("level-47/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L47_FIGHTER("level-47/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L47_CRUISER("level-47/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L48_SCOUT("level-48/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L48_FIGHTER("level-48/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L48_CRUISER("level-48/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L49_SCOUT("level-49/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L49_FIGHTER("level-49/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L49_CRUISER("level-49/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L50_SCOUT("level-50/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L50_FIGHTER("level-50/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L50_CRUISER("level-50/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),


    /**
     * Fallback only. Bosses are animated, so the renderer draws a frame from
     * {@link BossArt} instead of this -- but {@code Entity} requires some sprite, and a visible ship
     * beats a null image if a new draw path ever misses the animation branch.
     */
    BOSS("boss-sentinel/1.png", 210, 162),

    ASTEROID_SMALL("asteroid-small.png", 36, 36),
    ASTEROID_BIG("asteroid-big.png", 58, 58),
    ASTEROID_HUGE("asteroid-huge.png", 92, 92),

    PLAYER_BULLET("PlayProjectile.png", 16, 22),
    ENEMY_BULLET("EnemyProjectile1.png", 16, 22),

    /**
     * The flagship's rocket: the enemy projectile art at heavy-ordnance size.
     *
     * Reused rather than redrawn, as {@link #SHIELD_AURA} reuses the shield pickup. The renderer
     * gives it the hostile halo for free, since nothing fired by an enemy has an owner.
     * ponytail: generate a rocket.png if 1.5x scaling does not read as ordnance.
     */
    BOSS_ROCKET("EnemyProjectile1.png", 24, 34),

    /** A hydra head's fire. The hostile halo is already the right orange, so no new art. */
    FIREBALL("EnemyProjectile1.png", 26, 30),

    /**
     * A hydra head's acid.
     *
     * Bigger than a bolt and its own art, because the ordinary enemy projectile is already bright
     * green: without a distinct read the two are the same object at a glance.
     */
    ACID_BALL("acid-ball.png", 34, 34),

    /** One ring of the Dune Leviathan's body; the renderer trails several behind the maw. */
    WORM_SEGMENT("worm-segment.png", 104, 104),

    /**
     * One ring of the Storm Serpent's body.
     *
     * Its own sprite rather than the Leviathan's because that one is not rotationally symmetric --
     * it carries its bristles down one side, which is right for a worm crossing the screen and
     * wrong for one striking down it. See {@code entity.BurrowingWorm.segmentSprite}.
     */
    STORM_SEGMENT("storm-segment.png", 104, 104),
    /** The rocket pickup's projectile. Was the mega laser's round; the beam replaced it. */
    ROCKET("MegaLaser.png", 26, 40),
    TRI_BULLET_LEFT("triBulletL.png", 18, 24),
    TRI_BULLET_UP("triBulletU.png", 18, 26),
    TRI_BULLET_RIGHT("triBulletR.png", 18, 24),

    /**
     * The three rounds the new weapons fire, and the only generated projectiles in the game.
     *
     * The rest are the author's own; these are drawn because there is no hand-made sheet for them.
     * The scythe is wider than it is tall on purpose -- it is the one shot that crosses the lane
     * rather than running down it, and the silhouette has to say so before the damage does.
     */
    /**
     * What a flagship fires, one round per galaxy.
     *
     * Verdance keeps {@link #ENEMY_BULLET}, the hand-drawn original, so the fight everybody meets
     * first is the one they always met. The other four are generated. See {@code entity.Ordnance}.
     */
    BOSS_EMBER("boss-ember.png", 18, 26),
    BOSS_SHARD("boss-shard.png", 15, 29),
    BOSS_BOLT("boss-bolt.png", 17, 28),
    BOSS_VOID("boss-void.png", 23, 23),

    SCYTHE_BLADE("scythe-blade.png", 96, 32),
    FLAK_PELLET("flak-pellet.png", 14, 14),
    NOVA_SHELL("nova-shell.png", 30, 38),

    PICKUP_TRI_SHOT("pickup-tri-shot.png", 36, 36),
    PICKUP_MEGA_LASER("pickup-mega-laser.png", 36, 36),
    PICKUP_ROCKET("pickup-rocket.png", 36, 36),
    PICKUP_SHIELD("pickup-shield.png", 36, 36),
    PICKUP_HEALTH("pickup-health.png", 36, 36),
    PICKUP_SPEED("pickup-speed.png", 36, 36),
    PICKUP_EXTRA_LIFE("pickup-extra-life.png", 36, 36),
    PICKUP_SCYTHE("pickup-scythe.png", 36, 36),
    PICKUP_FLAK("pickup-flak.png", 36, 36),
    PICKUP_NOVA("pickup-nova.png", 36, 36),

    SHIELD_AURA("pickup-shield.png", 84, 84),

    /**
     * Three backdrop layers per level, scrolled at different rates for parallax depth.
     *
     * Which set is drawn comes from {@code mode.Level}; the renderer never names these directly. Each
     * one is a full arena, so they are the only sprites decoded on demand rather than at startup --
     * three are on screen at a time and twenty-four exist.
     */
    L1_FAR("level-1/far.png"),
    L1_MID("level-1/mid.png"),
    L1_NEAR("level-1/near.png"),

    L2_FAR("level-2/far.png"),
    L2_MID("level-2/mid.png"),
    L2_NEAR("level-2/near.png"),

    L3_FAR("level-3/far.png"),
    L3_MID("level-3/mid.png"),
    L3_NEAR("level-3/near.png"),

    L4_FAR("level-4/far.png"),
    L4_MID("level-4/mid.png"),
    L4_NEAR("level-4/near.png"),

    L5_FAR("level-5/far.png"),
    L5_MID("level-5/mid.png"),
    L5_NEAR("level-5/near.png"),

    L6_FAR("level-6/far.png"),
    L6_MID("level-6/mid.png"),
    L6_NEAR("level-6/near.png"),

    L7_FAR("level-7/far.png"),
    L7_MID("level-7/mid.png"),
    L7_NEAR("level-7/near.png"),

    L8_FAR("level-8/far.png"),
    L8_MID("level-8/mid.png"),
    L8_NEAR("level-8/near.png"),

    /** Level 9's sky tiles horizontally, since that is the axis its level scrolls on. */

    /**
     * Rank insignia for the debrief: four tiers, one to four marks each.
     *
     * Sixteen frames rather than twenty-six, because {@code prefs.Rank} already folds the ladder
     * onto a tier plus a count of marks. Named so {@code engine.DebriefOverlay} can look one up
     * from those two without a switch.
     */
    INSIGNIA_CHEVRONS_1("insignia/chevrons-1.png", 48, 22),
    INSIGNIA_CHEVRONS_2("insignia/chevrons-2.png", 48, 22),
    INSIGNIA_CHEVRONS_3("insignia/chevrons-3.png", 48, 22),
    INSIGNIA_CHEVRONS_4("insignia/chevrons-4.png", 48, 22),

    INSIGNIA_RODS_1("insignia/rods-1.png", 48, 22),
    INSIGNIA_RODS_2("insignia/rods-2.png", 48, 22),
    INSIGNIA_RODS_3("insignia/rods-3.png", 48, 22),
    INSIGNIA_RODS_4("insignia/rods-4.png", 48, 22),

    INSIGNIA_BARS_1("insignia/bars-1.png", 48, 22),
    INSIGNIA_BARS_2("insignia/bars-2.png", 48, 22),
    INSIGNIA_BARS_3("insignia/bars-3.png", 48, 22),
    INSIGNIA_BARS_4("insignia/bars-4.png", 48, 22),

    INSIGNIA_STARS_1("insignia/stars-1.png", 48, 22),
    INSIGNIA_STARS_2("insignia/stars-2.png", 48, 22),
    INSIGNIA_STARS_3("insignia/stars-3.png", 48, 22),
    INSIGNIA_STARS_4("insignia/stars-4.png", 48, 22),

    L9_FAR("level-9/far.png"),
    L9_MID("level-9/mid.png"),
    L9_NEAR("level-9/near.png"),

    L10_FAR("level-10/far.png"),
    L10_MID("level-10/mid.png"),
    L10_NEAR("level-10/near.png"),

    // ---- Galaxy 2: Ashfall (levels 11-20) --------------------------------------------
    L11_FAR("level-11/far.png"),
    L11_MID("level-11/mid.png"),
    L11_NEAR("level-11/near.png"),

    L12_FAR("level-12/far.png"),
    L12_MID("level-12/mid.png"),
    L12_NEAR("level-12/near.png"),

    L13_FAR("level-13/far.png"),
    L13_MID("level-13/mid.png"),
    L13_NEAR("level-13/near.png"),

    L14_FAR("level-14/far.png"),
    L14_MID("level-14/mid.png"),
    L14_NEAR("level-14/near.png"),

    L15_FAR("level-15/far.png"),
    L15_MID("level-15/mid.png"),
    L15_NEAR("level-15/near.png"),

    L16_FAR("level-16/far.png"),
    L16_MID("level-16/mid.png"),
    L16_NEAR("level-16/near.png"),

    L17_FAR("level-17/far.png"),
    L17_MID("level-17/mid.png"),
    L17_NEAR("level-17/near.png"),

    L18_FAR("level-18/far.png"),
    L18_MID("level-18/mid.png"),
    L18_NEAR("level-18/near.png"),

    L19_FAR("level-19/far.png"),
    L19_MID("level-19/mid.png"),
    L19_NEAR("level-19/near.png"),

    L20_FAR("level-20/far.png"),
    L20_MID("level-20/mid.png"),
    L20_NEAR("level-20/near.png"),

    L21_FAR("level-21/far.png"),
    L21_MID("level-21/mid.png"),
    L21_NEAR("level-21/near.png"),
    L22_FAR("level-22/far.png"),
    L22_MID("level-22/mid.png"),
    L22_NEAR("level-22/near.png"),
    L23_FAR("level-23/far.png"),
    L23_MID("level-23/mid.png"),
    L23_NEAR("level-23/near.png"),
    L24_FAR("level-24/far.png"),
    L24_MID("level-24/mid.png"),
    L24_NEAR("level-24/near.png"),
    L25_FAR("level-25/far.png"),
    L25_MID("level-25/mid.png"),
    L25_NEAR("level-25/near.png"),
    L26_FAR("level-26/far.png"),
    L26_MID("level-26/mid.png"),
    L26_NEAR("level-26/near.png"),
    L27_FAR("level-27/far.png"),
    L27_MID("level-27/mid.png"),
    L27_NEAR("level-27/near.png"),
    L28_FAR("level-28/far.png"),
    L28_MID("level-28/mid.png"),
    L28_NEAR("level-28/near.png"),
    L29_FAR("level-29/far.png"),
    L29_MID("level-29/mid.png"),
    L29_NEAR("level-29/near.png"),
    L30_FAR("level-30/far.png"),
    L30_MID("level-30/mid.png"),
    L30_NEAR("level-30/near.png"),

    // ---- Galaxy 4: Tempest (levels 31-40) --------------------------------------------
    L31_FAR("level-31/far.png"),
    L31_MID("level-31/mid.png"),
    L31_NEAR("level-31/near.png"),
    L32_FAR("level-32/far.png"),
    L32_MID("level-32/mid.png"),
    L32_NEAR("level-32/near.png"),
    L33_FAR("level-33/far.png"),
    L33_MID("level-33/mid.png"),
    L33_NEAR("level-33/near.png"),
    L34_FAR("level-34/far.png"),
    L34_MID("level-34/mid.png"),
    L34_NEAR("level-34/near.png"),
    L35_FAR("level-35/far.png"),
    L35_MID("level-35/mid.png"),
    L35_NEAR("level-35/near.png"),
    L36_FAR("level-36/far.png"),
    L36_MID("level-36/mid.png"),
    L36_NEAR("level-36/near.png"),
    L37_FAR("level-37/far.png"),
    L37_MID("level-37/mid.png"),
    L37_NEAR("level-37/near.png"),
    L38_FAR("level-38/far.png"),
    L38_MID("level-38/mid.png"),
    L38_NEAR("level-38/near.png"),
    L39_FAR("level-39/far.png"),
    L39_MID("level-39/mid.png"),
    L39_NEAR("level-39/near.png"),
    L40_FAR("level-40/far.png"),
    L40_MID("level-40/mid.png"),
    L40_NEAR("level-40/near.png"),

    // ---- Galaxy 5: Null (levels 41-50) -----------------------------------------------
    L41_FAR("level-41/far.png"),
    L41_MID("level-41/mid.png"),
    L41_NEAR("level-41/near.png"),
    L42_FAR("level-42/far.png"),
    L42_MID("level-42/mid.png"),
    L42_NEAR("level-42/near.png"),
    L43_FAR("level-43/far.png"),
    L43_MID("level-43/mid.png"),
    L43_NEAR("level-43/near.png"),
    L44_FAR("level-44/far.png"),
    L44_MID("level-44/mid.png"),
    L44_NEAR("level-44/near.png"),
    L45_FAR("level-45/far.png"),
    L45_MID("level-45/mid.png"),
    L45_NEAR("level-45/near.png"),
    L46_FAR("level-46/far.png"),
    L46_MID("level-46/mid.png"),
    L46_NEAR("level-46/near.png"),
    L47_FAR("level-47/far.png"),
    L47_MID("level-47/mid.png"),
    L47_NEAR("level-47/near.png"),
    L48_FAR("level-48/far.png"),
    L48_MID("level-48/mid.png"),
    L48_NEAR("level-48/near.png"),
    L49_FAR("level-49/far.png"),
    L49_MID("level-49/mid.png"),
    L49_NEAR("level-49/near.png"),
    L50_FAR("level-50/far.png"),
    L50_MID("level-50/mid.png"),
    L50_NEAR("level-50/near.png");

    /**
     * Draw sizes shared by a whole family of sprites, so a hull is the same target in every level.
     *
     * A nested holder rather than fields on the enum itself: a constant cannot forward-reference a
     * static of its own class, and the sizes have to be declared before the constants that use them.
     */
    private static final class Draw {
        static final double PLAYER_W = 60;
        static final double PLAYER_H = 64;
        static final double SCOUT_W = 46;
        static final double SCOUT_H = 41;
        static final double FIGHTER_W = 64;
        static final double FIGHTER_H = 60;
        static final double CRUISER_W = 74;
        static final double CRUISER_H = 83;

        private Draw() {
        }
    }

    private final String fileName;
    private final double width;
    private final double height;
    private final boolean decodedOnDemand;

    Sprite(String fileName, double width, double height) {
        this(fileName, width, height, false);
    }

    /** An arena-sized backdrop layer, faulted in on first use. */
    Sprite(String fileName) {
        this(fileName, GameConfig.WIDTH, GameConfig.HEIGHT, true);
    }

    Sprite(String fileName, double width, double height, boolean decodedOnDemand) {
        this.fileName = fileName;
        this.width = width;
        this.height = height;
        this.decodedOnDemand = decodedOnDemand;
    }

    public String resourcePath() {
        String path = "/sprites/" + fileName;
        return path;
    }


    /**
     * Each frame's side-on twin, matched by name.
     *
     * Derived rather than declared: the generator writes a hull's nose-up and turned cuts in one
     * pass, so a hand-written table would only be a second place for them to fall out of step.
     * Anything without a side-on cut maps to itself, which is what lets a caller ask any sprite
     * without checking first.
     */
    private static final Map<Sprite, Sprite> SIDE_CUTS = sideCuts();

    private static Map<Sprite, Sprite> sideCuts() {
        Map<Sprite, Sprite> cuts = new EnumMap<>(Sprite.class);
        for (Sprite sprite : values()) {
            Sprite turned = named(sprite.name() + "_SIDE");
            cuts.put(sprite, turned == null ? sprite : turned);
        }
        return cuts;
    }

    private static Sprite named(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException noSuchFrame) {
            return null;
        }
    }

    /** This frame as cut for a level flown side-on, or this frame where there is no turned cut. */
    public Sprite sideOn() {
        return SIDE_CUTS.get(this);
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    /** Whether {@code Assets.load()} skips this one and leaves it to be decoded on first request. */
    public boolean decodedOnDemand() {
        return decodedOnDemand;
    }
}
