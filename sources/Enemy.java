package sources;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.image.ImageView;

import java.util.ArrayList;
import java.util.List;


public class Enemy extends GameObject {


    private ArrayList<Bullet> bullets;

    public Enemy(Node view) {
        super(view);
        health = 50;
        bullets = new ArrayList<>();
    }

    public void engage(Player player) {
        if (player.getX() > this.getX()) {
            this.setX(this.getX() + 1);
        } else if (player.getX() < this.getX()) {
            this.setX(this.getX() - 1);
        }
    }

    public static void shoot(Enemy enemy, Bullet bullet) {
        bullet.setImage(Sprites.sprites.get("enemyBullet"));
        bullet.getView().setTranslateX(enemy.getView().getTranslateX() + 30);
        bullet.getView().setTranslateY(enemy.getView().getTranslateY() + 30);
        bullet.setVelocity(new Point2D(0,3));
    }

    public Bullet addBullet() {
        Bullet bullet = new Bullet(new ImageView(Sprites.sprites.get("enemyBullet")));
        bullets.add(bullet);
        return bullet;
    }

    public ArrayList<Bullet> getBullets(){
        return bullets;
    }
}
