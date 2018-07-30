package sources;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;

/**
     *
     * @author hashimjacobs
     */
    public class GameObject {
    protected Node view;

    protected Image image;


    protected int timeBetweenFire = 0;
    protected int firePowerLevel = 1;
    protected int health = 0;
    protected int damage;
    private int animations;
    protected double hbY;

    protected boolean dead = false;
    private boolean fireReady;
    protected boolean colliding;

    protected Point2D velocity = new Point2D(0, 0);


    protected GameObject(Node view){
        this.view = view;
    }

    // return x-coordinate in fourth quadrant
    protected double getX(){
        return view.getTranslateX();
    }

    // return y-coordinate in fourth quadrant
    protected double getY(){
        return view.getTranslateY();
    }

    // sets x and y coordinates
    protected void setX(double x){
        this.view.setTranslateX(x);
    }

    protected void setY(double y){
        this.view.setTranslateY(y);
    }

    // Returns current amount of health
    protected int getHealth(){
        return this.health;
    }

    // Modifies the amount of health
    protected void setHealth(int health){
        this.health = health;
    }

    // return true if collision occurs
    protected boolean intersects(GameObject other){
        return view.getBoundsInParent().intersects(other.view.getBoundsInParent());
    }


    // updates game object
    public void update() {
        view.setTranslateX(view.getTranslateX() + velocity.getX());
        view.setTranslateY(view.getTranslateY() + velocity.getY());
    }

    // Sets the velocity, following vector on 2D plane, of game object
    protected void setVelocity(Point2D velocity) {
        this.velocity = velocity;
    }

    // returns the image of game object to be drawn onto canvas
    protected Image getImage() {
        return this.image;
    }

    // Sets the image of game object to be drawn onto canvas
    protected void setImage(Image image) {
        this.image = image;
    }

    // returns view of game object
    protected Node getView() {
        return view;
    }

    // returns true if object is dead
    protected boolean isDead() {
        return dead;
    }

    // Sets living state of game object
    protected void setDead() {
        dead = true;
    }

    // Moves game object left
    protected void moveLeft(){
        if (this.getX() > 4) {
            setVelocity(new Point2D(getVelocity().getX() - 4, getVelocity().getY()));
        }
    }

    // Moves game object right
    protected void moveRight(){
        if (this.view.getTranslateX() < 986) {
            setVelocity(new Point2D(getVelocity().getX() + 4, getVelocity().getY()));
        }
    }

    // Moves game object forwards
    protected void moveForwards() {
        setVelocity(new Point2D(getVelocity().getX(),getVelocity().getY()-4));
    }

    // Moves game object backwards
    protected void moveBackwards() {
        setVelocity(new Point2D(getVelocity().getX(), getVelocity().getY()+4));
    }

    // Returns players current velocity
    protected Point2D getVelocity() {
        double RX_COOR = 0;
        double RY_COOR = 0;
        return new Point2D(RY_COOR, RX_COOR);
    }

    // Returns true if player is ready to fire, or enough time between shots has passed
    protected boolean isFireReady() {
        return fireReady;
    }

    // Set ready to fire
    protected void setFireReady(boolean ready) {
        fireReady = ready;
    }

    // Modify time between fire to keep track
    protected void setTimeBetweenFire(int timeBetweenFire) {
        this.timeBetweenFire = timeBetweenFire;
    }

    // Returns current time between fire
    protected int getTimeBetweenFire() {
        return this.timeBetweenFire;
    }

    // return game object's weapon level
    protected int getFirePowerLevel() {
        return firePowerLevel;
    }

    // sets game object's weapon level
    protected void setFirePowerLevel(int firePowerLevel) {
        this.firePowerLevel = firePowerLevel;
    }

    // Sets health bar coordinates
    protected void setHb(double hbX, double hbY) {
        this.hbY = hbY;
    }

    // Return player health bar y coordinate
    protected double getHbY() {
        return hbY;
    }

    protected int getDamage(){
        return damage;
    }

    protected void setDamage(int damage){
        this.damage = damage;
    }

    protected boolean isColliding(){
        return !colliding;
    }

    protected void setColliding(boolean colliding){
        this.colliding = !colliding;
    }

    protected void setAnimations(int animations){
        this.animations = animations;
    }

    protected int getAnimations(){
        return animations;
    }

    protected void insert(Pane root, double x, double y){
        this.getView().setTranslateX(x);
        this.getView().setTranslateY(y);
        root.getChildren().add(this.getView());
    }

}
