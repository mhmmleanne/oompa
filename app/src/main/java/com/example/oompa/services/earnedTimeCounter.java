package com.example.oompa.services;

import android.content.Context;
import android.content.SharedPreferences;

public class earnedTimeCounter {
    private static final String PREFS_NAME = "EarnedTimePrefs";
    private static final String KEY_EARNED_TIME = "earnedTime";
    private static final String KEY_LAST_UPDATE = "lastUpdate";

    private long earnedTime;   // remaining time in ms
    private long lastUpdate;   // last system time when countdown was updated

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
        countdown();
        earnedTime += millis;
        saveToPreferences();
    }

    /** Countdown based on elapsed time */
    public void countdown() {
        long now = System.currentTimeMillis();
        long delta = now - lastUpdate;

        if (earnedTime > 0) {
            earnedTime -= delta;
            if (earnedTime < 0) earnedTime = 0;
        }

        lastUpdate = now;
        saveToPreferences();
    }

    /** Get remaining time */
    public long getEarnedTime() {
        countdown();
        return earnedTime;
    }

    /** Reset counter */
    public void reset() {
        earnedTime = 0;
        lastUpdate = System.currentTimeMillis();
        saveToPreferences();
    }

    /** Check if there is any earned time left */
    public boolean hasTime() {
        countdown();
        return earnedTime > 0;
    }

    /** Get formatted time string (MM:SS) */
    public String getFormattedTime() {
        long timeInSeconds = getEarnedTime() / 1000;
        long minutes = timeInSeconds / 60;
        long seconds = timeInSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /** Save state */
    private void saveToPreferences() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(KEY_EARNED_TIME, earnedTime);
        editor.putLong(KEY_LAST_UPDATE, lastUpdate);
        editor.apply();
    }

    /** Load state */
    private void loadFromPreferences() {
        earnedTime = prefs.getLong(KEY_EARNED_TIME, 0);
        lastUpdate = prefs.getLong(KEY_LAST_UPDATE, System.currentTimeMillis());
        countdown(); // sync with real elapsed time
    }
}
