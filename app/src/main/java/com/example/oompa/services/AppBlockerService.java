package com.example.oompa.services;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

import com.example.oompa.MainActivity;
import com.example.oompa.classes.LockedApp;

import java.util.HashMap;
import java.util.Map;

public class AppBlockerService extends AccessibilityService {
    private Map<String, LockedApp> lockedApps;
    private int lock_unlock_duration;

    @Override
    public void onServiceConnected(){
        super.onServiceConnected();

        lockedApps = new HashMap<>();
        lock_unlock_duration = 3;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event){
        if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
            String packageName = (event.getPackageName() != null) ? event.getPackageName().toString() : "";
            //If app is locked, lock the screen
            LockedApp app = lockedApps.get(packageName);
            if(app != null && app.isLocked() && isInlockPeriod(app)){
                //TODO LOCKSCREEN ACTIVITY
                Intent i = new Intent(this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        }
    }

    @Override
    public void onInterrupt(){

    }

    private boolean isInlockPeriod(LockedApp app){
        //TODO Check current time vs lock/unlocktimes from SHARED PREFERENCES
        //True app is locked, false otherwise
        long currentTime = System.currentTimeMillis();
        return currentTime >= app.getLockTime() && currentTime <= app.getUnlockTime();
    }

    public void setLock_unlock_duration(int lock_unlock_duration){
        this.lock_unlock_duration = lock_unlock_duration;
    }

    public void lockApp(String packageName,long lockTime){
        LockedApp app = lockedApps.get(packageName);
        if(app != null){
            app.setLocked(true);
            app.setLockTime(lockTime);
            app.setUnlockTime(lockTime+lock_unlock_duration * 60 * 60 * 1000L);
        }



    }
    public void addApp(LockedApp app){
        lockedApps.put(app.getPackageName(),app);
    }

    public void removeApp(String packageName){
        lockedApps.remove(packageName);
    }

    public void unlockApp(String packageName){
        LockedApp app = lockedApps.get(packageName);
        if(app != null){
            app.setLocked(false);
        }
    }

    public void lockApp(String packageName){
        LockedApp app = lockedApps.get(packageName);
        if(app != null){
            app.setLocked(true);
        }
    }

    public void unlockAllApps(){
        for(LockedApp app : lockedApps.values()){
            app.setLocked(false);
        }
    }

    public void lockAllApps(){
        for(LockedApp app : lockedApps.values()){
            app.setLocked(true);
        }
    }

    public void check_Outside_Lock_Duration(){
        for(LockedApp app : lockedApps.values()){
            //If app is outside of lock period
            app.setLocked(isInlockPeriod(app));
        }
    }
}
