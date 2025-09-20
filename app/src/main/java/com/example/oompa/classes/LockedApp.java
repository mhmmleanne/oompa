package com.example.oompa.classes;
import android.graphics.drawable.Drawable;

public class LockedApp {
    private final String packageName;
    private final String appName;
    private final Drawable appIcon;

    private boolean activeLocked;  // new: true during locking period

    public LockedApp(String packageName, String appName, Drawable appIcon){
        this.packageName = packageName;
        this.appName = appName;
        this.appIcon = appIcon;
        this.activeLocked = false;
    }

    public boolean isActiveLocked() { return activeLocked; }
    public void setActiveLocked(boolean locked) { this.activeLocked = locked; }

    public String getPackageName() { return packageName; }
    public String getAppName() { return appName; }
    public Drawable getAppIcon() { return appIcon; }
}


