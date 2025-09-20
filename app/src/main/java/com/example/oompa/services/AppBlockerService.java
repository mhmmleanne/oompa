package com.example.oompa.services;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.example.oompa.App;
import com.example.oompa.MainActivity;
import com.example.oompa.classes.LockedApp;

import java.util.HashMap;
import java.util.Map;

public class AppBlockerService extends AccessibilityService {

    private Map<String, App> lockedApps;

    // --- Earned Time Counter ---
    private earnedTimeCounter timeCounter;

    // --- Daily full lock schedule ---
    private long dailyFullLock = System.currentTimeMillis();
    private int lockUnlockDurationHours = 3;// start of full lock period
    private long dailyFullUnlock = dailyFullLock + (lockUnlockDurationHours * 60 * 60 * 1000L);    // end of full lock period

    private long lastLockTimeSet;
    private static final long ONE_WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000;

    // --- Exercise unlock state ---
    private long exerciseUnlockStart;
    private long exerciseUnlockEnd;
    private int maxDailyExerciseUnlocks = 2;
    private int exerciseUnlocksUsed = 0;
    private boolean isExerciseUnlockActive = false; // Track if we're in exercise unlock mode

    private Handler handler = new Handler();

    private static AppBlockerService instance;

    public static AppBlockerService getInstance() {
        return instance;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this; // 🔹 keep a static reference
        lockedApps = new HashMap<>();
        timeCounter = new earnedTimeCounter(this);
        handler.post(lockChecker);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = (event.getPackageName() != null) ? event.getPackageName().toString() : "";
            Log.d("YTC", packageName);
            App app = lockedApps.get(packageName);

            if(app != null && app.getSelected()) {
                // Redirect to lock screen
                Intent i = new Intent(this, MainActivity.class);
                i.putExtra("blockedApp", packageName);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        }
    }

    @Override
    public void onInterrupt() { }

    // ------------------ Backend Methods ------------------

    /** Add a locked app */
    public void addLockedApp(App app) {
        lockedApps.put(app.getPackageName(), app);
    }

    /** Remove a locked app */
    public void removeLockedApp(String packageName) {
        lockedApps.remove(packageName);
    }

    /** Set daily full lock schedule (only once per week) */
    public void setDailyFullLockingTime(long startMillis) {
        long currentTime = System.currentTimeMillis();
        if(currentTime - lastLockTimeSet >= ONE_WEEK_MILLIS) {
            this.dailyFullLock = startMillis;
            this.dailyFullUnlock = startMillis + lockUnlockDurationHours * 60 * 60 * 1000L;
            this.lastLockTimeSet = currentTime;
            this.exerciseUnlocksUsed = 0;  // reset exercise unlocks for new week
            updateActiveLocks();
        }
    }

    /** Start an exercise-based unlock period using earnedTimeCounter */
    public void startExerciseUnlock(long earnedMillis) {
        if(exerciseUnlocksUsed < maxDailyExerciseUnlocks) {
            // Add time to the counter if provided
            if (earnedMillis > 0) {
                timeCounter.addTime(earnedMillis);
            }

            // Start exercise unlock period using earned time
            if (timeCounter.hasTime()) {
                exerciseUnlockStart = System.currentTimeMillis();
                exerciseUnlockEnd = exerciseUnlockStart + timeCounter.getEarnedTime();
                exerciseUnlocksUsed++;
                isExerciseUnlockActive = true;
                updateActiveLocks();
            }
        }
    }

    /** Start exercise unlock using existing earned time */
    public void startExerciseUnlockWithEarnedTime() {
        startExerciseUnlock(0); // Use existing earned time
    }

    /** Add earned time without starting unlock period */
    public void addEarnedTime(long earnedMillis) {
        timeCounter.addTime(earnedMillis);
    }

    /** End an exercise unlock early */
    public void endExerciseUnlock() {
        exerciseUnlockEnd = System.currentTimeMillis();
        isExerciseUnlockActive = false;
        // Reset the timer since unlock ended early
        timeCounter.reset();
        updateActiveLocks();
    }

    /** Check if we are in daily full lock period */
    private boolean isDailyLocked() {
        long currentTime = System.currentTimeMillis();
        return currentTime >= dailyFullLock && currentTime < dailyFullUnlock;
    }

    /** Check if we are in an exercise unlock period */
    private boolean isExerciseUnlocked() {
        if (!isExerciseUnlockActive) return false;

        long currentTime = System.currentTimeMillis();
        boolean timeBasedUnlock = currentTime >= exerciseUnlockStart && currentTime < exerciseUnlockEnd;

        // Also check if earned time counter still has time
        boolean hasEarnedTime = timeCounter.hasTime();

        return timeBasedUnlock && hasEarnedTime;
    }

    /** Update active locks on all apps */
    public void updateActiveLocks() {
        long currentTime = System.currentTimeMillis();

        boolean exerciseUnlock = isExerciseUnlocked();
        boolean dailyLock = isDailyLocked();

        // Reset exercise unlocks if we passed the daily full unlock period
        if (currentTime >= dailyFullUnlock) {
            exerciseUnlocksUsed = 0;
            exerciseUnlockStart = 0;
            exerciseUnlockEnd = 0;
            isExerciseUnlockActive = false;
        }

        // If exercise unlock period ended, mark as inactive
        if (isExerciseUnlockActive && currentTime >= exerciseUnlockEnd) {
            isExerciseUnlockActive = false;
        }

        for (App app : lockedApps.values()) {
            // normal unlocked state
            if (exerciseUnlock) {
                app.setSelected(false); // temporary unlock for exercise
            } else app.setSelected(dailyLock); // enforce daily lock
        }
    }

    /** Runnable to periodically check lock status */
    private Runnable lockChecker = new Runnable() {
        @Override
        public void run() {
            // If we're in an active exercise unlock, countdown the timer
            if (isExerciseUnlockActive) {
                timeCounter.countdown();

                // Check if earned time ran out
                if (!timeCounter.hasTime()) {
                    // Time expired - end exercise unlock
                    isExerciseUnlockActive = false;
                    exerciseUnlockEnd = System.currentTimeMillis();
                }
            }

            updateActiveLocks();

            // Check every second during exercise unlock, every 2 seconds otherwise
            long delay = isExerciseUnlockActive ? 1000 : 2000;
            handler.postDelayed(this, delay);
        }
    };

    // ------------------ Public Access Methods ------------------

    /** Get the earned time counter for external access */
    public earnedTimeCounter getTimeCounter() {
        return timeCounter;
    }

    /** Check if exercise unlock is currently active */
    public boolean isExerciseUnlockCurrentlyActive() {
        return isExerciseUnlockActive;
    }

    // ------------------ Getters ------------------

    public long getDailyFullLock() { return dailyFullLock; }
    public long getDailyFullUnlock() { return dailyFullUnlock; }
    public long getLastLockTimeSet() { return lastLockTimeSet; }
    public int getLockUnlockDurationHours() { return lockUnlockDurationHours; }
    public int getExerciseUnlocksUsed() { return exerciseUnlocksUsed; }
}