package com.example.oompa.classes;
import android.graphics.drawable.Drawable;

public class LockedApp {
    private final String packageName;
    private final String appName;
    private long lockTime;
    private long unlockTime;
    private boolean isLocked;

    private final Drawable appIcon;

    public LockedApp(String packageName, String appName, Drawable appIcon)
    {
        this.packageName = packageName;
        this.appName = appName;
        this.appIcon = appIcon;
        this.isLocked = false;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAppName() {
        return appName;
    }

    public Drawable getAppIcon() {
        return appIcon;

    }

    public long getLockTime() {
        return lockTime;
    }

    public long getUnlockTime() {
        return unlockTime;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public void setLockTime(long lockTime) {
        this.lockTime = lockTime;
    }

    public void setUnlockTime(long unlockTime) {
        this.unlockTime = unlockTime;
    }

    public void setLocked(boolean locked) {
        isLocked = locked;
    }


}
