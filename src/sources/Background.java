package sources;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;

public class Background {
    private Image image;
    private double x, y;
    private int scrollVelocity;

    public Background(){
        image = null;
        x = 0;
        y = 0;
    }

    public Background(Image image){
        this.image = image;
        x = 0;
        y = 0;
    }

    public Background(Image image, double x, double y){
        this.image = image;
        this.x = x;
        this.y = y;
    }

    public Background(Background background){
        this.image = background.image;
    }

    public void setImage(Image image){
        this.image = image;
    }

    public Image getImage(){
        return image;
    }

    public void setCoor(double x, double y){
        this.x = x;
        this.y = y;
    }

    public double getX(){
        return x;
    }

    public double getY(){
        return y;
    }

    // Scroll background
    public void scrollBackgroundV(GraphicsContext gc, Background background1, Background background2, int yVel) {
        if (background1.y != 0) {
            gc.drawImage(Sprites.sprites.get("background"), 0, background1.y += yVel, Attributes.W, Attributes.H);
            gc.drawImage(Sprites.sprites.get("background"), 0, background2.y += yVel, Attributes.W, Attributes.H);
        } else {
            background2.y = background1.y;
            background1.y = -Attributes.H;
            gc.drawImage(Sprites.sprites.get("background"), 0, background1.y += yVel, Attributes.W, Attributes.H);
            gc.drawImage(Sprites.sprites.get("background"), 0, background2.y += yVel, Attributes.W, Attributes.H);
        }
    }

    public void scrollBackgroundH(GraphicsContext gc, Background background1, Background background2, int xVel){
        if (background1.x != 0) {
            gc.drawImage(Sprites.sprites.get("background"), 0, background1.x += xVel, Attributes.W, Attributes.H);
            gc.drawImage(Sprites.sprites.get("background"), 0, background2.x += xVel, Attributes.W, Attributes.H);
        } else {
            background2.x = background1.x;
            background1.x = -Attributes.H;
            gc.drawImage(Sprites.sprites.get("background"), 0, background1.x += xVel, Attributes.W, Attributes.H);
            gc.drawImage(Sprites.sprites.get("background"), 0, background2.x += xVel, Attributes.W, Attributes.H);
        }
    }

    public void setScrollVelocity(int scrollVelocity){
        this.scrollVelocity = scrollVelocity;
    }

    public int getScrollVelocity(){
        return this.scrollVelocity;
    }

}
