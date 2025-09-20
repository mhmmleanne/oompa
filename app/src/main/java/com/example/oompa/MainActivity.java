package com.example.oompa;

import static com.example.oompa.DialogFragment.appArray;

import android.content.Intent;
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

import java.util.List;
import java.util.concurrent.locks.Lock;


public class MainActivity extends AppCompatActivity implements DialogFragmentListener<App> {

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

        // Add test blocking button (you'll need to add this to your layout)
        // testBlockingButton = findViewById(R.id.test_blocking_button);

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

        // Test blocking button (uncomment when you add the button to layout)
        /*
        testBlockingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AppBlockerService blocker = AppBlockerService.getInstance();
                if (blocker != null) {
                    blocker.enableImmediateBlocking();
                    Toast.makeText(MainActivity.this, "Blocking enabled for testing", Toast.LENGTH_SHORT).show();
                }
            }
        });
        */
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update count when activity resumes
        updateAppCount();
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

        // Update UI
        updateAppCount();
    }
}