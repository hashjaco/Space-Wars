package com.hashimjacobs.spacecase;

import android.view.KeyEvent;

import com.gluonhq.helloandroid.MainActivity;

/**
 * Gluon's activity, with the controller buttons it drops put back.
 *
 * Gluon's MainActivity turns Android key codes into JavaFX ones from a fixed table that knows A, B
 * and X and no other pad button, so Start, Y, the shoulders, the triggers and the stick clicks all
 * reached the game as one indistinguishable "undefined" key. JavaFX has no codes for them to be
 * given, so each is re-sent here as a function key the table does know, and
 * SceneRouter.installAndroidController turns it back into its button. F3 and F11 are skipped: the
 * game uses them.
 *
 * This runs on Android's own VM, not in the native image, so it is not part of the Maven build. It
 * ships as the compiled class under src/android/resources, where Gluon collects it into the APK;
 * after changing this file rebuild that with src/android/dalvik/build.sh.
 */
public class TvActivity extends MainActivity {

    private static int remap(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_Y: return KeyEvent.KEYCODE_F1;
            case KeyEvent.KEYCODE_BUTTON_L1: return KeyEvent.KEYCODE_F2;
            case KeyEvent.KEYCODE_BUTTON_R1: return KeyEvent.KEYCODE_F4;
            case KeyEvent.KEYCODE_BUTTON_START: return KeyEvent.KEYCODE_F5;
            case KeyEvent.KEYCODE_BUTTON_SELECT: return KeyEvent.KEYCODE_F6;
            case KeyEvent.KEYCODE_BUTTON_L2: return KeyEvent.KEYCODE_F7;
            case KeyEvent.KEYCODE_BUTTON_R2: return KeyEvent.KEYCODE_F8;
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return KeyEvent.KEYCODE_F9;
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return KeyEvent.KEYCODE_F10;
            default: return code;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = remap(event.getKeyCode());
        if (code == event.getKeyCode()) {
            return super.dispatchKeyEvent(event);
        }
        return super.dispatchKeyEvent(new KeyEvent(event.getDownTime(), event.getEventTime(),
                event.getAction(), code, event.getRepeatCount(), event.getMetaState(),
                event.getDeviceId(), event.getScanCode(), event.getFlags(), event.getSource()));
    }
}
