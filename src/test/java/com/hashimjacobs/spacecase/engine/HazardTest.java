package com.hashimjacobs.spacecase.engine;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Forgeworks' heat and Coronal Arc's solar wind actually reach the ship. */
class HazardTest {

    @Test
    void theForgeworksCooksAShipThatSitsInIt() {
        World world = new World(GameMode.SOLO);
        world.enterLevel(Level.THE_FORGEWORKS);
        PlayerShip pilot = world.players().get(0);
        int before = pilot.health();
        new CollisionSystem(SoundPlayer.SILENT).resolve(world);
        assertTrue(pilot.health() < before, "heat should tick on the first second");
    }

    @Test
    void coronalArcDragsTheShipDownrange() {
        World world = new World(GameMode.SOLO);
        world.enterLevel(Level.CORONAL_ARC);
        PlayerShip pilot = world.players().get(0);
        pilot.setPosition(pilot.x(), 300);
        world.update();
        assertTrue(pilot.y() > 300, "the solar wind should push toward the bottom, was " + pilot.y());
    }
}
