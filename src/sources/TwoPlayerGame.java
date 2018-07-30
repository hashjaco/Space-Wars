package sources;

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

    private QuadTree<Bullet> bulletTree1 = new QuadTree<>(1, new Rectangle(0, 0, (int) Attributes.W, (int) Attributes.H));
    private QuadTree<Bullet> bulletTree2 = new QuadTree<>(1, new Rectangle(0, 0, (int) Attributes.W, (int) Attributes.H));
    private QuadTree<Asteroid> asteroidTree = new QuadTree<>(1, new Rectangle(0, 0, (int) Attributes.W, (int) Attributes.H));
    private QuadTree<Player> playerTree = new QuadTree<>(1, new Rectangle(0, 0, (int) Attributes.W, (int) Attributes.H));
    private QuadTree<Enemy> enemyTree = new QuadTree<>(1, new Rectangle(0, 0, (int) Attributes.W, (int) Attributes.H));

    private QuadTree<Enemy> objectTree = new QuadTree<>(1, new Rectangle(0, 0, (int) Attributes.W, (int) Attributes.H));


    // players
    private Player firstPlayer;
    private Player secondPlayer;



    // enemy and asteroid count
    private int enemyTotal = 0;
    private int asteroidTotal = 0;


    private AnimationTimer timer;

    private Canvas intro = new Canvas(Attributes.W, Attributes.H);
    private GraphicsContext introContext = intro.getGraphicsContext2D();

    private Canvas canvas;
    private Canvas canvas2;
    private GraphicsContext graphicsContext;
    private GraphicsContext graphicsContext2;
    private Background background, background2;

    public TwoPlayerGame(){
        splitPane = new SplitPane(firstPane,secondPane);
        canvas = new Canvas(Attributes.W, Attributes.H);
        canvas2 = new Canvas(Attributes.W, Attributes.H);
        graphicsContext = canvas.getGraphicsContext2D();
        graphicsContext2 = canvas2.getGraphicsContext2D();
        background = new Background();
        background2 = new Background();
    }

    public List<GraphicsContext> getGraphicsContexts(){
        ArrayList<GraphicsContext> graphicsContexts = new ArrayList<>();
        graphicsContexts.add(graphicsContext);
        graphicsContexts.add(graphicsContext2);
        return graphicsContexts;
    }
}


