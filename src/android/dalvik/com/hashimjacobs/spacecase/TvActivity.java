package com.hashimjacobs.spacecase;

import android.content.Intent;
import android.os.Bundle;
import android.os.FileObserver;
import android.view.KeyEvent;

import java.io.File;

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
 * It also starts the install of a downloaded update. The game runs as a native image and cannot
 * send Android an intent itself, so it moves the verified APK into files/update/SpaceCase.apk and
 * this activity, watching that folder, asks the system installer to take it (see ApkProvider).
 *
 * This runs on Android's own VM, not in the native image, so it is not part of the Maven build. It
 * ships as the compiled class under src/android/resources, where Gluon collects it into the APK;
 * after changing this file rebuild that with src/android/dalvik/build.sh.
 */
public class TvActivity extends MainActivity {

    /** Held, because a FileObserver nothing refers to stops watching when it is collected. */
    private FileObserver updates;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        File folder = ApkProvider.apk(this).getParentFile();
        folder.mkdirs();
        updates = new FileObserver(folder.getPath(), FileObserver.MOVED_TO) {
            @Override
            public void onEvent(int event, String path) {
                if ("SpaceCase.apk".equals(path)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            askToInstall();
                        }
                    });
                }
            }
        };
        updates.startWatching();
    }

    @Override
    public void onDestroy() {
        if (updates != null) {
            updates.stopWatching();
        }
        super.onDestroy();
    }

    /** The system's own prompt. The first time, it sends the player to allow installs from this app. */
    private void askToInstall() {
        Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        install.setDataAndType(ApkProvider.uri(), ApkProvider.APK_TYPE);
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(install);
        } catch (RuntimeException refused) {
            android.util.Log.e("SpaceCase", "could not start the update install", refused);
        }
    }

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
