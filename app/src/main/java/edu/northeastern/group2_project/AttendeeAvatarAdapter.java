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

public class AttendeeAvatarAdapter extends RecyclerView.Adapter<AttendeeAvatarAdapter.AvatarViewHolder> {
    private final List<Attendee> attendees;
    private final Context context;
    private final int maxAvatars;
    private final OnAvatarClickListener listener;

    public interface OnAvatarClickListener {
        void onAvatarClick(); // Trigger popup when any avatar is clicked
    }

    public AttendeeAvatarAdapter(Context context, List<Attendee> attendees, int maxAvatars, OnAvatarClickListener listener) {
        this.context = context;
        this.attendees = attendees;
        this.maxAvatars = maxAvatars;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AvatarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_attendee_avatar, parent, false);
        return new AvatarViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AvatarViewHolder holder, int position) {
        if (position < maxAvatars && position < attendees.size()) {
            Attendee attendee = attendees.get(position);
            holder.moreText.setVisibility(View.GONE);
            holder.avatarImage.setVisibility(View.VISIBLE);
            if (attendee.getProfileImageUrl() != null && !attendee.getProfileImageUrl().isEmpty()) {
                Glide.with(context)
                        .load(attendee.getProfileImageUrl())
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .into(holder.avatarImage);
            } else {
                holder.avatarImage.setImageResource(R.drawable.ic_launcher_foreground);
            }
        } else if (position == maxAvatars && attendees.size() > maxAvatars) {
            holder.avatarImage.setVisibility(View.GONE);
            holder.moreText.setVisibility(View.VISIBLE);
            holder.moreText.setText("+" + (attendees.size() - maxAvatars));
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onAvatarClick();
        });
    }

    @Override
    public int getItemCount() {
        return Math.min(attendees.size(), maxAvatars + 1);
    }

    static class AvatarViewHolder extends RecyclerView.ViewHolder {
        ImageView avatarImage;
        TextView moreText;
        public AvatarViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImage = itemView.findViewById(R.id.attendeeAvatarImage);
            moreText = itemView.findViewById(R.id.attendeeAvatarMore);
        }
    }
} 