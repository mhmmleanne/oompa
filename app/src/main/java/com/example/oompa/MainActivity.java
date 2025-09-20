package com.example.oompa;

import static com.example.oompa.DialogFragment.appArray;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


public class MainActivity extends AppCompatActivity implements DialogFragmentListener<App> {

    DialogFragment dialogFragment;
    TextView appCount;
    Button blockedAppsButton;
    Button blockedTimingButton;
    Button unlockAppsButton;
    Button startExercisingButton;
    RecycleViewAdapter adapter;



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

        blockedAppsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialogFragment = new DialogFragment();
                dialogFragment.show(getSupportFragmentManager(),"DialogFragment");

            }
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





}