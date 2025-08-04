package edu.northeastern.group2_project;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {
    public static final String EXTRA_USERNAME = "extra_username";
    public static final String EXTRA_USER_ID = "extra_user_id";
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String userId;
    private String targetUserId; // ID of the user whose profile we're viewing
    private boolean isViewingOwnProfile; // Flag to indicate if viewing own profile
    
    // UI elements
    private TextView profileName;
    private TextView attendedCount;
    private TextView hostedCount;
    private TextView likedCount;
    private ImageView profileAvatar;
    private TextView emailView, phoneView;
    private TabLayout tabLayout;
    private RecyclerView eventsRecyclerView;
    private EventsAdapter eventsAdapter;
    
    // Data
    private List<Event> attendedEvents = new ArrayList<>();
    private List<Event> hostedEvents = new ArrayList<>();
    private List<Event> likedEvents = new ArrayList<>();
    private List<Event> currentEvents = new ArrayList<>();

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_profile);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();
        }

        // Get target user ID from intent (if viewing someone else's profile)
        targetUserId = getIntent().getStringExtra(EXTRA_USER_ID);
        if (targetUserId == null) {
            // If no target user ID, use current user's ID
            targetUserId = userId;
        }
        
        // Check if viewing own profile
        isViewingOwnProfile = targetUserId.equals(userId);

        // Configure Google Sign-In for logout
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Initialize UI elements
        initializeViews();
        
        // Set up TabLayout
        setupTabLayout();
        
        // Set up RecyclerView
        setupRecyclerView();
        
        // Load user data
        loadUserData();
        
        // Load events
        loadUserEvents();
        
        // Set up logout button (only show if viewing own profile)
        Button logoutButton = findViewById(R.id.logoutButton);
        if (targetUserId.equals(userId)) {
            // Viewing own profile, show logout button
            logoutButton.setVisibility(View.VISIBLE);
            logoutButton.setOnClickListener(view -> confirmLogout());
        } else {
            // Viewing someone else's profile, hide logout button
            logoutButton.setVisibility(View.GONE);
        }

        // Find Edit Profile button (Only show if viewing own profile)
        Button editProfileBtn = findViewById(R.id.btn_edit_profile);
        if (isViewingOwnProfile) {
            editProfileBtn.setVisibility(View.VISIBLE);
            editProfileBtn.setOnClickListener(v -> {
                // Launch edit profile screen
                Intent i = new Intent(ProfileActivity.this, EditProfileActivity.class);
                i.putExtra(EditProfileActivity.EXTRA_USERNAME, targetUserId);
                startActivity(i);
            });
        } else {
            // Viewing someone else's profile, hide edit profile button
            editProfileBtn.setVisibility(View.GONE);
        }

    }

    private void initializeViews() {
        profileName = findViewById(R.id.profile_name);
        attendedCount = findViewById(R.id.profile_attended_count);
        hostedCount = findViewById(R.id.profile_hosted_count);
        likedCount = findViewById(R.id.profile_liked_count);
        profileAvatar = findViewById(R.id.profile_avatar);
        emailView        = findViewById(R.id.profile_email);
        phoneView        = findViewById(R.id.profile_phone);
        tabLayout = findViewById(R.id.tabLayout);
        eventsRecyclerView = findViewById(R.id.eventsRecyclerView);
    }

    private void setupTabLayout() {
        tabLayout.addTab(tabLayout.newTab().setText("Attended"));
        tabLayout.addTab(tabLayout.newTab().setText("Hosted"));
        tabLayout.addTab(tabLayout.newTab().setText("Liked"));
        
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    // Attended tab
                    currentEvents.clear();
                    currentEvents.addAll(attendedEvents);
                    eventsAdapter.updateEvents(currentEvents);
                } else if (tab.getPosition() == 1) {
                    // Hosted tab
                    currentEvents.clear();
                    currentEvents.addAll(hostedEvents);
                    eventsAdapter.updateEvents(currentEvents);
                } else {
                    // Liked tab
                    currentEvents.clear();
                    currentEvents.addAll(likedEvents);
                    eventsAdapter.updateEvents(currentEvents);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupRecyclerView() {
        eventsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        eventsAdapter = new EventsAdapter(currentEvents, clickedEvent -> {
            Log.d("ProfileActivity", "Clicked event: " + (clickedEvent.getName() != null ? clickedEvent.getName() : "null") + ", id: " + clickedEvent.getId());
            Intent intent = new Intent(ProfileActivity.this, eventDetail.class);
            intent.putExtra("EVENT_ID", clickedEvent.getId());
            // Pass flag to disable profile navigation if viewing someone else's profile
            intent.putExtra("DISABLE_PROFILE_NAVIGATION", !isViewingOwnProfile);
            startActivity(intent);
        });
        eventsRecyclerView.setAdapter(eventsAdapter);
    }

    private void loadUserData() {
        if (targetUserId != null) {
            // Load user info from Firestore
            db.collection("users").document(targetUserId)
                    .get()
                    .addOnSuccessListener(document -> {
                        if (document.exists()) {
                            String name = document.getString("name");
                            String profileImageUrl = document.getString("profileImageUrl");
                            String email        = document.getString("email");
                            String phone        = document.getString("phone");

                            if (name != null) {
                                profileName.setText(name);
                            } else {
                                // Fallback to email if no name
                                String emailFallback = document.getString("email");
                                if (emailFallback != null) {
                                    profileName.setText(emailFallback.split("@")[0]);
                                } else {
                                    profileName.setText("Unknown User");
                                }
                            }
                            
                            // Load profile image
                            if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                                Glide.with(this)
                                        .load(profileImageUrl)
                                        .placeholder(R.drawable.ic_default_avatar)
                                        .into(profileAvatar);
                            }
                            // load email & phone
                            if (email != null && !email.isEmpty()) {
                                emailView.setText("Email: " + email);
                            }
                            if (phone != null && !phone.isEmpty()) {
                                phoneView.setText("Phone: " + phone);
                            }
                        } else {
                            profileName.setText("User not found");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w("ProfileActivity", "Error loading user data", e);
                        profileName.setText("Error loading profile");
                    });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh events when returning to this activity
        loadUserEvents();
    }

    private void loadUserEvents() {
        if (targetUserId == null) return;
        
        // Load attended events
        loadAttendedEvents();
        
        // Load hosted events
        loadHostedEvents();
        
        // Load liked events
        loadLikedEvents();
    }

    private void loadAttendedEvents() {
        db.collection("users").document(targetUserId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        List<String> joinedEventIds = (List<String>) document.get("joinedEvents");
                        if (joinedEventIds != null && !joinedEventIds.isEmpty()) {
                            loadEventsByIds(joinedEventIds, true);
                        } else {
                            attendedCount.setText("0");
                            updateEventsList();
                        }
                    } else {
                        attendedCount.setText("0");
                        updateEventsList();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w("ProfileActivity", "Error loading attended events", e);
                    attendedCount.setText("0");
                    updateEventsList();
                });
    }

    private void loadHostedEvents() {
        Log.d("ProfileActivity", "Loading hosted events for user ID: " + targetUserId);
        db.collection("users").document(targetUserId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        List<String> hostedEventIds = (List<String>) document.get("hostedEvents");
                        if (hostedEventIds != null && !hostedEventIds.isEmpty()) {
                            loadHostedEventsByIds(hostedEventIds);
                        } else {
                            hostedCount.setText("0");
                            updateEventsList();
                        }
                    } else {
                        hostedCount.setText("0");
                        updateEventsList();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w("ProfileActivity", "Error loading hosted events", e);
                    hostedCount.setText("0");
                    updateEventsList();
                });
    }

    private void loadLikedEvents() {
        Log.d("ProfileActivity", "Loading liked events for user ID: " + targetUserId);
        db.collection("users").document(targetUserId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        List<String> favoritedEventIds = (List<String>) document.get("favoritedEventIds");
                        if (favoritedEventIds != null && !favoritedEventIds.isEmpty()) {
                            loadEventsByIds(favoritedEventIds, false);
                        } else {
                            likedCount.setText("0");
                            updateEventsList();
                        }
                    } else {
                        likedCount.setText("0");
                        updateEventsList();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w("ProfileActivity", "Error loading liked events", e);
                    likedCount.setText("0");
                    updateEventsList();
                });
    }

    private void loadEventsByIds(List<String> eventIds, boolean isAttended) {
        List<Event> events = new ArrayList<>();
        final int[] loadedCount = {0};
        
        for (String eventId : eventIds) {
            db.collection("events").document(eventId)
                    .get()
                    .addOnSuccessListener(document -> {
                        if (document.exists()) {
                            Event event = createEventFromDocument(document);
                            events.add(event);
                        }
                        loadedCount[0]++;
                        
                        if (loadedCount[0] == eventIds.size()) {
                            if (isAttended) {
                                attendedEvents.clear();
                                attendedEvents.addAll(events);
                                attendedCount.setText(String.valueOf(attendedEvents.size()));
                            } else {
                                // This is for liked events
                                likedEvents.clear();
                                likedEvents.addAll(events);
                                likedCount.setText(String.valueOf(likedEvents.size()));
                            }
                            updateEventsList();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w("ProfileActivity", "Error loading event " + eventId, e);
                        loadedCount[0]++;
                        if (loadedCount[0] == eventIds.size()) {
                            if (isAttended) {
                                attendedEvents.clear();
                                attendedEvents.addAll(events);
                                attendedCount.setText(String.valueOf(attendedEvents.size()));
                            } else {
                                // This is for liked events
                                likedEvents.clear();
                                likedEvents.addAll(events);
                                likedCount.setText(String.valueOf(likedEvents.size()));
                            }
                            updateEventsList();
                        }
                    });
        }
    }

    private void loadHostedEventsByIds(List<String> eventIds) {
        List<Event> events = new ArrayList<>();
        final int[] loadedCount = {0};
        
        for (String eventId : eventIds) {
            db.collection("events").document(eventId)
                    .get()
                    .addOnSuccessListener(document -> {
                        if (document.exists()) {
                            Event event = createEventFromDocument(document);
                            events.add(event);
                            Log.d("ProfileActivity", "Added hosted event: " + event.getName() + " (ID: " + event.getId() + ")");
                        }
                        loadedCount[0]++;
                        
                        if (loadedCount[0] == eventIds.size()) {
                            hostedEvents.clear();
                            hostedEvents.addAll(events);
                            hostedCount.setText(String.valueOf(hostedEvents.size()));
                            updateEventsList();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w("ProfileActivity", "Error loading hosted event " + eventId, e);
                        loadedCount[0]++;
                        if (loadedCount[0] == eventIds.size()) {
                            hostedEvents.clear();
                            hostedEvents.addAll(events);
                            hostedCount.setText(String.valueOf(hostedEvents.size()));
                            updateEventsList();
                        }
                    });
        }
    }

    private Event createEventFromDocument(DocumentSnapshot doc) {
        Event event = new Event();
        event.setId(doc.getId());
        
        // Handle name with null check
        String name = doc.getString("name");
        event.setName(name != null ? name : "Unnamed Event");
        
        // Handle location with null check
        String location = doc.getString("location");
        event.setLocation(location != null ? location : "Unknown Location");
        
        // Handle description with null check
        String description = doc.getString("description");
        event.setDescription(description != null ? description : "No description available.");

        // Handle start time
        Object startTimeObj = doc.get("startTime");
        String startTimeStr = "";
        if (startTimeObj != null) {
            if (startTimeObj instanceof String) {
                startTimeStr = (String) startTimeObj;
            } else if (startTimeObj instanceof com.google.firebase.Timestamp) {
                Date date = ((com.google.firebase.Timestamp) startTimeObj).toDate();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                startTimeStr = sdf.format(date);
            } else if (startTimeObj instanceof Date) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                startTimeStr = sdf.format((Date) startTimeObj);
            } else {
                startTimeStr = startTimeObj.toString();
            }
        }
        event.setStartTime(startTimeStr);

        // Handle end time
        Object endTimeObj = doc.get("endTime");
        String endTimeStr = "";
        if (endTimeObj != null) {
            if (endTimeObj instanceof String) {
                endTimeStr = (String) endTimeObj;
            } else if (endTimeObj instanceof com.google.firebase.Timestamp) {
                Date date = ((com.google.firebase.Timestamp) endTimeObj).toDate();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                endTimeStr = sdf.format(date);
            } else if (endTimeObj instanceof Date) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                endTimeStr = sdf.format((Date) endTimeObj);
            } else {
                endTimeStr = endTimeObj.toString();
            }
        }
        event.setEndTime(endTimeStr);

        // Handle image preview with null check
        List<String> imageUrls = (List<String>) doc.get("imageUrls");
        if (imageUrls != null && !imageUrls.isEmpty()) {
            event.setImageUrl(imageUrls.get(0));
        } else {
            event.setImageUrl(null); // Explicitly set to null if no images
        }

        return event;
    }

    private void updateEventsList() {
        // Update the current events list based on selected tab
        if (tabLayout.getSelectedTabPosition() == 0) {
            // Attended tab
            currentEvents.clear();
            currentEvents.addAll(attendedEvents);
        } else if (tabLayout.getSelectedTabPosition() == 1) {
            // Hosted tab
            currentEvents.clear();
            currentEvents.addAll(hostedEvents);
        } else {
            // Liked tab
            currentEvents.clear();
            currentEvents.addAll(likedEvents);
        }
        eventsAdapter.updateEvents(currentEvents);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
    
    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout from TrailConnect?")
                .setPositiveButton("Yes", (dialog, which) -> logout())
                .setNegativeButton("No", null)
                .show();
    }
    
    private void logout() {
        // Sign out from Google Sign-In
        mGoogleSignInClient.signOut()
                .addOnCompleteListener(this, task -> {
                    // Sign out from Firebase
                    FirebaseAuth.getInstance().signOut();

                    // Clear locally stored user data
                    UserLocalStorage localStorage = new UserLocalStorage(this);
                    localStorage.clearUserData();

                    Toast.makeText(this, "You've been logged out", Toast.LENGTH_SHORT).show();

                    // Navigate back to login screen
                    Intent intent = new Intent(ProfileActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }
}

