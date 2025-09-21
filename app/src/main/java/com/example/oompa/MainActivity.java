package com.example.oompa;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.example.oompa.services.AppBlockerService;
import com.example.oompa.services.PreferenceManager;
import com.example.oompa.services.earnedTimeCounter;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class MainActivity extends AppCompatActivity implements DialogFragmentListener<App>, SensorEventListener {

    private DialogFragment dialogFragment;
    private TextView appCount, exerciseCountInfo, remainingTime;
    private Button blockedAppsButton, unlockAppsButton, startExercisingButton;

    private PreferenceManager preferenceManager;
    private Handler handler = new Handler();
    private SensorManager sensorManager;
    private Sensor accelerometer;

    private earnedTimeCounter timeCounter;
    private ExerciseCounter exerciseCounter;

    private long unlockTimeLeft;
    private boolean isUnlockActive = false;
    private Runnable unlockRunnable;
    private boolean isExercising = false; // Track exercise state

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        preferenceManager = new PreferenceManager(this);

        blockedAppsButton = findViewById(R.id.modify_blocked_apps_button);
        unlockAppsButton = findViewById(R.id.unlock_app_button);
        startExercisingButton = findViewById(R.id.start_exercise_button);

        appCount = findViewById(R.id.blocked_apps_count);
        exerciseCountInfo = findViewById(R.id.exercises_count);
        remainingTime = findViewById(R.id.remaining_time_count);

        timeCounter = earnedTimeCounter.getInstance(this);
        exerciseCounter = ExerciseCounter.getInstance(timeCounter);

        // Start AppBlockerService
        startService(new Intent(this, AppBlockerService.class));

        updateAppCount();

        // --- Button Listeners ---
        blockedAppsButton.setOnClickListener(v -> {
            dialogFragment = new DialogFragment();
            dialogFragment.show(getSupportFragmentManager(), "DialogFragment");
        });

        startExercisingButton.setOnClickListener(v -> {
            // Only allow exercise if not currently unlocking
            if (!isUnlockActive) {
                isExercising = true;
                startExercisingButton.setText("Stop Exercise");
                sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                if (accelerometer != null) {
                    sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
                }
            } else {
                // Stop exercising
                stopExercising();
            }
        });

        unlockAppsButton.setOnClickListener(v -> {
            // Stop any ongoing exercise
            stopExercising();

            long credits = timeCounter.getEarnedTime();
            if (credits <= 0) {
                remainingTime.setText("No time earned!");
                return;
            }

            // Start unlock session
            AppBlockerService blocker = AppBlockerService.getInstance();
            if (blocker != null) {
                blocker.startExerciseUnlock(credits);
            } else {
                // Fallback if service not ready
                timeCounter.startCountdown(credits);
                timeCounter.resetCredits();
            }

            isUnlockActive = true;
            updateUIForUnlockState();
            startUnlockCountdown();
        });
    }

    private void stopExercising() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        isExercising = false;
        startExercisingButton.setText("Start Exercise");
    }

    private void updateUIForUnlockState() {
        if (isUnlockActive) {
            startExercisingButton.setEnabled(false);
            startExercisingButton.setAlpha(0.5f);
            startExercisingButton.setText("Unlocking...");
            unlockAppsButton.setEnabled(false);
        } else {
            startExercisingButton.setEnabled(true);
            startExercisingButton.setAlpha(1f);
            startExercisingButton.setText("Start Exercise");
            unlockAppsButton.setEnabled(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAppCount();

        // Check if unlock is active from service
        AppBlockerService blocker = AppBlockerService.getInstance();
        if (blocker != null) {
            isUnlockActive = blocker.isExerciseUnlockActive();
        } else {
            isUnlockActive = timeCounter.isCountingDown();
        }

        if (isUnlockActive) {
            unlockTimeLeft = timeCounter.getRemainingUnlockTime();
            if (unlockTimeLeft > 0) {
                updateUIForUnlockState();
                if (unlockRunnable == null) {
                    startUnlockCountdown();
                }
            } else {
                // Countdown finished, reset state
                isUnlockActive = false;
                updateUIForUnlockState();
            }
        } else {
            // Not unlocking, show earned credits
            remainingTime.setText(timeCounter.getFormattedCredits());
            updateUIForUnlockState();
        }

        // Only register sensor if we were exercising and not unlocking
        if (isExercising && !isUnlockActive && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    private void updateAppCount() {
        AppBlockerService blocker = AppBlockerService.getInstance();
        int count;
        if (blocker != null) {
            count = blocker.getLockedApps().size();
        } else {
            count = 0;
            for (App app : preferenceManager.getLockedApps()) {
                if (app.getSelected()) count++;
            }
            handler.postDelayed(this::updateAppCount, 500);
        }
        appCount.setText(String.valueOf(count));
    }

    @Override
    public void onDataSelected(int position, App app) {
        if (app.getSelected()) {
            preferenceManager.addLockedApp(app);
        } else {
            preferenceManager.removeLockedApp(app.getPackageName());
        }
        updateAppCount();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Only process sensor data if exercising and not unlocking
        if (isExercising && !isUnlockActive) {
            exerciseCounter.onSensorChanged(event);

            String info = "Jumps: " + exerciseCounter.getJumpCount() +
                    "\nJumping Jacks: " + exerciseCounter.getJumpingJackCount() +
                    "\nEarned Time: " + timeCounter.getFormattedCredits();
            exerciseCountInfo.setText(info);

            // Update remaining time display with earned credits
            remainingTime.setText(timeCounter.getFormattedCredits());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void startUnlockCountdown() {
        if (unlockRunnable != null) {
            handler.removeCallbacks(unlockRunnable);
        }

        unlockRunnable = new Runnable() {
            @Override
            public void run() {
                unlockTimeLeft = timeCounter.getRemainingUnlockTime();

                if (isUnlockActive && unlockTimeLeft > 0) {
                    remainingTime.setText(timeCounter.formatMillis(unlockTimeLeft));
                    handler.postDelayed(this, 1000);
                } else {
                    // Countdown finished
                    remainingTime.setText(timeCounter.getFormattedCredits()); // Show earned credits
                    isUnlockActive = false;
                    updateUIForUnlockState();

                    // Ensure service state is updated
                    AppBlockerService blocker = AppBlockerService.getInstance();
                    if (blocker != null) {
                        blocker.endExerciseUnlock();
                    }

                    unlockRunnable = null; // Clear the runnable
                }
            }
        };
        handler.post(unlockRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (unlockRunnable != null) {
            handler.removeCallbacks(unlockRunnable);
        }
        stopExercising();
    }
}