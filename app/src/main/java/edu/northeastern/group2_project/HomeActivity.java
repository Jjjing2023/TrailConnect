package edu.northeastern.group2_project;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.firestore.Query;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import android.text.Editable;
import android.text.TextWatcher;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import edu.northeastern.group2_project.UserLocalStorage;

public class HomeActivity extends AppCompatActivity {
    private GoogleSignInClient mGoogleSignInClient;
    private EditText searchInput;
    private List<Event> allEvents = new ArrayList<>();
    private EventsAdapter eventsAdapter;
    private FirebaseFirestore db;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Initialize Firebase Firestore
        db = FirebaseFirestore.getInstance();

        // Configure Google Sign-In for logout
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        TextView welcomeText = findViewById(R.id.welcomeText);

        // Try to get name from local storage first for faster UI loading
        UserLocalStorage localStorage = new UserLocalStorage(this);
        String userName = localStorage.getUserName();

        if (!userName.isEmpty()) {
            welcomeText.setText("Welcome " + userName + "!");
        } else {
            // Fall back to Firebase user
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                welcomeText.setText("Welcome " + user.getDisplayName() + "!");
                localStorage.storeUserName(user.getDisplayName());
            } else if (user != null && user.getEmail() != null) {
                // Use email prefix if no display name
                String emailName = user.getEmail().split("@")[0];
                welcomeText.setText("Welcome " + emailName + "!");
                localStorage.storeUserName(emailName);
            } else {
                welcomeText.setText("Welcome to TrailConnect!");
            }
        }

        // Update last login timestamp
        localStorage.updateLastLogin();

        // Initialize search input
        searchInput = findViewById(R.id.searchInput);
        setupSearchFunctionality();

        // Initialize SwipeRefreshLayout
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // Refresh events when user pulls down
            refreshEvents();
        });

        // button to post a new event
        ImageButton postButton = findViewById(R.id.btnPost);
        postButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, CreateEventActivity.class);
            startActivity(intent);
        });

        RecyclerView eventsRecyclerView = findViewById(R.id.eventsRecyclerView);
        eventsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initialize adapter
        eventsAdapter = new EventsAdapter(allEvents, clickedEvent -> {
            Log.d("HomeActivity", "Clicked event: " + (clickedEvent.getName() != null ? clickedEvent.getName() : "null") + ", id: " + clickedEvent.getId());
            Intent intent = new Intent(HomeActivity.this, eventDetail.class);
            intent.putExtra("EVENT_ID", clickedEvent.getId());
            startActivity(intent);
        });
        eventsRecyclerView.setAdapter(eventsAdapter);

        // Load events from Firestore
        loadEvents();

        ImageView btnHome = findViewById(R.id.btnHome);
        ImageView btnMe = findViewById(R.id.btnMe);

        btnHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Launch HomeActivity
                Intent intent = new Intent(HomeActivity.this, HomeActivity.class);
                startActivity(intent);
            }
        });

        btnMe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Launch ProfileActivity
                Intent intent = new Intent(HomeActivity.this, ProfileActivity.class);
                startActivity(intent);
            }
        });
    }

    private void setupSearchFunctionality() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not needed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Not needed
            }

            @Override
            public void afterTextChanged(Editable s) {
                filterEvents(s.toString());
            }
        });
    }

    private void filterEvents(String query) {
        List<Event> filteredEvents = new ArrayList<>();
        
        if (query.isEmpty()) {
            // If search is empty, show all events
            filteredEvents.addAll(allEvents);
        } else {
            // Filter events based on search query
            String lowerCaseQuery = query.toLowerCase();
            for (Event event : allEvents) {
                if (event.getName() != null && event.getName().toLowerCase().contains(lowerCaseQuery) ||
                    event.getLocation() != null && event.getLocation().toLowerCase().contains(lowerCaseQuery) ||
                    event.getDescription() != null && event.getDescription().toLowerCase().contains(lowerCaseQuery)) {
                    filteredEvents.add(event);
                }
            }
        }
        
        // Update adapter with filtered events
        eventsAdapter.updateEvents(filteredEvents);
    }

    private void refreshEvents() {
        // Clear current events and reload from Firestore
        allEvents.clear();
        eventsAdapter.updateEvents(allEvents);
        
        // Load fresh data from Firestore
        db.collection("events")
                .orderBy("postTime", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    allEvents.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
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

                        // --- START TIME ---
                        Object startTimeObj = doc.get("startTime");
                        String startTimeStr = "";
                        if (startTimeObj != null) {
                            Log.d("FirestoreDebug", "startTime type: " + startTimeObj.getClass().getName());
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
                        } else {
                            Log.d("FirestoreDebug", "startTime is null");
                        }
                        event.setStartTime(startTimeStr);

                        // --- END TIME ---
                        Object endTimeObj = doc.get("endTime");
                        String endTimeStr = "";
                        if (endTimeObj != null) {
                            Log.d("FirestoreDebug", "endTime type: " + endTimeObj.getClass().getName());
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
                        } else {
                            Log.d("FirestoreDebug", "endTime is null");
                        }
                        event.setEndTime(endTimeStr);

                        // --- IMAGE PREVIEW ---
                        List<String> imageUrls = (List<String>) doc.get("imageUrls");
                        if (imageUrls != null && !imageUrls.isEmpty()) {
                            event.setImageUrl(imageUrls.get(0));
                        } else {
                            event.setImageUrl(null); // Explicitly set to null if no images
                        }

                        // Add event to the list
                        allEvents.add(event);
                    }

                    // Update adapter with all events and apply current search filter
                    eventsAdapter.updateEvents(allEvents);
                    if (searchInput != null && !searchInput.getText().toString().isEmpty()) {
                        filterEvents(searchInput.getText().toString());
                    }
                    
                    // Stop the refresh animation
                    swipeRefreshLayout.setRefreshing(false);
                    
                    // Show success message
                    Toast.makeText(this, "Events refreshed successfully!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e("FirestoreDebug", "Error refreshing events", e);
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(this, "Failed to refresh events", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadEvents() {
        db.collection("events")
                .orderBy("postTime", Query.Direction.DESCENDING)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.e("FirestoreDebug", "Listen failed.", error);
                        return;
                    }
                    
                    allEvents.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
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

                        // --- START TIME ---
                        Object startTimeObj = doc.get("startTime");
                        String startTimeStr = "";
                        if (startTimeObj != null) {
                            Log.d("FirestoreDebug", "startTime type: " + startTimeObj.getClass().getName());
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
                        } else {
                            Log.d("FirestoreDebug", "startTime is null");
                        }
                        event.setStartTime(startTimeStr);

                        // --- END TIME ---
                        Object endTimeObj = doc.get("endTime");
                        String endTimeStr = "";
                        if (endTimeObj != null) {
                            Log.d("FirestoreDebug", "endTime type: " + endTimeObj.getClass().getName());
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
                        } else {
                            Log.d("FirestoreDebug", "endTime is null");
                        }
                        event.setEndTime(endTimeStr);

                        // --- IMAGE PREVIEW ---
                        List<String> imageUrls = (List<String>) doc.get("imageUrls");
                        if (imageUrls != null && !imageUrls.isEmpty()) {
                            event.setImageUrl(imageUrls.get(0));
                        } else {
                            event.setImageUrl(null); // Explicitly set to null if no images
                        }

                        // Add event to the list
                        allEvents.add(event);
                    }

                    // Update adapter with all events and apply current search filter
                    eventsAdapter.updateEvents(allEvents);
                    if (searchInput != null && !searchInput.getText().toString().isEmpty()) {
                        filterEvents(searchInput.getText().toString());
                    }
                });
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
                    Intent intent = new Intent(HomeActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }
}