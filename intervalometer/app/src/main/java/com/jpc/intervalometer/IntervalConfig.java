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
 * CUSTOM slots: stepping past the last preset lands on a per-session value
 * edited in place and persisted with the config. It is a value, not a named
 * preset - the slot's position in the list stays fixed (append-only rule
 * above). The long exposure is the same idea taken further (0.2.7 UX): the
 * timed range is the camera's own stock list, and the single app-side value
 * at the Bulb position is always custom - 5-1800 s, no preset list.
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
    // 0.2.11 rework: 5..600 in 5-shot steps, then unlimited (-1) - 121 slots.
    private static final int[] COUNTS = new int[121];
    static {
        for (int i = 0; i < 120; i++)
            COUNTS[i] = 5 * (i + 1);
        COUNTS[120] = -1; // unlimited - the engine runs until stopped (shotCount <= 0)
    }
    private static final int[] DELAYS_S = {0, 2, 5, 10};
    private static final int DEF_INTERVAL = 4; // 30 s - the Phase 1 gate value
    private static final int DEF_COUNT = 11;   // 60 shots = 30 min @ 30 s
    private static final int DEF_DELAY = 0;
    private static final int INTERVAL_CUSTOM_DEF = 45, INTERVAL_CUSTOM_MIN = 2, INTERVAL_CUSTOM_MAX = 3600;
    // the custom long exposure - the one app-side exposure value (camera Bulb position)
    private static final int BULB_CUSTOM_DEF = 60, BULB_CUSTOM_MIN = 5, BULB_CUSTOM_MAX = 1800;

    private final SharedPreferences prefs;

    // the persisted state - every field clamped on load
    private int intervalIdx = DEF_INTERVAL;
    private int countIdx = DEF_COUNT;
    private int delayIdx = DEF_DELAY;
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
        // 0.2.11: the count list was reworked (5-shot steps + unlimited), which
        // shifts indices - a fresh key instead of "countIdx" so a 0.2.10 store
        // can't silently select a different count (the lists-grow-at-the-end
        // rule covers appends, not deliberate reworks). A missing key lands on
        // DEF_COUNT (60); a corrupt one clamps to slot 0 (5 shots) - both safe.
        countIdx = clampIdx(prefs.getInt("countSlot", DEF_COUNT), COUNTS.length);
        delayIdx = clampIdx(prefs.getInt("delayIdx", DEF_DELAY), DELAYS_S.length);
        customIntervalS = clampInt(prefs.getInt("customIntervalS", INTERVAL_CUSTOM_DEF),
                INTERVAL_CUSTOM_MIN, INTERVAL_CUSTOM_MAX);
        customBulbS = clampInt(prefs.getInt("customBulbS", BULB_CUSTOM_DEF),
                BULB_CUSTOM_MIN, BULB_CUSTOM_MAX);
        Logger.info("config loaded: interval " + intervalStr() + ", count " + countStr()
                + ", delay " + delayStr() + ", long exposure " + bulbStr());
    }

    /** Persists every choice; called after each config-screen edit. */
    public void save() {
        SharedPreferences.Editor e = prefs.edit();
        e.putInt("intervalIdx", intervalIdx);
        e.putInt("countSlot", countIdx); // fresh key with the 0.2.11 count rework (see load)
        e.putInt("delayIdx", delayIdx);
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

    /** Shots per run; -1 = unlimited. */
    public int shotCount() {
        return COUNTS[countIdx];
    }

    /** True while the interval selection sits on the CUSTOM slot (UP/DOWN edit the value). */
    public boolean intervalIsCustom() {
        return intervalIdx == INTERVALS_S.length;
    }

    /** True at the 5 s floor - DOWN would step out of Bulb (the caller steps the camera). */
    public boolean bulbAtFloor() {
        return customBulbS <= BULB_CUSTOM_MIN;
    }

    // --- display strings (config screen + logs) ---

    public String intervalStr() {
        return intervalIdx < INTERVALS_S.length ? fmtDur(INTERVALS_S[intervalIdx])
                : customIntervalS + " s (custom)";
    }

    public String bulbStr() {
        return customBulbS + " s";
    }

    public String countStr() {
        return COUNTS[countIdx] > 0 ? String.valueOf(COUNTS[countIdx]) : "all";
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