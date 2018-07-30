package sources;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

public class Game implements GameStateMachine {
    private Pane root;
    private ScrollPane pane;

    private List<Enemy> enemies;
    private List<PowerUp> powerups;
    private List<Asteroid> asteroids;

    private List<GameObject> deadAsteroids;
    private List<GameObject> deadEnemies;
    private List<GameObject> deadBullets;
    private List<GameObject> deadPowerups;

    private ArrayList<GameObject> returnObjects;
    private QuadTree<Asteroid> asteroidTree;

    private QuadTree<Enemy> enemyTree;
    private Player player;

    // Time measures
    private int ticks;
    private int seconds;
    private int minute;

    // enemy and asteroid count
    private int enemyTotal;
    private int asteroidTotal;

    private Canvas canvas;
    private GraphicsContext graphicsContext;
    private Background background, background2;

    private boolean isPaused;

    public Game(){
        root = new Pane();
        root.setPrefSize(Attributes.W, Attributes.H);
        root.setMaxSize(Attributes.W, Attributes.H);
        pane = new ScrollPane();
        pane.setPrefSize(Attributes.W, Attributes.H);
        pane.setPrefViewportHeight(Attributes.H);
        pane.setPrefViewportWidth(Attributes.W / 2);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        enemies = new ArrayList<>();
        powerups = new ArrayList<>();
        asteroids = new ArrayList<>();

        deadAsteroids = new ArrayList<>();
        deadEnemies = new ArrayList<>();
        deadBullets = new ArrayList<>();
        deadPowerups = new ArrayList<>();

        returnObjects = new ArrayList<>();
        asteroidTree = new QuadTree<>(1, new Rectangle(0, 0, (int) Attributes.W, (int) Attributes.H));

        enemyTree = new QuadTree<>(1, new Rectangle(0, 0, (int) Attributes.W, (int) Attributes.H));
        player = new Player(null);

        isPaused = false;

        // Time measures
        ticks = 1;
        seconds = 60;
        minute = 3600;

        // enemy and asteroid count
        enemyTotal = 0;
        asteroidTotal = 0;

        canvas = new Canvas(Attributes.W, Attributes.H);
        graphicsContext = canvas.getGraphicsContext2D();
        background = new Background();
        background2 = background;
    }

    public ScrollPane getPane(){
        return this.pane;
    }

    public void setBackground(Image image){
        this.background.setImage(image);
    }

    public Background getBackground(){
        return background;
    }

    public GraphicsContext getGraphicsContext() {
        return graphicsContext;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public void addTick(){
        this.ticks++;
    }

    public int getTicks() {
        return ticks;
    }

    public int getSeconds() {
        return this.ticks/seconds;
    }

    public int getMinute(){
        return this.ticks/minute;
    }

    public ArrayList<GameObject> getReturnObjects() {
        return returnObjects;
    }

    public void setAsteroidTotal(int asteroidTotal) {
        this.asteroidTotal = asteroidTotal;
    }

    public int getAsteroidTotal() {
        return asteroidTotal;
    }

    public void resetAsteroidTotal(){
        asteroidTotal = 0;
    }

    public int getEnemyTotal() {
        return enemyTotal;
    }
    public void addAsteroid(Asteroid asteroid){
        asteroids.add(asteroid);
    }

    public List<Asteroid> getAsteroids() {
        return asteroids;
    }

    public void addEnemy(Enemy enemy){
        enemies.add(enemy);
    }

    public List<Enemy> getEnemies() {
        return enemies;
    }

    public void clearEnemies(){
        enemies.clear();
    }

    public void addDeadAsteroid(GameObject asteroid){
        deadAsteroids.add(asteroid);
    }

    public List<GameObject> getDeadAsteroids() {
        return deadAsteroids;
    }

    public void clearDeadAsteroids(){
        deadAsteroids.clear();
    }

    public void addDeadBullet(GameObject bullet){
        deadBullets.add(bullet);
    }

    public List<GameObject> getDeadBullets() {
        return deadBullets;
    }

    public void clearDeadBullets(){
        deadBullets.clear();
    }

    public void addDeadEnemy(GameObject enemy){
        deadEnemies.add(enemy);
    }

    public List<GameObject> getDeadEnemies() {
        return deadEnemies;
    }

    public void clearDeadEnemies(){
        deadEnemies.clear();
    }

    public void addDeadPowerUp(GameObject powerup){
        deadPowerups.add(powerup);
    }
    public List<GameObject> getDeadPowerups() {
        return deadPowerups;
    }

    public void clearDeadPowerUps(){
        deadPowerups.clear();
    }

    public Pane getRoot() {
        return root;
    }

    public void addPowerUp(PowerUp powerup){
        powerups.add(powerup);
    }
    public List<PowerUp> getPowerUps() {
        return powerups;
    }

    public Player getPlayer() {
        return player;
    }

    public QuadTree<Enemy> getEnemyTree() {
        return enemyTree;
    }

    public QuadTree<Asteroid> getAsteroidTree() {
        return asteroidTree;
    }

    public void setEnemyTotal(int enemyTotal) {
        this.enemyTotal = enemyTotal;
    }

    public void setPaused(boolean paused) {
        isPaused = paused;
    }

    public void setPlayerImage(Image image) {
        this.player.setImage(image);
    }

    @Override
    public void init() {
        
    }

    @Override
    public void cleanUp() {

    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void handle() {

    }

    @Override
    public void update() {

    }

    @Override
    public void render() {

    }
}
