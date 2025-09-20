package com.example.oompa;

import static com.example.oompa.DialogFragment.appArray;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.oompa.classes.LockedApp;
import com.example.oompa.services.AppBlockerService;
import com.example.oompa.services.PreferenceManager;
import com.example.oompa.services.earnedTimeCounter;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;



import java.util.List;
import java.util.concurrent.locks.Lock;


public class MainActivity extends AppCompatActivity implements DialogFragmentListener<App>, SensorEventListener{

    DialogFragment dialogFragment;
    TextView appCount;
    Button blockedAppsButton;
    Button blockedTimingButton;
    Button unlockAppsButton;
    Button startExercisingButton;
    Button testBlockingButton; // Add test button
    RecycleViewAdapter adapter;

    private PreferenceManager preferenceManager;
    private Handler handler = new Handler();
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private TextView textView;
    private TextView exerciseCountInfo;

    private TextView remainingTime;

    private earnedTimeCounter timeCounter;
    private ExerciseCounter exerciseCounter;

    private long unlockTimeLeft; // milliseconds
    private boolean isUnlockActive = false;
    private long leftoverTime = 0;  // leftover from previous unlock session
    private Runnable unlockRunnable;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize PreferenceManager
        preferenceManager = new PreferenceManager(this);

        blockedAppsButton = findViewById(R.id.modify_blocked_apps_button);
        appCount = findViewById(R.id.blocked_apps_count);
        startExercisingButton = findViewById(R.id.start_exercise_button);
        unlockAppsButton = findViewById(R.id.unlock_app_button);
        exerciseCountInfo = findViewById(R.id.exercises_count);
        remainingTime = findViewById(R.id.remaining_time_count);


        //Init Counters
        timeCounter = earnedTimeCounter.getInstance(this);
        exerciseCounter = ExerciseCounter.getInstance(timeCounter);



        // Start the service
        Intent intent = new Intent(this, AppBlockerService.class);
        startService(intent);

        // Update UI with initial count
        updateAppCount();

        // Set up button click listener
        blockedAppsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialogFragment = new DialogFragment();
                dialogFragment.show(getSupportFragmentManager(),"DialogFragment");

            }
        });

        startExercisingButton.setOnClickListener(v -> {
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            }
        });

        unlockAppsButton.setOnClickListener(v -> {
            if (sensorManager != null) sensorManager.unregisterListener(this);

            long newEarnedTime = timeCounter.getEarnedTime(); // only new earned from exercises
            if (newEarnedTime <= 0) {
                remainingTime.setText("No time earned!");
                return;
            }

            startExercisingButton.setEnabled(false);
            startExercisingButton.setAlpha(0.5f);

            // Combine leftover + new earned time
            unlockTimeLeft = leftoverTime + newEarnedTime;
            leftoverTime = 0;  // reset leftover

            // Start countdown in earnedTimeCounter singleton (so service knows)
            timeCounter.reset();          // consume all earned time
            timeCounter.startCountdown(); // begin countdown with unlockTimeLeft

            isUnlockActive = true;

            AppBlockerService blocker = AppBlockerService.getInstance();
            if (blocker != null) {
                blocker.startExerciseUnlock(unlockTimeLeft);
            }

            startUnlockCountdown();
        });



    }
    @Override
    protected void onResume() {
        super.onResume();
        updateAppCount();

        if (timeCounter.isCountingDown()) {
            isUnlockActive = true;
            unlockTimeLeft = timeCounter.getEarnedTime();

            // Only start countdown if no existing Runnable is running
            if (unlockRunnable == null) {
                startUnlockCountdown();
            }

            startExercisingButton.setEnabled(false);
            startExercisingButton.setAlpha(0.5f);
        } else {
            remainingTime.setText(timeCounter.getFormattedTime());
            startExercisingButton.setEnabled(true);
            startExercisingButton.setAlpha(1f);
        }

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }






    private void updateAppCount() {
        AppBlockerService blocker = AppBlockerService.getInstance();
        if (blocker != null) {
            int count = blocker.getLockedApps().size();
            appCount.setText(String.valueOf(count));
            Log.d("MainActivity", "Service available, locked apps: " + count);
        } else {
            List<App> savedApps = preferenceManager.getLockedApps();
            int count = 0;
            for (App app : savedApps) {
                if (app.getSelected()) {
                    count++;
                }
            }
            appCount.setText(String.valueOf(count));
            Log.d("MainActivity", "Service not ready, using saved data: " + count);

            // Try again in 500ms if service isn't ready
            handler.postDelayed(this::updateAppCount, 500);
        }
    }

    @Override
    public void onDataSelected(int position, App app) {
        // Save to preferences as backup
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

        String info =
                "Jumps: " + exerciseCounter.getJumpCount() +
                        "\nJumping Jacks: " + exerciseCounter.getJumpingJackCount() +
                        "\nEarned Time: " + timeCounter.getFormattedTime();

        exerciseCountInfo.setText(info);
        if (!isUnlockActive) {
            remainingTime.setText(timeCounter.getFormattedTime());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    private void startUnlockCountdown() {
        if (unlockRunnable != null) {
            handler.removeCallbacks(unlockRunnable); // cancel previous
        }

        unlockRunnable = new Runnable() {
            @Override
            public void run() {
                if (isUnlockActive && unlockTimeLeft > 0) {
                    long minutes = unlockTimeLeft / 1000 / 60;
                    long seconds = (unlockTimeLeft / 1000) % 60;
                    remainingTime.setText(String.format("%02d:%02d", minutes, seconds));

                    unlockTimeLeft -= 1000;

                    // decrement singleton
                    timeCounter.countdown();

                    handler.postDelayed(this, 1000);
                } else {
                    remainingTime.setText("00:00");
                    isUnlockActive = false;
                    startExercisingButton.setEnabled(true);
                    startExercisingButton.setAlpha(1f);

                    AppBlockerService blocker = AppBlockerService.getInstance();
                    if (blocker != null) blocker.endExerciseUnlock();

                    leftoverTime = timeCounter.getEarnedTime();
                    timeCounter.stopCountdown();
                }
            }
        };

        handler.post(unlockRunnable);
    }





}
