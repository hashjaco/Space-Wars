package sources;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Player extends GameObject {

    private int lives;
    private int score;
    private double initX;
    private double initY;
    private double hbX;
    private double hbY;
    private int timeBetweenFire;
    private int firePowerLevel;
    private int enemiesKilled;
    private int asteroidsDestroyed;

    private Image image;



    private List<Bullet> liveBullets;
    private List<PowerUp> powerUps;
    private QuadTree<Bullet> bulletQuadTree;

    private boolean fireReady;



    public Player(Node view){
        super(view);
        health = 100;
        lives = 3;
        fireReady = true;
        score = 0;
        asteroidsDestroyed = 0;
        enemiesKilled = 0;
        timeBetweenFire = 0;
        firePowerLevel = 0;
        liveBullets = new ArrayList<>();
        powerUps = new ArrayList<>();
        bulletQuadTree = new QuadTree<>(1, new Rectangle());
            this.setVelocity(new Point2D(0, 0));
            this.setHb(10, 20);
            this.setInitPos(270, 600);
            this.setHealth(100);
    }

    public Image getImage() {
        return image;
    }

    public void setImage(Image image) {
        this.image = image;
    }

    // Return health bar y coordinate
    public double getHbY() {
        return hbY;
    }

    // Set health bar coordinates
    public void setHb(double hbX, double hbY) {
        this.hbX = hbX;
        this.hbY = hbY;
    }

    // Set player score
    public void setScore(int newScore){
        this.score = newScore;
    }

    // Get player score
    public int getScore(){
        return this.score;
    }

    public int getScoreDigs(){
        int digits;
        if (this.score > 999999){
            return 7;
        }
        else if (this.score > 99999) digits = 6;
        else if (this.score > 9999) digits = 5;
        else if (this.score > 999) digits = 4;
        else if (this.score > 99) digits = 3;
        else if (this.score > 9) digits = 2;
        else digits = 1;
        return digits;
    }

    // Returns beginning x coordinate
    public double getInitX(){
        return this.initX;
    }

    // Returns beginning y coordinate
    public double getInitY(){
        return this.initY;
    }

    // Sets initial x and y coordinates
    public void setInitPos(double initX, double initY){
        this.initX = initX;
        this.initY = initY;
    }

    // Returns number of lives
    public int getLives(){
        return this.lives;
    }

    // Sets number of lives
    public void setLives(int lives){
        this.lives = lives;
    }

    // Returns true if enough time has lapsed between shots
    public boolean isFireReady(){
        return fireReady;
    }

    // Sets true if enough time has lapsed between shots
    public void setFireReady(boolean ready){
        fireReady = ready;
    }

    // Modifies the time between player shots
    public void setTimeBetweenFire(int timeBetweenFire){
        this.timeBetweenFire = timeBetweenFire;
    }

    // Returns the time between player shots
    public int getTimeBetweenFire() {
        return this.timeBetweenFire;
    }

    // Returns the player's current fire level for weapon upgrades
    public int getFirePowerLevel() {
        return firePowerLevel;
    }

    // Modifies the player's fire level for weapon upgrades
    public void setFirePowerLevel(int firePowerLevel) {
        this.firePowerLevel = firePowerLevel;
    }

    // Returns number of enemies killed
    public int getEnemiesKilled(){
        return enemiesKilled;
    }

    // Sets the number of enemies killed
    public void setEnemiesKilled(int enemiesKilled){
        this.enemiesKilled = enemiesKilled;
    }

    // Return number of asteroids destroyed
    public int getAsteroidsDestroyed() {
        return asteroidsDestroyed;
    }

    // Set the number of asteroids destroyed
    public void setAsteroidsDestroyed(int asteroidsDestroyed) {
        this.asteroidsDestroyed = asteroidsDestroyed;
    }

    // Returns the list of live bullets
    public List<Bullet> getLiveBullets() {
        return liveBullets;
    }

    // Adds new bullet to list of live bullets
    public void addLiveBullet(Bullet bullet){ this.liveBullets.add(bullet); }


}
