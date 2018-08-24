package sources;

import javafx.scene.Node;

public class MegaBullet extends PowerUp {
    public MegaBullet(Node view, int xCoor, int yCoor) {
        super(view, xCoor, yCoor);
        this.image = Sprites.sprites.get("megalaser");
    }
}
