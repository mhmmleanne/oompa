package com.example.oompa;

import static android.view.View.VISIBLE;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.oompa.services.AppBlockerService;
import com.example.oompa.services.PreferenceManager;

import java.util.ArrayList;

public class DialogFragment extends androidx.fragment.app.DialogFragment implements RecycleViewInterface {

    private View rootView;
    private RecyclerView appView;
    private Button confirmButton;
    private TextView titleText;
    private RecycleViewAdapter adapter;
    private DialogFragmentListener<App> listener;

    // static list of apps
    public static ArrayList<App> appArray;
    PreferenceManager preferenceManager;


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        rootView = inflater.inflate(R.layout.fragment_select_group, container, false);

        // Initialize PreferenceManager
        preferenceManager = new PreferenceManager(getContext());

        titleText = rootView.findViewById(R.id.select_group);
        titleText.setText(R.string.modify_blocked_apps);
        appView = rootView.findViewById(R.id.select_group_recycle);
        appArray = new ArrayList<>();
        showAppList();
        adapter = new RecycleViewAdapter(getActivity(), appArray, this);
        appView.setAdapter(adapter);
        appView.setLayoutManager(new LinearLayoutManager(getActivity()));

        confirmButton = rootView.findViewById(R.id.confirm_button);
        confirmButton.setVisibility(View.GONE);

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    //  Toggle selected state when user clicks an item button
    @Override
    public void onButtonClick(int position) {
        App app = appArray.get(position);
        app.setSelected(!app.getSelected());
        appView.getAdapter().notifyItemChanged(position);

        confirmButton.setVisibility(VISIBLE);
        confirmButton.setOnClickListener(v -> {
            if (listener != null) {
                ArrayList<App> selectedApps = getSelectedApps();
                ArrayList<App> deselectedApps = getDeselectedApps();

                Log.d("DialogFragment", "Selected apps: " + selectedApps.size());
                Log.d("DialogFragment", "Deselected apps: " + deselectedApps.size());

                // Add selected apps
                for (App selectedApp : selectedApps) {
                    Log.d("DialogFragment", "Adding app: " + selectedApp.toString());
                    AppBlockerService blocker = AppBlockerService.getInstance();
                    if (blocker != null) {
                        blocker.addLockedApp(selectedApp);
                        blocker.updateActiveLocks();
                    } else {
                        // Service not ready, save to preferences
                        preferenceManager.addLockedApp(selectedApp);
                    }
                    listener.onDataSelected(position, selectedApp);
                }

                // Remove deselected apps
                for (App deselectedApp : deselectedApps) {
                    Log.d("DialogFragment", "Removing app: " + deselectedApp.toString());
                    AppBlockerService blocker = AppBlockerService.getInstance();
                    if (blocker != null) {
                        blocker.removeLockedApp(deselectedApp.getPackageName());
                        blocker.updateActiveLocks();
                    } else {
                        // Service not ready, remove from preferences
                        preferenceManager.removeLockedApp(deselectedApp.getPackageName());
                    }
                    listener.onDataSelected(position, deselectedApp);
                }

                AppBlockerService blocker = AppBlockerService.getInstance();
                if (blocker != null) {
                    Log.d("DialogFragment", "Total locked apps: " + blocker.getLockedApps().size());
                }
            }
            dismiss();
        });
    }

    private ArrayList<App> getSelectedApps() {
        ArrayList<App> selected = new ArrayList<>();
        for (App app : appArray) {
            if (app.getSelected()) {
                selected.add(app);
            }
        }
        return selected;
    }

    private ArrayList<App> getDeselectedApps() {
        ArrayList<App> deselected = new ArrayList<>();
        for (App app : appArray) {
            if (!app.getSelected()) {
                // Check if this app was previously selected (locked)
                AppBlockerService blocker = AppBlockerService.getInstance();
                boolean wasLocked = false;
                if (blocker != null) {
                    wasLocked = blocker.isLocked(app.getPackageName());
                } else {
                    wasLocked = preferenceManager.isLocked(app.getPackageName());
                }

                if (wasLocked) {
                    deselected.add(app);
                }
            }
        }
        return deselected;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof DialogFragmentListener) {
            listener = (DialogFragmentListener<App>) context;
        } else {
            throw new RuntimeException(context + " must implement DialogFragmentListener");
        }
    }

    private void showAppList() {
        String[] nameList = getResources().getStringArray(R.array.category_name_array);
        String[] packageList = getResources().getStringArray(R.array.category_package_array);
        TypedArray imageArray = getResources().obtainTypedArray(R.array.category_item_array);

        AppBlockerService blocker = AppBlockerService.getInstance();

        for (int i = 0; i < nameList.length; i++) {
            boolean exists = false;
            for (App app : appArray) {
                if (app.getPackageName().equals(packageList[i])) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                int imageResId = imageArray.getResourceId(i, 0);

                boolean isSelected = false;
                if (blocker != null && blocker.isLocked(packageList[i])) {
                    isSelected = true; // restore blocked state from service
                } else if (preferenceManager.isLocked(packageList[i])) {
                    isSelected = true; // restore blocked state from preferences if service not ready
                }

                appArray.add(new App(packageList[i], nameList[i], isSelected, imageResId));
                Log.d("DialogFragment", "Added app: " + nameList[i] + ", locked: " + isSelected);
            }
        }

        imageArray.recycle();
    }
}