package com.example.oompa;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ExerciseActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private TextView textView;

    private int jumpCount = 0;
    private int jumpingJackCount = 0;

    // States
    private enum JumpState { ON_GROUND, IN_AIR, LANDED }
    private JumpState state = JumpState.ON_GROUND;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.exercise_activity);

        textView = findViewById(R.id.exerciseText);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        } else {
            textView.setText("Accelerometer not available");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        double magnitude = Math.sqrt(x * x + y * y + z * z);

        // Thresholds (tune experimentally)
        double takeoffThreshold = 13.0;  // big upward push
        double freefallThreshold = 7.0;  // lighter than gravity (in air)
        double landingThreshold = 16.0;  // big impact when hitting ground

        switch (state) {
            case ON_GROUND:
                if (magnitude > takeoffThreshold) {
                    state = JumpState.IN_AIR;
                }
                break;

            case IN_AIR:
                if (magnitude < freefallThreshold) {
                    // confirm actually airborne
                    state = JumpState.LANDED;
                }
                break;

            case LANDED:
                if (magnitude > landingThreshold) {
                    jumpCount++;
                    if (jumpCount % 2 == 0) {
                        jumpingJackCount++;
                    }
                    state = JumpState.ON_GROUND;
                }
                break;
        }

        textView.setText(
                "x: " + x +
                        "\ny: " + y +
                        "\nz: " + z +
                        "\nmag: " + magnitude +
                        "\nJumps: " + jumpCount +
                        "\nJumping Jacks: " + jumpingJackCount +
                        "\nState: " + state
        );
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
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }
}
