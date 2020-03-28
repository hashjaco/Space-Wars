package sources;

import javafx.scene.Node;

public class Shooter extends GameObject {

    private boolean fireReady;

    protected Shooter(Node view) {
        super(view);
    }

    // Returns true if player is ready to fire, or enough time between shots has passed
    protected boolean isFireReady() {
        return fireReady;
    }

    // Set ready to fire
    protected void setFireReady(boolean ready) {
        fireReady = ready;
    }

    // Modify time between fire to keep track
    protected void setTimeBetweenFire(int timeBetweenFire) {
        this.timeBetweenFire = timeBetweenFire;
    }

    // Returns current time between fire
    protected int getTimeBetweenFire() {
        return this.timeBetweenFire;
    }

    // return game object's weapon level
    protected int getFirePowerLevel() {
        return firePowerLevel;
    }

    // sets game object's weapon level
    protected void setFirePowerLevel(int firePowerLevel) {
        this.firePowerLevel = firePowerLevel;
    }
    
}
