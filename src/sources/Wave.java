package sources;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.ArrayList;

public class Wave {


    private int noOfEntities;
    private ArrayList<ArrayList<GameObject>> entityGoups;

    public Wave(){
        entityGoups = new ArrayList<>();
        this.noOfEntities = 0;
    }

    public int getNumberOfEntities() {
        return this.noOfEntities;
    }

    public void setNumberOfEntities(int noOfEntities) {
        this.noOfEntities = noOfEntities;
    }

    public void addEntityGroup(ArrayList<GameObject> groupOfObjects, int size, String spritePath){
        groupOfObjects = new ArrayList<>(size);
        entityGoups.add(groupOfObjects);
        for (int i = 0;i < size;i++){
            groupOfObjects.add(new GameObject(new ImageView(new Image(spritePath))));
        }
    }

}
