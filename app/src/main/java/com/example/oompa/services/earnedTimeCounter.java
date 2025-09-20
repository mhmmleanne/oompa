package com.example.oompa.services;

import android.content.Context;
import android.content.SharedPreferences;

public class earnedTimeCounter {
    private static final String PREFS_NAME = "EarnedTimePrefs";
    private static final String KEY_EARNED_TIME = "earnedTime";
    private static final String KEY_LAST_UPDATE = "lastUpdate";

    private long earnedTime;       // remaining time in milliseconds
    private long lastUpdate;       // last system time when countdown was updated
    private Context context;

    public earnedTimeCounter() {
        this.earnedTime = 0;
        this.lastUpdate = System.currentTimeMillis();
    }

    public earnedTimeCounter(Context context) {
        this.context = context;
        loadFromPreferences();
    }

    /** Add earned time in milliseconds */
    public void addTime(long millis) {
        // First update countdown so we don't overwrite elapsed time
        countdown();
        earnedTime += millis;
        saveToPreferences();
    }

    /** Call periodically when the user is active */
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
        countdown();  // update before returning
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

    /** Save state to SharedPreferences */
    private void saveToPreferences() {
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(KEY_EARNED_TIME, earnedTime);
            editor.putLong(KEY_LAST_UPDATE, lastUpdate);
            editor.apply();
        }
    }

    /** Load state from SharedPreferences */
    private void loadFromPreferences() {
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            earnedTime = prefs.getLong(KEY_EARNED_TIME, 0);
            lastUpdate = prefs.getLong(KEY_LAST_UPDATE, System.currentTimeMillis());

            // Update time based on elapsed time since last save
            countdown();
        } else {
            this.earnedTime = 0;
            this.lastUpdate = System.currentTimeMillis();
        }
    }
}