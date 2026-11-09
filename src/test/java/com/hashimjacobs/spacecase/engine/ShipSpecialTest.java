package com.hashimjacobs.spacecase.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.Rocket;
import com.hashimjacobs.spacecase.garage.Chassis;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import org.junit.jupiter.api.Test;

/** The meter, the frozen held breath, and each airframe's shot on the first tick after it. */
class ShipSpecialTest {

    private static final Intent PRESS = new Intent(0, 0, false, true);

    private World world;
    private PlayerShip ship;
    private ShipController controller;

    private void fly(Chassis chassis) {
        world = new World(GameMode.SOLO);
        world.enterLevel(Level.values()[0]);
        ship = world.players().get(0);
        ship.loadout().unlock(chassis);
        ship.loadout().select(chassis);
        controller = new ShipController(ship, PlayerControls.none(), number -> null);
    }

    /** One fight tick the way GameLoop runs it: the hold skips the controller, never the clock. */
    private void tick(Intent intent) {
        if (!world.frozen()) {
            controller.apply(intent, world, SoundPlayer.SILENT);
        }
        world.update();
    }

    /** Presses with a full meter and runs out the hold; returns the bullets the shot added. */
    private int fireSpecial() {
        ship.chargeSpecial(GameConfig.SPECIAL_FULL);
        tick(PRESS);
        assertTrue(world.frozen(), "a full meter and a press holds the arena");
        int before = world.bullets().size();
        while (world.frozen()) {
            tick(Intent.NEUTRAL);
            assertEquals(before, world.bullets().size(), "nothing leaves the hull during the hold");
        }
        tick(Intent.NEUTRAL);
        return world.bullets().size() - before;
    }

    @Test
    void theMeterFillsOnItsOwnAndOnlyAFullOneFires() {
        fly(Chassis.STOCK);
        for (int i = 0; i < GameConfig.SPECIAL_FULL - 1; i++) {
            tick(Intent.NEUTRAL);
        }
        assertFalse(ship.specialReady());
        tick(PRESS);
        assertFalse(world.frozen(), "an almost-full meter does nothing");
        assertTrue(ship.specialReady(), "and that tick's own gain fills it");
        tick(PRESS);
        assertTrue(world.frozen());
        assertEquals(0, ship.specialCharge(), "firing spends the whole meter");
    }

    @Test
    void damageChargesTheMeterAndASpecialDoesNotChargeItself() {
        fly(Chassis.STOCK);
        ship.chargeSpecial(100);
        assertEquals(100, ship.specialCharge());
        ship.chargeSpecial(GameConfig.SPECIAL_FULL * 2);
        assertEquals(GameConfig.SPECIAL_FULL, ship.specialCharge(), "clamped at full");
        fireSpecial();
        ship.chargeSpecial(500);
        assertEquals(0, ship.specialCharge(), "the Nova Cannon burning does not feed the next one");
    }

    @Test
    void stockBurnsANovaCannonFourTimesTheLaser() {
        fly(Chassis.STOCK);
        double laser = ship.beamBox()[2];
        fireSpecial();
        assertTrue(ship.isFiringBeam());
        assertTrue(ship.novaCannon());
        assertEquals(laser * GameConfig.NOVA_CANNON_WIDTH, ship.beamBox()[2], 1e-9);
        for (int i = 0; i < GameConfig.NOVA_CANNON_TICKS; i++) {
            tick(Intent.NEUTRAL);
        }
        assertFalse(ship.isFiringBeam(), "and then it stops");
    }

    @Test
    void interceptorLaunchesTheSwarm() {
        fly(Chassis.INTERCEPTOR);
        assertEquals(GameConfig.SWARM_ROCKETS, fireSpecial());
        assertTrue(world.bullets().stream().allMatch(b -> b instanceof Rocket));
    }

    @Test
    void gunshipFiresAWallOfShells() {
        fly(Chassis.GUNSHIP);
        assertEquals(GameConfig.BROADSIDE_SHELLS, fireSpecial());
        double minX = world.bullets().stream().mapToDouble(b -> b.x()).min().orElseThrow();
        double maxX = world.bullets().stream().mapToDouble(b -> b.x()).max().orElseThrow();
        assertTrue(maxX - minX > GameConfig.WIDTH * 0.8, "edge to edge across the lane");
    }

    @Test
    void twinBoomSweepsTwoLances() {
        fly(Chassis.TWIN_BOOM);
        int first = fireSpecial();
        assertEquals(2, first, "one bolt from each boom a tick");
        for (int i = 1; i < GameConfig.LANCE_TICKS; i++) {
            tick(Intent.NEUTRAL);
        }
        int during = world.bullets().size();
        tick(Intent.NEUTRAL);
        assertTrue(world.bullets().size() <= during, "the lances stop when the sweep is done");
    }

    @Test
    void theMeterIsPartOfTheChecksumAndTheButtonsRideOneByte() {
        fly(Chassis.STOCK);
        long before = world.checksum();
        ship.chargeSpecial(1);
        assertNotEquals(before, world.checksum());

        Intent both = new Intent(0.5, -1, true, true);
        assertEquals(both, Intent.of(0.5, -1, both.buttons()));
        assertEquals(Intent.NEUTRAL, Intent.of(0, 0, Intent.NEUTRAL.buttons()));
    }
}
