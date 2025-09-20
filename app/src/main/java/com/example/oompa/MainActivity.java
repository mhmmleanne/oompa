package com.example.oompa;

import static com.example.oompa.DialogFragment.appArray;

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
import com.example.oompa.services.earnedTimeCounter;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;



import java.util.concurrent.locks.Lock;


public class MainActivity extends AppCompatActivity implements DialogFragmentListener<App>, SensorEventListener{

    DialogFragment dialogFragment;
    TextView appCount;
    Button blockedAppsButton;
    Button blockedTimingButton;
    Button unlockAppsButton;
    Button startExercisingButton;
    RecycleViewAdapter adapter;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private TextView textView;
    private TextView exerciseCountInfo;

    private TextView remainingTime;

    private earnedTimeCounter timeCounter;
    private ExerciseCounter exerciseCounter;

    private Handler handler = new Handler();


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
        blockedAppsButton = findViewById(R.id.modify_blocked_apps_button);
        appCount = findViewById(R.id.blocked_apps_count);
        startExercisingButton = findViewById(R.id.start_exercise_button);
        unlockAppsButton = findViewById(R.id.unlock_app_button);
        exerciseCountInfo = findViewById(R.id.exercises_count);
        remainingTime = findViewById(R.id.remaining_time_count);


        //Init Counters
        timeCounter = earnedTimeCounter.getInstance(this);
        exerciseCounter = ExerciseCounter.getInstance(timeCounter);




        blockedAppsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialogFragment = new DialogFragment();
                dialogFragment.show(getSupportFragmentManager(), "DialogFragment");

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
            sensorManager.unregisterListener(this);

            // Check if we have earned time first
            if (!timeCounter.hasTime()) {
                Log.d("MainActivity", "No earned time available");
                remainingTime.setText("No time earned!");
                return;
            }

            Log.d("MainActivity", "Starting unlock with time: " + timeCounter.getFormattedTime());

            // Start the countdown in the timer
            timeCounter.startCountdown();

            // Try to notify the service if available
            AppBlockerService blocker = AppBlockerService.getInstance();
            if (blocker != null) {
                Log.d("MainActivity", "Notifying AppBlockerService");
                blocker.startExerciseUnlockWithEarnedTime();
            } else {
                Log.w("MainActivity", "AppBlockerService not available - countdown will work but apps won't unlock");
            }

            // Start UI countdown regardless
            startCountdownDuringUnlock();
        });
    }





    @Override
    public void onDataSelected(int position, App app) {
        // Loop through all items in your static list
        int count = 0;
        for (int i = 0; i < appArray.size(); i++) {
            App current = appArray.get(i);
            if (current.getSelected()) {
                count += 1;
                Log.d("MainActivity", "Selected: " + current.getAppName() + " at pos " + i);
            }
        }
        appCount.setText(String.valueOf(count));
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        exerciseCounter.onSensorChanged(event);

        String info =
                "Jumps: " + exerciseCounter.getJumpCount() +
                        "\nJumping Jacks: " + exerciseCounter.getJumpingJackCount() +
                        "\nEarned Time: " + timeCounter.getFormattedTime();

        exerciseCountInfo.setText(info);
        remainingTime.setText(timeCounter.getFormattedTime());
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // re-attach listener if needed
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    private void startCountdownDuringUnlock() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                // DON'T call countdown() here - let AppBlockerService handle it
                // Just update the UI with current time
                long currentTime = timeCounter.getEarnedTime();
                android.util.Log.d("MainActivity", "UI Update - current time: " + currentTime + "ms");

                if (timeCounter.hasTime()) {
                    remainingTime.setText(timeCounter.getFormattedTime());
                    handler.postDelayed(this, 1000); // Update UI every second
                } else {
                    // countdown finished
                    remainingTime.setText("00:00");
                    android.util.Log.d("MainActivity", "Time expired - ending unlock");
                    AppBlockerService blocker = AppBlockerService.getInstance();
                    if (blocker != null) blocker.endExerciseUnlock();
                }
            }
        });
    }

}