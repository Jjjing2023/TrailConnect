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

// Adapter for RecyclerView displaying events
public class EventsAdapter extends RecyclerView.Adapter<EventsAdapter.EventViewHolder> {
    public interface OnEventClickListener {
        void onEventClick(Event event);
    }

    private List<Event> events;
    private OnEventClickListener listener;

    public EventsAdapter(List<Event> events, OnEventClickListener listener) {
        this.events = events;
        this.listener = listener;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = events.get(position);
        holder.eventName.setText(event.getName());
        holder.eventLocation.setText(event.getLocation());
        holder.eventStartTime.setText(event.getStartTime());

        // Load event image if available
        if (event.getImageUrl() != null && !event.getImageUrl().isEmpty()) {
            Glide.with(holder.eventImage.getContext())
                    .load(event.getImageUrl())
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .into(holder.eventImage);
        } else {
            holder.eventImage.setImageResource(R.drawable.ic_launcher_foreground);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEventClick(event);
        });
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    public static class EventViewHolder extends RecyclerView.ViewHolder {
        ImageView eventImage;
        TextView eventName, eventLocation, eventStartTime;
        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            eventImage = itemView.findViewById(R.id.eventImage);
            eventName = itemView.findViewById(R.id.eventName);
            eventLocation = itemView.findViewById(R.id.eventLocation);
            eventStartTime = itemView.findViewById(R.id.eventStartTime);
        }
    }
}