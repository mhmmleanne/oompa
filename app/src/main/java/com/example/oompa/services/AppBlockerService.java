package com.example.oompa.services;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

import com.example.oompa.classes.LockedApp;

import java.util.HashMap;
import java.util.Map;

public class AppBlockerService extends AccessibilityService {

    private Map<String, LockedApp> lockedApps;
    private int lockUnlockDurationHours;   // duration of lock in hours

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        lockedApps = new HashMap<>();
        lockUnlockDurationHours = 3;  // default 3 hours
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = (event.getPackageName() != null) ? event.getPackageName().toString() : "";

            LockedApp app = lockedApps.get(packageName);
            if(app != null && app.isCurrentlyLocked()) {
                // App is locked: trigger lock screen or block usage
                // For backend purposes, we just log or handle state
                handleLockedApp(app);
            }
        }
    }

    @Override
    public void onInterrupt() { }

    // ------------------ Backend Methods ------------------

    /** Handles logic when app is locked */
    private void handleLockedApp(LockedApp app) {
        // Placeholder: can start LockScreenActivity or log for backend
        // e.g., Log.d("AppBlockerService", app.getAppName() + " is locked!");
    }

    /** Set lock duration in hours */
    public void setLockUnlockDuration(int hours) {
        this.lockUnlockDurationHours = hours;
    }

    /** Initial lock when user selects app for locking */
    public void initialLock(String packageName) {
        LockedApp app = lockedApps.get(packageName);
        long currentTime = System.currentTimeMillis();
        if(app != null) {
            app.setInitialLocked(true);
            app.setLockTime(currentTime);
            app.setUnlockTime(currentTime + lockUnlockDurationHours * 60 * 60 * 1000L);
        }
    }

    /** Updates active lock states dynamically based on current time */
    public void updateActiveLocks() {
        long now = System.currentTimeMillis();
        for(LockedApp app : lockedApps.values()) {
            boolean withinPeriod = now >= app.getLockTime() && now <= app.getUnlockTime();
            app.setActiveLocked(withinPeriod);
        }
    }

    /** Add a new app to the backend */
    public void addApp(LockedApp app) {
        lockedApps.put(app.getPackageName(), app);
    }

    /** Remove an app from the backend */
    public void removeApp(String packageName) {
        lockedApps.remove(packageName);
    }

    /** Unlock app manually */
    public void unlockApp(String packageName) {
        LockedApp app = lockedApps.get(packageName);
        if(app != null) {
            app.setActiveLocked(false);
        }
    }

    /** Lock app manually */
    public void lockApp(String packageName) {
        LockedApp app = lockedApps.get(packageName);
        if(app != null) {
            app.setActiveLocked(true);
        }
    }

    /** Unlock all apps */
    public void unlockAllApps() {
        for(LockedApp app : lockedApps.values()) {
            app.setActiveLocked(false);
        }
    }

    /** Lock all apps */
    public void lockAllApps() {
        for(LockedApp app : lockedApps.values()) {
            app.setActiveLocked(true);
        }
    }
}

