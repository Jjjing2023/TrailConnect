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
    private final OnAttendeeClickListener listener;

    public interface OnAttendeeClickListener {
        void onAttendeeClick(Attendee attendee);
    }

    public AttendeeAdapter(Context context, List<Attendee> attendees) {
        this.context = context;
        this.attendees = attendees;
        this.listener = null;
    }

    public AttendeeAdapter(Context context, List<Attendee> attendees, OnAttendeeClickListener listener) {
        this.context = context;
        this.attendees = attendees;
        this.listener = listener;
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
                    .placeholder(R.drawable.ic_default_avatar)
                    .into(holder.profileImage);
        } else {
            holder.profileImage.setImageResource(R.drawable.ic_default_avatar);
        }
        
        // Set click listener for the entire item
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAttendeeClick(attendee);
            }
        });
    }

    @Override
    public int getItemCount() {
        return attendees.size();
    }
} 