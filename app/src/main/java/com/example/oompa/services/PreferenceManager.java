package com.example.oompa.services;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.oompa.App;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;


public class PreferenceManager {
    private static final String PREF_NAME = "MyAppPreferences";
    private static final String KEY_LOCKED_APPS = "locked_apps";
    private static final String KEY_UNLOCK_TIME = "unlock_time_millis";
    private static final String KEY_EARNED_CREDITS = "earned_credits";

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    private Gson gson;

    public PreferenceManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
        gson = new Gson();
    }

    // =========================
    // Locked apps management
    // =========================
    public void saveLockedApps(List<App> apps) {
        String json = gson.toJson(apps);
        editor.putString(KEY_LOCKED_APPS, json);
        editor.apply();
    }

    public List<App> getLockedApps() {
        String json = sharedPreferences.getString(KEY_LOCKED_APPS, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<ArrayList<App>>(){}.getType();
        return gson.fromJson(json, type);
    }

    public void addLockedApp(App app) {
        List<App> apps = getLockedApps();
        boolean exists = false;
        for (App a : apps) {
            if (a.getPackageName().equals(app.getPackageName())) {
                exists = true;
                break;
            }
        }
        if (!exists) apps.add(app);
        saveLockedApps(apps);
    }

    public void removeLockedApp(String packageName) {
        List<App> apps = getLockedApps();
        List<App> updated = new ArrayList<>();
        for (App a : apps) {
            if (!a.getPackageName().equals(packageName)) updated.add(a);
        }
        saveLockedApps(updated);
    }

    public boolean isLocked(String packageName) {
        for (App a : getLockedApps()) {
            if (a.getPackageName().equals(packageName) && a.getSelected()) return true;
        }
        return false;
    }

    // =========================
    // Earned credits (static)
    // =========================
    public void addEarnedCredits(long millis) {
        long current = getEarnedCredits();
        editor.putLong(KEY_EARNED_CREDITS, current + millis);
        editor.apply();
    }

    public long getEarnedCredits() {
        return sharedPreferences.getLong(KEY_EARNED_CREDITS, 0L);
    }

    public void clearEarnedCredits() {
        editor.putLong(KEY_EARNED_CREDITS, 0L);
        editor.apply();
    }

    // =========================
    // Unlock countdown (dynamic)
    // =========================
    public void startUnlockCountdown(long durationMillis) {
        long endTime = System.currentTimeMillis() + durationMillis;
        editor.putLong(KEY_UNLOCK_TIME, endTime);
        editor.apply();
    }

    public long getUnlockRemainingTime() {
        long endTime = sharedPreferences.getLong(KEY_UNLOCK_TIME, 0L);
        long remaining = endTime - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }

    public boolean isUnlockActive() {
        return getUnlockRemainingTime() > 0;
    }

    public void clearUnlockCountdown() {
        editor.remove(KEY_UNLOCK_TIME);
        editor.apply();
    }
}
