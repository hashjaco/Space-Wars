package sources;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class QuadTree<T>{
    private int MAX_OBJECTS = 5;
    private int MAX_LEVELS = 3;

    private int levels;
    private List<GameObject> objects;
    private Rectangle bounds;
    private QuadTree[] trees;

    /**
    *
    * Constructor
     */

    public QuadTree(int pLevel, Rectangle pBounds){
        levels = pLevel;
        bounds = pBounds;
        objects = new ArrayList<>();
        trees = new QuadTree[4];
    }

    /**
    * Clears the quadrants recursively
    *
     *  */
    public void clear(){
        objects.clear();
        int length = trees.length;
        for(int i = 0; i < length; i++){
            if (trees[i] != null){
                trees[i].clear();
                trees[i] = null;
            }
        }
    }

    /**
    * Splits quadrant into four sub-quadrants
    *
     */
    public void split(){
        int subWidth = (int)(bounds.getWidth()/2);
        int subHeight = (int)(bounds.getHeight()/2);
        int x = (int)(bounds.getX());
        int y = (int)(bounds.getY());

        trees[0] = new QuadTree(levels+1, new Rectangle(x+subWidth,y,subWidth,subHeight));
        trees[1] = new QuadTree(levels+1, new Rectangle(x,y,subWidth,subHeight));
        trees[2] = new QuadTree(levels+1, new Rectangle(x,y+subHeight,subWidth,subHeight));
        trees[3] = new QuadTree(levels+1, new Rectangle(x+subWidth,y+subHeight,subWidth,subHeight));
    }

    /**
     * Determine which quadrant object belongs to. If -1, the object is not within any bounds and belongs to parent node
     * */
    public int getIndex(GameObject object){
        int index = -1;
        double horizontalMidpoint = bounds.getX() + bounds.getWidth()/2;
        double verticalMidpoint = bounds.getY() + bounds.getHeight()/2;

        // Object can completely fit into top quadrants
        boolean topQuadrant = (object.getY() < verticalMidpoint && object.getY() + object.getImage().getHeight() < verticalMidpoint);
        // Object can completely fit into bottom quadrants
        boolean bottomQuadrant = (object.getY() > verticalMidpoint);

        // Object can completely fit within left quadrants
        if (object.getX() < horizontalMidpoint && object.getX() + object.getImage().getWidth() < horizontalMidpoint && object.getX() >= 0){
            if (topQuadrant) index = 1;
            else if (bottomQuadrant) index = 2;
        }
        else if (object.getX() > horizontalMidpoint && object.getX() + object.getImage().getWidth() <= bounds.getMaxX()){
            if (topQuadrant) index = 0;
            else if (bottomQuadrant) index = 3;
        }

        return index;
    }

    /**
 * Insert the object into the quadtree. If the node
 * exceeds the capacity, it will split and add all
 * objects to their corresponding nodes.
     * */
    public void insert(GameObject object){
        if (trees[0] != null){
            int index = getIndex(object);

            if (index != -1){
                trees[index].insert(object);
                return;
            }
        }

        objects.add(object);

        if(objects.size() > MAX_OBJECTS && levels < MAX_LEVELS){
            if (trees[0] == null){
                split();
            }
            int i = 0;
            while (i < objects.size()){
                int index = getIndex(objects.get(i));
                if (index != -1){
                    trees[index].insert(objects.get(i));
                    objects.remove(i);
                }
                else {
                    i++;
                }
            }
        }
    }

    /**
     * Return all objects that could collide with the given object
     */
    public ArrayList<GameObject> retrieve(ArrayList<GameObject> returnObjects, GameObject object){
        int index = this.getIndex(object);
        if(index != -1 && trees[0] != null){
            trees[index].retrieve(returnObjects, object);
        }
        returnObjects.addAll(this.objects);

        return returnObjects;
    }

    public boolean isEmpty(){
        return this.trees==null;
    }

}
