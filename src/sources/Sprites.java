package sources;

import java.util.HashMap;
import javafx.scene.image.Image;


public class Sprites {

    protected static final HashMap<String,Image> sprites = new HashMap();
    private final Image enemy, boss, asteroid, asteroid2;
    private final Image player1leftDamage, player1left,player1slightL, player1straightDamage, player1straight, player1slightR, player1rightDamage, player1right, player2leftDamage, player2left,player2slightL, player2straightDamage, player2straight,player2slightR, player2RightDamage, player2right;
    private final Image bullet;
    private final Image enemyBullet;
    private final Image background;
    private final Image megaBullet;
    private final Image health;
    private final Image speed;
    private final Image powerup1;

    public Sprites(){
        bullet = new Image("sources/Sprites/PlayProjectile.png",20,20,true,false);
        player1leftDamage = new Image("sources/Sprites/shootaLeftDamage.png",60,60,true,true);
        player1left = new Image("sources/Sprites/shootaLeft.png",60,60,true,true);
        player1slightL = new Image("sources/Sprites/daShootasSlightLeft.png",60,80,true,true);
        player1straightDamage = new Image("sources/Sprites/daShootaStraightDamage.png",60,60,true,true);
        player1straight = new Image("sources/Sprites/daShootaStraight.png",60,80,true,true);
        player1slightR = new Image("sources/Sprites/daShootasSlightRight.png",60,80,true,true);
        player1rightDamage = new Image("sources/Sprites/daShootasRightDamage.png",60,60,true,true);
        player1right = new Image("sources/Sprites/daShootasRight.png",60,60,true,true);
        player2leftDamage = new Image("sources/Sprites/shootaLeftDamage.png",60,60,true,true);
        player2left = new Image("sources/Sprites/shootaLeft.png",60,60,true,true);
        player2straightDamage = new Image("sources/Sprites/daShootaStraightDamage.png",60,60,true,true);
        player2slightL = new Image("sources/Sprites/daShootasSlightLeft.png",60,80,true,true);
        player2straight = new Image("sources/Sprites/daShootaStraight.png",60,80,true,true);
        player2slightR = new Image("sources/Sprites/daShootasSlightRight.png",60,80,true,true);
        player2RightDamage = new Image("sources/Sprites/daShootasRightDamage.png",60,60,true,true);
        player2right = new Image("sources/Sprites/daShootasRight.png",60,60,true,true);
        enemy = new Image("sources/Sprites/daShootasBossKill.png",80,80,true,true);
        boss = new Image("sources/Sprites/daShootasBossKill.png",100,100,true,false);
        powerup1 = new Image("sources/Sprites/MegaLaser.png",40,40,true,false);
        background = new Image("sources/Sprites/spaceBackground.gif",992,862,false,true);
        megaBullet = new Image("sources/Sprites/MegaLaser.png",20,20,true,false);
        enemyBullet = new Image("sources/Sprites/EnemyProjectile1.png",20,20,true,false);
        health = new Image("sources/Sprites/shield.png",40,40,true,false);
        speed = new Image("sources/Sprites/redbull.png",40,40,true,false);
        asteroid = new Image("sources/Sprites/aNuttaAsteroid.png", 40,40,true,false);
        asteroid2 = new Image("sources/Sprites/asteroid.png", 40,40,true,false);
        sprites.put("enemy",enemy);
        sprites.put("boss", boss);
        sprites.put("player1LeftDamage",player1leftDamage);
        sprites.put("player1left", player1left);
        sprites.put("player1StraightDamage", player1straightDamage);
        sprites.put("player1slightL", player1slightL);
        sprites.put("player1straight", player1straight);
        sprites.put("player1slightR", player1slightR);
        sprites.put("player1RightDamage", player1rightDamage);
        sprites.put("player1right", player1right);
        sprites.put("player2LeftDamage", player2leftDamage);
        sprites.put("player2left", player2left);
        sprites.put("player2slightL", player2slightL);
        sprites.put("player2straight", player2straight);
        sprites.put("player2slightR", player2slightR);
        sprites.put("player2right", player2right);
        sprites.put("bullet", bullet);
        sprites.put("background", background);
        sprites.put("megalaser", megaBullet);
        sprites.put("health", health);
        sprites.put("crack",speed);
        sprites.put("megalaserPU", powerup1);
        sprites.put("bigAsteroid",asteroid);
        sprites.put("asteroid", asteroid2);
        sprites.put("enemyBullet", enemyBullet);
    }

    public Image getValue(String type){
        return sprites.get(type);
    }

}//ends sprite class

