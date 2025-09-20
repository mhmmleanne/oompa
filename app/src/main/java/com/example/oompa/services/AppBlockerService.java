package com.example.oompa.services;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.example.oompa.App;
import com.example.oompa.MainActivity;
import com.example.oompa.classes.LockedApp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppBlockerService extends AccessibilityService {

    private static final Map<String, App> lockedApps = new HashMap<>();
    private PreferenceManager preferenceManager;

    // --- Earned Time Counter ---
    private earnedTimeCounter timeCounter;

    // --- Daily full lock schedule ---
    private long dailyFullLock = 0; // Default: not locked until set
    private int lockUnlockDurationHours = 3;
    private long dailyFullUnlock = 0;

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
        instance = this;

        // Initialize PreferenceManager
        preferenceManager = new PreferenceManager(this);

        // Load saved apps from preferences
        loadSavedApps();

        timeCounter = new earnedTimeCounter(this);
        handler.post(lockChecker);

        Log.d("AppBlockerService", "Service connected, loaded " + lockedApps.size() + " apps");
    }

    private void loadSavedApps() {
        lockedApps.clear(); // Clear existing
        List<App> savedApps = preferenceManager.getLockedApps();
        for (App app : savedApps) {
            if (app.getSelected()) {
                lockedApps.put(app.getPackageName(), app);
            }
        }
        Log.d("AppBlockerService", "Loaded " + lockedApps.size() + " locked apps from preferences");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Save apps before destroying
        saveAppsToPreferences();
        instance = null;
    }

    private void saveAppsToPreferences() {
        if (preferenceManager != null) {
            List<App> appsToSave = new ArrayList<>(lockedApps.values());
            preferenceManager.saveLockedApps(appsToSave);
            Log.d("AppBlockerService", "Saved " + appsToSave.size() + " apps to preferences");
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = (event.getPackageName() != null) ? event.getPackageName().toString() : "";

            // Skip our own app to avoid infinite loops
            if (packageName.equals(getPackageName())) {
                return;
            }

            Log.d("AccessibilityEvent", "Package: " + packageName);

            // Check if this app should be blocked
            if (shouldBlockApp(packageName)) {
                Log.d("AppBlocker", "Blocking app: " + packageName);
                // Redirect to lock screen
                Intent i = new Intent(this, MainActivity.class);
                i.putExtra("blockedApp", packageName);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
            }
        }
    }

    private boolean shouldBlockApp(String packageName) {
        App app = lockedApps.get(packageName);
        if (app == null) return false;

        // Only check if app is selected
        return app.getSelected();
    }

    /*private boolean shouldBlockApp(String packageName) {
        // Check if app is in locked list
        App app = lockedApps.get(packageName);
        if (app == null) {
            return false;
        }

        // Check current lock status based on time rules
        boolean dailyLock = isDailyLocked();
        boolean exerciseUnlock = isExerciseUnlocked();

        Log.d("AppBlocker", "App: " + packageName +
                ", DailyLock: " + dailyLock +
                ", ExerciseUnlock: " + exerciseUnlock +
                ", Selected: " + app.getSelected());

        // Block if it's daily lock time and not in exercise unlock period
        return dailyLock && !exerciseUnlock && app.getSelected();
    }*/

    @Override
    public void onInterrupt() { }

    // ------------------ Backend Methods ------------------

    public void addLockedApp(App app) {
        app.setSelected(true);
        lockedApps.put(app.getPackageName(), app);
        // Save to preferences immediately
        if (preferenceManager != null) {
            preferenceManager.addLockedApp(app);
        }
        Log.d("AppBlockerService", "Added locked app: " + app.getAppName() + ", total: " + lockedApps.size());
    }

    public void removeLockedApp(String packageName) {
        App removedApp = lockedApps.remove(packageName);
        // Remove from preferences
        if (preferenceManager != null) {
            preferenceManager.removeLockedApp(packageName);
        }
        if (removedApp != null) {
            Log.d("AppBlockerService", "Removed locked app: " + removedApp.getAppName() + ", total: " + lockedApps.size());
        }
    }

    public List<App> getLockedApps() {
        return new ArrayList<>(lockedApps.values());
    }

    public boolean isLocked(String packageName) {
        App app = lockedApps.get(packageName);
        return app != null && app.getSelected();
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
        // If no daily lock time is set, don't lock
        if (dailyFullLock == 0 || dailyFullUnlock == 0) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        boolean locked = currentTime >= dailyFullLock && currentTime < dailyFullUnlock;
        Log.d("DailyLock", "Current: " + currentTime + ", Start: " + dailyFullLock + ", End: " + dailyFullUnlock + ", Locked: " + locked);
        return locked;
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

        Log.d("UpdateActiveLocks", "DailyLock: " + dailyLock + ", ExerciseUnlock: " + exerciseUnlock + ", Apps: " + lockedApps.size());

        // Don't modify the selected state here - keep apps selected as chosen by user
        // The blocking decision is made in shouldBlockApp() based on time rules
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

    /** Enable immediate blocking for testing (bypasses daily schedule) */
    public void enableImmediateBlocking() {
        dailyFullLock = System.currentTimeMillis() - 1000; // Start 1 second ago
        dailyFullUnlock = System.currentTimeMillis() + (24 * 60 * 60 * 1000L); // End in 24 hours
        updateActiveLocks();
        Log.d("AppBlockerService", "Immediate blocking enabled");
    }

    /** Disable immediate blocking */
    public void disableImmediateBlocking() {
        dailyFullLock = 0;
        dailyFullUnlock = 0;
        updateActiveLocks();
        Log.d("AppBlockerService", "Immediate blocking disabled");
    }

    // ------------------ Getters ------------------

    public long getDailyFullLock() { return dailyFullLock; }
    public long getDailyFullUnlock() { return dailyFullUnlock; }
    public long getLastLockTimeSet() { return lastLockTimeSet; }
    public int getLockUnlockDurationHours() { return lockUnlockDurationHours; }
    public int getExerciseUnlocksUsed() { return exerciseUnlocksUsed; }
}