package sources;

import javafx.scene.Node;
import javafx.scene.image.Image;

public class Asteroid extends Enemy {
    public Asteroid(Node view) {
        super(view);
    }

    public Image getSmallAsteroid(){
        return Sprites.sprites.get("smallAsteroid");
    }

    public Image getMediumAsteroid(){
        return Sprites.sprites.get("bigAsteroid");
    }

    public Image getLargeAsteroid(){
        return Sprites.sprites.get("hugeAsteroid");
    }

}
