package com.jpc.intervalometer;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import com.github.ma1co.openmemories.framework.DisplayManager;
import com.sony.scalar.sysutil.ScalarInput;

/**
 * Base activity with Sony camera key handling and display management,
 * modeled after PMCADemo's BaseActivity (trimmed to the keys a camera app
 * typically uses). Override the on*KeyDown/Up hooks you need; returning
 * true consumes the key, false lets the firmware handle it.
 */
public class BaseActivity extends Activity implements DisplayManager.Listener {
    private DisplayManager displayManager;
    /** Last display-change event (also seeded at resume) - the PWR run's restore target (0.3.0). */
    protected DisplayManager.Display currentDisplay = DisplayManager.Display.SCREEN;

    @Override
    protected void onResume() {
        Logger.info("Resume " + getComponentName().getClassName());
        super.onResume();

        displayManager = DisplayManager.create(this);
        displayManager.addListener(this);
        currentDisplay = displayManager.getActiveDisplay(); // seed the mirror before the first event

        // LOW color depth while the camera preview is running (as PMCADemo's CameraActivity)
        setColorDepth(false);
        notifyAppInfo();
    }

    @Override
    protected void onPause() {
        Logger.info("Pause " + getComponentName().getClassName());
        super.onPause();

        displayManager.release();
        displayManager = null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        try {
            switch (event.getScanCode()) {
                case ScalarInput.ISV_KEY_UP:
                    return onUpKeyDown();
                case ScalarInput.ISV_KEY_DOWN:
                    return onDownKeyDown();
                case ScalarInput.ISV_KEY_LEFT:
                    return onLeftKeyDown();
                case ScalarInput.ISV_KEY_RIGHT:
                    return onRightKeyDown();
                case ScalarInput.ISV_KEY_ENTER:
                    return onEnterKeyDown();
                case ScalarInput.ISV_KEY_S1_1:
                    return onFocusKeyDown();
                case ScalarInput.ISV_KEY_S1_2:
                    return true;
                case ScalarInput.ISV_KEY_S2:
                    return onShutterKeyDown();
                case ScalarInput.ISV_KEY_DELETE:
                case ScalarInput.ISV_KEY_SK2:
                    return onDeleteKeyDown();
                default:
                    return super.onKeyDown(keyCode, event);
            }
        } catch (Throwable t) {
            // 0.3.0 on-camera: Camera calls in handlers throw RuntimeExceptions on this
            // firmware (cancelAutoFocus with the preview stopped) and an uncaught one
            // killed the main looper - with the PWR panel dark that read as a frozen
            // camera. Log the full stack; the key counts as consumed.
            Logger.error("key down handler crashed: " + Log.getStackTraceString(t));
            return true;
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        try {
            switch (event.getScanCode()) {
                case ScalarInput.ISV_KEY_UP:
                    return onUpKeyUp();
                case ScalarInput.ISV_KEY_DOWN:
                    return onDownKeyUp();
                case ScalarInput.ISV_KEY_LEFT:
                    return onLeftKeyUp();
                case ScalarInput.ISV_KEY_RIGHT:
                    return onRightKeyUp();
                case ScalarInput.ISV_KEY_ENTER:
                    return onEnterKeyUp();
                case ScalarInput.ISV_KEY_S1_1:
                    return onFocusKeyUp();
                case ScalarInput.ISV_KEY_S2:
                    return onShutterKeyUp();
                case ScalarInput.ISV_KEY_DELETE:
                case ScalarInput.ISV_KEY_SK2:
                    return onDeleteKeyUp();
                default:
                    return super.onKeyUp(keyCode, event);
            }
        } catch (Throwable t) {
            // same shield as the down side - a release crash must never kill the looper
            Logger.error("key up handler crashed: " + Log.getStackTraceString(t));
            return true;
        }
    }

    protected boolean onUpKeyDown() { return false; }
    protected boolean onUpKeyUp() { return false; }
    protected boolean onDownKeyDown() { return false; }
    protected boolean onDownKeyUp() { return false; }
    protected boolean onLeftKeyDown() { return false; }
    protected boolean onLeftKeyUp() { return false; }
    protected boolean onRightKeyDown() { return false; }
    protected boolean onRightKeyUp() { return false; }
    protected boolean onEnterKeyDown() { return false; }
    protected boolean onEnterKeyUp() { return false; }
    protected boolean onFocusKeyDown() { return false; }
    protected boolean onFocusKeyUp() { return false; }
    protected boolean onShutterKeyDown() { return false; }
    protected boolean onShutterKeyUp() { return false; }

    protected boolean onDeleteKeyDown() {
        return true;
    }

    protected boolean onDeleteKeyUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void displayChanged(DisplayManager.Display display) {
        currentDisplay = display;
        Logger.info("Display changed: " + display);
    }

    /** The live DisplayManager (null outside onResume..onPause). */
    protected DisplayManager getDisplayManager() {
        return displayManager;
    }

    protected void setColorDepth(boolean highQuality) {
        displayManager.setColorDepth(highQuality ? DisplayManager.ColorDepth.HIGH : DisplayManager.ColorDepth.LOW);
    }

    protected void notifyAppInfo() {
        Intent intent = new Intent("com.android.server.DAConnectionManagerService.AppInfoReceive");
        intent.putExtra("package_name", getComponentName().getPackageName());
        intent.putExtra("class_name", getComponentName().getClassName());
        sendBroadcast(intent);
    }
}

