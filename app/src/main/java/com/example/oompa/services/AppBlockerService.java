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

    private earnedTimeCounter timeCounter;

    private long dailyFullLock = System.currentTimeMillis();
    private int lockUnlockDurationHours = 3;
    private long dailyFullUnlock = dailyFullLock + (lockUnlockDurationHours * 60 * 60 * 1000L);
    private boolean isExerciseUnlockActive = false;

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
        preferenceManager = new PreferenceManager(this);
        loadSavedApps();
        timeCounter = earnedTimeCounter.getInstance(this);
        handler.post(lockChecker);
        Log.d("AppBlockerService", "Service connected, loaded " + lockedApps.size() + " apps");
    }

    private void loadSavedApps() {
        lockedApps.clear();
        for (App app : preferenceManager.getLockedApps()) {
            if (app.getSelected()) lockedApps.put(app.getPackageName(), app);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timeCounter != null) timeCounter.stopCountdown();
        saveAppsToPreferences();
        instance = null;
    }

    private void saveAppsToPreferences() {
        preferenceManager.saveLockedApps(new ArrayList<>(lockedApps.values()));
    }

    private String currentForegroundApp = "";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
            if (packageName.equals(getPackageName())) return;

            // Track current foreground app
            currentForegroundApp = packageName;

            if (shouldBlockApp(packageName)) {
                blockApp(packageName);
            }
        }
    }

    private void blockApp(String packageName) {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("blockedApp", packageName);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        Log.d("AppBlockerService", "Blocked access to: " + packageName);
    }

    private boolean shouldBlockApp(String packageName) {
        App app = lockedApps.get(packageName);
        if (app == null) return false;

        // Block if app is selected AND we're not in exercise unlock period
        boolean shouldBlock = !isExerciseUnlockActive && app.getSelected();
        Log.d("AppBlockerService", "Should block " + packageName + ": " + shouldBlock +
                " (unlockActive: " + isExerciseUnlockActive + ", selected: " + app.getSelected() + ")");
        return shouldBlock;
    }

    @Override
    public void onInterrupt() { }

    public void addLockedApp(App app) {
        app.setSelected(true);
        lockedApps.put(app.getPackageName(), app);
        preferenceManager.addLockedApp(app);
        Log.d("AppBlockerService", "Added locked app: " + app.getPackageName());
    }

    public void removeLockedApp(String packageName) {
        lockedApps.remove(packageName);
        preferenceManager.removeLockedApp(packageName);
        Log.d("AppBlockerService", "Removed locked app: " + packageName);
    }

    public List<App> getLockedApps() {
        return new ArrayList<>(lockedApps.values());
    }

    public void updateActiveLocks() {
        boolean dailyLock = System.currentTimeMillis() >= dailyFullLock && System.currentTimeMillis() < dailyFullUnlock;

        for (App app : lockedApps.values()) {
            // Apps are locked during daily lock period, unless exercise unlock is active
            if (dailyLock) {
                app.setSelected(true); // Always locked during daily lock period
            } else {
                app.setSelected(false); // Not in daily lock period
            }
        }

        Log.d("AppBlockerService", "Updated active locks. Daily lock: " + dailyLock +
                ", Exercise unlock: " + isExerciseUnlockActive + ", Total apps: " + lockedApps.size());
    }

    public void endExerciseUnlock() {
        Log.d("AppBlockerService", "Ending exercise unlock - was active: " + isExerciseUnlockActive);
        isExerciseUnlockActive = false;
        timeCounter.stopCountdown();
        preferenceManager.clearUnlockCountdown();

        // Force all locked apps to be active again
        for (App app : lockedApps.values()) {
            app.setSelected(true);
        }

        // Check if we need to block the currently running app
        if (!currentForegroundApp.isEmpty() && shouldBlockApp(currentForegroundApp)) {
            Log.d("AppBlockerService", "Blocking currently running app after unlock expired: " + currentForegroundApp);
            blockApp(currentForegroundApp);
        }

        Log.d("AppBlockerService", "Exercise unlock ended - apps now locked: " + lockedApps.size());
    }

    public void startExerciseUnlock(long earnedMillis) {
        if (exerciseUnlocksUsed >= maxDailyExerciseUnlocks) {
            Log.d("AppBlockerService", "Max daily unlocks reached");
            return;
        }

        // Only add newly earned time if provided
        if (earnedMillis > 0) {
            preferenceManager.addEarnedCredits(earnedMillis);
        }

        // Get total available credits and start unlock session
        long available = preferenceManager.getEarnedCredits();
        if (available > 0) {
            // Start countdown and clear credits (consume them)
            preferenceManager.startUnlockCountdown(available);
            preferenceManager.clearEarnedCredits();

            exerciseUnlockStart = System.currentTimeMillis();
            exerciseUnlockEnd = exerciseUnlockStart + available;
            exerciseUnlocksUsed++;
            isExerciseUnlockActive = true;
            updateActiveLocks();

            Log.d("AppBlockerService", "Exercise unlock started for " + available + "ms");
        } else {
            Log.d("AppBlockerService", "No credits available for unlock");
        }
    }

    public void startExerciseUnlockWithoutCountdown(long earnedMillis) {
        if (exerciseUnlocksUsed >= maxDailyExerciseUnlocks) return;
        exerciseUnlockStart = System.currentTimeMillis();
        exerciseUnlockEnd = exerciseUnlockStart + earnedMillis;
        exerciseUnlocksUsed++;
        isExerciseUnlockActive = true;
        updateActiveLocks();
    }

    private final Runnable lockChecker = new Runnable() {
        @Override
        public void run() {
            long remaining = preferenceManager.getUnlockRemainingTime();
            boolean wasUnlockActive = isExerciseUnlockActive;

            if (remaining > 0 && !isExerciseUnlockActive) {
                // We have an active countdown but unlock isn't active - restore state
                timeCounter.resetCredits();
                timeCounter.addTime(remaining);
                timeCounter.startCountdown(remaining);
                isExerciseUnlockActive = true;
                Log.d("AppBlockerService", "Restored unlock state with " + remaining + "ms remaining");
            } else if (remaining <= 0 && isExerciseUnlockActive) {
                // Countdown finished - immediately end unlock
                Log.d("AppBlockerService", "Countdown expired, ending unlock immediately");
                endExerciseUnlock();
            }

            // Update lock states if unlock status changed
            if (wasUnlockActive != isExerciseUnlockActive) {
                updateActiveLocks();

                // If unlock just ended, check current app immediately
                if (wasUnlockActive && !isExerciseUnlockActive &&
                        !currentForegroundApp.isEmpty() && shouldBlockApp(currentForegroundApp)) {
                    Log.d("AppBlockerService", "Immediately blocking current app after unlock ended: " + currentForegroundApp);
                    blockApp(currentForegroundApp);
                }
            }

            // Check more frequently during unlock periods for better responsiveness
            handler.postDelayed(this, isExerciseUnlockActive ? 1000 : 2000);
        }
    };

    public earnedTimeCounter getTimeCounter() { return timeCounter; }
    public boolean isExerciseUnlockActive() { return isExerciseUnlockActive; }

    public boolean isLocked(String packageName) {
        App app = lockedApps.get(packageName);
        boolean locked = app != null && app.getSelected() && !isExerciseUnlockActive;
        Log.d("AppBlockerService", "Is " + packageName + " locked: " + locked);
        return locked;
    }
}