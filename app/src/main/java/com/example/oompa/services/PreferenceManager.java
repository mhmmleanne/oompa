package com.example.oompa.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.oompa.App;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;


public class PreferenceManager {
    private static final String PREF_NAME = "MyAppPreferences";
    private static final String KEY_LOCKED_APPS = "locked_apps";

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    private Gson gson;

    public PreferenceManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
        gson = new Gson();
    }

    // Save blocked apps list
    public void saveLockedApps(List<App> apps) {
        String json = gson.toJson(apps);
        editor.putString(KEY_LOCKED_APPS, json);
        editor.apply();
    }

    // Load blocked apps list
    public List<App> getLockedApps() {
        String json = sharedPreferences.getString(KEY_LOCKED_APPS, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<ArrayList<App>>(){}.getType();
        return gson.fromJson(json, type);
    }

    // Add a single app
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

    // Remove a single app
    public void removeLockedApp(String packageName) {
        List<App> apps = getLockedApps();
        List<App> updated = new ArrayList<>();
        for (App a : apps) {
            if (!a.getPackageName().equals(packageName)) updated.add(a);
        }
        saveLockedApps(updated);
    }

    // Check if an app is blocked
    public boolean isLocked(String packageName) {
        List<App> apps = getLockedApps();
        for (App a : apps) {
            if (a.getPackageName().equals(packageName) && a.getSelected()) return true;
        }
        return false;
    }
}
