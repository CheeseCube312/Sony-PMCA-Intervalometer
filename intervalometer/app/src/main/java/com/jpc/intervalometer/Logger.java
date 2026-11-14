package com.jpc.intervalometer;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Appends log lines to /sdcard/<LOG_DIR>/LOG.TXT on the camera's SD card
 * (including full stack traces and a header naming the app version).
 * beginSession() moves the previous log to LOG.OLD.TXT - but this firmware's
 * storage rejects the rename (observed on-camera: sessions silently append),
 * so a failed rotation is logged and the file simply keeps growing.
 * Retrieve it by pulling the SD card (or USB mass storage) and opening
 * <LOG_DIR>/LOG.TXT on a PC.
 *
 * Retarget per app: LOG_DIR and TAG.
 */
public class Logger {
    public static final String TAG = "INTVL";
    public static final String LOG_DIR = "INTVL";

    public static File getFile() {
        return new File(Environment.getExternalStorageDirectory(), LOG_DIR + "/LOG.TXT");
    }

    /** Moves the previous log aside and starts a fresh one for this run. */
    public static synchronized void beginSession(String version) {
        File file = getFile();
        file.getParentFile().mkdirs();
        File old = new File(file.getParentFile(), "LOG.OLD.TXT");
        old.delete();
        // renameTo returns false both when the rename is rejected (this
        // firmware does that) and when no previous file exists (fresh card) -
        // only report a failure when there actually was a log to rotate.
        boolean hadPrevious = file.exists();
        boolean rotated = hadPrevious && file.renameTo(old);
        log("INFO", "--- " + TAG + " v" + version + " session start "
                + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + " ---");
        if (hadPrevious && !rotated)
            log("INFO", "log rotation failed - appending to previous sessions");
    }

    protected static synchronized void log(String msg) {
        Log.i(TAG, msg);
        try {
            getFile().getParentFile().mkdirs();
            BufferedWriter writer = new BufferedWriter(new FileWriter(getFile(), true));
            writer.append(new Date().toString()).append(" ").append(msg);
            writer.newLine();
            writer.close();
        } catch (IOException e) {
        }
    }

    protected static void log(String type, String msg) {
        log("[" + type + "] " + msg);
    }

    public static void info(String msg) {
        log("INFO", msg);
    }

    public static void error(String msg) {
        log("ERROR", msg);
    }
}