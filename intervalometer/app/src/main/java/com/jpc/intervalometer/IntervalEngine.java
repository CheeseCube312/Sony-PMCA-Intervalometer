package com.jpc.intervalometer;

import android.os.Handler;
import android.os.SystemClock;

/**
 * Fixed-grid interval scheduler (Phase 1).
 *
 * Shot n fires at t0 + startDelay + (n-1)*interval, recomputed from the
 * elapsedRealtime() anchor at every tick - never chained off the previous
 * shot's completion - so a slow capture (2-3 s store time on this body)
 * shifts nothing after it and drift stays zero by construction. Ticks run on
 * the main looper via Handler, at the next deadline or every 1 s, whichever
 * comes first (the 1 s heartbeat drives the countdown overlay and the
 * auto-power-off refresh without extra timers).
 *
 * Time base is SystemClock.elapsedRealtime() (monotonic); wall clock is only
 * in Logger stamps. The engine owns timing/state only - the activity fires
 * the actual capture from Listener.onShoot and renders from onTick.
 *
 * A late shot (Handler jitter, firmware busy) still fires and is logged with
 * its delta; slots are never skipped, so the log shows the true interval
 * floor. Since 0.2.4 a slot additionally holds while the previous capture is
 * still in flight (Listener.canShoot): firing into a busy camera silently
 * loses BOTH shots on this firmware - no error, no image (0.2.3 log: 5 s
 * interval vs a 30 s exposure lost 8 of 10 shots). A held slot fires when the
 * camera goes idle and shows up as "late" on its fire line, so with long
 * exposures the spacing stretches and interval acts as a minimum.
 * A slot fails only if the firmware rejects takePicture - onShoot's
 * error path logs that, and the stored-vs-fired count in the summary catches
 * it either way.
 */
public class IntervalEngine {
    /** Run callbacks - the activity wires camera + UI in here. Main thread only. */
    public interface Listener {
        /** Fire shot n (1-based) at its grid slot. */
        void onShoot(int n);
        /** True when the camera is idle (previous capture stored) - the engine never fires into a busy camera. */
        boolean canShoot();
        /** ~1 Hz heartbeat: elapsed ms, shots so far, ms until next deadline. */
        void onTick(long elapsedMs, int shotsFired, long nextShotInMs);
        /** Run over: REASON_*, shots fired, elapsed ms, late-shot count. */
        void onFinished(int reason, int shotsFired, long elapsedMs, int lateShots);
    }

    public static final int REASON_DONE = 1;    // shot count reached
    public static final int REASON_ABORTED = 2; // trash key / activity paused

    private static final String ACTION_APO = "com.android.server.DAConnectionManagerService.apo";

    private final Handler handler = new Handler();
    private final Listener listener;
    private final long intervalMs;
    private final long startDelayMs;
    private final int shotCount; // <= 0 = unlimited (runs until stopped)
    private long t0;
    private int fired, lateShots;
    private boolean running, holdLogged; // holdLogged: one line per camera-busy hold, not one per tick

    public IntervalEngine(long intervalMs, long startDelayMs, int shotCount, Listener listener) {
        this.intervalMs = intervalMs;
        this.startDelayMs = startDelayMs;
        this.shotCount = shotCount;
        this.listener = listener;
    }

    public boolean isRunning() {
        return running;
    }

    /** Arms the grid: shot 1 fires startDelay ms from now (0 = immediately). */
    public void start() {
        if (running)
            return;
        running = true;
        fired = 0;
        lateShots = 0;
        holdLogged = false;
        t0 = SystemClock.elapsedRealtime();
        Logger.info("run start: interval " + (intervalMs / 1000) + " s, count "
                + (shotCount > 0 ? String.valueOf(shotCount) : "unlimited")
                + ", delay " + (startDelayMs / 1000) + " s");
        handler.post(tick);
    }

    /** Stops the grid and reports. Safe to call when not running. */
    public void stop(int reason) {
        if (!running)
            return;
        running = false;
        handler.removeCallbacks(tick);
        long elapsed = SystemClock.elapsedRealtime() - t0;
        Logger.info("run finished: " + reasonName(reason) + ", " + fired + " shots, elapsed "
                + mmss(elapsed) + ", late " + lateShots);
        listener.onFinished(reason, fired, elapsed, lateShots);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running)
                return;
            long now = SystemClock.elapsedRealtime();
            // Fire everything the grid is due for (Handler jitter can bunch ticks).
            while (running) {
                long deadline = t0 + startDelayMs + (long) fired * intervalMs;
                if (now < deadline)
                    break;
                if (!listener.canShoot()) {
                    // Previous capture still in flight - firing now would
                    // silently lose both shots. Hold the slot; it fires when
                    // the camera goes idle, logged as late on the fire line.
                    if (!holdLogged) {
                        holdLogged = true;
                        Logger.info("shot " + (fired + 1) + " held - camera busy, previous capture in flight");
                    }
                    break;
                }
                holdLogged = false;
                fired++;
                long late = now - deadline;
                if (late > 0)
                    lateShots++;
                Logger.info("shot " + fired + " fired @ +" + mmss(now - t0) + " (planned +"
                        + mmss(deadline - t0)
                        + (late > 0 ? ", late " + late + " ms)" : ", on time)"));
                listener.onShoot(fired);
                if (shotCount > 0 && fired >= shotCount) {
                    stop(REASON_DONE);
                    return;
                }
                now = SystemClock.elapsedRealtime(); // captures take seconds - re-read
            }
            long next = t0 + startDelayMs + (long) fired * intervalMs;
            listener.onTick(now - t0, fired, Math.max(0, next - now));
            // Re-tick at the deadline or in 1 s (heartbeat), whichever is sooner.
            long wait = Math.max(0, next - SystemClock.elapsedRealtime());
            handler.postDelayed(this, Math.min(wait, 1000));
        }
    };

    static String reasonName(int reason) {
        return reason == REASON_DONE ? "done" : reason == REASON_ABORTED ? "aborted" : "?" + reason;
    }

    static String mmss(long ms) {
        long s = ms / 1000;
        return String.format("%02d:%02d", s / 60, s % 60);
    }
}
