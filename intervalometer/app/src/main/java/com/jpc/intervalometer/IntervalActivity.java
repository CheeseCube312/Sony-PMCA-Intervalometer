package com.jpc.intervalometer;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import com.sony.scalar.hardware.CameraEx;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Intervalometer main activity (Phase 1): config screen + fixed-grid runs.
 *
 * Live view is PMCADemo's CameraActivity pattern: the firmware HAL renders
 * straight into the SurfaceView (CameraEx.open -> getNormalCamera()
 * .setPreviewDisplay -> startPreview). The app never touches preview bytes -
 * no decode, no pixel access, no NDK.
 *
 * Keys: UP/DOWN adjust the selected config value, LEFT/RIGHT switch field,
 * S2 starts the run (mid-run S2 is ignored - the engine owns shooting), S1
 * autofocus works as always, trash stops a run (summary stays up; a second
 * trash exits).
 *
 * Runs hold the camera awake: auto power-off is held off via the
 * DAConnectionManagerService.apo broadcast (the same one PMCADemo's
 * WifiActivity and Recipe Lab use), renewed every minute from the engine's
 * 1 Hz tick since the firmware re-asserts APO, and restored on stop; the
 * window keeps the screen on. Leaving the activity (onPause) stops the run.
 *
 * 0.1.3 - the monochrome live view (AND stills) is a camera LOOK state, not
 * a display problem: Recipe Lab proves the HAL preview is this same
 * open(0,null)+SurfaceView path and still gets color, because it drives the
 * Sony runtime look keys over Camera.Parameters after startPreview. Confirmed
 * on camera 2026-11-14: the session opens with color-mode=mono;
 * forceColorLook() forces the color baseline (standard/off/false) right
 * after startPreview and the camera keeps it. One improvement over PMCADemo:
 * the preview also (re)starts when the surface already exists on resume
 * (PMCADemo shows a black screen after a pause/resume cycle that kept it).
 *
 * 0.2.2 - multi-shot runs need the shutter key's FULL cycle per frame, not
 * just takePicture: the HAL must get cancelTakePicture (S2 up) before it
 * accepts the next takePicture. Recipe Lab's automated run does takePicture,
 * then cancelTakePicture + startPreview SHUTTER_MS later; the engine slots do
 * the same now (v0.2.0/0.2.1 restarted the preview but never released, so
 *
 * 0.2.4 - long exposures: the grid fired start-to-start and ignored whether
 * the previous capture was still running, so at a 5 s interval with a 30 s
 * shutter 8 of 10 shots fired INTO an in-flight exposure and were silently
 * lost (takePicture "succeeds", no image ever stores - only the two slots
 * that started idle produced files). Now the engine holds a slot while the
 * camera is busy (interval = minimum spacing), the summary waits out an
 * in-flight last capture, and a 90 s cap marks a never-storing capture as
 * lost instead of wedging the run.
 *
 * 0.2.5 - exposure control + true bulb. The Sony SDK (verified against the
 * compiled stubs) has real shutter-speed control: incrementShutterSpeed() /
 * decrementShutterSpeed() step the camera's software value (the dials are
 * just input devices for the same state - there is no fixed dial position to
 * fight), and ShutterSpeedChangeListener is the only way to READ it (no
 * getter). The config screen gains an Exposure field that mirrors the
 * camera's current value and steps it live; stepping past the timed end stop
 * reaches Bulb. Bulb has no duration on the camera, so UP/DOWN at the Bulb
 * position adjusts the app-side bulb duration instead (the exposure length
 * the engine enforces). In bulb runs each shot schedules cancelExposure()
 * (the SDK's exposure-end primitive - cancelTakePicture is the timed-shot
 * release and is ignored mid-exposure, the v0.2.4 side finding) at the bulb
 * duration, and the in-flight watchdog + summary deadline scale with it.
 * ShutterListener / CaptureStatusListener / ExposureCompleteListener are
 * armed as log-only ground truth for now (shutter-open anchor, real
 * in-flight state) - nothing behavioral depends on them yet.
 */
public class IntervalActivity extends BaseActivity implements SurfaceHolder.Callback {
    /** Config choices are discrete lists - no typing on a 4-key camera. */
    private static final int[] INTERVALS_S = {2, 5, 10, 15, 30, 60};
    private static final int[] COUNTS = {10, 60, -1}; // -1 = unlimited
    private static final int[] DELAYS_S = {0, 2, 5, 10};
    private static final int[] BULBS_S = {5, 10, 15, 30, 60, 120, 300}; // app-side bulb duration
    private static final int DEF_INTERVAL = 4; // 30 s - the Phase 1 gate value
    private static final int DEF_COUNT = 1;    // 60 shots = 30 min @ 30 s
    private static final int DEF_DELAY = 0;
    private static final int DEF_BULB = 4;     // 60 s bulb exposure

    private static final String ACTION_APO = "com.android.server.DAConnectionManagerService.apo";
    private static final long APO_REFRESH_MS = 60000; // firmware re-asserts APO (Recipe Lab)

    private OverlayView overlayView;
    private SurfaceHolder surfaceHolder;
    private CameraEx camera;
    private String version = "?";

    // config screen state (Phase 2 persists this)
    private int selField; // 0 interval, 1 exposure, 2 count, 3 delay
    private int intervalIdx = DEF_INTERVAL, countIdx = DEF_COUNT, delayIdx = DEF_DELAY;
    private int bulbIdx = DEF_BULB;

    // exposure state (0.2.5) - mirror of the camera's software shutter speed
    private CameraEx.ShutterSpeedInfo shutterInfo; // latest change callback (no getter in the SDK)
    private volatile boolean bulbPos;              // camera sits at Bulb - 0xFFFF sentinel, confirmed on camera
    /** Bulb duration for THIS run (ms, 0 = timed shots - camera closes on its own). */
    private volatile long bulbShotMs;
    /** Watchdog cap for the shot in flight (timed: INFLIGHT_CAP_MS; bulb: bulb duration + margin). */
    private volatile long inFlightCapMs = INFLIGHT_CAP_MS;

    // run state
    private IntervalEngine engine;
    private volatile boolean summaryShown;
    /** takePicture timestamp, consumed by the storeImage log + capture stats. */
    private volatile long stillT0;
    private int stored;          // storeImage callbacks while running
    private long capSum, capMax; // capture ms sum/max for the summary
    private int capN;
    private long lastApoMs;      // elapsedRealtime of the last APO-off broadcast

    /**
     * The shutter key's release, automated. Recipe Lab's proven per-frame cycle
     * on this body: takePicture (S2 down), then SHUTTER_MS later cancelTakePicture
     * + startPreview (S2 up). Skipping the release leaves the HAL in capture
     * state - the next takePicture throws (the v0.2.0/0.2.1 "shot 2+ all fail").
     */
    private static final long SHUTTER_MS = 2500; // DevTools.SHUTTER_MS - what a capture needs on this body
    private static final long SUMMARY_DELAY_MS = 4000; // last store lands ~3 s after run end; wait for it
    private static final long INFLIGHT_CAP_MS = 90000; // timed shots: give up on a capture that never stores
    private static final long WATCH_MARGIN_MS = 30000; // bulb shots: store + slack after the exposure ends
    private final Handler handler = new Handler();
    private Runnable summaryTask; // set per finished run, cancelled on pause
    private long summaryDeadline; // elapsedRealtime until which the summary waits out the last capture
    private final Runnable shutterRelease = new Runnable() {
        @Override
        public void run() {
            if (camera == null)
                return;
            try {
                camera.cancelTakePicture();
                Logger.info("capture released (cancelTakePicture)");
            } catch (Throwable t) {
                Logger.error("cancelTakePicture: " + t);
            }
            resumePreview();
        }
    };

    /**
     * Bulb release (0.2.5): ends the exposure at the configured bulb duration.
     * cancelExposure() is the SDK's exposure-end primitive; cancelTakePicture
     * follows as the proven S2-up half (the firmware ignores it mid-exposure,
     * so it is a harmless no-op here) and resumePreview() restarts the view.
     * Scheduled from takePicture return - the ShutterListener log measures the
     * true open offset for the next iteration.
     */
    private final Runnable bulbRelease = new Runnable() {
        @Override
        public void run() {
            if (camera == null)
                return;
            try {
                camera.cancelExposure();
                Logger.info("bulb exposure ended (cancelExposure) @ " + bulbShotMs + " ms");
            } catch (Throwable t) {
                Logger.error("cancelExposure: " + t); // shutter may hang open - loud, the watchdog caps the run
            }
            try {
                camera.cancelTakePicture();
                Logger.info("bulb released (cancelTakePicture)");
            } catch (Throwable t) {
                Logger.error("bulb cancelTakePicture: " + t);
            }
            resumePreview();
        }
    };

    /**
     * One-in-flight watchdog. If a capture never stores (firmware ate it, or a
     * bulb exposure nobody ends), clear the in-flight flag after INFLIGHT_CAP_MS
     * so the engine can fire the next slot and the summary can print; the shot
     * shows up in the stored-vs-fired count as not stored.
     */
    private final Runnable inFlightCap = new Runnable() {
        @Override
        public void run() {
            if (stillT0 != 0) {
                stillT0 = 0;
                Logger.info("capture still in flight after " + (inFlightCapMs / 1000)
                        + " s - giving up on it (not stored)");
            }
        }
    };

    /** The engine callback set, wired to this activity's camera + overlay. */
    private final IntervalEngine.Listener engineListener = new IntervalEngine.Listener() {
        @Override
        public void onShoot(int n) {
            if (camera == null)
                return;
            boolean bulb = bulbShotMs > 0;
            try {
                resumePreview(); // safety net: takePicture needs a live preview (no-op if already running)
                stillT0 = System.currentTimeMillis();
                inFlightCapMs = bulb ? bulbShotMs + WATCH_MARGIN_MS : INFLIGHT_CAP_MS;
                camera.getNormalCamera().takePicture(null, null, null);
                if (bulb) {
                    // True bulb: the app owns the exposure end. cancelExposure at
                    // the configured duration replaces the fixed +2.5 s release
                    // (which would cut every bulb exposure short).
                    handler.postDelayed(bulbRelease, bulbShotMs);
                } else {
                    handler.postDelayed(shutterRelease, SHUTTER_MS); // the S2 up half of the press
                }
                handler.postDelayed(inFlightCap, inFlightCapMs); // never-storing capture watchdog
            } catch (Throwable t) {
                stillT0 = 0;
                handler.removeCallbacks(shutterRelease);
                handler.removeCallbacks(bulbRelease);
                handler.removeCallbacks(inFlightCap);
                Logger.error("shot " + n + ": takePicture failed: " + Log.getStackTraceString(t));
            }
        }

        @Override
        public boolean canShoot() {
            return stillT0 == 0; // engine holds slots while a capture is in flight
        }

        @Override
        public void onTick(long elapsedMs, int shotsFired, long nextShotInMs) {
            if (lastApoMs == 0 || SystemClock.elapsedRealtime() - lastApoMs > APO_REFRESH_MS)
                apoOff();
            String plan = COUNTS[countIdx] > 0 ? "/" + COUNTS[countIdx] : "";
            overlayView.setLines(new String[]{
                    "Intervalometer v" + version + " - RUNNING",
                    "shot " + shotsFired + plan + " | next " + IntervalEngine.mmss(nextShotInMs)
                            + " | +" + IntervalEngine.mmss(elapsedMs)
                            + (bulbShotMs > 0 ? " | Bulb " + (bulbShotMs / 1000) + " s" : "")});
        }

        @Override
        public void onFinished(int reason, int shotsFired, long elapsedMs, int lateShots) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            apoRestore();
            // The last shot's JPEG lands ~3 s after the run ends - hold the summary
            // until it does, or "stored N of N" undercounts by one (0.2.2 printed
            // "9 of 10" and the tenth store arrived seconds later).
            summaryTask = new Runnable() {
                @Override
                public void run() {
                    // A long exposure can still be in flight at run end - hold
                    // the summary until its store lands or the cap expires, or
                    // "stored N of N" lies (0.2.3 printed "1 of 10"; the second
                    // store arrived 21 s later).
                    if (stillT0 != 0 && SystemClock.elapsedRealtime() < summaryDeadline) {
                        handler.postDelayed(summaryTask, 1000);
                        return;
                    }
                    summaryShown = true;
                    Logger.info("stored " + stored + " of " + shotsFired + " fired"
                            + (capN > 0 ? ", capture avg " + (capSum / capN) + " ms, max " + capMax + " ms"
                            : ""));
                    overlayView.setLines(new String[]{
                            IntervalEngine.reasonName(reason).toUpperCase() + ": " + shotsFired
                                    + " shots in " + IntervalEngine.mmss(elapsedMs) + " (late " + lateShots + ")",
                            "stored " + stored + "/" + shotsFired
                                    + (capN > 0 ? ", capture avg " + (capSum / capN) + " ms, max " + capMax + " ms"
                                    : ""),
                            "S2 config | trash exit"});
                }
            };
            // Bulb: the last exposure can outlive the run by its whole duration -
            // scale the wait instead of the fixed 90 s (0.2.4 kept the fixed cap
            // and would have printed the summary mid-exposure).
            summaryDeadline = SystemClock.elapsedRealtime()
                    + (bulbShotMs > 0 ? bulbShotMs + WATCH_MARGIN_MS : INFLIGHT_CAP_MS);
            handler.postDelayed(summaryTask, SUMMARY_DELAY_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
        }
        Logger.beginSession(version);
        Logger.info("device: " + Build.MODEL + " / Android " + Build.VERSION.RELEASE
                + " / abi " + Build.CPU_ABI + " / " + Build.CPU_ABI2);
        logCpuInfo();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                java.io.StringWriter sw = new java.io.StringWriter();
                sw.append(throwable.toString()).append("\n");
                throwable.printStackTrace(new java.io.PrintWriter(sw));
                Logger.error(sw.toString());
                System.exit(0);
            }
        });

        overlayView = (OverlayView) findViewById(R.id.preview);
        surfaceHolder = ((SurfaceView) findViewById(R.id.surface)).getHolder();
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        showConfig();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Logger.info("CameraEx.open...");
        try {
            camera = CameraEx.open(0, null);
        } catch (Throwable t) {
            // e.g. the stock camera app still holds the device
            Logger.error("camera open failed: " + Log.getStackTraceString(t));
            return;
        }
        // Shot log only: the firmware writes the full-res JPEG to the card by
        // itself; the app never touches that file. NOTE: the int is a monotonic
        // sequence counter on this firmware (0,1,2,... - keeps counting across
        // app restarts), NOT a status.
        camera.setStoreImageCompleteListener(new CameraEx.StoreImageCompleteListener() {
            @Override
            public void onDone(int result, CameraEx.StoreImageInfo info, CameraEx cam) {
                String f = info == null ? "null"
                        : info.DirectoryName + "/" + info.FileName + " (FileNo " + info.FileNo + ")";
                long ms = stillT0 == 0 ? -1 : System.currentTimeMillis() - stillT0;
                stillT0 = 0;
                Logger.info("still: storeImage done - seq " + result + ", " + ms + " ms, " + f
                        + " - file untouched");
                if (ms >= 0) { // any store with an in-flight stillT0 belongs to a fired shot - the last one lands AFTER run end
                    handler.removeCallbacks(inFlightCap); // stored normally - disarm the watchdog
                    stored++;
                    capSum += ms;
                    capN++;
                    if (ms > capMax)
                        capMax = ms;
                }
                if (summaryShown) // landed after the summary printed - log the corrected count
                    Logger.info("late store after summary: stored count now " + stored);
                // no preview restart here - shutterRelease owns the release + restart
            }
        });

        // 0.2.5 - exposure mirror + event ground truth. ShutterSpeedChangeListener
        // is the only way to READ the shutter speed (no getter); its change events
        // drive the Exposure field. Log-only listeners below stay behavioral no-ops
        // until the camera proves their semantics (shutter-open anchor, in-flight).
        camera.setShutterSpeedChangeListener(new CameraEx.ShutterSpeedChangeListener() {
            @Override
            public void onShutterSpeedChange(CameraEx.ShutterSpeedInfo info, CameraEx cam) {
                shutterInfo = info;
                bulbPos = isBulb(info);
                Logger.info("shutter speed: " + fmtSpeed(info)
                        + " (n=" + (info == null ? -1 : info.currentShutterSpeed_n)
                        + " d=" + (info == null ? -1 : info.currentShutterSpeed_d)
                        + " min n=" + (info == null ? -1 : info.currentAvailableMin_n)
                        + " d=" + (info == null ? -1 : info.currentAvailableMin_d)
                        + " max n=" + (info == null ? -1 : info.currentAvailableMax_n)
                        + " d=" + (info == null ? -1 : info.currentAvailableMax_d) + ")");
                if (!busy())
                    showConfig(); // mirror live changes into the Exposure field
            }
        });
        camera.setShutterListener(new CameraEx.ShutterListener() {
            @Override
            public void onShutter(int status, CameraEx cam) {
                String s = status == CameraEx.ShutterListener.STATUS_OK ? "OK"
                        : status == CameraEx.ShutterListener.STATUS_CANCELED ? "CANCELED"
                        : status == CameraEx.ShutterListener.STATUS_ERROR ? "ERROR" : "?" + status;
                Logger.info("shutter event: " + s + (stillT0 != 0
                        ? " @ +" + (System.currentTimeMillis() - stillT0) + " ms" : ""));
            }
        });
        camera.setCaptureStatusListener(new CameraEx.OnCaptureStatusListener() {
            @Override
            public void onStart(int mode, CameraEx cam) {
                Logger.info("capture status: START mode " + mode);
            }

            @Override
            public void onEnd(int mode, int reason, CameraEx cam) {
                Logger.info("capture status: END mode " + mode + " reason "
                        + (reason == CameraEx.OnCaptureStatusListener.REASON_CANCEL ? "CANCEL" : "NORMAL")
                        + (stillT0 != 0 ? " @ +" + (System.currentTimeMillis() - stillT0) + " ms" : ""));
            }
        });
        camera.setExposureCompleteListener(new CameraEx.ExposureCompleteListener() {
            @Override
            public void onDone(int result, CameraEx.ExposureInfo info, CameraEx cam) {
                Logger.info("exposure complete: result " + result
                        + (info != null ? " (F" + info.FNo + " ISO " + info.IsoSpeedRate
                        + " n=" + info.ShutterSpeedNumber + " d=" + info.ShutterSpeedDeanom + ")" : ""));
            }
        });
        // Provoke one shutter-speed callback with a net-zero down/up pair so the
        // Exposure field mirrors the camera from the first paint (the listener may
        // or may not fire an initial state on arm - this guarantees a value).
        try {
            camera.decrementShutterSpeed();
            camera.incrementShutterSpeed();
            Logger.info("exposure probe: down/up step pair done");
        } catch (Throwable t) {
            Logger.info("exposure probe failed: " + t); // stepping may be disallowed in this exposure mode
        }
        logSupportedParameters(); // one-time diagnostic: every key this HAL accepts
        Logger.info("camera open ok");
        surfaceHolder.addCallback(this);
        tryStartPreview(); // surface may already exist (resume with screen kept on)
    }

    @Override
    protected void onPause() {
        if (engine != null && engine.isRunning())
            engine.stop(IntervalEngine.REASON_ABORTED); // leaving = run over
        handler.removeCallbacks(shutterRelease);
        handler.removeCallbacks(bulbRelease);
        handler.removeCallbacks(inFlightCap);
        if (summaryTask != null) {
            handler.removeCallbacks(summaryTask);
            summaryTask = null;
        }
        surfaceHolder.removeCallback(this);
        stopPreview();
        if (camera != null) {
            camera.release();
            camera = null;
            Logger.info("camera released");
        }
        super.onPause();
    }

    // --- SurfaceHolder.Callback ---

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        tryStartPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Logger.info("surface changed " + width + "x" + height + " fmt " + format);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopPreview();
    }

    /** Starts the HAL preview if both the camera and a valid surface exist. */
    private void tryStartPreview() {
        if (camera == null)
            return; // onResume opens the camera and calls us again
        if (surfaceHolder.getSurface() == null || !surfaceHolder.getSurface().isValid())
            return; // surfaceCreated will call us again
        try {
            camera.getNormalCamera().setPreviewDisplay(surfaceHolder);
            camera.getNormalCamera().startPreview();
            Logger.info("startPreview ok - plain HAL live view");
            forceColorLook();
        } catch (Throwable t) {
            Logger.error("startPreview failed: " + Log.getStackTraceString(t));
        }
    }

    /**
     * Logs the Sony runtime look keys the session opened with, forces the
     * color baseline, then logs what the camera kept (a dropped value throws
     * nothing - Recipe Lab's applyPreview() read-back pattern). The string
     * get/set path of Camera.Parameters works on this platform - Recipe Lab
     * is built on it; only the LIST getters are hollow, as the starter
     * README warned. Each set is harmless if the firmware drops the key.
     */
    private void forceColorLook() {
        try {
            android.hardware.Camera cam = camera.getNormalCamera();
            android.hardware.Camera.Parameters p = cam.getParameters();
            String[] keys = { "color-mode", "saturation", "contrast", "sharpness",
                    "picture-effect", "rgb-matrix-mode" };
            StringBuilder before = new StringBuilder("look before:");
            for (String k : keys)
                before.append(' ').append(k).append('=').append(p.get(k));
            Logger.info(before.toString());

            p.set("color-mode", "standard"); // Creative Style; Recipe Lab's table: 6 = "mono"
            p.set("picture-effect", "off"); // PE off; rough-mono / richtone-mono are the mono PEs
            p.set("rgb-matrix-mode", "false"); // Recipe Lab always sends false
            cam.setParameters(p);

            android.hardware.Camera.Parameters back = cam.getParameters();
            StringBuilder after = new StringBuilder("look after:");
            for (String k : keys)
                after.append(' ').append(k).append('=').append(back.get(k));
            Logger.info(after.toString());
        } catch (Throwable t) {
            Logger.error("forceColorLook failed: " + Log.getStackTraceString(t));
        }
    }

    private void stopPreview() {
        if (camera == null)
            return;
        try {
            camera.getNormalCamera().stopPreview();
            Logger.info("preview stopped");
        } catch (Throwable t) {
            Logger.info("stopPreview: " + t); // not started / already stopped - fine
        }
    }

    /**
     * Restarts the HAL preview. Every takePicture stops it (Recipe Lab's
     * resumePreview()); if it is already running the call is a no-op, and a
     * HAL that objects throws in here rather than out there - logged, so a
     * silent failure can never hide again (the v0.2.1 lesson).
     */
    private void resumePreview() {
        try {
            if (camera != null)
                camera.getNormalCamera().startPreview();
        } catch (Throwable t) {
            Logger.error("resumePreview: " + t);
        }
    }

    // --- config screen ---

    private void showConfig() {
        overlayView.setLines(new String[]{
                "Intervalometer v" + version,
                "Interval " + mark(0, INTERVALS_S[intervalIdx] + " s")
                        + "  Count " + mark(2, countStr()),
                "Exposure " + mark(1, exposureStr())
                        + "  Delay " + mark(3, DELAYS_S[delayIdx] + " s"),
                "UP/DOWN value  LEFT/RIGHT field",
                "S2 start | trash exit"});
    }

    private String mark(int field, String value) {
        return selField == field ? "[" + value + "]" : value;
    }

    private String countStr() {
        return COUNTS[countIdx] > 0 ? String.valueOf(COUNTS[countIdx]) : "inf";
    }

    /**
     * The Exposure field's display: the camera's own shutter speed, or the app
     * bulb duration while the camera sits at the Bulb position.
     */
    private String exposureStr() {
        if (bulbPos)
            return "Bulb " + BULBS_S[bulbIdx] + " s";
        return fmtSpeed(shutterInfo);
    }

    /**
     * Bulb position encoding, CONFIRMED on camera (first 0.2.5 session): the
     * camera reports n=65535 (0xFFFF sentinel), d=1 - not the n==0/d==0
     * guess, which is why the field first showed "65535 s". One step up from
     * the timed end stop (30 s on this body) lands on Bulb, one step down
     * returns to 30 s. n==0 && d==0 kept as a defensive fallback; raw n/d
     * are still logged on every event.
     */
    private static boolean isBulb(CameraEx.ShutterSpeedInfo info) {
        return info != null && (info.currentShutterSpeed_n == 0xFFFF
                || (info.currentShutterSpeed_n == 0 && info.currentShutterSpeed_d == 0));
    }

    /** Human shutter speed: "30 s" / "1/125 s" / "Bulb"; "?" until the first change event. */
    private static String fmtSpeed(CameraEx.ShutterSpeedInfo info) {
        if (info == null)
            return "?";
        if (isBulb(info))
            return "Bulb";
        if (info.currentShutterSpeed_d == 1)
            return info.currentShutterSpeed_n + " s";
        return info.currentShutterSpeed_n + "/" + info.currentShutterSpeed_d + " s";
    }

    /** True while a run (or its summary) owns the screen. */
    private boolean busy() {
        return (engine != null && engine.isRunning()) || summaryShown;
    }

    private static int wrap(int i, int n) {
        return (i + n) % n;
    }

    /** UP/DOWN on the config screen: cycle the selected field's value. */
    private void adjust(int dir) {
        if (selField == 0)
            intervalIdx = wrap(intervalIdx + dir, INTERVALS_S.length);
        else if (selField == 1)
            adjustExposure(dir);
        else if (selField == 2)
            countIdx = wrap(countIdx + dir, COUNTS.length);
        else
            delayIdx = wrap(delayIdx + dir, DELAYS_S.length);
        showConfig();
    }

    /**
     * Exposure field: UP/DOWN step the camera's real shutter speed (software
     * state - the dials are just another input for the same value). At the
     * Bulb position the timed list has ended and the camera has no duration,
     * so UP/DOWN adjust the app-side bulb duration instead; DOWN below the
     * shortest steps back out of Bulb into the timed list.
     */
    private void adjustExposure(int dir) {
        try {
            if (bulbPos) {
                if (dir > 0) {
                    bulbIdx = wrap(bulbIdx + dir, BULBS_S.length);
                } else if (bulbIdx > 0) {
                    bulbIdx--;
                } else {
                    camera.decrementShutterSpeed(); // leave Bulb - camera reports the timed value again
                }
            } else if (dir > 0) {
                camera.incrementShutterSpeed();
            } else {
                camera.decrementShutterSpeed();
            }
        } catch (Throwable t) {
            Logger.error("shutter step: " + t); // e.g. mode disallows stepping - field keeps showing the camera's value
        }
        // The display refreshes on the ShutterSpeedChange callback (immediate
        // showConfig would repaint a stale value for timed steps).
    }

    /** One-time per session: dump every Camera.Parameters key the HAL supports. */
    private void logSupportedParameters() {
        try {
            String flat = camera.getSupportedParameters().flatten();
            Logger.info("supported parameters: " + flat.length() + " chars");
            for (int i = 0; i < flat.length(); i += 180) {
                int end = Math.min(flat.length(), i + 180);
                Logger.info("params[" + i + ".." + end + "]: " + flat.substring(i, end));
            }
        } catch (Throwable t) {
            Logger.error("supported parameters dump failed: " + t);
        }
    }

    // --- endurance plumbing ---

    /** APO off via the DAConnectionManagerService broadcast; renewed by the tick. */
    private void apoOff() {
        sendBroadcast(new Intent(ACTION_APO).putExtra("apo_info", "APO/NO"));
        lastApoMs = SystemClock.elapsedRealtime();
    }

    private void apoRestore() {
        try {
            sendBroadcast(new Intent(ACTION_APO).putExtra("apo_info", "APO/NORMAL"));
        } catch (Throwable ignored) {
        }
        lastApoMs = 0;
        Logger.info("apo restored");
    }

    // --- camera keys (BaseActivity hooks; this camera has no touch screen) ---

    @Override
    protected boolean onUpKeyDown() {
        if (busy())
            return true;
        adjust(1);
        return true;
    }

    @Override
    protected boolean onDownKeyDown() {
        if (busy())
            return true;
        adjust(-1);
        return true;
    }

    @Override
    protected boolean onLeftKeyDown() {
        if (busy())
            return true;
        selField = wrap(selField - 1, 4);
        showConfig();
        return true;
    }

    @Override
    protected boolean onRightKeyDown() {
        if (busy())
            return true;
        selField = wrap(selField + 1, 4);
        showConfig();
        return true;
    }

    @Override
    protected boolean onShutterKeyDown() {
        if (engine != null && engine.isRunning())
            return true; // mid-run S2 is ignored - the engine owns shooting
        if (summaryShown) {
            summaryShown = false;
            showConfig();
            return true;
        }
        startRun();
        return true;
    }

    @Override
    protected boolean onShutterKeyUp() {
        if (camera == null)
            return true;
        try {
            camera.cancelTakePicture();
        } catch (Throwable ignored) {
        }
        return true;
    }

    @Override
    protected boolean onDeleteKeyDown() {
        if (engine != null && engine.isRunning()) {
            engine.stop(IntervalEngine.REASON_ABORTED); // trash stops the run, stays in app
            return true;
        }
        return super.onDeleteKeyDown();
    }

    @Override
    protected boolean onDeleteKeyUp() {
        if (summaryShown)
            return true; // the stop already happened on key-down; keep the summary up
        return super.onDeleteKeyUp(); // config screen (or after summary): exit
    }

    @Override
    protected boolean onFocusKeyDown() {
        if (camera == null)
            return true;
        camera.getNormalCamera().autoFocus(null);
        return true;
    }

    @Override
    protected boolean onFocusKeyUp() {
        if (camera == null)
            return true;
        camera.getNormalCamera().cancelAutoFocus();
        return true;
    }

    /** Arms a run from the current config selection. */
    private void startRun() {
        if (camera == null) {
            Logger.error("cannot start: camera not open");
            return;
        }
        stored = 0;
        capSum = 0;
        capMax = 0;
        capN = 0;
        stillT0 = 0;
        summaryShown = false;
        bulbShotMs = bulbPos ? BULBS_S[bulbIdx] * 1000L : 0; // frozen per run - config changes mid-run do not apply
        Logger.info("exposure: " + exposureStr() + (bulbShotMs > 0
                ? " - bulb, app ends exposure via cancelExposure"
                : " - camera timed"));
        engine = new IntervalEngine(INTERVALS_S[intervalIdx] * 1000L,
                DELAYS_S[delayIdx] * 1000L, COUNTS[countIdx], engineListener);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        apoOff();
        engine.start();
    }

    /** CPU identity, once per session (decides the SIMD story for any future pixel work). */
    private void logCpuInfo() {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            while ((line = in.readLine()) != null)
                if (line.startsWith("Processor") || line.startsWith("Hardware")
                        || line.startsWith("Features"))
                    Logger.info("cpu: " + line.trim());
        } catch (IOException e) {
            Logger.info("cpuinfo unavailable: " + e);
        } finally {
            if (in != null)
                try {
                    in.close();
                } catch (IOException ignored) {
                }
        }
    }
}
