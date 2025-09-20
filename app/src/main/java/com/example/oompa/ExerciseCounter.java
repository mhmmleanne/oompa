package com.example.oompa;

import android.hardware.SensorEvent;
import com.example.oompa.services.earnedTimeCounter;

public class ExerciseCounter {
    private int jumpCount = 0;
    private int jumpingJackCount = 0;

    private earnedTimeCounter timeCounter;

    // Singleton instance
    private static ExerciseCounter instance;

    // States
    private enum JumpState { ON_GROUND, IN_AIR, LANDED }
    private JumpState state = JumpState.ON_GROUND;

    // Thresholds
    private static final double TAKEOFF_THRESHOLD = 13.0;
    private static final double FREEFALL_THRESHOLD = 7.0;
    private static final double LANDING_THRESHOLD = 16.0;

    // 🔹 Private constructor to enforce singleton
    private ExerciseCounter(earnedTimeCounter counter) {
        this.timeCounter = counter;
    }

    // 🔹 Get singleton instance (pass earnedTimeCounter once at startup)
    public static synchronized ExerciseCounter getInstance(earnedTimeCounter counter) {
        if (instance == null) {
            instance = new ExerciseCounter(counter);
        }
        return instance;
    }

    // 🔹 Get existing instance (after initialization)
    public static ExerciseCounter getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ExerciseCounter not initialized. Call getInstance(earnedTimeCounter) first.");
        }
        return instance;
    }

    /** Process sensor data */
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        double magnitude = Math.sqrt(x * x + y * y + z * z);

        switch (state) {
            case ON_GROUND:
                if (magnitude > TAKEOFF_THRESHOLD) {
                    state = JumpState.IN_AIR;
                }
                break;

            case IN_AIR:
                if (magnitude < FREEFALL_THRESHOLD) {
                    state = JumpState.LANDED;
                }
                break;

            case LANDED:
                if (magnitude > LANDING_THRESHOLD) {
                    jumpCount++;
                    if (jumpCount % 2 == 0) {
                        jumpingJackCount++;

                        // 🔹 Award earned time
                        if (timeCounter != null) {
                            timeCounter.addTime(1000);
                        }
                    }
                    state = JumpState.ON_GROUND;
                }
                break;
        }
    }

    public int getJumpCount() {
        return jumpCount;
    }

    public int getJumpingJackCount() {
        return jumpingJackCount;
    }

    public void reset() {
        jumpCount = 0;
        jumpingJackCount = 0;
        state = JumpState.ON_GROUND;
    }
}
