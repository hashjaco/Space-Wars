package sources;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.image.Image;

public class PowerUp extends GameObject {

    int xCoor, yCoor, level;
    Image image;

    public PowerUp(Node view) {
        super(view);
        this.xCoor = 0;
        this.yCoor = 0;
    }

    public PowerUp(Node view, int xCoor, int yCoor) {
        super(view);
        this.xCoor = xCoor;
        this.yCoor = yCoor;
    }

    public Image releasePowerUp(){
        this.level = (int) (Math.random() + 5);
        this.view.setTranslateX(this.xCoor);
        this.view.setTranslateY(this.yCoor);
        this.setVelocity(new Point2D(0,2));
        switch(level){
            case 1: this.image = Sprites.sprites.get("megalaserPU");
            case 2: this.image = Sprites.sprites.get("triBulletPU");
            case 3: this.image = Sprites.sprites.get("armor");
            case 4: this.image = Sprites.sprites.get("healthPU");
        }
        return this.image;
    }


}
