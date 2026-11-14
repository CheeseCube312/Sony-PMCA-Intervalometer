package com.jpc.intervalometer;

import android.app.Activity;
import android.content.Intent;
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

    @Override
    protected void onResume() {
        Logger.info("Resume " + getComponentName().getClassName());
        super.onResume();

        displayManager = DisplayManager.create(this);
        displayManager.addListener(this);

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
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
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
        Logger.info("Display changed: " + display);
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

