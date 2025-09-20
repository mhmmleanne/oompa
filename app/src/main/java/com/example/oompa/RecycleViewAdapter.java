package com.example.oompa;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class RecycleViewAdapter extends RecyclerView.Adapter<RecycleViewAdapter.MyViewHolder> {
    //RecycleView Adapter when selecting users to associate with certain items in AddPeople.java
    private Context context;
    private ArrayList<App> appArray = new ArrayList<>();
    private final RecycleViewInterface recycleViewInterface;




    //default initialization
    public RecycleViewAdapter(Context context, ArrayList<App> appArray, RecycleViewInterface recycleViewInterface){
        this.context = context;
        this.appArray = appArray;
        this.recycleViewInterface = recycleViewInterface;
    }

    @NonNull
    @Override
    public RecycleViewAdapter.MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        //where we inflate the layout
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.person_recycle_view_row,parent,false);

        return new RecycleViewAdapter.MyViewHolder(view, recycleViewInterface);
    }

    @Override
    public void onBindViewHolder(@NonNull RecycleViewAdapter.MyViewHolder holder, int position) {
        App currentApp = appArray.get(position);

        holder.icon.setImageResource(currentApp.getAppIcon());
        holder.nameText.setText(currentApp.getAppName());

        if (currentApp.getSelected()) {
            holder.groupButton.setImageResource(R.drawable.added_icon);
        } else {
            holder.groupButton.setImageResource(R.drawable.add_icon);
        }
    }


    @Override
    public int getItemCount() {
        //number of items want displayed
        return appArray.size();
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder {
        //recycle view oncreate
        TextView nameText;
        ImageButton groupButton;
        ImageView icon;
        public MyViewHolder(@NonNull View itemView, RecycleViewInterface recycleViewInterface) {
            super(itemView);
           icon = itemView.findViewById(R.id.person_avatar);
           nameText = itemView.findViewById(R.id.person_name);
           groupButton = itemView.findViewById(R.id.add_button);
           groupButton.setOnClickListener(new View.OnClickListener() {
               @Override
               public void onClick(View view) {
                   if (recycleViewInterface != null){
                       int position = getAdapterPosition();

                       if (position != RecyclerView.NO_POSITION){
                           recycleViewInterface.onButtonClick(position);
                       }
                   }
               }
           });

        }
    }
}
