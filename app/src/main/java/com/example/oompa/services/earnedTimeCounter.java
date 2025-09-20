package com.example.oompa.services;

public class earnedTimeCounter {
    private long earnedTime;

    public earnedTimeCounter() {
        this.earnedTime = 0;
    }

    public void addTime() {
        this.earnedTime += (60*1000L);
    }

    public long getEarnedTime() {
        return this.earnedTime;
    }

    public void reset() {
        this.earnedTime = 0;
    }

}
