package com.example.oompa;

import static android.view.View.VISIBLE;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class DialogFragment extends androidx.fragment.app.DialogFragment implements RecycleViewInterface {

    private View rootView;
    private RecyclerView appView;
    private Button confirmButton;
    private TextView titleText;
    private RecycleViewAdapter adapter;

    private DialogFragmentListener<App> listener;

    // 🔹 static list of apps
    public static ArrayList<App> appArray = new ArrayList<>();


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        rootView = inflater.inflate(R.layout.fragment_select_group, container, false);

        titleText = rootView.findViewById(R.id.select_group);
        titleText.setText(R.string.modify_blocked_apps);

        appView = rootView.findViewById(R.id.select_group_recycle);
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

    // 🔹 Toggle selected state when user clicks an item button
    @Override
    public void onButtonClick(int position) {
        appArray.get(position).setSelected(!appArray.get(position).getSelected());
        appView.getAdapter().notifyItemChanged(position);

        confirmButton.setVisibility(VISIBLE);
        confirmButton.setOnClickListener(v -> {
            if (listener != null) {
                ArrayList<App> selectedApps = getSelectedApps();
                for (App app : selectedApps) {
                    listener.onDataSelected(position, app);
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
        String[] nameList = getResources().getStringArray(R.array.category_package_array);
        String[] packageList = getResources().getStringArray(R.array.category_name_array);
        TypedArray imageArray = getResources().obtainTypedArray(R.array.category_item_array);


        for (int i = 0; i < nameList.length; i++) {
            int imageResId = imageArray.getResourceId(i, 0);
            appArray.add(new App(packageList[i], nameList[i], false, imageResId));
        }
        imageArray.recycle();
    }
}
