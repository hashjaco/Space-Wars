package sources;

import java.util.ArrayList;

public class Level {
    private int wave;
    private Background background;
    private Boss boss;
    private ArrayList<Wave> waves;

    public Level(){
        wave = 0;
        background = null;
        boss = null;
    }

    public Level(int waves, Background background, Boss boss){
        this.wave = waves;
        this.background = background;
        this.boss = boss;
    }

    public int getNumberOfWaves() {
        return wave;
    }

    public void setNumberOfWaves(int waves) {
        this.wave = waves;
    }

    public void addNewWave(Wave wave){
        waves.add(wave);
    }

    public Wave getWave(int index){
        if (index<this.waves.size());
        return waves.get(index);
    }

    public Background getBackground() {
        return background;
    }

    public void setBackground(Background background) {
        this.background = background;
    }

    public Boss getBoss() {
        return boss;
    }

    public void setBoss(Boss boss) {
        this.boss = boss;
    }
}
