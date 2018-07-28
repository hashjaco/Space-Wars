package sample;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.image.ImageView;

import java.util.ArrayList;


public class Enemy extends GameObject {

    private Point2D enemySight;
    private int value;

    private ArrayList<Bullet> bullets;

    public Enemy(Node view){
        super(view);
        health = 50;
        bullets = new ArrayList<>();
    }

    public void engage(Player player) {
        if (player.getX() > this.getX()) {
            this.setX(this.getX() + 2);
        }
        else if (player.getX() < this.getX()){
            this.setX(this.getX() - 2);
        }
        else {
            this.shoot(this.addBullet());
        }
    }

    public void shoot(Bullet bullet) {
        this.bullets.add(bullet);
        bullet.setImage(Sprites.sprites.get("bullet"));
        bullet.setX(this.getX() - this.getImage().getWidth()/2);
        bullet.setY(this.getY() + this.getImage().getHeight());
    }

    public Bullet addBullet(){
        Bullet bullet = new Bullet(new ImageView(Sprites.sprites.get("enemyBullet")));
        bullets.add(bullet);
        return bullet;
    }

    public void removeBullet(Bullet bullet){
        bullets.remove(bullet);
    }

}
