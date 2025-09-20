package com.example.oompa.services;

import android.content.Context;
import android.content.SharedPreferences;

public class earnedTimeCounter {
    private static final String PREFS_NAME = "EarnedTimePrefs";
    private static final String KEY_EARNED_TIME = "earnedTime";
    private static final String KEY_LAST_UPDATE = "lastUpdate";
    private static final String KEY_IS_COUNTING_DOWN = "isCountingDown";

    private long earnedTime;   // remaining time in ms
    private long lastUpdate;   // last system time when countdown was updated
    private boolean isCountingDown = false; // Only countdown when this is true

    private final SharedPreferences prefs;
    private static earnedTimeCounter instance;

    // 🔹 Private constructor
    earnedTimeCounter(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadFromPreferences();
    }

    // 🔹 Get singleton instance with context
    public static synchronized earnedTimeCounter getInstance(Context context) {
        if (instance == null) {
            instance = new earnedTimeCounter(context);
        }
        return instance;
    }

    // 🔹 Get existing instance (must call getInstance(context) once first)
    public static earnedTimeCounter getInstance() {
        if (instance == null) {
            throw new IllegalStateException("earnedTimeCounter not initialized. Call getInstance(context) first.");
        }
        return instance;
    }

    /** Add earned time in milliseconds */
    public void addTime(long millis) {
        // Only update elapsed time if we're currently counting down
        if (isCountingDown) {
            updateElapsedTime();
        }
        earnedTime += millis;
        saveToPreferences();
    }

    /** Start countdown (call this when unlock period begins) */
    public void startCountdown() {
        if (!isCountingDown && earnedTime > 0) {
            isCountingDown = true;
            lastUpdate = System.currentTimeMillis();
            saveToPreferences();
        }
    }

    /** Stop countdown (call this when unlock period ends) */
    public void stopCountdown() {
        if (isCountingDown) {
            updateElapsedTime(); // Update time before stopping
            isCountingDown = false;
            saveToPreferences();
        }
    }

    /** Manual countdown (only call this during active unlock period) */
    public void countdown() {
        if (isCountingDown) {
            updateElapsedTime();
            saveToPreferences();
        }
    }

    /** Update elapsed time if countdown is active */
    private void updateElapsedTime() {
        if (!isCountingDown) return;

        long now = System.currentTimeMillis();
        long delta = now - lastUpdate;

        if (earnedTime > 0) {
            earnedTime -= delta;
            if (earnedTime < 0) {
                earnedTime = 0;
                isCountingDown = false; // Auto-stop when time runs out
            }
        }

        lastUpdate = now;
    }

    /** Get remaining time WITHOUT automatically counting down */
    public long getEarnedTime() {
        // Only update if we're actively counting down
        if (isCountingDown) {
            updateElapsedTime();
        }
        return earnedTime;
    }

    /** Reset counter */
    public void reset() {
        earnedTime = 0;
        isCountingDown = false;
        lastUpdate = System.currentTimeMillis();
        saveToPreferences();
    }

    /** Check if there is any earned time left WITHOUT automatically counting down */
    public boolean hasTime() {
        // Only update if we're actively counting down
        if (isCountingDown) {
            updateElapsedTime();
        }
        return earnedTime > 0;
    }

    /** Check if countdown is currently active */
    public boolean isCountingDown() {
        return isCountingDown;
    }

    /** Get formatted time string */
    public String getFormattedTime() {
        long totalSeconds = getEarnedTime() / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    /** Save state */
    private void saveToPreferences() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(KEY_EARNED_TIME, earnedTime);
        editor.putLong(KEY_LAST_UPDATE, lastUpdate);
        editor.putBoolean(KEY_IS_COUNTING_DOWN, isCountingDown);
        editor.apply();
    }

    /** Load state */
    private void loadFromPreferences() {
        earnedTime = prefs.getLong(KEY_EARNED_TIME, 0);
        lastUpdate = prefs.getLong(KEY_LAST_UPDATE, System.currentTimeMillis());
        isCountingDown = prefs.getBoolean(KEY_IS_COUNTING_DOWN, false);

        // If we were counting down when app was killed, update elapsed time
        if (isCountingDown) {
            updateElapsedTime();
        }
    }
}