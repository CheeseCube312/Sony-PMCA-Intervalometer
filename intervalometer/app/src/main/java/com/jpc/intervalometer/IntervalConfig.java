package com.jpc.intervalometer;

import android.content.SharedPreferences;

/**
 * The config screen's state: preset tables, CUSTOM slots, stepping rules,
 * persistence and display strings. Extracted from IntervalActivity (0.2.7)
 * so the activity keeps camera, exposure and screen work only - this is the
 * one place that knows what a config choice is. No behavior change vs 0.2.6.
 *
 * Persistence (0.2.6): SharedPreferences - the store Recipe Lab proves works
 * on this firmware. Survives app restarts; goes with an uninstall
 * (documented trade-off). Every index clamps on load, so a hand-edited or
 * older file can never select a nonexistent slot, and the lists only ever
 * grow at the end, so persisted indices from older versions keep pointing
 * at the same value. The exposure POSITION is not ours to persist - the
 * camera keeps its own shutter-speed store and the activity's field mirrors
 * it; only the app-side bulb duration lives here.
 *
 * CUSTOM slots: stepping past the last preset lands on a per-session value
 * edited in place and persisted with the config. It is a value, not a named
 * preset - the slot's position in the list stays fixed (append-only rule
 * above).
 */
public class IntervalConfig {
    /** Config choices are discrete lists - no typing on a 4-key camera. */
    private static final int[] INTERVALS_S = {2, 5, 10, 15, 30, 60, 120, 300, 600, 1800, 3600};
    private static final int[] COUNTS = {10, 60, -1}; // -1 = unlimited
    private static final int[] DELAYS_S = {0, 2, 5, 10};
    private static final int[] BULBS_S = {5, 10, 15, 30, 60, 120, 300, 600}; // app-side bulb duration
    private static final int DEF_INTERVAL = 4; // 30 s - the Phase 1 gate value
    private static final int DEF_COUNT = 1;    // 60 shots = 30 min @ 30 s
    private static final int DEF_DELAY = 0;
    private static final int DEF_BULB = 4;     // 60 s bulb exposure
    private static final int INTERVAL_CUSTOM_DEF = 45, INTERVAL_CUSTOM_MIN = 2, INTERVAL_CUSTOM_MAX = 3600;
    private static final int BULB_CUSTOM_DEF = 60, BULB_CUSTOM_MIN = 5, BULB_CUSTOM_MAX = 1800;

    private final SharedPreferences prefs;

    // the persisted state - every field clamped on load
    private int intervalIdx = DEF_INTERVAL;
    private int countIdx = DEF_COUNT;
    private int delayIdx = DEF_DELAY;
    private int bulbIdx = DEF_BULB;
    private int customIntervalS = INTERVAL_CUSTOM_DEF;
    private int customBulbS = BULB_CUSTOM_DEF;

    /** Reads the persisted choices (clamped) - safe to construct in onCreate. */
    public IntervalConfig(SharedPreferences prefs) {
        this.prefs = prefs;
        load();
    }

    // --- persistence ---

    private void load() {
        intervalIdx = clampIdx(prefs.getInt("intervalIdx", DEF_INTERVAL), INTERVALS_S.length + 1);
        countIdx = clampIdx(prefs.getInt("countIdx", DEF_COUNT), COUNTS.length);
        delayIdx = clampIdx(prefs.getInt("delayIdx", DEF_DELAY), DELAYS_S.length);
        bulbIdx = clampIdx(prefs.getInt("bulbIdx", DEF_BULB), BULBS_S.length + 1);
        customIntervalS = clampInt(prefs.getInt("customIntervalS", INTERVAL_CUSTOM_DEF),
                INTERVAL_CUSTOM_MIN, INTERVAL_CUSTOM_MAX);
        customBulbS = clampInt(prefs.getInt("customBulbS", BULB_CUSTOM_DEF),
                BULB_CUSTOM_MIN, BULB_CUSTOM_MAX);
        Logger.info("config loaded: interval " + intervalStr() + ", count " + countStr()
                + ", delay " + delayStr() + ", bulb choice " + bulbStr());
    }

    /** Persists every choice; called after each config-screen edit. */
    public void save() {
        SharedPreferences.Editor e = prefs.edit();
        e.putInt("intervalIdx", intervalIdx);
        e.putInt("countIdx", countIdx);
        e.putInt("delayIdx", delayIdx);
        e.putInt("bulbIdx", bulbIdx);
        e.putInt("customIntervalS", customIntervalS);
        e.putInt("customBulbS", customBulbS);
        e.apply();
    }

    // --- config-screen stepping (UP/DOWN per field) ---

    /**
     * Interval field: UP/DOWN walk the presets and one CUSTOM slot
     * (index == INTERVALS_S.length). ON the custom slot the same keys edit
     * the value in place (intervalStep()); stepping below its floor returns
     * to the presets at the top (3600 s).
     */
    public void adjustInterval(int dir) {
        if (intervalIdx == INTERVALS_S.length) { // CUSTOM slot - UP/DOWN edit the value in place
            int v = customIntervalS + dir * intervalStep();
            if (v < INTERVAL_CUSTOM_MIN)
                intervalIdx = INTERVALS_S.length - 1; // below the floor: back into the presets
            else
                customIntervalS = Math.min(INTERVAL_CUSTOM_MAX, v);
        } else {
            intervalIdx = wrap(intervalIdx + dir, INTERVALS_S.length + 1); // +1 = the CUSTOM slot
        }
    }

    /**
     * Bulb duration (the Exposure field while the camera sits at Bulb): UP
     * walks the presets then the CUSTOM slot, DOWN reverses; below the 5 s
     * floor steps back into the presets.
     *
     * @return false only when already at the shortest value and the camera
     *         should leave the Bulb position - the caller steps the camera's
     *         real shutter speed down; everything else is handled here.
     */
    public boolean adjustBulb(int dir) {
        if (dir > 0) {
            if (bulbIdx < BULBS_S.length)
                bulbIdx++; // walks the presets, then steps into the CUSTOM slot
            else if (customBulbS < BULB_CUSTOM_MAX)
                customBulbS = Math.min(BULB_CUSTOM_MAX, customBulbS + bulbStep());
        } else if (bulbIdx == BULBS_S.length) { // CUSTOM slot
            if (customBulbS - bulbStep() >= BULB_CUSTOM_MIN)
                customBulbS -= bulbStep();
            else
                bulbIdx = BULBS_S.length - 1; // below the 5 s floor: back into the presets
        } else if (bulbIdx > 0) {
            bulbIdx--;
        } else {
            return false; // shortest already - leave Bulb
        }
        return true;
    }

    public void adjustCount(int dir) {
        countIdx = wrap(countIdx + dir, COUNTS.length);
    }

    public void adjustDelay(int dir) {
        delayIdx = wrap(delayIdx + dir, DELAYS_S.length);
    }

    /** CUSTOM interval slot edit step: 5 s below the 120 s mark, 30 s at and above. */
    private int intervalStep() {
        return customIntervalS < 120 ? 5 : 30;
    }

    /** CUSTOM bulb slot edit step: 5 s in the short range, 30 s at 60 s and up. */
    private int bulbStep() {
        return customBulbS < 60 ? 5 : 30;
    }

    // --- the values a run consumes ---

    /** The interval THIS run uses: the preset list value, or the custom slot's value. */
    public int intervalSeconds() {
        return intervalIdx < INTERVALS_S.length ? INTERVALS_S[intervalIdx] : customIntervalS;
    }

    /** The bulb duration THIS run uses: preset or custom slot. */
    public int bulbSeconds() {
        return bulbIdx < BULBS_S.length ? BULBS_S[bulbIdx] : customBulbS;
    }

    /** Start delay in seconds (0 = fire immediately). */
    public int delaySeconds() {
        return DELAYS_S[delayIdx];
    }

    /** Shots per run; -1 = unlimited. */
    public int shotCount() {
        return COUNTS[countIdx];
    }

    // --- display strings (config screen + logs) ---

    public String intervalStr() {
        return intervalIdx < INTERVALS_S.length ? fmtDur(INTERVALS_S[intervalIdx])
                : customIntervalS + " s (custom)";
    }

    public String bulbStr() {
        return bulbIdx < BULBS_S.length ? fmtDur(BULBS_S[bulbIdx]) : customBulbS + " s (custom)";
    }

    public String countStr() {
        return COUNTS[countIdx] > 0 ? String.valueOf(COUNTS[countIdx]) : "inf";
    }

    public String delayStr() {
        return DELAYS_S[delayIdx] + " s";
    }

    /** Preset durations in friendly units: 60 s stays "60 s", 120 s becomes "2 min", 3600 s "1 h". */
    private static String fmtDur(int s) {
        if (s >= 3600)
            return (s / 3600) + " h";
        if (s >= 120)
            return (s / 60) + " min";
        return s + " s";
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