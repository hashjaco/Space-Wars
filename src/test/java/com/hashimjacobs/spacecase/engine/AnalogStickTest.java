package com.hashimjacobs.spacecase.engine;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.prefs.PadButton;

import javafx.scene.input.KeyCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Analog movement: the ship flies at the speed the stick is pushed, not all or nothing.
 *
 * The pad reaches the engine as synthetic key events, which carry no magnitude, so the stick travels
 * a side channel instead. These are the rules of that channel -- that it scales, that it yields to
 * the keyboard when centred, and that a diagonal is not a speed exploit. No toolkit and no hardware:
 * PadState is a record and KeyCode a plain enum.
 */
class AnalogStickTest {

    private static final double DEADZONE = 0.30;

    @Test
    void aFullPushRightFliesAtFullSpeed() {
        PlayerShip ship = shipOne();
        controller(ship, pad(1.0, 0)).apply(input(), world(), SoundPlayer.SILENT, analog(1.0));

        assertEquals(ship.speed(), ship.velocityX(), 0.0001);
        assertEquals(0, ship.velocityY(), 0.0001);
    }

    /**
     * Travel is measured from the edge of the deadzone, so half speed comes at 0.65, not at 0.5:
     * (0.65 - 0.30) / (1 - 0.30) is exactly a half.
     */
    @Test
    void aPushTwoThirdsOfTheWayFliesAtHalfSpeed() {
        PlayerShip ship = shipOne();
        controller(ship, pad(0.65, 0)).apply(input(), world(), SoundPlayer.SILENT, analog(1.0));

        assertEquals(ship.speed() * 0.5, ship.velocityX(), 0.0001);
    }

    /**
     * The ship must ease off the mark, not leap onto it.
     *
     * Before travel was rescaled, clearing the deadzone by a thousandth jumped the ship straight to
     * three tenths of full speed, because the raw magnitude was used as the throttle. Nothing else
     * in the suite would notice: every other case pushes the stick well past this point.
     */
    @Test
    void clearingTheDeadzoneByAHairBarelyMovesTheShip() {
        PlayerShip ship = shipOne();
        controller(ship, pad(DEADZONE + 0.001, 0))
                .apply(input(), world(), SoundPlayer.SILENT, analog(1.0));

        assertTrue(ship.velocityX() < ship.speed() * 0.01,
                "just past the deadzone must be a crawl, not a leap to " + ship.velocityX());
    }

    /**
     * The balance guarantee: sensitivity bends the curve, it never raises the ceiling.
     *
     * Travel is one at a full push and one raised to any power is one, so every setting has to land
     * on exactly the ship's own speed -- which is what keeps this a feel control rather than a way
     * to outrun the garage speed upgrades, or to out-fly the other player in Battle.
     */
    @Test
    void aFullPushGivesFullSpeedAtEverySensitivity() {
        for (double sensitivity : new double[] {0.5, 1.0, 1.5, 2.0}) {
            PlayerShip ship = shipOne();
            controller(ship, pad(1.0, 0))
                    .apply(input(), world(), SoundPlayer.SILENT, analog(sensitivity));

            assertEquals(ship.speed(), ship.velocityX(), 0.0001,
                    "sensitivity " + sensitivity + " must not change the ship's top speed");
        }
    }

    @Test
    void higherSensitivityReachesSpeedSoonerFromTheSameStickReading() {
        double[] speeds = new double[3];
        double[] settings = {0.5, 1.0, 2.0};
        for (int i = 0; i < settings.length; i++) {
            PlayerShip ship = shipOne();
            controller(ship, pad(0.65, 0))
                    .apply(input(), world(), SoundPlayer.SILENT, analog(settings[i]));
            speeds[i] = ship.velocityX();
        }

        assertTrue(speeds[0] < speeds[1] && speeds[1] < speeds[2],
                "50% < 100% < 200% expected, got " + java.util.Arrays.toString(speeds));
    }

    /** Each pad carries its own feel, the same way each carries its own fire button. */
    @Test
    void sensitivityIsPerPlayer() {
        World world = new World(GameMode.COOP);
        PlayerShip one = world.players().get(0);
        PlayerShip two = world.players().get(1);
        PadState reading = pad(0.65, 0);

        new ShipController(one, someKeys(), number -> reading)
                .apply(input(), world, SoundPlayer.SILENT, analog(2.0));
        new ShipController(two, someKeys(), number -> reading)
                .apply(input(), world, SoundPlayer.SILENT, analog(0.5));

        assertTrue(one.velocityX() > two.velocityX(),
                "the same stick reading must give each player the feel they chose");
    }

    /** SDL clamps each axis on its own, so an unscaled diagonal would outrun a cardinal push. */
    @Test
    void aFullDiagonalIsNoFasterThanACardinal() {
        PlayerShip diagonal = shipOne();
        controller(diagonal, pad(1.0, 1.0))
                .apply(input(), world(), SoundPlayer.SILENT, analog(1.0));

        double speed = Math.hypot(diagonal.velocityX(), diagonal.velocityY());
        assertEquals(diagonal.speed(), speed, 0.0001);
    }

    @Test
    void aStickInsideTheDeadzoneLeavesTheKeyboardInCharge() {
        PlayerShip ship = shipOne();
        InputState input = input();
        input.press(KeyCode.D);

        controller(ship, pad(0.1, 0)).apply(input, world(), SoundPlayer.SILENT, analog(1.0));

        assertEquals(ship.speed(), ship.velocityX(), 0.0001,
                "a resting stick must yield to the keys, not pin the ship still");
    }

    @Test
    void analogOffIgnoresTheStickEntirely() {
        PlayerShip ship = shipOne();
        controller(ship, pad(0.5, 0)).apply(input(), world(), SoundPlayer.SILENT, digital());

        assertEquals(0, ship.velocityX(), 0.0001, "digital play must not read the stick at all");
    }

    @Test
    void aDisconnectedPadFallsBackToTheKeyboard() {
        PlayerShip ship = shipOne();
        InputState input = input();
        input.press(KeyCode.A);

        new ShipController(ship, someKeys(), number -> PadState.disconnected())
                .apply(input, world(), SoundPlayer.SILENT, analog(1.0));

        assertTrue(ship.velocityX() < 0, "a dead pad must not strand a player on the keyboard");
    }

    private static StickTuning analog(double sensitivity) {
        return new StickTuning(DEADZONE, true, sensitivity);
    }

    private static StickTuning digital() {
        return new StickTuning(DEADZONE, false, 1.0);
    }

    private static ShipController controller(PlayerShip ship, PadState state) {
        return new ShipController(ship, someKeys(), number -> state);
    }

    private static PadState pad(double x, double y) {
        return new PadState(true, x, y, false, false, false, false, Set.<PadButton>of());
    }

    private static PlayerShip shipOne() {
        return world().players().get(0);
    }

    private static World world() {
        return new World(GameMode.SOLO);
    }

    private static InputState input() {
        return new InputState();
    }

    /**
     * Any workable set of keys. These tests drive the stick, not the keyboard, and building the
     * real bindings would mean reaching into a preference store none of this needs.
     */
    private static PlayerControls someKeys() {
        return new PlayerControls(Set.of(KeyCode.W), Set.of(KeyCode.S), Set.of(KeyCode.A),
                Set.of(KeyCode.D), Set.of(KeyCode.SHIFT));
    }

}
