package edu.northeastern.group2_project;

public class Attendee {
    private String userId;
    private String name;
    private String profileImageUrl;
    private String email;

    public Attendee() {
        // Required empty constructor for Firestore
    }

    public Attendee(String userId, String name, String profileImageUrl, String email) {
        this.userId = userId;
        this.name = name;
        this.profileImageUrl = profileImageUrl;
        this.email = email;
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
} 