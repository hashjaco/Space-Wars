package sources;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class Controller {

    private BooleanProperty UP_Pressed = new SimpleBooleanProperty(false);
    private BooleanProperty DOWN_Pressed = new SimpleBooleanProperty(false);
    private BooleanProperty RIGHT_Pressed = new SimpleBooleanProperty(false);
    private BooleanProperty LEFT_Pressed = new SimpleBooleanProperty(false);
    private BooleanProperty SHIFT_Pressed = new SimpleBooleanProperty(false);
    private BooleanProperty SPACE_Pressed = new SimpleBooleanProperty(false);
    private BooleanProperty A_Pressed = new SimpleBooleanProperty(false);
    private BooleanProperty W_Pressed = new SimpleBooleanProperty(false);
    private BooleanProperty D_Pressed = new SimpleBooleanProperty(false);
    private BooleanProperty S_Pressed = new SimpleBooleanProperty(false);
    private BooleanProperty ESCAPE_Pressed = new SimpleBooleanProperty(false);
    private BooleanBinding upAndLeftPressed = UP_Pressed.and(LEFT_Pressed);
    private BooleanBinding upAndRightPressed = UP_Pressed.and(RIGHT_Pressed);
    private BooleanBinding downAndLeftPressed = DOWN_Pressed.and(LEFT_Pressed);
    private BooleanBinding downAndRightPressed = DOWN_Pressed.and(RIGHT_Pressed);
    private BooleanBinding W_and_A_Pressed = W_Pressed.and(A_Pressed);
    private BooleanBinding W_and_D_Pressed = W_Pressed.and(D_Pressed);
    private BooleanBinding S_and_A_Pressed = S_Pressed.and(A_Pressed);
    private BooleanBinding S_and_D_Pressed = S_Pressed.and(D_Pressed);

    public Controller(){}

    public void setActions(KeyEvent keyEvent){
        if (keyEvent.getCode() == KeyCode.W)
            W_Pressed.set(true);
        if (keyEvent.getCode() == KeyCode.S)
            S_Pressed.set(true);
        if (keyEvent.getCode() == KeyCode.A)
            A_Pressed.set(true);
        if (keyEvent.getCode() == KeyCode.D)
            D_Pressed.set(true);
        if (keyEvent.getCode() == KeyCode.SHIFT)
            SHIFT_Pressed.set(true);

        if (keyEvent.getCode() == KeyCode.UP)
            UP_Pressed.set(true);
        if (keyEvent.getCode() == KeyCode.DOWN)
            DOWN_Pressed.set(true);
        if (keyEvent.getCode() == KeyCode.LEFT)
            LEFT_Pressed.set(true);
        if (keyEvent.getCode() == KeyCode.RIGHT)
            RIGHT_Pressed.set(true);
        if (keyEvent.getCode() == KeyCode.SPACE)
            SPACE_Pressed.set(true);

        // Pause Menu
        if (keyEvent.getCode() == KeyCode.ESCAPE){
            ESCAPE_Pressed.set(true);
        }
    }
}
