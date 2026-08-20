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
    /** The rocket pickup's projectile. Was the mega laser's round; the beam replaced it. */
    ROCKET("MegaLaser.png", 26, 40),
    TRI_BULLET_LEFT("triBulletL.png", 18, 24),
    TRI_BULLET_UP("triBulletU.png", 18, 26),
    TRI_BULLET_RIGHT("triBulletR.png", 18, 24),

    PICKUP_TRI_SHOT("pickup-tri-shot.png", 36, 36),
    PICKUP_MEGA_LASER("pickup-mega-laser.png", 36, 36),
    PICKUP_ROCKET("pickup-rocket.png", 36, 36),
    PICKUP_SHIELD("pickup-shield.png", 36, 36),
    PICKUP_HEALTH("pickup-health.png", 36, 36),
    PICKUP_SPEED("pickup-speed.png", 36, 36),
    PICKUP_EXTRA_LIFE("pickup-extra-life.png", 36, 36),

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
    L20_NEAR("level-20/near.png");

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
