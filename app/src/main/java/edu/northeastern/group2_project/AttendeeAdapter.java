package edu.northeastern.group2_project;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class AttendeeAdapter extends RecyclerView.Adapter<AttendeeAdapter.AttendeeViewHolder> {

    private final List<Attendee> attendees;
    private final Context context;

    public AttendeeAdapter(Context context, List<Attendee> attendees) {
        this.context = context;
        this.attendees = attendees;
    }

    public static class AttendeeViewHolder extends RecyclerView.ViewHolder {
        ImageView profileImage;
        TextView name;

        public AttendeeViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.attendeeProfileImage);
            name = itemView.findViewById(R.id.attendeeName);
        }
    }

    @NonNull
    @Override
    public AttendeeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_attendee, parent, false);
        return new AttendeeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AttendeeViewHolder holder, int position) {
        Attendee attendee = attendees.get(position);
        
        holder.name.setText(attendee.getName());
        
        // Load profile image using Glide
        if (attendee.getProfileImageUrl() != null && !attendee.getProfileImageUrl().isEmpty()) {
            Glide.with(context)
                    .load(attendee.getProfileImageUrl())
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .into(holder.profileImage);
        } else {
            holder.profileImage.setImageResource(R.drawable.ic_launcher_foreground);
        }
    }

    @Override
    public int getItemCount() {
        return attendees.size();
    }
} 