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
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        });

        unlockAppsButton.setOnClickListener(v -> {
            if (sensorManager != null) sensorManager.unregisterListener(this);

            long credits = timeCounter.getEarnedTime();
            if (credits <= 0) {
                remainingTime.setText("No time earned!");
                return;
            }

            startExercisingButton.setEnabled(false);
            startExercisingButton.setAlpha(0.5f);

            // Consume credits and start unlock countdown
            timeCounter.resetCredits();
            timeCounter.startCountdown(credits);
            isUnlockActive = true;

            AppBlockerService blocker = AppBlockerService.getInstance();
            if (blocker != null) blocker.startExerciseUnlock(credits);

            startUnlockCountdown();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAppCount();

        if (timeCounter.isCountingDown()) {
            isUnlockActive = true;
            unlockTimeLeft = timeCounter.getRemainingUnlockTime();
            if (unlockRunnable == null) startUnlockCountdown();

            startExercisingButton.setEnabled(false);
            startExercisingButton.setAlpha(0.5f);
        } else {
            remainingTime.setText(timeCounter.getFormattedCredits());
            startExercisingButton.setEnabled(true);
            startExercisingButton.setAlpha(1f);
        }

        if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
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
        exerciseCounter.onSensorChanged(event);

        String info = "Jumps: " + exerciseCounter.getJumpCount() +
                "\nJumping Jacks: " + exerciseCounter.getJumpingJackCount() +
                "\nEarned Time: " + timeCounter.getFormattedCredits();
        exerciseCountInfo.setText(info);

        if (!isUnlockActive) {
            remainingTime.setText(timeCounter.getFormattedCredits());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void startUnlockCountdown() {
        if (unlockRunnable != null) handler.removeCallbacks(unlockRunnable);

        unlockRunnable = new Runnable() {
            @Override
            public void run() {
                unlockTimeLeft = timeCounter.getRemainingUnlockTime();

                if (isUnlockActive && unlockTimeLeft > 0) {
                    remainingTime.setText(timeCounter.formatMillis(unlockTimeLeft));
                    handler.postDelayed(this, 1000);
                } else {
                    remainingTime.setText("00:00");
                    isUnlockActive = false;
                    startExercisingButton.setEnabled(true);
                    startExercisingButton.setAlpha(1f);

                    AppBlockerService blocker = AppBlockerService.getInstance();
                    if (blocker != null) blocker.endExerciseUnlock();

                    timeCounter.stopCountdown();
                }
            }
        };
        handler.post(unlockRunnable);
    }
}
