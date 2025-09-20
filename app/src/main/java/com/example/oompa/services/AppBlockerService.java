package com.example.oompa.services;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.view.accessibility.AccessibilityEvent;

import com.example.oompa.MainActivity;
import com.example.oompa.classes.LockedApp;
import com.example.oompa.services.earnedTimeCounter;

import java.util.HashMap;
import java.util.Map;

public class AppBlockerService extends AccessibilityService {

    private Map<String, LockedApp> lockedApps;

    // --- Daily full lock schedule ---
    private long dailyFullLock;      // start of full lock period
    private long dailyFullUnlock;    // end of full lock period
    private int lockUnlockDurationHours = 3;
    private long lastLockTimeSet;
    private static final long ONE_WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000;

    // --- Exercise unlock state ---
    private long exerciseUnlockStart;
    private long exerciseUnlockEnd;
    private int maxDailyExerciseUnlocks = 2;
    private int exerciseUnlocksUsed = 0;

    private Handler handler = new Handler();

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        lockedApps = new HashMap<>();

        // Start periodic lock checking
        handler.post(lockChecker);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = (event.getPackageName() != null) ? event.getPackageName().toString() : "";
            LockedApp app = lockedApps.get(packageName);

            if(app != null && app.isActiveLocked()) {
                // Redirect to lock screen
                Intent i = new Intent(this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        }
    }

    @Override
    public void onInterrupt() { }

    // ------------------ Backend Methods ------------------

    /** Add a locked app */
    public void addLockedApp(LockedApp app) {
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

    /** Start an exercise-based unlock period */
    public void startExerciseUnlock(long earnedMillis) {
        if(exerciseUnlocksUsed < maxDailyExerciseUnlocks) {
            exerciseUnlockStart = System.currentTimeMillis();
            exerciseUnlockEnd = exerciseUnlockStart + earnedMillis;
            exerciseUnlocksUsed++;
            updateActiveLocks();
        }
    }

    /** End an exercise unlock early */
    public void endExerciseUnlock() {
        exerciseUnlockEnd = System.currentTimeMillis();
        updateActiveLocks();
    }

    /** Check if we are in daily full lock period */
    private boolean isDailyLocked() {
        long currentTime = System.currentTimeMillis();
        return currentTime >= dailyFullLock && currentTime < dailyFullUnlock;
    }

    /** Check if we are in an exercise unlock period */
    private boolean isExerciseUnlocked() {
        long currentTime = System.currentTimeMillis();
        return currentTime >= exerciseUnlockStart && currentTime < exerciseUnlockEnd;
    }

    /** Update active locks on all apps */
    public void updateActiveLocks() {
        long currentTime = System.currentTimeMillis();
        boolean dailyLock = isDailyLocked();
        boolean exerciseUnlock = isExerciseUnlocked();

        // Reset exercise unlocks if we passed the daily full unlock period
        if(currentTime >= dailyFullUnlock) {
            exerciseUnlocksUsed = 0;
            exerciseUnlockStart = 0;
            exerciseUnlockEnd = 0;
        }

        for(LockedApp app : lockedApps.values()) {
            if(dailyLock) {
                app.setActiveLocked(true); // daily lock enforced
            } else if(exerciseUnlock) {
                app.setActiveLocked(false); // temporary unlock for exercise
            } else {
                app.setActiveLocked(false); // normal unlocked state
            }
        }
    }


    /** Runnable to periodically check lock status */
    private Runnable lockChecker = new Runnable() {
        @Override
        public void run() {
            updateActiveLocks();
            handler.postDelayed(this, 2000); // check every 2 seconds
        }
    };

    // ------------------ Getters ------------------

    public long getDailyFullLock() { return dailyFullLock; }
    public long getDailyFullUnlock() { return dailyFullUnlock; }
    public long getLastLockTimeSet() { return lastLockTimeSet; }
    public int getLockUnlockDurationHours() { return lockUnlockDurationHours; }
    public int getExerciseUnlocksUsed() { return exerciseUnlocksUsed; }
}


