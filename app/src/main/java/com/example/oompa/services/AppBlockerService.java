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

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
            if (packageName.equals(getPackageName())) return;

            if (shouldBlockApp(packageName)) {
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
        return !isExerciseUnlockActive && app.getSelected();
    }

    @Override
    public void onInterrupt() { }

    public void addLockedApp(App app) {
        app.setSelected(true);
        lockedApps.put(app.getPackageName(), app);
        preferenceManager.addLockedApp(app);
    }

    public void removeLockedApp(String packageName) {
        lockedApps.remove(packageName);
        preferenceManager.removeLockedApp(packageName);
    }

    public List<App> getLockedApps() {
        return new ArrayList<>(lockedApps.values());
    }

    public void updateActiveLocks() {
        boolean dailyLock = System.currentTimeMillis() >= dailyFullLock && System.currentTimeMillis() < dailyFullUnlock;
        for (App app : lockedApps.values()) {
            app.setSelected(!isExerciseUnlockActive && dailyLock);
        }
    }

    public void endExerciseUnlock() {
        isExerciseUnlockActive = false;
        timeCounter.stopCountdown();
        preferenceManager.clearUnlockCountdown();
        updateActiveLocks();
    }

    public void startExerciseUnlock(long earnedMillis) {
        if (exerciseUnlocksUsed >= maxDailyExerciseUnlocks) return;

        if (earnedMillis > 0) preferenceManager.addEarnedCredits(earnedMillis);

        long available = preferenceManager.getEarnedCredits();
        if (available > 0) {
            preferenceManager.startUnlockCountdown(available);
            exerciseUnlockStart = System.currentTimeMillis();
            exerciseUnlockEnd = exerciseUnlockStart + available;
            preferenceManager.clearEarnedCredits();
            exerciseUnlocksUsed++;
            isExerciseUnlockActive = true;
            updateActiveLocks();
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

            if (remaining > 0 && !isExerciseUnlockActive) {
                timeCounter.resetCredits();
                timeCounter.addTime(remaining);
                timeCounter.startCountdown(remaining);
                isExerciseUnlockActive = true;
            } else if (remaining <= 0 && isExerciseUnlockActive) {
                endExerciseUnlock();
            }

            updateActiveLocks();
            handler.postDelayed(this, isExerciseUnlockActive ? 1000 : 2000);
        }
    };

    public earnedTimeCounter getTimeCounter() { return timeCounter; }
    public boolean isExerciseUnlockActive() { return isExerciseUnlockActive; }

    public boolean isLocked(String packageName) {
        App app = lockedApps.get(packageName);
        return app != null && app.getSelected();
    }

}
