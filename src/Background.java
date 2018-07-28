package sample;

import javafx.scene.image.Image;

public class Background {
    Image image;
    double x, y;

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

    public void scrollBackground(){

    }

}
