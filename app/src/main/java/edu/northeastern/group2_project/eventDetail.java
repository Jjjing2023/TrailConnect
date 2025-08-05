package edu.northeastern.group2_project;

import edu.northeastern.group2_project.ProfileActivity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import android.app.Dialog;
import android.view.Window;
import android.view.WindowManager;

public class eventDetail extends AppCompatActivity implements OnMapReadyCallback {
    private FirebaseFirestore db;
    private FirebaseUser user;
    private String userId;
    private String eventId;
    private GoogleMap mMap;
    private boolean disableProfileNavigation;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.event_detail);

        // initialize db
        db = FirebaseFirestore.getInstance();

        // todo: pass real event_id via event list
        eventId = getIntent().getStringExtra("EVENT_ID");
        disableProfileNavigation = getIntent().getBooleanExtra("DISABLE_PROFILE_NAVIGATION", false);

        if (eventId == null) {
            Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ViewPager2 viewPager = findViewById(R.id.viewPagerImages);
        ImageButton favoriteButton = findViewById(R.id.buttonFavorite);
        TabLayout tabLayout = findViewById(R.id.tabLayoutIndicator);
        ImageButton shareButton = findViewById(R.id.buttonShare);
        Button btnGetDirections = findViewById(R.id.btnGetDirections);
        // Initialize UI elements
        TextView textEventTitle = findViewById(R.id.textEventTitle);
        TextView textEventDateTime = findViewById(R.id.textEventDateTime);
        TextView textEventAbout = findViewById(R.id.textEventAbout);
        TextView textEventLocation = findViewById(R.id.textEventLocation);
        TextView textEventAddress = findViewById(R.id.textEventAddress);

        // Initialize Host & Attendees UI elements
        ImageView hostProfileImage = findViewById(R.id.hostProfileImage);
        TextView hostName = findViewById(R.id.hostName);
        TextView attendeesCount = findViewById(R.id.attendeesCount);
        RecyclerView attendeesRecyclerView = findViewById(R.id.attendeesRecyclerView);
        RecyclerView attendeesAvatarRecyclerView = findViewById(R.id.attendeesAvatarRecyclerView);
        attendeesAvatarRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // Load event data from Firebase with error handling
        try {
            loadEventDataFromFirebase();
        } catch (Exception e) {
            Log.e("eventDetail", "Error loading event data in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error loading event details.", Toast.LENGTH_LONG).show();
            finish(); // Close activity if data loading fails critically
        }

        // Contact Host button
        ImageButton contactButton = findViewById(R.id.btn_contact);
        contactButton.setOnClickListener(v -> {
            // For now, just a Toast message
            Toast.makeText(this, "Contact Host coming soon…", Toast.LENGTH_SHORT).show();
        });

        // Setup RecyclerView for attendees
        attendeesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        attendeesRecyclerView.setNestedScrollingEnabled(false);

        // Initialize Google Maps
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Load event data from Firebase
        loadEventDataFromFirebase();
        
        // Set up real-time listener for attendees
        setupAttendeesListener();

        boolean[] isFavorited = {false};

        user = FirebaseAuth.getInstance().getCurrentUser();
        userId = user.getUid();

        db.collection("users").document(userId)
                .collection("favorites")
                .document(eventId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        // User has saved this event
                        favoriteButton.setImageResource(R.drawable.ic_favorite_filled);
                        isFavorited[0] = true;
                    } else {
                        favoriteButton.setImageResource(R.drawable.ic_favorite_border);
                        isFavorited[0] = false;
                    }
                });


        // Initialize carousel with empty list (will be populated from Firebase)
        List<String> imageUrls = new ArrayList<>();
        ImageCarouselAdapter adapter = new ImageCarouselAdapter(this, imageUrls);
        viewPager.setAdapter(adapter);

        // page indicator
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            Log.d("TabLayoutMediator", "Binding tab for position " + position);
        }).attach();

        // force style of dot page indicator
        TabLayout tabLayoutIndicator = findViewById(R.id.tabLayoutIndicator);
        tabLayoutIndicator.post(() -> {
            for (int i = 0; i < tabLayoutIndicator.getTabCount(); i++) {
                View tab = ((ViewGroup) tabLayoutIndicator.getChildAt(0)).getChildAt(i);
                ViewGroup.LayoutParams params = tab.getLayoutParams();

                // Set exact width and height for dot tab
                params.width = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 6, tab.getResources().getDisplayMetrics());
                params.height = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 6, tab.getResources().getDisplayMetrics());

                // dd spacing
                if (params instanceof ViewGroup.MarginLayoutParams) {
                    ((ViewGroup.MarginLayoutParams) params).setMargins(8, 0, 8, 0); // spacing between dots
                }

                tab.setLayoutParams(params);
            }
        });


        // Auto-scroll every 3 seconds
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (imageUrls.size() > 0) {
                    int current = viewPager.getCurrentItem();
                    int next = (current + 1) % imageUrls.size();
                    viewPager.setCurrentItem(next, true);
                    handler.postDelayed(this, 3000); // 3 seconds
                }
            }
        };
        if (imageUrls.size() > 0) {
            handler.postDelayed(runnable, 3000);
        }

        // make favorite button interactive
        favoriteButton.setOnClickListener(v -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Toast.makeText(this, "Please sign in to save favorites", Toast.LENGTH_SHORT).show();
                return;
            }

            String userId = user.getUid();
            DocumentReference userRef = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId);

            if (!isFavorited[0]) {
                favoriteButton.setImageResource(R.drawable.ic_favorite_filled);

                userRef.update("favoritedEventIds", FieldValue.arrayUnion(eventId))
                        .addOnSuccessListener(unused -> {
                            Toast.makeText(this, "Event saved to favorites", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Failed to save favorite", Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        });
            } else {
                favoriteButton.setImageResource(R.drawable.ic_favorite_border);

                userRef.update("favoritedEventIds", FieldValue.arrayRemove(eventId))
                        .addOnSuccessListener(unused -> {
                            Toast.makeText(this, "Event removed from favorites", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Failed to remove favorite", Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        });
            }
            isFavorited[0] = !isFavorited[0];
        });

        // make share button interactive
        shareButton.setOnClickListener(v->{
            // Get current text from UI (loaded from Firebase)
            String eventTitle = textEventTitle.getText().toString();
            String eventDescription = "Join us for an exciting hike!";
            //todo: use firebase dynamic links to create smart URLs for sharing
            String shareText = eventTitle + "\n\n" + eventDescription;

            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            sendIntent.setType("text/plain");

            Intent shareIntent = Intent.createChooser(sendIntent, "Share this event via");
            startActivity(shareIntent);
        });

        // Handle price showing logic
        TextView priceTextView = findViewById(R.id.event_price);

        db.collection("events").document(eventId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Number priceNumber = documentSnapshot.getDouble("price"); // Use getDouble or getLong directly from Firestore DocumentSnapshot
                        if (priceNumber != null) {
                            long priceValue = priceNumber.longValue();
                            String priceText = priceValue == 0 ? "FREE" : "$" + priceValue + "/person";
                            priceTextView.setText(priceText);
                        } else {
                            priceTextView.setText("FREE");
                        }
                    } else {
                        priceTextView.setText("FREE"); // Event document not found
                    }
                })
                .addOnFailureListener(e -> {
                    priceTextView.setText("FREE"); // Error fetching document
                    Log.e("EventDetail", "Error fetching price from Firestore: " + e.getMessage());
                });

        // Handle Join Button Logic
        Button joinButton = findViewById(R.id.join_button);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference userRef = db.collection("users").document(userId);

        eventRef.get().addOnSuccessListener(eventSnapshot -> {
            if (eventSnapshot.exists()) {
                String hostId = eventSnapshot.getString("host");

                if (userId.equals(hostId)) {
                    // if host, then hide the join button
                    joinButton.setText("Delete");
                    joinButton.setVisibility(View.VISIBLE);
                    joinButton.setOnClickListener(v -> {
                        new AlertDialog.Builder(eventDetail.this)
                                .setTitle("Delete Event")
                                .setMessage("Are you sure you want to delete this event?")
                                .setPositiveButton("Yes", (dialog, which) -> {
                                    // 1. delete event document
                                    eventRef.delete().addOnSuccessListener(aVoid -> {
                                        // 2. remove from hostedEvents
                                        userRef.update("hostedEvents", FieldValue.arrayRemove(eventId));

                                        // 3. remove eventId from joinedEvents for all users
                                        db.collection("users").get().addOnSuccessListener(querySnapshot -> {
                                            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                                List<String> joinedEvents = (List<String>) doc.get("joinedEvents");
                                                if (joinedEvents != null && joinedEvents.contains(eventId)) {
                                                    doc.getReference().update("joinedEvents", FieldValue.arrayRemove(eventId));
                                                }
                                            }
                                            Snackbar.make(findViewById(android.R.id.content), "Event deleted", Snackbar.LENGTH_SHORT).show();
                                            finish(); // back to last activity
                                        }).addOnFailureListener(e -> {
                                            Snackbar.make(findViewById(android.R.id.content), "Event deleted, but failed to update joined users", Snackbar.LENGTH_LONG).show();
                                            finish();
                                        });
                                    }).addOnFailureListener(e -> {
                                        Snackbar.make(findViewById(android.R.id.content), "Failed to delete event", Snackbar.LENGTH_SHORT).show();
                                    });
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    });

                    return;
                }

                // Not host, then check if joined already
                userRef.get().addOnSuccessListener(userSnapshot -> {
                    if (userSnapshot.exists()) {
                        List<String> joinedEvents = (List<String>) userSnapshot.get("joinedEvents");
                        boolean alreadyJoined = joinedEvents != null && joinedEvents.contains(eventId);
                        joinButton.setText(alreadyJoined ? "Joined" : "Join");

                        joinButton.setOnClickListener(v -> {
                            if (joinButton.getText().toString().equals("Join")) {
                                // Join the event
                                eventRef.update("attendees", FieldValue.arrayUnion(userId))
                                        .addOnSuccessListener(aVoid -> {
                                            userRef.update("joinedEvents", FieldValue.arrayUnion(eventId))
                                                    .addOnSuccessListener(aVoid2 -> {
                                                        joinButton.setText("Joined");
                                                        Snackbar.make(findViewById(android.R.id.content), "You have joined the event", Snackbar.LENGTH_SHORT).show();
                                                        refreshAttendeesList();
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        Snackbar.make(findViewById(android.R.id.content), "Failed to update user data", Snackbar.LENGTH_SHORT).show();
                                                    });
                                        })
                                        .addOnFailureListener(e -> {
                                            Snackbar.make(findViewById(android.R.id.content), "Failed to join event", Snackbar.LENGTH_SHORT).show();
                                        });
                            } else {
                                // If already joined, able to cancel the appointment
                                new AlertDialog.Builder(eventDetail.this)
                                        .setTitle("Cancel Appointment")
                                        .setMessage("Are you sure you want to cancel your participation?")
                                        .setPositiveButton("Yes", (dialog, which) -> {
                                            eventRef.update("attendees", FieldValue.arrayRemove(userId))
                                                    .addOnSuccessListener(aVoid -> {
                                                        userRef.update("joinedEvents", FieldValue.arrayRemove(eventId))
                                                                .addOnSuccessListener(aVoid2 -> {
                                                                    joinButton.setText("Join");
                                                                    Snackbar.make(findViewById(android.R.id.content), "You have cancelled the appointment", Snackbar.LENGTH_SHORT).show();
                                                                    refreshAttendeesList();
                                                                })
                                                                .addOnFailureListener(e -> {
                                                                    Snackbar.make(findViewById(android.R.id.content), "Failed to update user data", Snackbar.LENGTH_SHORT).show();
                                                                });
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        Snackbar.make(findViewById(android.R.id.content), "Failed to cancel participation", Snackbar.LENGTH_SHORT).show();
                                                    });
                                        })
                                        .setNegativeButton("No", null)
                                        .show();
                            }
                        });
                    }
                }).addOnFailureListener(e -> {
                    Snackbar.make(findViewById(android.R.id.content), "Failed to load user info.", Snackbar.LENGTH_SHORT).show();
                });
            }
        }).addOnFailureListener(e -> {
            Snackbar.make(findViewById(android.R.id.content), "Failed to load event info.", Snackbar.LENGTH_SHORT).show();
        });
        btnGetDirections.setOnClickListener(v -> {
            String address = textEventAddress.getText().toString();
            openAddressInMaps(this, address);
        });
    }

    private void openProfile(String username, String userId) {
        Intent i = new Intent(this, ProfileActivity.class);
        i.putExtra(ProfileActivity.EXTRA_USERNAME, username);
        i.putExtra(ProfileActivity.EXTRA_USER_ID, userId);
        startActivity(i);
    }

    private void openAttendeeProfile(Attendee attendee) {
        // Check if profile navigation is disabled
        if (disableProfileNavigation) {
            Toast.makeText(this, "Profile navigation disabled", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check if attendee has valid user ID
        if (attendee.getUserId() == null || attendee.getUserId().isEmpty()) {
            Toast.makeText(this, "Attendee information not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra(ProfileActivity.EXTRA_USERNAME, attendee.getName());
        intent.putExtra(ProfileActivity.EXTRA_USER_ID, attendee.getUserId());
        startActivity(intent);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.d("EventDetail", "Google Maps loaded successfully");
        mMap = googleMap;

        // Map will be updated when event data is loaded from Firebase
        // For now, set a default location
        LatLng defaultLocation = new LatLng(37.8919, -122.5775);
        mMap.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(defaultLocation, 12));
        Log.d("EventDetail", "Map camera moved to default location: " + defaultLocation.latitude + ", " + defaultLocation.longitude);
    }

    private void loadEventDataFromFirebase() {
        db.collection("events").document(eventId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        // Get UI elements
                        TextView textEventTitle = findViewById(R.id.textEventTitle);
                        TextView textEventDateTime = findViewById(R.id.textEventDateTime);
                        TextView textEventAbout = findViewById(R.id.textEventAbout);
                        TextView textEventLocation = findViewById(R.id.textEventLocation);
                        TextView textEventAddress = findViewById(R.id.textEventAddress);
                        ViewPager2 viewPager = findViewById(R.id.viewPagerImages);

                        // Get Host & Attendees UI elements
                        ImageView hostProfileImage = findViewById(R.id.hostProfileImage);
                        TextView hostName = findViewById(R.id.hostName);
                        TextView attendeesCount = findViewById(R.id.attendeesCount);
                        RecyclerView attendeesRecyclerView = findViewById(R.id.attendeesRecyclerView);

                        // Set event data
                        String title = document.getString("name");
                        String startTime = document.getString("startTime");
                        String endTime = document.getString("endTime");
                        String dateTime = "";
                        if (startTime != null && endTime != null) {
                            dateTime = startTime + " - " + endTime;
                        } else if (startTime != null) {
                            dateTime = startTime;
                        } else if (endTime != null) {
                            dateTime = endTime;
                        }
                        String about = document.getString("description");
                        String location = document.getString("location");
                        String address = document.getString("address");
                        List<String> imageUrls = (List<String>) document.get("imageUrls");
                        Double latitude = document.getDouble("latitude");
                        Double longitude = document.getDouble("longitude");

                        // Get host and attendees data
                        String hostId = document.getString("host");
                        List<String> attendeeIds = (List<String>) document.get("attendees");

                        // Update UI with null checks
                        textEventTitle.setText(title != null ? title : "No Title");
                        textEventAbout.setText(about != null ? about : "No Description");
                        textEventDateTime.setText(dateTime);
                        textEventLocation.setText(location != null ? location : "No Location");
                        textEventAddress.setText(address != null ? address : "No Address");

                        // Update image carousel with null check
                        if (imageUrls != null && !imageUrls.isEmpty()) {
                            ImageCarouselAdapter adapter = new ImageCarouselAdapter(this, imageUrls);
                            viewPager.setAdapter(adapter);

                            // Update page indicator
                            TabLayout tabLayout = findViewById(R.id.tabLayoutIndicator);
                            new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
                                Log.d("TabLayoutMediator", "Binding tab for position " + position);
                            }).attach();
                        } else {
                            // Set default image if no images available
                            List<String> defaultImage = new ArrayList<>();
                            defaultImage.add("android.resource://" + getPackageName() + "/" + R.drawable.ic_launcher_foreground);
                            ImageCarouselAdapter adapter = new ImageCarouselAdapter(this, defaultImage);
                            viewPager.setAdapter(adapter);
                        }

                        // Update map location with null checks
                        if (latitude != null && longitude != null && mMap != null) {
                            LatLng eventLocation = new LatLng(latitude, longitude);
                            Log.d("EventDetail", "Updating map with event location: " + latitude + ", " + longitude);
                            mMap.clear();
                            mMap.addMarker(new MarkerOptions()
                                    .position(eventLocation)
                                    .title(title != null ? title : "Event Location")
                                    .snippet(location != null ? location : ""));
                            mMap.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(eventLocation, 12));
                            Log.d("EventDetail", "Map updated with event marker and camera moved");
                        } else {
                            Log.w("EventDetail", "Map not updated - missing coordinates or map not ready. lat: " + latitude + ", lng: " + longitude + ", map: " + (mMap != null));
                            findViewById(R.id.map).setVisibility(View.GONE);
                        }

                        // Load host information with null check
                        if (hostId != null) {
                            loadHostInfo(hostId, hostProfileImage, hostName);
                            // Set up click listeners for host avatar/name (only if navigation is enabled)
                            if (!disableProfileNavigation) {
                                hostProfileImage.setOnClickListener(v ->
                                    openProfile(hostName.getText().toString(), hostId)
                                );
                                hostName.setOnClickListener(v ->
                                    openProfile(hostName.getText().toString(), hostId)
                                );
                            } else {
                                // Disable profile navigation - show toast instead
                                hostProfileImage.setOnClickListener(v ->
                                    Toast.makeText(this, "Profile navigation disabled", Toast.LENGTH_SHORT).show()
                                );
                                hostName.setOnClickListener(v ->
                                    Toast.makeText(this, "Profile navigation disabled", Toast.LENGTH_SHORT).show()
                                );
                            }
                        } else {
                            hostName.setText("Unknown Host");
                            hostProfileImage.setImageResource(R.drawable.ic_default_avatar);
                            // Disable click listeners for unknown host
                            hostProfileImage.setOnClickListener(v ->
                                Toast.makeText(this, "Host information not available", Toast.LENGTH_SHORT).show()
                            );
                            hostName.setOnClickListener(v ->
                                Toast.makeText(this, "Host information not available", Toast.LENGTH_SHORT).show()
                            );
                        }

                        // Load attendees information
                        if (attendeeIds != null && !attendeeIds.isEmpty()) {
                            loadAttendeesInfo(attendeeIds, attendeesCount, attendeesRecyclerView);
                        } else {
                            attendeesCount.setText("No attendees yet");
                        }

                        Log.d("EventDetail", "Event data loaded successfully from Firebase");
                    } else {
                        Log.d("EventDetail", "No such document");
                        Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w("EventDetail", "Error getting document", e);
                    Toast.makeText(this, "Failed to load event data", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadHostInfo(String hostId, ImageView hostProfileImage, TextView hostName) {
        db.collection("users").document(hostId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String name = document.getString("name");
                        String profileImageUrl = document.getString("profileImageUrl");

                        if (name != null) {
                            hostName.setText(name);
                        } else {
                            hostName.setText("Unknown Host");
                        }

                        // Load profile image with null check
                        if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(profileImageUrl)
                                    .placeholder(R.drawable.ic_default_avatar)
                                    .circleCrop()
                                    .into(hostProfileImage);
                        } else {
                            hostProfileImage.setImageResource(R.drawable.ic_default_avatar);
                        }
                    } else {
                        hostName.setText("Unknown Host");
                        hostProfileImage.setImageResource(R.drawable.ic_default_avatar);
                        // Disable click listeners when host document doesn't exist
                        hostProfileImage.setOnClickListener(v ->
                            Toast.makeText(this, "Host information not available", Toast.LENGTH_SHORT).show()
                        );
                        hostName.setOnClickListener(v ->
                            Toast.makeText(this, "Host information not available", Toast.LENGTH_SHORT).show()
                        );
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w("EventDetail", "Error loading host info", e);
                    hostName.setText("Unknown Host");
                    hostProfileImage.setImageResource(R.drawable.ic_default_avatar);
                    // Disable click listeners when host info loading fails
                    hostProfileImage.setOnClickListener(v ->
                        Toast.makeText(this, "Host information not available", Toast.LENGTH_SHORT).show()
                    );
                    hostName.setOnClickListener(v ->
                        Toast.makeText(this, "Host information not available", Toast.LENGTH_SHORT).show()
                    );
                });
    }

    private void loadAttendeesInfo(List<String> attendeeIds, TextView attendeesCount, RecyclerView attendeesRecyclerView) {
        RecyclerView attendeesAvatarRecyclerView = findViewById(R.id.attendeesAvatarRecyclerView);
        // Find the Attendees title TextView
        View attendeesRow = findViewById(R.id.attendeesRow);
        TextView attendeesTitle = null;
        if (attendeesRow instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) attendeesRow;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View v = vg.getChildAt(i);
                if (v instanceof TextView && ((TextView) v).getText().toString().contains("Attendees")) {
                    attendeesTitle = (TextView) v;
                    break;
                }
            }
        }
        
        // Clear existing attendees list to avoid duplicates
        List<Attendee> attendees = new ArrayList<>();
        
        // Update attendees count
        if (attendeesTitle != null) {
            attendeesTitle.setText("\uD83D\uDC65 Attendees (" + attendeeIds.size() + ")");
        }
        
        // If no attendees, clear the adapters
        if (attendeeIds.isEmpty()) {
            attendeesAvatarRecyclerView.setAdapter(null);
            attendeesRecyclerView.setAdapter(null);
            return;
        }
        
        // Load each attendee's information
        final int[] loadedCount = {0};
        for (String attendeeId : attendeeIds) {
            db.collection("users").document(attendeeId)
                    .get()
                    .addOnSuccessListener(document -> {
                        if (document.exists()) {
                            Attendee attendee = new Attendee();
                            attendee.setUserId(attendeeId);
                            attendee.setName(document.getString("name"));
                            attendee.setProfileImageUrl(document.getString("profileImageUrl"));
                            attendee.setEmail(document.getString("email"));
                            attendees.add(attendee);
                            
                            loadedCount[0]++;
                            // Update both avatar row and full list when all data is loaded
                            if (loadedCount[0] == attendeeIds.size()) {
                                // Horizontal avatar adapter with attendee click support
                                AttendeeAvatarAdapter avatarAdapter = new AttendeeAvatarAdapter(this, attendees, 5, 
                                    () -> showAttendeesDialog(attendees), 
                                    clickedAttendee -> openAttendeeProfile(clickedAttendee));
                                attendeesAvatarRecyclerView.setAdapter(avatarAdapter);
                                // Full list adapter for popup with attendee click support
                                AttendeeAdapter adapter = new AttendeeAdapter(this, attendees, clickedAttendee -> openAttendeeProfile(clickedAttendee));
                                attendeesRecyclerView.setAdapter(adapter);
                            }
                        } else {
                            loadedCount[0]++;
                            // Handle case where user document doesn't exist
                            if (loadedCount[0] == attendeeIds.size()) {
                                updateAttendeesAdapters(attendees);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w("EventDetail", "Error loading attendee info for " + attendeeId, e);
                        loadedCount[0]++;
                        // Handle case where loading failed
                        if (loadedCount[0] == attendeeIds.size()) {
                            updateAttendeesAdapters(attendees);
                        }
                    });
        }
    }
    
    private void updateAttendeesAdapters(List<Attendee> attendees) {
        RecyclerView attendeesAvatarRecyclerView = findViewById(R.id.attendeesAvatarRecyclerView);
        RecyclerView attendeesRecyclerView = findViewById(R.id.attendeesRecyclerView);
        
        // Horizontal avatar adapter with attendee click support
        AttendeeAvatarAdapter avatarAdapter = new AttendeeAvatarAdapter(this, attendees, 5, 
            () -> showAttendeesDialog(attendees), 
            clickedAttendee -> openAttendeeProfile(clickedAttendee));
        attendeesAvatarRecyclerView.setAdapter(avatarAdapter);
        // Full list adapter for popup with attendee click support
        AttendeeAdapter adapter = new AttendeeAdapter(this, attendees, clickedAttendee -> openAttendeeProfile(clickedAttendee));
        attendeesRecyclerView.setAdapter(adapter);
    }

    private void showAttendeesDialog(List<Attendee> attendees) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_attendees_list);
        // Set transparent background
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        RecyclerView dialogRecyclerView = dialog.findViewById(R.id.dialogAttendeesRecyclerView);
        dialogRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        dialogRecyclerView.setAdapter(new AttendeeAdapter(this, attendees, clickedAttendee -> openAttendeeProfile(clickedAttendee)));
        dialog.findViewById(R.id.dialogCloseButton).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void setupAttendeesListener() {
        // Listen for real-time changes to the event document
        db.collection("events").document(eventId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.w("EventDetail", "Error listening for attendees changes", error);
                        return;
                    }
                    
                    if (snapshot != null && snapshot.exists()) {
                        List<String> attendeeIds = (List<String>) snapshot.get("attendees");
                        TextView attendeesCount = findViewById(R.id.attendeesCount);
                        RecyclerView attendeesRecyclerView = findViewById(R.id.attendeesRecyclerView);
                        
                        if (attendeeIds != null && !attendeeIds.isEmpty()) {
                            loadAttendeesInfo(attendeeIds, attendeesCount, attendeesRecyclerView);
                        } else {
                            // Find the Attendees title TextView
                            View attendeesRow = findViewById(R.id.attendeesRow);
                            TextView attendeesTitle = null;
                            if (attendeesRow instanceof ViewGroup) {
                                ViewGroup vg = (ViewGroup) attendeesRow;
                                for (int i = 0; i < vg.getChildCount(); i++) {
                                    View v = vg.getChildAt(i);
                                    if (v instanceof TextView && ((TextView) v).getText().toString().contains("Attendees")) {
                                        attendeesTitle = (TextView) v;
                                        break;
                                    }
                                }
                            }
                            if (attendeesTitle != null) {
                                attendeesTitle.setText("\uD83D\uDC65 Attendees (0)");
                            }
                            
                            // Clear adapters when no attendees
                            RecyclerView attendeesAvatarRecyclerView = findViewById(R.id.attendeesAvatarRecyclerView);
                            attendeesAvatarRecyclerView.setAdapter(null);
                            attendeesRecyclerView.setAdapter(null);
                        }
                    }
                });
    }

    private void refreshAttendeesList() {
        // Get the current attendees from the event document
        db.collection("events").document(eventId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        List<String> attendeeIds = (List<String>) document.get("attendees");
                        TextView attendeesCount = findViewById(R.id.attendeesCount);
                        RecyclerView attendeesRecyclerView = findViewById(R.id.attendeesRecyclerView);
                        
                        if (attendeeIds != null && !attendeeIds.isEmpty()) {
                            loadAttendeesInfo(attendeeIds, attendeesCount, attendeesRecyclerView);
                        } else {
                            // Find the Attendees title TextView
                            View attendeesRow = findViewById(R.id.attendeesRow);
                            TextView attendeesTitle = null;
                            if (attendeesRow instanceof ViewGroup) {
                                ViewGroup vg = (ViewGroup) attendeesRow;
                                for (int i = 0; i < vg.getChildCount(); i++) {
                                    View v = vg.getChildAt(i);
                                    if (v instanceof TextView && ((TextView) v).getText().toString().contains("Attendees")) {
                                        attendeesTitle = (TextView) v;
                                        break;
                                    }
                                }
                            }
                            if (attendeesTitle != null) {
                                attendeesTitle.setText("\uD83D\uDC65 Attendees (0)");
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w("EventDetail", "Error refreshing attendees list", e);
                });
    }
    public static void openAddressInMaps(Context context, String address) {
        if (address == null || address.trim().isEmpty()) {
            Toast.makeText(context, "No address provided.", Toast.LENGTH_SHORT).show();
            return;
        }
        String uri = "geo:0,0?q=" + Uri.encode(address);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setPackage("com.google.android.apps.maps");
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        } else {
            // Fallback: Show chooser for any available map app
            Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            Intent chooser = Intent.createChooser(fallback, "Choose Maps App");
            context.startActivity(chooser);
        }
    }
}
