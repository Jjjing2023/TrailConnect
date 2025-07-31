package edu.northeastern.group2_project;

// Simple event model for Firestore mapping.
// Add more fields as needed - these match your Firestore "events" doc structure.
public class Event {
    private String id;        // Firestore doc ID
    private String name;
    private String startTime;
    private String endTime;
    private String location;
    private String description;
    private String imageUrl;  // Use first image from imageUrls for preview

    public Event() {} // Needed for Firestore

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}