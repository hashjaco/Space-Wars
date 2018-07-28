package sample;

import javafx.scene.Node;

public class Pawn extends Enemy {

    private int rank = 0;
    private int health = 30;

    public Pawn(Node view) {
        super(view);
    }

    public Pawn(Node view, int rank){
        super(view);
        this.rank = rank;
    }

    public int getDamage(){
        return 5;
    }

}
