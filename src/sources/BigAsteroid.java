package sources;

import javafx.scene.Node;
import javafx.scene.image.Image;

public class BigAsteroid extends Asteroid {
    public BigAsteroid(Node view) {
        super(view);
        this.image = new Image("Sprites/asteroid.png", 80, 80,true,true);
    }
}
