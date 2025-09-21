package com.example.oompa.services;

import android.content.Context;


public class earnedTimeCounter {
    private static earnedTimeCounter instance;
    private final PreferenceManager pref;

    private earnedTimeCounter(Context context) {
        pref = new PreferenceManager(context.getApplicationContext());
    }

    public static synchronized earnedTimeCounter getInstance(Context context) {
        if (instance == null) {
            instance = new earnedTimeCounter(context);
        }
        return instance;
    }

    public static earnedTimeCounter getInstance() {
        if (instance == null) {
            throw new IllegalStateException("earnedTimeCounter not initialized. Call getInstance(context) first.");
        }
        return instance;
    }

    // --- Credits (static) ---
    public void addTime(long millis) {
        pref.addEarnedCredits(millis);
    }

    public long getEarnedTime() {
        return pref.getEarnedCredits();
    }

    public void resetCredits() {
        pref.clearEarnedCredits();
    }

    // --- Unlock session ---
    public void startCountdown(long durationMillis) {
        pref.startUnlockCountdown(durationMillis);
    }

    public void stopCountdown() {
        pref.clearUnlockCountdown();
    }

    public boolean isCountingDown() {
        return pref.isUnlockActive();
    }

    public long getRemainingUnlockTime() {
        return pref.getUnlockRemainingTime();
    }

    // --- Formatting helper ---
    public String formatMillis(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    public String getFormattedCredits() {
        return formatMillis(getEarnedTime());
    }

    public String getFormattedUnlockRemaining() {
        return formatMillis(getRemainingUnlockTime());
    }
}
