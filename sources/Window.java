package sources;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;

public class Window {
    private Pane root;
    private ScrollPane pane;
    private int width, height;


    public Window(){
        root = new Pane();
        pane = new ScrollPane();
        root.getChildren().add(pane);
    }

    public Window(int width, int height){
        root = new Pane();
        root.setPrefSize(width, height);
        root.setMaxSize(width, height);
        pane = new ScrollPane();
        pane.setPrefSize(Attributes.W, Attributes.H);
        pane.setPrefViewportHeight(Attributes.H);
        pane.setPrefViewportWidth(Attributes.W / 2);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        root.getChildren().add(pane);
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public Pane getPane() {
        return root;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setWidth(int width) {
        this.width = width;
    }
}
