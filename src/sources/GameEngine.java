package sources;

import javafx.geometry.Bounds;
import javafx.scene.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class GameEngine {

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
    private static List<Bullet> enemyBullets = new ArrayList<>();
    private List<Asteroid> asteroids = new ArrayList<>();
    private List<Animation> animations = new ArrayList<>();

    private List<GameObject> objects = new ArrayList<>();

    private List<GameObject> deadAsteroids = new ArrayList<>();
    private List<GameObject> deadEnemies = new ArrayList<>();
    private List<GameObject> deadBullets = new ArrayList<>();
    private List<GameObject> deadEnemyBullets = new ArrayList<>();

    private ArrayList<GameObject> returnObjects1 = new ArrayList<>();
    private ArrayList<GameObject> returnObjects2 = new ArrayList<>();

    private QuadTree<Bullet> bulletTree1 = new QuadTree<>(1, new Rectangle(0, 0, (int) W, (int) H));
    private QuadTree<Bullet> bulletTree2 = new QuadTree<>(1, new Rectangle(0, 0, (int) W, (int) H));
    private QuadTree<Bullet> enemyBulletTree = new QuadTree<>(1, new Rectangle(0, 0, (int) W, (int) H));

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

    private boolean isPaused;

    // HashMaps containing sprites and sounds
    private Sprites sprites;
    private Sound sounds;
    private AnimationTimer timer;

    private Canvas intro = new Canvas(W, H);
    private GraphicsContext introContext = intro.getGraphicsContext2D();

    private Canvas canvas = new Canvas(W, H);
    private Canvas canvas2 = new Canvas(W, H);
    private GraphicsContext graphicsContext = canvas.getGraphicsContext2D();
    private GraphicsContext graphicsContext2 = canvas2.getGraphicsContext2D();
    private Background background, background2;
    private MediaPlayer backgroundMusic;


    private Scene scene;

    public GameEngine() {
        scene = new Scene(createContent(), W, H, Color.BLACK);
        scene.getStylesheets().add(GameEngine.class.getResource("../styles.css").toExternalForm());
    }


    // Creates all static content for both screens
    private Parent createContent() {
        backgroundMusic = Sound.sounds.get("background music");
        playMedia(backgroundMusic);
        isPaused = false;
        sounds = new Sound();
        sprites = new Sprites();
        background = new Background(sprites.getValue("background"));
        background2 = new Background(background);
        root = new Pane();
        splitPane = new SplitPane();
        firstPane = new ScrollPane();
        secondPane = new ScrollPane();
        root.setPrefSize(996, H);
        root.setMaxSize(996, H);
        splitPane.setPrefSize(W, H);
        firstPane.setPrefSize(W, H);
        firstPane.setPrefViewportHeight(H);
        firstPane.setPrefViewportWidth(W / 2);
        secondPane.setPrefSize(W, H);
        secondPane.setPrefViewportHeight(H);
        secondPane.setPrefViewportWidth(W / 2);
        secondPane.setHvalue(W / 2);
        // Remove scroll bars
        firstPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        firstPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        secondPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        secondPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        //Create player objects (players), Health bar, Lives count
        initPlayers();
        addGameObject(firstPlayer, W/4, 600);
        addGameObject(secondPlayer, W-W/4, 600);
        intro.setWidth(W / 2);
        intro.setHeight(H / 2);
        intro.setTranslateX(W / 4);
        intro.setTranslateY(H / 4);
        firstPane.setContent(canvas);
        secondPane.setContent(canvas2);
        splitPane.getItems().add(firstPane);
        splitPane.getItems().add(secondPane);
        root.getChildren().add(splitPane);

        // Main Game Loop
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {

                onUpdate(); // This function updates the game on every tick
            }
        };
        timer.start();
        return root;
    }


    // Updates the board by eliminating any dead objects due to collision, adds enemies to the board at random, and changes position of objects as necessary
    private void onUpdate() {
        doMovement();
        handleBoundaries();
        handleCollisions();
        spawnAsteroid();
        spawnEnemy();

        for (Enemy enemy : enemies) {
            if (Math.sqrt(Math.pow(enemy.getX() - firstPlayer.getX(), 2) + (Math.pow(enemy.getY() - firstPlayer.getY(), 2))) < (Math.sqrt(Math.pow(enemy.getX() - secondPlayer.getX(), 2) + (Math.pow(enemy.getY() - secondPlayer.getY(), 2))))) {
                enemy.engage(firstPlayer);
                if (enemy.isFireReady()){
                    if (enemy.getX() <= firstPlayer.getX()+5 || enemy.getX() >= firstPlayer.getX()-5){
                        Bullet bullet = new Bullet(new ImageView(sprites.getValue("enemyBullet")));
                        Enemy.shoot(enemy,bullet); enemyBullets.add(bullet);
                    }
                }
            } else {
                enemy.engage(secondPlayer);
                if (enemy.isFireReady()){
                    Bullet bullet = new Bullet(new ImageView(sprites.getValue("enemyBullet")));
                    if (enemy.getX() <= secondPlayer.getX()+5 || enemy.getX() >= secondPlayer.getX()-5){
                        Enemy.shoot(enemy,bullet); enemyBullets.add(bullet);
                    }
                }
            }
            if (enemy.getY() < H / 2) {
                enemy.setY(enemy.getY() + 1);
            }

            if (enemy.getTimeBetweenFire() < 50) {
                enemy.setFireReady(false);
                enemy.setTimeBetweenFire(enemy.getTimeBetweenFire() + 1);
            } else {
                enemy.setFireReady(true);
                enemy.setTimeBetweenFire(0);
            }
        }

        // Update time between fire for each player to control fire rate
        players.forEach(player -> {
            if (player.getTimeBetweenFire() < 30) {
                player.setFireReady(false);
                player.setTimeBetweenFire(player.getTimeBetweenFire() + 1);
            } else {
                player.setFireReady(true);
                //player.setTimeBetweenFire(0);
            }
        });

        // move bullets vertically and set dead when collides with edge of pane
        bullets.forEach(bullet -> {
            if (bullet.getY() >= 8) { bullet.setVelocity(new Point2D(bullet.getVelocity().getX(), bullet.getVelocity().getY() - 8)); bullet.update(); }
            else { bullet.setDead(); }
        });

        enemyBullets.forEach(bullet -> {
            if (bullet.getY() < Attributes.H) { bullet.setY(bullet.getY()+8); bullet.update(); }
            else { bullet.setDead(); }
        });

        // Remove dead objects from their lists to be disposed of
        bullets.removeIf(Bullet::isDead);
        enemies.removeIf(Enemy::isDead);
        players.removeIf(Player::isDead);
        asteroids.removeIf(Asteroid::isDead);
        enemyBullets.removeIf(Bullet::isDead);


        // update position and state of each game object on each tick
        enemies.forEach(Enemy::update);
        players.forEach(Player::update);

        try {
            for (Asteroid asteroid : asteroids) {
                asteroid.update();
            }
        } catch (Exception e) {
            System.out.println(e);
        }

        ensureVisible(firstPane, firstPlayer.getView());
        ensureVisible(secondPane, secondPlayer.getView());
        render(graphicsContext, 1);
        render(graphicsContext2, 2);
        ticks += 1;

        System.out.println("The size of enemy bullet array is " + enemyBullets.size());

    }


    // moves viewport with ship
    private void ensureVisible(ScrollPane pane, Node node) {
        Bounds viewport = pane.getViewportBounds();
        double contentHeight = pane.getContent().getBoundsInLocal().getHeight();
        double contentWidth = pane.getContent().getBoundsInLocal().getWidth();
        double nodeMinY = node.getBoundsInParent().getMinY();
        double nodeMaxY = node.getBoundsInParent().getMaxY();
        double nodeMinX = node.getBoundsInParent().getMinX();
        double nodeMaxX = node.getBoundsInParent().getMaxX();
        double viewportMinY = (contentHeight - viewport.getHeight()) * pane.getVvalue();
        double viewportMaxY = viewportMinY + viewport.getHeight();
        double viewportMinX = (contentWidth - viewport.getWidth()) * pane.getHvalue();
        double viewportMaxX = viewportMinX + viewport.getWidth();
        if (nodeMinY < viewportMinY) {
            pane.setVvalue(nodeMinY / (contentHeight - viewport.getHeight()));
        } else if (nodeMaxY > viewportMaxY) {
            pane.setVvalue((nodeMaxY - viewport.getHeight()) / (contentHeight - viewport.getHeight()));
        }
        if (nodeMinX < viewportMinX) {
            pane.setHvalue(nodeMinX / (contentWidth - viewport.getWidth()));
        } else if (nodeMaxX > viewportMaxX) {
            pane.setHvalue((nodeMaxX - viewport.getWidth()) / (contentWidth - viewport.getWidth()));
        }

    }

    // movement controls
    protected synchronized void doMovement() {

        scene.setOnKeyPressed((KeyEvent e) -> {
            if (e.getCode() == KeyCode.UP) {
                if (secondPlayer.getView().getTranslateY() >= 5)
                    secondPlayer.moveForwards();
                else
                    secondPlayer.getView().setTranslateY(0);
            }
            if (e.getCode() == KeyCode.DOWN) {
                if (secondPlayer.getView().getTranslateY() < H - 140)
                    secondPlayer.moveBackwards();
                else
                    secondPlayer.getView().setTranslateY(H - 140);
            }
            if (e.getCode() == KeyCode.LEFT) {
                if (secondPlayer.getView().getTranslateX() > 0) {
                    if (secondPlayer.getAnimations() == 0)
                        secondPlayer.setImage(sprites.getValue("player2left"));
                    else {
                        secondPlayer.setImage(sprites.getValue("player2LeftDamage"));
                    }
                    secondPlayer.moveLeft();
                } else secondPlayer.getView().setTranslateX(0);
            }
            if (e.getCode() == KeyCode.RIGHT) {
                if (secondPlayer.getView().getTranslateX() <= W - 50) {
                    secondPlayer.setImage(sprites.getValue("player2right"));
                    secondPlayer.moveRight();
                } else secondPlayer.getView().setTranslateX(W - 50);
            }

            if (e.getCode() == KeyCode.W) {
                if (firstPlayer.getView().getTranslateY() >= 5)
                    firstPlayer.moveForwards();
                else
                    firstPlayer.getView().setTranslateY(0);
            }
            if (e.getCode() == KeyCode.S) {
                if (firstPlayer.getView().getTranslateY() < H - 140)
                    firstPlayer.moveBackwards();
                else
                    firstPlayer.getView().setTranslateY(H - 140);
            }
            if (e.getCode() == KeyCode.A) {
                if (firstPlayer.getView().getTranslateX() >= 5) {
                    firstPlayer.setImage(sprites.getValue("player1left"));
                    firstPlayer.moveLeft();
                } else
                    firstPlayer.getView().setTranslateX(0);

            }
            if (e.getCode() == KeyCode.D) {
                if (firstPlayer.getView().getTranslateX() < W - 50) {
                    firstPlayer.setImage(sprites.getValue("player1right"));
                    firstPlayer.moveRight();
                } else firstPlayer.getView().setTranslateX(W - 50);
            }

            // Bullets
            if (e.getCode() == KeyCode.COMMA) {
                fireIfReady(secondPlayer, player2bullets);
            }
            if (e.getCode() == KeyCode.SHIFT) {
                fireIfReady(firstPlayer, player1bullets);
            }

            // Pause Menu
            if (e.getCode() == KeyCode.ESCAPE) {
                if(isPaused==false) {
                    timer.stop(); isPaused=true; pauseMedia(backgroundMusic);
                }
                else {
                    timer.start(); isPaused=false;
                }
            }

        });

        scene.setOnKeyReleased((KeyEvent e) -> {
            if (e.getCode() == KeyCode.UP) {

                secondPlayer.setVelocity(new Point2D(0, 0));
            }
            if (e.getCode() == KeyCode.DOWN) {

                secondPlayer.setVelocity(new Point2D(0, 0));
            }
            if (e.getCode() == KeyCode.LEFT) {
                secondPlayer.setImage(sprites.getValue("player2straight"));
                secondPlayer.setVelocity(new Point2D(0, 0));
            }
            if (e.getCode() == KeyCode.RIGHT) {
                secondPlayer.setImage(sprites.getValue("player2straight"));
                secondPlayer.setVelocity(new Point2D(0, 0));
            }
            if (e.getCode() == KeyCode.A) {
                firstPlayer.setImage(sprites.getValue("player1straight"));
                firstPlayer.setVelocity(new Point2D(0, 0));
            }
            if (e.getCode() == KeyCode.D) {
                firstPlayer.setImage(sprites.getValue("player1straight"));
                firstPlayer.setVelocity(new Point2D(0, 0));
            }
            if (e.getCode() == KeyCode.W) {

                firstPlayer.setVelocity(new Point2D(0, 0));
            }
            if (e.getCode() == KeyCode.S) {
                firstPlayer.setVelocity(new Point2D(0, 0));
            }
        });
    }

    // fires bullet if enough time has elapsed between fire
    public void fireIfReady(Player gameObject, List<Bullet> somebullets) {
        if (gameObject.isFireReady()) {
            switch(gameObject.getFirePowerLevel()) {
                case 0: Bullet bullet = new Bullet(new ImageView(sprites.getValue("bullet")));
                    shoot(gameObject, bullet);
                    somebullets.add(bullet);
                    playMedia(new MediaPlayer(new Media(new File("/Users/hashimjacobs/OneDrive/Projects/SpaceWars/src/sources/Sounds/lazer.wav").toURI().toString())));
                    break;
                case 1:
            }
        }
    }

    // Instantiates player player objects, set initial velocity, and add to map
    public void initPlayers() {
        firstPlayer = new Player(new ImageView(sprites.getValue("player1straight")));
        firstPlayer.setVelocity(new Point2D(0, 0));
        firstPlayer.setHb(10, 20);
        firstPlayer.setImage(sprites.getValue("player1straight"));
        firstPlayer.setInitPos(270, 600);
        firstPlayer.setHealth(100);

        secondPlayer = new Player(new ImageView(sprites.getValue("player1straight")));
        secondPlayer.setVelocity(new Point2D(0, 0));
        secondPlayer.setHb(870, 20);
        secondPlayer.setImage(sprites.getValue("player2straight"));
        secondPlayer.setInitPos(682, 600);
        secondPlayer.setHealth(100);
        try {
            players.add(firstPlayer);
            players.add(secondPlayer);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    // resets player after death
    public void resetPlayer(Player player) {
        player.getView().setTranslateX(player.getInitX());
        player.getView().setTranslateY(player.getInitY());
        player.setHealth(100);
        player.setLives(player.getLives() - 1);
    }


    // Adds a bullet object to the scene
    public void shoot(Player player, Bullet bullet) {
        bullets.add(bullet);
        bullet.setImage(sprites.getValue("bullet"));
        bullet.getView().setTranslateX(player.getView().getTranslateX() + 20);
        bullet.getView().setTranslateY(player.getView().getTranslateY());
    }

    // Adds an enemy to the scene
    public void spawnEnemy() {

        if (enemyTotal < 5) {
            int chance = (int) (Math.random() * 1000) + 1;
            if (chance < 5) {
                Enemy enemy = new Enemy(new ImageView(sprites.getValue("enemy")));
                enemy.setImage(sprites.getValue("enemy"));
                enemy.setX((int) (Math.random() * W) + 1);
                //addGameObject(enemy,enemy.getX(),0);
                enemies.add(enemy);
                enemyTotal += 1;
            }
        }
    }

    public void spawnAsteroid() {
        int chance = (int) (Math.random() * 1000) + 1;
        if (chance < 10) {
            Asteroid asteroid;
            if (chance >= 0 && chance <= 5) {
                asteroid = new Asteroid(new ImageView(sprites.getValue("asteroid")));
                asteroid.setImage(sprites.getValue("asteroid"));
                asteroid.setVelocity(new Point2D((Math.random() * -2) + 1, (Math.random() * 2) + 3));
                asteroid.setX((int) (Math.random() * W));
                asteroid.setDamage(10);
                asteroid.setHealth(20);
            } else {
                asteroid = new Asteroid(new ImageView(sprites.getValue("bigAsteroid")));
                asteroid.setImage(sprites.getValue("bigAsteroid"));
                asteroid.setVelocity(new Point2D((Math.random() * -2) + 1, (Math.random() * 1) + 2));
                asteroid.getView().setTranslateX((int) (Math.random() * W));
                asteroid.setDamage(15);
                asteroid.setHealth(50);
            }
            asteroids.add(asteroid);
            asteroidTotal += 1;
        }
    }

    // render player screen
    public void render(GraphicsContext gc, int player) {
        Bounds viewport;
        gc.clearRect(W / 4, H / 4, W / 2, H / 2);
        switch (player) {
            case 1:
                double contentWidth;
                double viewportMinX;
                double viewportMaxX;
                viewport = firstPane.getViewportBounds();
                contentWidth = firstPane.getContent().getBoundsInLocal().getWidth();
                viewportMinX = (contentWidth - viewport.getWidth()) * firstPane.getHvalue();
                viewportMaxX = viewportMinX + viewport.getWidth();
                scrollBackground(gc);
                gc.setFill(Color.DARKVIOLET);
                gc.setFont(Font.font(20));
                gc.fillText("Player 1", viewportMinX + 20, 20);
                gc.fillText("Lives: " + firstPlayer.getLives(), viewportMinX + 20, 70);
                if (firstPlayer.isColliding() && firstPlayer.getAnimations() == 0) {
                    gc.setFill(Color.LIMEGREEN);
                } else {
                    gc.setFill(Color.RED);
                    firstPlayer.setAnimations(firstPlayer.getAnimations() - 1);
                }
                gc.fillRoundRect(viewportMinX + 20, firstPlayer.getHbY() + 5, firstPlayer.getHealth(), 20, 10, 10);
                gc.setFill(Color.GOLD);
                gc.fillText("SCORE", viewportMaxX - 105, 20);
                gc.setStroke(Color.GOLD);
                gc.strokeRoundRect(viewportMaxX - 120, firstPlayer.getHbY() + 5, 100, 50, 5, 5);
                gc.setFont(Font.font("Actor", 25));
                gc.setFill(Color.WHITE);
                switch (firstPlayer.getScoreDigs()) {
                    case 1:
                        gc.fillText(("" + firstPlayer.getScore() + ""), viewportMaxX - 75, firstPlayer.getHbY() + 38, 30);
                        break;
                    case 2:
                        gc.fillText(("" + firstPlayer.getScore() + ""), viewportMaxX - 80, firstPlayer.getHbY() + 38, 35);
                        break;
                    case 3:
                        gc.fillText(("" + firstPlayer.getScore() + ""), viewportMaxX - 90, firstPlayer.getHbY() + 38, 45);
                        break;
                    case 4:
                        gc.fillText(("" + firstPlayer.getScore() + ""), viewportMaxX - 105, firstPlayer.getHbY() + 38, 60);
                        break;
                    case 5:
                        gc.fillText(("" + firstPlayer.getScore() + ""), viewportMaxX - 125, firstPlayer.getHbY() + 38, 75);
                        break;
                    case 6:
                        gc.fillText(("" + firstPlayer.getScore() + ""), viewportMaxX - 150, firstPlayer.getHbY() + 38, 55);
                        break;
                }
                break;

            case 2:
                viewport = secondPane.getViewportBounds();
                contentWidth = secondPane.getContent().getBoundsInLocal().getWidth();
                viewportMinX = (contentWidth - viewport.getWidth()) * secondPane.getHvalue();
                viewportMaxX = viewportMinX + viewport.getWidth();
                scrollBackground(gc);
                gc.setFill(Color.DARKVIOLET);
                gc.setFont(Font.font(20));
                gc.fillText("Lives: " + secondPlayer.getLives(), viewportMaxX - 120, 70);
                gc.fillText("Player 2", viewportMaxX - 120, 20);
                if (secondPlayer.isColliding() && secondPlayer.getAnimations() == 0) {
                    gc.setFill(Color.LIMEGREEN);
                } else {
                    gc.setFill(Color.RED);
                    secondPlayer.setAnimations(secondPlayer.getAnimations() - 1);
                }
                gc.fillRoundRect(viewportMaxX - 120, secondPlayer.getHbY() + 5, secondPlayer.getHealth(), 20, 10, 10);
                gc.setFill(Color.GOLD);
                gc.setStroke(Color.GOLD);
                gc.fillText("SCORE", viewportMinX + 32, 20);
                gc.strokeRoundRect(viewportMinX + 20, secondPlayer.getHbY() + 5, 100, 50, 5, 5);
                gc.setFont(Font.font("Actor", 25));
                gc.setFill(Color.WHITE);
                switch (secondPlayer.getScoreDigs()) {
                    case 1:
                        gc.fillText(("" + secondPlayer.getScore() + ""), viewportMinX + 60, secondPlayer.getHbY() + 38, 30);
                        break;
                    case 2:
                        gc.fillText(("" + secondPlayer.getScore() + ""), viewportMinX + 35, secondPlayer.getHbY() + 38, 35);
                        break;
                    case 3:
                        gc.fillText(("" + secondPlayer.getScore() + ""), viewportMinX + 30, secondPlayer.getHbY() + 38, 45);
                        break;
                    case 4:
                        gc.fillText(("" + secondPlayer.getScore() + ""), viewportMinX + 25, secondPlayer.getHbY() + 38, 60);
                        break;
                    case 5:
                        gc.fillText(("" + secondPlayer.getScore() + ""), viewportMaxX - 125, secondPlayer.getHbY() + 38, 75);
                        break;
                    case 6:
                        gc.fillText(("" + secondPlayer.getScore() + ""), viewportMaxX - 150, secondPlayer.getHbY() + 38, 55);
                        break;
                }
                break;
        }
        gc.drawImage(firstPlayer.getImage(), firstPlayer.getX(), firstPlayer.getY());
        gc.drawImage(secondPlayer.getImage(), secondPlayer.getX(), secondPlayer.getY());
        //spawnAsteroid();

        bullets.forEach(bullet -> {
            gc.drawImage(bullet.getImage(), bullet.getX(), bullet.getY());
        });

        enemyBullets.forEach(bullet -> {
            gc.drawImage(bullet.getImage(), bullet.getX(), bullet.getY());
        });

        asteroids.forEach(asteroid -> {
            if (asteroid.getY() >= Attributes.H || asteroid.getX() >= Attributes.W || asteroid.getX() <= 0){
                asteroid.setDead();
                //deadAsteroids.add(asteroid);
            }
            gc.drawImage(asteroid.getImage(), asteroid.getX(), asteroid.getY() + 8);
        });


        enemies.forEach(enemy -> {
            gc.drawImage(enemy.getImage(), enemy.getX(), enemy.getY());
        });

        animations.forEach(animation -> {
            if (!animation.isFinished()){
                System.out.println("more files exist");
                animation.setCurrentTime(animation.getCurrentTime()+1);
                if (animation.getCurrentTime()%animation.getDuration()/animation.listOfImages().size()==0)
                    gc.drawImage(animation.animate(),animation.getxLocation(),animation.getyLocation());
            }
            else {
                //animations.remove(animation);
                animation.setDead();
            }
        });



        //asteroids.removeAll(deadAsteroids);
    }

    public void handleBoundaries(){
        bullets.forEach(bullet -> {
            if (bullet.getY()<=0){
                bullet.setDead();
                deadBullets.add(bullet);
            }
        });
        enemyBullets.forEach(bullet -> {
            if(bullet.getY()>=Attributes.H){
                bullet.setDead();
                deadEnemyBullets.add(bullet);
            }
        });
        deadBullets.forEach(bullet -> {
            player1bullets.remove(bullet);
            player2bullets.remove(bullet);
        });
        bullets.removeAll(deadBullets);
    }

    // handles all collisions
    public void handleCollisions() {
        int playerIndex = 1;
        enemyBulletTree.clear();
        bulletTree1.clear();
        bulletTree2.clear();
        playerTree.clear();
        enemyTree.clear();
        asteroidTree.clear();
        deadAsteroids.clear();
        deadBullets.clear();
        deadEnemies.clear();
        returnObjects1.clear();
        returnObjects2.clear();

        for (Asteroid asteroid : asteroids) asteroidTree.insert(asteroid);
        for (Enemy enemy : enemies) enemyTree.insert(enemy);
        for (Bullet bullet : player1bullets) bulletTree1.insert(bullet);
        for (Bullet bullet : player2bullets) bulletTree2.insert(bullet);
        for (Player player : players) playerTree.insert(player);
        for (Bullet bullet : enemyBullets) enemyBulletTree.insert(bullet);



        for (Player player : players) {
            asteroidTree.retrieve(returnObjects1, player);

            for (GameObject asteroid : returnObjects1) {
                if (player.intersects(asteroid)) {
                    player.setHealth(player.getHealth() - asteroid.getDamage());
                    if (player.getHealth() <= 0)
                        resetPlayer(player);
                    playMedia(new MediaPlayer(new Media(new File("/Users/hashimjacobs/OneDrive/Projects/SpaceWars/src/sources/Sounds/explosion_x.wav").toURI().toString())));
                    deadAsteroids.add(asteroid);
                    asteroid.setDead();
                    player.setColliding(true);
                    player.setAnimations(3);
                }
            }
            returnObjects1.clear();
            System.out.println("______");
            enemyTree.retrieve(returnObjects1, player);

            for (GameObject enemy : returnObjects1){
                if (player.intersects(enemy)) {
                    player.setHealth(player.getHealth() - 30);
                    if (player.getHealth() <= 0)
                        resetPlayer(player);
                    playMedia(new MediaPlayer(new Media(new File("/Users/hashimjacobs/OneDrive/Projects/SpaceWars/src/sources/Sounds/explosion_x.wav").toURI().toString())));
                    deadEnemies.add(enemy);
                    enemy.setDead();
                    player.setColliding(true);
                    player.setAnimations(3);
                }
            }
            returnObjects1.clear();

            enemyBulletTree.retrieve(returnObjects1, player);

            for (GameObject bullet : returnObjects1){
                if (player.intersects(bullet)){
                    player.setHealth(player.getHealth() - 5);
                    if (player.getHealth() <= 0)
                        resetPlayer(player);
                    playMedia(new MediaPlayer(new Media(new File("/Users/hashimjacobs/OneDrive/Projects/SpaceWars/src/sources/Sounds/explosion_x.wav").toURI().toString())));
                    deadEnemyBullets.add(bullet);
                    bullet.setDead();
                    player.setColliding(true);
                    player.setAnimations(3);
                }
            }
            // End player collision checks
        }
        returnObjects1.clear();

        asteroidCollisions(player1bullets, firstPlayer);
        asteroidCollisions(player2bullets, secondPlayer);
        enemyCollisions(player1bullets,firstPlayer);
        enemyCollisions(player2bullets,secondPlayer);


        System.out.println("*****");
    }

    private void enemyCollisions(List<Bullet> playerbullets, Player player) {
        playerbullets.forEach(bullet -> {
            enemyTree.retrieve(returnObjects1, bullet);
            for (GameObject enemy : returnObjects1) {
                if (bullet.intersects(enemy)) {
                    bullet.setDead();
                    deadBullets.add(bullet);
                    enemy.setHealth(enemy.getHealth()-10);
                    if(enemy.getHealth() <= 0){
                        player.setScore(player.getScore() + 20);
                        enemy.setDead(); deadEnemies.add(enemy);
                        player.setEnemiesKilled(player.getEnemiesKilled() + 1);
                        animations.add(new Animation("/Users/hashimjacobs/OneDrive/Projects/SpaceWars/src/sources/Sprites/explosion2 sheet",enemy,4));
                    }
                }
            }
            returnObjects1.clear();
            asteroids.removeAll(deadAsteroids);
            bullets.removeAll(deadBullets);
            playerbullets.removeAll(deadBullets);
        });
    }

    private void asteroidCollisions(List<Bullet> playerBullets, Player player) {
        playerBullets.forEach(bullet -> {
            asteroidTree.retrieve(returnObjects1, bullet);
            for (GameObject asteroid : returnObjects1) {
                if (bullet.intersects(asteroid)) {
                    bullet.setDead();
                    deadBullets.add(bullet);
                    player.setScore(player.getScore() + 10);
                    asteroid.setDead();deadAsteroids.add(asteroid);
                    player.setAsteroidsDestroyed(player.getAsteroidsDestroyed() + 1);
                    animations.add(new Animation("/Users/hashimjacobs/OneDrive/Projects/SpaceWars/src/sources/Sprites/explosion",asteroid,3));
                }
            }
            returnObjects1.clear();
            asteroids.removeAll(deadAsteroids);
            bullets.removeAll(deadBullets);
            playerBullets.removeAll(deadBullets);
        });
    }


    // Scroll background
    private void scrollBackground(GraphicsContext gc) {
        if (background1Y != 0) {
            gc.drawImage(sprites.getValue("background"), 0, background1Y += 3, W, H);
            gc.drawImage(sprites.getValue("background"), 0, background2Y += 3, W, H);
        } else {
            background2Y = background1Y;
            background1Y = -H;
            gc.drawImage(sprites.getValue("background"), 0, background1Y += 3, W, H);
            gc.drawImage(sprites.getValue("background"), 0, background2Y += 3, W, H);
        }
    }

    // Adds a game object to the scene
    public void addGameObject(GameObject object, double x, double y) {
        object.getView().setTranslateX(x);
        object.getView().setTranslateY(y);
        root.getChildren().add(object.getView());
    }


    // control soundFX
    public  void playMedia(MediaPlayer mp) {
        //mp.setAutoPlay(true);
        mp.play();
        mp.setOnEndOfMedia(new Runnable() {
            @Override
            public void run() {
                mp.dispose();
            }
        });
    }

    public void pauseMedia(MediaPlayer mp){
        mp.pause();
    }

    public AnimationTimer getTimer(){
        return this.timer;
    }

    // return scene
    public Scene getScene() {
        return scene;
    }


}
