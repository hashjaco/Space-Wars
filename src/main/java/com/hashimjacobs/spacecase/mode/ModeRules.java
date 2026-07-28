package com.hashimjacobs.spacecase.mode;

/**
 * What varies between modes, expressed as data.
 *
 * @param playerCount        ships the player(s) control
 * @param spawnEnemies       whether AI ships attack
 * @param spawnAsteroids     whether asteroids drift through
 * @param spawnPowerUps      whether pickups appear
 * @param friendlyFire       whether a player's bullets damage the other player
 * @param lastPlayerStanding true when the round ends once only one player has lives left,
 *                           false when it ends once every player is out
 */
public record ModeRules(
        int playerCount,
        boolean spawnEnemies,
        boolean spawnAsteroids,
        boolean spawnPowerUps,
        boolean friendlyFire,
        boolean lastPlayerStanding) {
}
