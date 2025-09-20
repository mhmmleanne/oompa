package com.example.oompa.services;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.example.oompa.App;
import com.example.oompa.MainActivity;

import java.util.HashMap;
import java.util.Map;

public class AppBlockerService extends AccessibilityService {

    private Map<String, App> lockedApps;
    private earnedTimeCounter timeCounter;

    private long dailyFullLock = System.currentTimeMillis();
    private int lockUnlockDurationHours = 3;
    private long dailyFullUnlock = dailyFullLock + (lockUnlockDurationHours * 60 * 60 * 1000L);

    private boolean isExerciseUnlockActive = false;

    private Handler handler = new Handler();
    private static AppBlockerService instance;

    public static AppBlockerService getInstance() {
        return instance;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        lockedApps = new HashMap<>();
        timeCounter = earnedTimeCounter.getInstance(this);
        handler.post(lockChecker);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Make sure to stop countdown when service is destroyed
        if (timeCounter != null) {
            timeCounter.stopCountdown();
        }
        instance = null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
            App app = lockedApps.get(packageName);

            if(app != null && app.getSelected()) {
                Intent i = new Intent(this, MainActivity.class);
                i.putExtra("blockedApp", packageName);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        }
    }

    @Override
    public void onInterrupt() { }

    public void addLockedApp(App app) { lockedApps.put(app.getPackageName(), app); }
    public void removeLockedApp(String packageName) { lockedApps.remove(packageName); }

    /** Start exercise unlock period manually (on Unlock Apps button press) */
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
        isExerciseUnlockActive = false;
        timeCounter.stopCountdown(); // 🔹 STOP countdown here
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

    /** Periodically refresh locks and check countdown */
    private Runnable lockChecker = new Runnable() {
        @Override
        public void run() {
            // If exercise unlock is active, manually call countdown
            if (isExerciseUnlockActive && timeCounter.isCountingDown()) {
                timeCounter.countdown(); // 🔹 Only countdown when unlock is active

                // Check if time expired
                if (!timeCounter.hasTime()) {
                    Log.d("AppBlockerService", "Earned time expired - ending exercise unlock");
                    endExerciseUnlock(); // This will stop the countdown
                } else {
                    // Log current time for debugging
                    Log.d("AppBlockerService", "Time remaining: " + timeCounter.getFormattedTime());
                }
            }

            updateActiveLocks();

            // Check every second during unlock, every 2 seconds otherwise
            long delay = isExerciseUnlockActive ? 1000 : 2000;
            handler.postDelayed(this, delay);
        }
    };

    public earnedTimeCounter getTimeCounter() { return timeCounter; }
    public boolean isExerciseUnlockActive() { return isExerciseUnlockActive; }
}