package sources;

public class GameState {
    private State gameState;

    public GameState(){
        gameState = null;
    }

    public GameState(State gameState){
        this.gameState = gameState;
    }

    public State getGameState() {
        return gameState;
    }

    public void setGameState(State gameState) {
        this.gameState = gameState;
    }

}
