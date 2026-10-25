package org.jellyfin.androidtv.ui.playback;

import android.view.KeyEvent;

/** Consume the entire press that reveals the controls, so its key-up cannot click Pause. */
public final class PlaybackControlsKeyHandler {
    private int revealingKey = KeyEvent.KEYCODE_UNKNOWN;

    public void reset() {
        revealingKey = KeyEvent.KEYCODE_UNKNOWN;
    }

    public boolean onKey(int keyCode, int action, int repeatCount, boolean controlsVisible) {
        if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER) return false;

        if (revealingKey == keyCode) {
            if (action == KeyEvent.ACTION_UP) revealingKey = KeyEvent.KEYCODE_UNKNOWN;
            return true;
        }

        if (!controlsVisible && action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
            revealingKey = keyCode;
            return true;
        }

        return false;
    }
}
