package sources;

import javafx.scene.image.Image;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.stream.Stream;

public class Animation {
    protected ArrayList<Image> animation;
    protected Image image;
    protected double xLocation, yLocation;
    protected int currentTime, duration;
    protected int index=0;
    protected boolean isDead=false;

    protected GameObject gameObject;

    public Animation(){
        this.animation = new ArrayList<>();
        this.xLocation = 0;
        this.yLocation = 0;
        this.currentTime = 0;
        this.duration = 0;
    }

    public Animation(File path){
        this.xLocation = 0;
        this.yLocation = 0;
        this.currentTime = 0;
        this.duration = 0;
        if (path.isDirectory() && path.exists()) {
            this.animation = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(Paths.get(path.toURI().toString()))) {
                paths.forEach(filePath -> {
                    if (Files.isRegularFile(filePath)) {
                        try {
                                this.animation.add(new Image(filePath.toString()));
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                });
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        else {
            this.image = new Image(path.toURI().toString());
        }
    }

    public Animation(String path, GameObject gameObject,int durationInSeconds){
        this.duration = durationInSeconds;
        this.gameObject = gameObject;
        this.xLocation = gameObject.getX();
        this.yLocation = gameObject.getY();
        if (new File(path).exists() && new File(path).isDirectory()) {
            this.animation = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(Paths.get(path))) {
                paths.forEach(filePath -> {
                    if (Files.isRegularFile(filePath)) {
                        try {
                            this.animation.add(new Image(filePath.toUri().toString()));
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                });
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public void setAnimationFolder(File path){
        if (path.isDirectory()) {
            this.animation = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(Paths.get(path.toURI().toString()))) {
                paths.forEach(filePath -> {
                    if (Files.isRegularFile(filePath)) {
                        try {
                            if (this.animation != null) {
                                this.animation.add(new Image(filePath.toString()));
                            }
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                });
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public ArrayList<Image> listOfImages(){
        return this.animation;
    }

    public void setLocation(double xLocation, double yLocation){
        this.yLocation = yLocation;
        this.xLocation = xLocation;
    }

    public double getxLocation(){
        return this.xLocation;
    }

    public double getyLocation(){
        return this.yLocation;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public void setImage(String image){
        this.image = new Image(image);
    }

    public Image getImage(int index){
        return this.animation.get(index);
    }

    public GameObject getGameObject() {
        return gameObject;
    }

    public void setGameObject(GameObject gameObject) {
        this.gameObject = gameObject;
    }

    public Image animate(){
        this.image = this.animation.get(this.index);
        this.index++;
        return image;
    }

    public boolean isFinished(){
        return this.index == this.animation.size()-1;
    }

    public int getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(int currentTime) {
        this.currentTime = currentTime;
    }

    public void setDead(){ this.isDead=true;}

    public boolean isDead(){
        return this.isDead;
    }
}
