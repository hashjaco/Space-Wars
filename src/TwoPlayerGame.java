package sample;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Pane;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TwoPlayerGame extends Game {
    private Pane root;
    private SplitPane splitPane;
    private ScrollPane firstPane;
    private ScrollPane secondPane;

    // Define dynamic lists for controlling multiple objects on screen
    private List<Bullet> bullets = new ArrayList<>();
    private List<Enemy> enemies = new ArrayList<>();
    private List<Player> players = new ArrayList<>();
    private List<PowerUp> powerups = new ArrayList<>();
    private List<Bullet> player1bullets = new ArrayList<>();
    private List<Bullet> player2bullets = new ArrayList<>();
    private List<Asteroid> asteroids = new ArrayList<>();

    private List<GameObject> objects = new ArrayList<>();

    private List<GameObject> deadAsteroids = new ArrayList<>();
    private List<GameObject> deadEnemies = new ArrayList<>();
    private List<GameObject> deadBullets = new ArrayList<>();

    private ArrayList<GameObject> returnObjects1 = new ArrayList<>();

    private QuadTree<Bullet> bulletTree1 = new QuadTree<>(1, new Rectangle(0, 0, (int) W, (int) H));
    private QuadTree<Bullet> bulletTree2 = new QuadTree<>(1, new Rectangle(0, 0, (int) W, (int) H));
    private QuadTree<Asteroid> asteroidTree = new QuadTree<>(1, new Rectangle(0, 0, (int) W, (int) H));
    private QuadTree<Player> playerTree = new QuadTree<>(1, new Rectangle(0, 0, (int) W, (int) H));
    private QuadTree<Enemy> enemyTree = new QuadTree<>(1, new Rectangle(0, 0, (int) W, (int) H));

    private QuadTree<Enemy> objectTree = new QuadTree<>(1, new Rectangle(0, 0, (int) W, (int) H));


    // Naming player players
    private Player firstPlayer;
    private Player secondPlayer;

    // Time measures
    private int ticks = 1;
    private int seconds = 60;
    private int minute = 3600;

    // enemy and asteroid count
    private int enemyTotal = 0;
    private int asteroidTotal = 0;

    // Window height and width
    private static double H = 864;
    private static double W = 996;

    // background position
    private double background1Y = -H;
    // background copy position (for scrolling)
    private double background2Y = 0;

    // HashMaps containing sprites and sounds
    private Sprites sprites;
    private Sound sounds;
    private AnimationTimer timer;

    private javafx.scene.canvas.Canvas intro = new javafx.scene.canvas.Canvas(W, H);
    private GraphicsContext introContext = intro.getGraphicsContext2D();

    private javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(W, H);
    private javafx.scene.canvas.Canvas canvas2 = new Canvas(W, H);
    private GraphicsContext graphicsContext = canvas.getGraphicsContext2D();
    private GraphicsContext graphicsContext2 = canvas2.getGraphicsContext2D();
    private Background background, background2;
}
