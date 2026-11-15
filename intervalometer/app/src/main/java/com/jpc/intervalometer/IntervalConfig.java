package com.jpc.intervalometer;

import android.content.SharedPreferences;

/**
 * The config screen's state: preset tables, CUSTOM slots, stepping rules,
 * persistence and display strings. Extracted from IntervalActivity (0.2.7)
 * so the activity keeps camera, exposure and screen work only - this is the
 * one place that knows what a config choice is.
 *
 * Persistence (0.2.6): SharedPreferences - the store Recipe Lab proves works
 * on this firmware. Survives app restarts; goes with an uninstall
 * (documented trade-off). Every index clamps on load, so a hand-edited or
 * older file can never select a nonexistent slot, and the lists only ever
 * grow at the end, so persisted indices from older versions keep pointing
 * at the same value. The exposure POSITION is not ours to persist - the
 * camera keeps its own shutter-speed store and the activity's field mirrors
 * it. Only the custom long-exposure seconds live here (0.2.7 UX: the old
 * bulb-preset index key is stale and no longer read).
 *
 * CUSTOM slots (0.4.0c rework): an ordinary entry in each list - LEFT/RIGHT
 * only SELECTS it, nothing edits in place anymore. ENTER on it opens the
 * activity's full-screen value editor (h/m/s units for the interval, a
 * four-digit wheel for the count) and the confirmed value persists here.
 * The count's CUSTOM sits before "all" (custom, then unlimited last), which
 * re-points the old indices - the count persists under a fresh key
 * ("countSlot2"), the same one-time-reset pattern the 0.2.11 rework used.
 * The long exposure is the same idea taken further (0.2.7 UX): the timed
 * range is the camera's own stock list, and the single app-side value at
 * the Bulb position is always custom - 5-1800 s, still edited by LEFT/RIGHT
 * in place (two step sizes need no editor).
 *
 * 0.2.11 - count rework: the old {10, 60, all} list becomes a 5-shot-step
 * ladder 5..600 ending in unlimited ("all"). Same wrap-around stepping as
 * the other fields; the engine treats <= 0 as unlimited. The old indices
 * would silently re-point under the new list, so the slot persists under a
 * fresh key ("countSlot") - one-time reset to the 60-shot default on the
 * first 0.2.11 launch.
 */
public class IntervalConfig {
    /** Config choices are discrete lists - no typing on a 4-key camera. */
    private static final int[] INTERVALS_S = {2, 5, 10, 15, 30, 60, 120, 300, 600, 1800, 3600};
    // 0.2.11 ladder, reworked 0.4.0c: 5..600 in 5-shot steps, then CUSTOM
    // (sentinel 0 - the value lives in customCount), then unlimited (-1).
    private static final int[] COUNTS = new int[122];
    static {
        for (int i = 0; i < 120; i++)
            COUNTS[i] = 5 * (i + 1);
        COUNTS[120] = 0;  // CUSTOM slot - ENTER opens the digit editor
        COUNTS[121] = -1; // unlimited - the engine runs until stopped (shotCount <= 0)
    }
    private static final int[] DELAYS_S = {0, 2, 5, 10};
    private static final int DEF_INTERVAL = 4; // 30 s - the Phase 1 gate value
    private static final int DEF_COUNT = 11;   // 60 shots = 30 min @ 30 s
    private static final int DEF_DELAY = 0;
    // 0.4.0c: the h/m/s editor reaches 99:59:59 - the cap follows (was 3600).
    private static final int INTERVAL_CUSTOM_DEF = 45, INTERVAL_CUSTOM_MIN = 2, INTERVAL_CUSTOM_MAX = 359999;
    // the CUSTOM shot count (0.4.0c): four digits, 1-9999
    private static final int COUNT_CUSTOM_DEF = 750, COUNT_CUSTOM_MIN = 1, COUNT_CUSTOM_MAX = 9999;
    // the custom long exposure - the one app-side exposure value (camera Bulb position)
    private static final int BULB_CUSTOM_DEF = 60, BULB_CUSTOM_MIN = 5, BULB_CUSTOM_MAX = 1800;

    private final SharedPreferences prefs;

    // the persisted state - every field clamped on load
    private int intervalIdx = DEF_INTERVAL;
    private int countIdx = DEF_COUNT;
    private int delayIdx = DEF_DELAY;
    private int customIntervalS = INTERVAL_CUSTOM_DEF;
    private int customCount = COUNT_CUSTOM_DEF;
    private int customBulbS = BULB_CUSTOM_DEF;
    private boolean pwr; // 0.3.0 power-saving run mode - panel off during runs, wake HUD (default OFF)
    private boolean debugLog; // 0.3.1 verbose-logging gate - the Logger's DEBUG tier (default OFF)

    /** The debug-log flag without a full load - onCreate arms the Logger before the session header. */
    public static boolean readDebugLog(SharedPreferences prefs) {
        return prefs.getBoolean("debugLog", false);
    }

    /** Reads the persisted choices (clamped) - safe to construct in onCreate. */
    public IntervalConfig(SharedPreferences prefs) {
        this.prefs = prefs;
        load();
    }

    // --- persistence ---

    private void load() {
        intervalIdx = clampIdx(prefs.getInt("intervalIdx", DEF_INTERVAL), INTERVALS_S.length + 1);
        // 0.2.11: the count list was reworked (5-shot steps + unlimited), which
        // shifts indices - a fresh key instead of "countIdx" so a 0.2.10 store
        // can't silently select a different count (the lists-grow-at-the-end
        // rule covers appends, not deliberate reworks). A missing key lands on
        // DEF_COUNT (60); a corrupt one clamps to slot 0 (5 shots) - both safe.
        // 0.4.0c: the CUSTOM count moved before "all" (an index shift) - the
        // count persists under a fresh key ("countSlot2"), one-time reset to
        // the 60-shot default on the first 0.4.0c launch (0.2.11's pattern).
        countIdx = clampIdx(prefs.getInt("countSlot2", DEF_COUNT), COUNTS.length);
        delayIdx = clampIdx(prefs.getInt("delayIdx", DEF_DELAY), DELAYS_S.length);
        customIntervalS = clampInt(prefs.getInt("customIntervalS", INTERVAL_CUSTOM_DEF),
                INTERVAL_CUSTOM_MIN, INTERVAL_CUSTOM_MAX);
        customCount = clampInt(prefs.getInt("customCount", COUNT_CUSTOM_DEF),
                COUNT_CUSTOM_MIN, COUNT_CUSTOM_MAX);
        customBulbS = clampInt(prefs.getInt("customBulbS", BULB_CUSTOM_DEF),
                BULB_CUSTOM_MIN, BULB_CUSTOM_MAX);
        pwr = prefs.getBoolean("pwr", false);
        debugLog = readDebugLog(prefs);
        Logger.info("config loaded: interval " + intervalStr() + ", count " + countStr()
                + ", delay " + delayStr() + ", long exposure " + bulbStr()
                + ", power saving " + pwrStr() + ", debug log " + debugLogStr());
    }

    /** Persists every choice; called after each config-screen edit. */
    public void save() {
        SharedPreferences.Editor e = prefs.edit();
        e.putInt("intervalIdx", intervalIdx);
        e.putInt("countSlot2", countIdx); // fresh key with the 0.4.0c CUSTOM-count rework (see load)
        e.putInt("customCount", customCount);
        e.putInt("delayIdx", delayIdx);
        e.putInt("customIntervalS", customIntervalS);
        e.putInt("customBulbS", customBulbS);
        e.putBoolean("pwr", pwr);
        e.putBoolean("debugLog", debugLog);
        e.apply();
    }

    // --- config-screen stepping (LEFT/RIGHT per field) ---

    /**
     * Interval field (0.4.0c): LEFT/RIGHT walk the presets and the CUSTOM
     * slot like any other list entry. The custom value is NOT edited here
     * anymore - while the slot is selected, the activity opens its h/m/s
     * editor on ENTER instead.
     */
    public void adjustInterval(int dir) {
        intervalIdx = wrap(intervalIdx + dir, INTERVALS_S.length + 1); // +1 = the CUSTOM slot
    }

    /**
     * Custom long-exposure seconds (the Exposure field while the camera sits
     * at Bulb; 0.2.7 UX - the timed range is the camera's stock list, this is
     * the one app-side value): UP/DOWN edit in place, 5 s steps below the
     * 60 s mark and 30 s at and above, clamped to 5-1800 s.
     *
     * @return false only when already at the 5 s floor and the camera should
     *         leave the Bulb position - the caller steps the camera's real
     *         shutter speed down into the timed list; everything else is
     *         handled here.
     */
    public boolean adjustBulb(int dir) {
        if (dir > 0) {
            customBulbS = Math.min(BULB_CUSTOM_MAX, customBulbS + bulbStep());
        } else if (customBulbS - bulbStep() >= BULB_CUSTOM_MIN) {
            customBulbS -= bulbStep();
        } else {
            return false; // at the 5 s floor - leave Bulb
        }
        return true;
    }

    /**
     * Floors the long-exposure duration for a fresh timed->Bulb entry
     * (0.4.0): the exposure dial lands on BULB 5 s one step past the 30 s
     * end stop - the same handshake value the LEFT floor-exit leaves from -
     * so the breakover reads as one continuous dial in both directions.
     */
    public void resetBulbToFloor() {
        customBulbS = BULB_CUSTOM_MIN;
    }

    /**
     * Direct write, used only by IntervalActivity's swallowed-entry
     * self-check (0.4.0) to restore the pre-entry duration when the Bulb
     * step proves lost. Clamped to the 5-1800 s range like every edit path.
     */
    public void setBulbSeconds(int s) {
        customBulbS = Math.max(BULB_CUSTOM_MIN, Math.min(BULB_CUSTOM_MAX, s));
    }

    public void adjustCount(int dir) {
        countIdx = wrap(countIdx + dir, COUNTS.length);
    }

    public void adjustDelay(int dir) {
        delayIdx = wrap(delayIdx + dir, DELAYS_S.length);
    }

    /** 0.3.0: PWR field toggle - direction is irrelevant on an ON/OFF choice. */
    public void adjustPwr() {
        pwr = !pwr;
    }

    /** 0.3.1: DEBUG field toggle; returns the new state so the caller arms the Logger live. */
    public boolean adjustDebugLog() {
        debugLog = !debugLog;
        return debugLog;
    }

    /** Long-exposure edit step: 5 s in the short range, 30 s at 60 s and up. */
    private int bulbStep() {
        return customBulbS < 60 ? 5 : 30;
    }

    // --- the values a run consumes ---

    /** The interval THIS run uses: the preset list value, or the custom slot's value. */
    public int intervalSeconds() {
        return intervalIdx < INTERVALS_S.length ? INTERVALS_S[intervalIdx] : customIntervalS;
    }

    /** The long-exposure duration (s) THIS run uses: the camera exposes in Bulb until cancelExposure ends it. */
    public int bulbSeconds() {
        return customBulbS;
    }

    /** Start delay in seconds (0 = fire immediately). */
    public int delaySeconds() {
        return DELAYS_S[delayIdx];
    }

    /** Shots per run: the ladder value, the CUSTOM count, or -1 = unlimited. */
    public int shotCount() {
        int v = COUNTS[countIdx];
        return v == 0 ? customCount : v;
    }

    /** True while the interval selection sits on the CUSTOM slot (ENTER opens the h/m/s editor). */
    public boolean intervalIsCustom() {
        return intervalIdx == INTERVALS_S.length;
    }

    /** True while the count selection sits on the CUSTOM slot (ENTER opens the digit editor). */
    public boolean countIsCustom() {
        return COUNTS[countIdx] == 0;
    }

    /** The CUSTOM shot count's current value (the digit editor seeds from it). */
    public int customCount() {
        return customCount;
    }

    /** Writes the interval editor's confirmed h/m/s - units clamped, total to 99:59:59. */
    public void setCustomIntervalHms(int h, int m, int s) {
        h = Math.max(0, Math.min(99, h));
        m = Math.max(0, Math.min(59, m));
        s = Math.max(0, Math.min(59, s));
        customIntervalS = clampInt(h * 3600 + m * 60 + s, INTERVAL_CUSTOM_MIN, INTERVAL_CUSTOM_MAX);
    }

    /** Writes the count editor's confirmed value, clamped to 1-9999. */
    public void setCustomCount(int n) {
        customCount = clampInt(n, COUNT_CUSTOM_MIN, COUNT_CUSTOM_MAX);
    }

    /** True when runs start dark (0.3.0 power-saving mode). */
    public boolean pwr() {
        return pwr;
    }

    /** True while the verbose-logging gate is armed (0.3.1). */
    public boolean debugLog() {
        return debugLog;
    }

    /** True at the 5 s floor - DOWN would step out of Bulb (the caller steps the camera). */
    public boolean bulbAtFloor() {
        return customBulbS <= BULB_CUSTOM_MIN;
    }

    // --- display strings (config screen + logs) ---

    public String intervalStr() {
        return fmtDur(intervalSeconds()) + (intervalIsCustom() ? " (custom)" : "");
    }

    /**
     * Grid display for the INTERVAL field (0.4.0): the custom slot drops
     * "(custom)" - the chip and the hint line carry that - and both slots
     * share fmtDur, so the custom ladder reads like the presets: 2 min,
     * 2.5 min, 3 min... instead of flipping back to raw seconds.
     */
    public String intervalGridStr() {
        return fmtDur(intervalSeconds());
    }

    public String bulbStr() {
        return customBulbS + " s";
    }

    public String countStr() {
        int v = COUNTS[countIdx];
        return v > 0 ? String.valueOf(v) : v == 0 ? String.valueOf(customCount) : "all";
    }

    public String delayStr() {
        return DELAYS_S[delayIdx] + " s";
    }

    public String pwrStr() {
        return pwr ? "ON" : "OFF";
    }

    public String debugLogStr() {
        return debugLog ? "ON" : "OFF";
    }

    /**
     * Durations in friendly units: raw seconds under 2 min (60 s), whole and
     * half minutes from 2 min up (2 min, 2.5 min, 3 min), whole and half
     * hours at and above 1 h (1 h, 1.5 h, 2 h - the h/m/s editor's range).
     * The free-form editor makes off-grid values ordinary now, so anything
     * else reads out in full (3 min 17 s, 1 h 1 min 1 s) instead of raw
     * seconds - truthful, no rounding.
     */
    private static String fmtDur(int s) {
        if (s >= 3600 && s % 1800 == 0) {
            int h = s / 3600, m = (s % 3600) / 60;
            return m == 0 ? h + " h" : h + ".5 h";
        }
        if (s >= 120 && s % 30 == 0)
            return s % 60 == 0 ? (s / 60) + " min" : (s / 60) + ".5 min";
        if (s >= 120) {
            int h = s / 3600, m = (s % 3600) / 60;
            StringBuilder b = new StringBuilder();
            if (h > 0)
                b.append(h).append(" h ");
            if (m > 0 || h > 0)
                b.append(m).append(" min ");
            return b.append(s % 60).append(" s").toString();
        }
        return s + " s";
    }

    /**
     * The series estimate's units: minutes under an hour, hours + minutes
     * above (seconds are noise on an estimate that already pads ~3 s of
     * store time per frame).
     */
    static String fmtHm(long s) {
        if (s < 60)
            return s + " s";
        if (s < 3600)
            return (s / 60) + " min";
        long h = s / 3600, m = (s % 3600) / 60;
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }

    /** Observed per-frame store time on this body (2-3 s) - the estimate's padding. */
    private static final int EST_STORE_S = 3;

    /**
     * Series wall-clock estimate: delay + (shots-1) x cadence + one exposure
     * + one store, where the cadence floor is exposure + store (the engine
     * never fires into a busy camera - a long exposure stretches the spacing
     * past a short interval, see IntervalEngine's hold rule). shots <= 0
     * (unlimited) estimates nothing.
     */
    static long seriesSeconds(int intervalS, int delayS, int shots, int exposureS) {
        if (shots <= 0)
            return 0;
        long cadence = Math.max(intervalS, exposureS + EST_STORE_S);
        return delayS + (long) (shots - 1) * cadence + exposureS + EST_STORE_S;
    }

    // --- clamps (a hand-edited or older store can never select a bad slot) ---

    private static int clampIdx(int i, int n) {
        return i >= 0 && i < n ? i : 0;
    }

    private static int clampInt(int v, int min, int max) {
        return v < min ? min : Math.min(max, v);
    }

    private static int wrap(int i, int n) {
        return (i + n) % n;
    }
}