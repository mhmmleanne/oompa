package com.example.oompa.services;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.example.oompa.App;
import com.example.oompa.MainActivity;

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
    private long dailyFullLock = System.currentTimeMillis();
    private int lockUnlockDurationHours = 3;// start of full lock period
    private long dailyFullUnlock = dailyFullLock + (lockUnlockDurationHours * 60 * 60 * 1000L);    // end of full lock period
    private boolean isExerciseUnlockActive = false;
    private long lastLockTimeSet;
    private static final long ONE_WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000;

    // --- Exercise unlock state ---
    private long exerciseUnlockStart;
    private long exerciseUnlockEnd;
    private int maxDailyExerciseUnlocks = 2;
    private int exerciseUnlocksUsed = 0;

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
        timeCounter = earnedTimeCounter.getInstance(this);
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
        // Make sure to stop countdown when service is destroyed
        if (timeCounter != null) {
            timeCounter.stopCountdown();
        }
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
        // Check if app is in locked list
        App app = lockedApps.get(packageName);
        if (app == null) {
            return false;
        }

        // Check current lock status based on time rules

        boolean exerciseUnlock = isExerciseUnlockActive;

        Log.d("AppBlocker", "App: " + packageName +
                ", ExerciseUnlock: " + exerciseUnlock +
                ", Selected: " + app.getSelected());

        // Block if it's daily lock time and not in exercise unlock period
        return !exerciseUnlock && app.getSelected();
    }

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

    public void startExerciseUnlockWithEarnedTime() {
        Log.d("AppBlockerService", "startExerciseUnlockWithEarnedTime called");
        Log.d("AppBlockerService", "Has time: " + timeCounter.hasTime());
        Log.d("AppBlockerService", "Current time: " + timeCounter.getFormattedTime());

        if (timeCounter.hasTime()) {
            isExerciseUnlockActive = true;
            timeCounter.startCountdown(); // 🔹 START countdown here
            updateActiveLocks();
            Log.d("AppBlockerService", "Exercise unlock started with time: " + timeCounter.getFormattedTime());
            Log.d("AppBlockerService", "Is counting down: " + timeCounter.isCountingDown());
        } else {
            Log.d("AppBlockerService", "No earned time available!");
        }
    }

    /** End exercise unlock */
    public void endExerciseUnlock() {
        exerciseUnlockEnd = System.currentTimeMillis();
        isExerciseUnlockActive = false;
        timeCounter.stopCountdown(); //
        preferenceManager.clearUnlockTime();
        updateActiveLocks();
        Log.d("AppBlockerService", "Exercise unlock ended");
    }

    /** Update app locks */
    public void updateActiveLocks() {
        boolean dailyLock = System.currentTimeMillis() >= dailyFullLock && System.currentTimeMillis() < dailyFullUnlock;
        for (App app : lockedApps.values()) {
            app.setSelected(!isExerciseUnlockActive && dailyLock);
        }
    }

    public void startExerciseUnlock(long earnedMillis) {
        if (exerciseUnlocksUsed < maxDailyExerciseUnlocks) {
            if (earnedMillis > 0) {
                timeCounter.addTime(earnedMillis);     // add to the service's counter
            }

            if (timeCounter.hasTime()) {
                // Ensure the service-side counter actually counts down
                timeCounter.startCountdown();

                exerciseUnlockStart = System.currentTimeMillis();
                exerciseUnlockEnd = exerciseUnlockStart + timeCounter.getEarnedTime();
                preferenceManager.saveUnlockTime(exerciseUnlockEnd);
                exerciseUnlocksUsed++;
                isExerciseUnlockActive = true;
                updateActiveLocks();

                Log.d("AppBlockerService", "Exercise unlock started for " + timeCounter.getEarnedTime() + " ms");
            } else {
                Log.d("AppBlockerService", "No earned time in service, cannot start unlock");
            }
        } else {
            Log.d("AppBlockerService", "Max exercise unlocks used");
        }
    }


    /** Periodically refresh locks and check countdown */
    private Runnable lockChecker = new Runnable() {
        @Override
        public void run() {
            // Check persisted unlock time
            long savedEndTime = preferenceManager.getUnlockTime(); // your new millis pref
            long now = System.currentTimeMillis();

            if (savedEndTime > now) {
                if (!isExerciseUnlockActive) {
                    // Calculate remaining time and set it in the counter
                    long remaining = savedEndTime - now;
                    timeCounter.reset();       // clear previous value
                    timeCounter.addTime(remaining);  // set remaining time
                    timeCounter.startCountdown();
                    isExerciseUnlockActive = true;
                }
            } else if (isExerciseUnlockActive) {
                endExerciseUnlock();
            }

            // Normal countdown
            if (isExerciseUnlockActive && timeCounter.isCountingDown()) {
                timeCounter.countdown(); // update elapsed time
                if (!timeCounter.hasTime()) {
                    endExerciseUnlock();
                }
            }

            updateActiveLocks();
            handler.postDelayed(this, isExerciseUnlockActive ? 1000 : 2000);
        }
    };
    public void startExerciseUnlockWithoutCountdown(long earnedMillis) {
        if(exerciseUnlocksUsed < maxDailyExerciseUnlocks) {
            exerciseUnlockStart = System.currentTimeMillis();
            exerciseUnlockEnd = exerciseUnlockStart + earnedMillis;
            exerciseUnlocksUsed++;
            isExerciseUnlockActive = true;
            updateActiveLocks();
            // Do NOT start timeCounter countdown here
        }
    }




    public earnedTimeCounter getTimeCounter() { return timeCounter; }
    public boolean isExerciseUnlockActive() { return isExerciseUnlockActive; }
}