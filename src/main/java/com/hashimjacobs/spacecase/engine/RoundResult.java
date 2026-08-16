package com.hashimjacobs.spacecase.engine;

import java.util.List;

import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;

/**
 * How a round ended.
 *
 * @param mode                the mode that was played
 * @param winningPlayerNumber 1 or 2 in battle mode; 0 when there is no winner to name
 * @param scores              final score per player, in player order
 * @param wavesSurvived       waves cleared across the whole run before it ended
 * @param level               level being fought when the round ended
 * @param loop                pass through the run it ended on, counting from one
 */
public record RoundResult(
        GameMode mode,
        int winningPlayerNumber,
        List<Integer> scores,
        int wavesSurvived,
        Level level,
        int loop) {

    public static RoundResult of(World world, SpawnDirector director) {
        List<PlayerShip> players = world.players();
        int winner = resolveWinner(world, players);
        List<Integer> scores = players.stream().map(PlayerShip::score).toList();
        RoundResult result = new RoundResult(world.mode(), winner, scores,
                director.wavesSurvived(), director.level(), director.loop());
        return result;
    }

    private static int resolveWinner(World world, List<PlayerShip> players) {
        if (!world.rules().lastPlayerStanding()) {
            return 0;
        }
        for (PlayerShip player : players) {
            if (!player.isOut()) {
                return player.playerNumber();
            }
        }
        return 0;
    }

    /** Highest score across the players, which is what a co-op or solo run is judged on. */
    public int bestScore() {
        int best = scores.stream().mapToInt(Integer::intValue).max().orElse(0);
        return best;
    }
}
