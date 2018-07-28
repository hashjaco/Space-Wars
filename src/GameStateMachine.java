package sample;

public interface GameStateMachine {
    void init();
    void cleanUp();

    void pause();
    void resume();

    void handle();
    void update();
    void render();
}
