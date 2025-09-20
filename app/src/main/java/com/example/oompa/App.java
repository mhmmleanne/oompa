package com.example.oompa;

import android.widget.ImageView;

public class App {

    String packageName;
    String appName;
    boolean isSelected;
    int appIcon;

    public App(String packageName, String appName, boolean isSelected, int appIcon){
        this.packageName = packageName;
        this.appName = appName;
        this.isSelected = isSelected;
        this.appIcon = appIcon;
    }

    public int getAppIcon() {
        return appIcon;
    }

    public String getAppName() {
        return appName;
    }

    public boolean getSelected(){
        return isSelected;
    }

    public void setAppIcon(int appIcon) {
        this.appIcon = appIcon;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getPackageName() {
        return packageName;
    }
}
