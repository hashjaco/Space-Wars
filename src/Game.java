package sample;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.canvas.Canvas;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Game {
    private Pane root;
    private ScrollPane firstPane;

    private List<Bullet> bullets = new ArrayList<>();
    private List<Enemy> enemies = new ArrayList<>();
    private List<PowerUp> powerups = new ArrayList<>();
    private List<Asteroid> asteroids = new ArrayList<>();

    private List<GameObject> deadAsteroids = new ArrayList<>();
    private List<GameObject> deadEnemies = new ArrayList<>();
    private List<GameObject> deadBullets = new ArrayList<>();

    private ArrayList<GameObject> returnObjects = new ArrayList<>();
    private QuadTree<Asteroid> asteroidTree = new QuadTree<>(1, new Rectangle(0, 0, (int) Attributes.W, (int) Attributes.H));

    private QuadTree<Enemy> enemyTree = new QuadTree<>(1, new Rectangle(0, 0, (int) Attributes.W, (int) Attributes.H));
    private Player firstPlayer;

    // Time measures
    private int ticks = 1;
    private int seconds = 60;
    private int minute = 3600;

    // enemy and asteroid count
    private int enemyTotal = 0;
    private int asteroidTotal = 0;

    private Canvas canvas = new Canvas(Attributes.W, Attributes.H);
    private GraphicsContext graphicsContext = canvas.getGraphicsContext2D();
    private Background background, background2;

    public Game(){
        root = new Pane();
        root.setPrefSize(Attributes.W, Attributes.H);
        root.setMaxSize(Attributes.W, Attributes.H);
        firstPane = new ScrollPane();
        firstPane.setPrefSize(Attributes.W, Attributes.H);
        firstPane.setPrefViewportHeight(Attributes.H);
        firstPane.setPrefViewportWidth(Attributes.W / 2);
        firstPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        firstPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);


        bullets = new ArrayList<>();
        enemies = new ArrayList<>();
        powerups = new ArrayList<>();
        asteroids = new ArrayList<>();

        deadAsteroids = new ArrayList<>();
        deadEnemies = new ArrayList<>();
        deadBullets = new ArrayList<>();

        returnObjects = new ArrayList<>();
        asteroidTree = new QuadTree<>(1, new Rectangle(0, 0, (int) Attributes.W, (int) Attributes.H));

        enemyTree = new QuadTree<>(1, new Rectangle(0, 0, (int) Attributes.W, (int) Attributes.H));
        firstPlayer = new Player(null);


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




}
