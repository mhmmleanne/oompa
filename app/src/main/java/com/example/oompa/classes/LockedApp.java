package com.example.oompa.classes;
import android.graphics.drawable.Drawable;

public class LockedApp {
    private final String packageName;
    private final String appName;
    private long lockTime;
    private long unlockTime;
    private boolean isLocked;

    private boolean initialLocked;
    private boolean activeLocked;

    private final Drawable appIcon;

    public LockedApp(String packageName, String appName, Drawable appIcon) {
        this.packageName = packageName;
        this.appName = appName;
        this.appIcon = appIcon;
        this.initialLocked = false;
        this.activeLocked = false;
        this.lockTime = 0;
        this.unlockTime = 0;
    }

    public String getPackageName() { return packageName; }
    public String getAppName() { return appName; }
    public Drawable getAppIcon() { return appIcon; }
    public boolean isInitialLocked() { return initialLocked; }
    public boolean isActiveLocked() { return activeLocked; }
    public long getLockTime() { return lockTime; }
    public long getUnlockTime() { return unlockTime; }

    // Setters
    public void setInitialLocked(boolean initialLocked) { this.initialLocked = initialLocked; }
    public void setActiveLocked(boolean activeLocked) { this.activeLocked = activeLocked; }
    public void setLockTime(long lockTime) { this.lockTime = lockTime; }
    public void setUnlockTime(long unlockTime) { this.unlockTime = unlockTime; }

    /**
     * Checks if the app is currently locked (both setup lock + active lock within time period)
     */
    public boolean isCurrentlyLocked() {
        long now = System.currentTimeMillis();
        boolean withinPeriod = now >= lockTime && now <= unlockTime;
        return initialLocked && activeLocked && withinPeriod;
    }


}
