package com.example.oompa.services;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

import com.example.oompa.MainActivity;
import com.example.oompa.classes.LockedApp;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class AppBlockerService extends AccessibilityService {

    private Map<String, LockedApp> lockedApps;
    private int lockUnlockDurationHours;   // duration of lock in hours

    private long lastLockTimeSet;
    private static final long ONE_WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000;
    private long lockingTime;
    private long unlockingTime;


    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        lockedApps = new HashMap<>();
        lockUnlockDurationHours = 3;  // default 3 hours
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event){
        if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
            String packageName = (event.getPackageName() != null) ? event.getPackageName().toString() : "";

            LockedApp app = lockedApps.get(packageName);
            if(app != null && app.isActiveLocked()){
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
    public void setLockUnlockDurationHours(int hours) {
        this.lockUnlockDurationHours = hours;
    }

    public boolean canChangeLockTime(){
        long currentTime = System.currentTimeMillis();
        return currentTime-lastLockTimeSet < ONE_WEEK_MILLIS;
    }

    public void setLockingTime(long time ){
        if (canChangeLockTime()) {
            this.lockingTime = time;
            setUnlockingTime();
        }
    }

    public void setUnlockingTime(){
        this.unlockingTime = this.lockingTime+((long) lockUnlockDurationHours *60*60*1000);
    }

    public boolean isLockingPeriod(){
        long currentTime = System.currentTimeMillis();
        return (currentTime >= this.lockingTime && currentTime < this.unlockingTime);
    }
    public void addLockedApp(LockedApp app){
        lockedApps.put(app.getPackageName(), app);
    }
    public void updateActiveLocks() {
        boolean locking = isLockingPeriod();
        for (LockedApp app : lockedApps.values()) {
            app.setActiveLocked(locking);
        }
    }
}

