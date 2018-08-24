package sources;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;

public class GameMenu {

    private MenuBar menuBar;
    private BorderPane layout;

    public GameMenu(){
        layout = new BorderPane();
        menuBar = new MenuBar();
    }

    public void addMenu(Menu menu){
        menuBar.getMenus().add(menu);
    }

    public void addMenuItem(Menu menu, MenuItem menuItem){
        menu.getItems().add(menuItem);
    }

    public MenuBar getMenuBar(){
        return menuBar;
    }
}
