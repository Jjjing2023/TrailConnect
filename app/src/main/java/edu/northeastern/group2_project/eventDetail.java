package edu.northeastern.group2_project;

import edu.northeastern.group2_project.ProfileActivity;
import android.content.Intent;
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.event_detail);

        // initialize db
        db = FirebaseFirestore.getInstance(); 

        // todo: pass real event_id via event list
        eventId = getIntent().getStringExtra("EVENT_ID");
        if (eventId == null) {
            Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ViewPager2 viewPager = findViewById(R.id.viewPagerImages);
        ImageButton favoriteButton = findViewById(R.id.buttonFavorite);
        TabLayout tabLayout = findViewById(R.id.tabLayoutIndicator);
        ImageButton shareButton = findViewById(R.id.buttonShare);
        // Initialize UI elements
        TextView textEventTitle = findViewById(R.id.textEventTitle);
        TextView textEventDateTime = findViewById(R.id.textEventDateTime);
        TextView textEventAbout = findViewById(R.id.textEventAbout);
        TextView textEventLocation = findViewById(R.id.textEventLocation);
        
        // Initialize Host & Attendees UI elements
        ImageView hostProfileImage = findViewById(R.id.hostProfileImage);
        TextView hostName = findViewById(R.id.hostName);
        TextView attendeesCount = findViewById(R.id.attendeesCount);
        RecyclerView attendeesRecyclerView = findViewById(R.id.attendeesRecyclerView);
        RecyclerView attendeesAvatarRecyclerView = findViewById(R.id.attendeesAvatarRecyclerView);
        attendeesAvatarRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

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
            if (!isFavorited[0]) {
                favoriteButton.setImageResource(R.drawable.ic_favorite_filled);
                Toast.makeText(this, "Event saved", Toast.LENGTH_SHORT).show();

                // save to firestore
                Map<String, Object> data = new HashMap<>();
                data.put("savedAt", new Timestamp(new Date()));

                db.collection("users").document(userId)
                        .collection("favorites")
                        .document(eventId)
                        .set(data);

            } else {
                favoriteButton.setImageResource(R.drawable.ic_favorite_border); // outline
                Toast.makeText(this, "Event removed", Toast.LENGTH_SHORT).show();

                // remove from firestore
                db.collection("users").document(userId)
                        .collection("favorites")
                        .document(eventId)
                        .delete();
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

        TextView priceTextView = findViewById(R.id.event_price);
        Button joinButton = findViewById(R.id.join_button);


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


        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
        DatabaseReference eventRef = FirebaseDatabase.getInstance().getReference("events").child(eventId);


        // check if user has joined this event
        userRef.child("joinedEvents").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean alreadyJoined = snapshot.hasChild(eventId);
                // If user has joined this event, able to cancel
                if (alreadyJoined) {
                    joinButton.setText("Joined");
                    joinButton.setEnabled(false);
                } else {
                    joinButton.setOnClickListener(v -> {
                        new AlertDialog.Builder(eventDetail.this)
                                .setTitle("Confirm Join")
                                .setMessage("Are you sure you want to join this event?")
                                .setPositiveButton("Yes", (dialog, which) -> {
                                    // update events/{eventId}/attendees
                                    eventRef.child("attendees").child(userId).setValue(true);

                                    // update users/{userId}/joinedEvents
                                    userRef.child("joinedEvents").child(eventId).setValue(true);

                                    Snackbar.make(findViewById(android.R.id.content), "You have joined the event", Snackbar.LENGTH_SHORT).show();


                                    joinButton.setText("Joined");
                                    joinButton.setEnabled(false);
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Snackbar.make(findViewById(android.R.id.content), "Failed to load join status.", Snackbar.LENGTH_SHORT).show();
            }
        });


    }

    private void openProfile(String username) {
        Intent i = new Intent(this, ProfileActivity.class);
        i.putExtra(ProfileActivity.EXTRA_USERNAME, username);
        startActivity(i);
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
                        String title = document.getString("title");
                        String dateTime = document.getString("dateTime");
                        String about = document.getString("about");
                        String location = document.getString("location");
                        String address = document.getString("address");
                        List<String> imageUrls = (List<String>) document.get("imageUrls");
                        Double latitude = document.getDouble("latitude");
                        Double longitude = document.getDouble("longitude");
                        
                        // Get host and attendees data
                        String hostId = document.getString("host");
                        List<String> attendeeIds = (List<String>) document.get("attendees");

                        // Update UI
                        if (title != null) textEventTitle.setText(title);
                        if (dateTime != null) textEventDateTime.setText(dateTime);
                        if (about != null) textEventAbout.setText(about);
                        if (location != null) textEventLocation.setText(location);
                        if (address != null) textEventAddress.setText(address);

                        // Update image carousel
                        if (imageUrls != null && !imageUrls.isEmpty()) {
                            ImageCarouselAdapter adapter = new ImageCarouselAdapter(this, imageUrls);
                            viewPager.setAdapter(adapter);
                            
                            // Update page indicator
                            TabLayout tabLayout = findViewById(R.id.tabLayoutIndicator);
                            new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
                                Log.d("TabLayoutMediator", "Binding tab for position " + position);
                            }).attach();
                        }

                        // Update map location
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
                            Log.d("EventDetail", "Map not updated - missing coordinates or map not ready. lat: " + latitude + ", lng: " + longitude + ", map: " + (mMap != null));
                        }

                        // Load host information
                        if (hostId != null) {
                            loadHostInfo(hostId, hostProfileImage, hostName);
                        }
                        // when user taps host avatar/name, open ProfileActivity
                        hostProfileImage.setOnClickListener(v ->
                            openProfile(hostName.getText().toString())
                        );
                        hostName.setOnClickListener(v ->
                            openProfile(hostName.getText().toString())
                        );

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
                        }
                        
                        // Load profile image
                        if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(profileImageUrl)
                                    .placeholder(R.drawable.ic_launcher_foreground)
                                    .into(hostProfileImage);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w("EventDetail", "Error loading host info", e);
                    hostName.setText("Unknown Host");
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
        List<Attendee> attendees = new ArrayList<>();
        // No longer set attendeesCount separately
        if (attendeesTitle != null) {
            attendeesTitle.setText("\uD83D\uDC65 Attendees (" + attendeeIds.size() + ")");
        }
        // Load each attendee's information
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
                            // Update both avatar row and full list when all data is loaded
                            if (attendees.size() == attendeeIds.size()) {
                                // Horizontal avatar adapter
                                AttendeeAvatarAdapter avatarAdapter = new AttendeeAvatarAdapter(this, attendees, 5, () -> showAttendeesDialog(attendees));
                                attendeesAvatarRecyclerView.setAdapter(avatarAdapter);
                                // Full list adapter for popup
                                AttendeeAdapter adapter = new AttendeeAdapter(this, attendees);
                                attendeesRecyclerView.setAdapter(adapter);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w("EventDetail", "Error loading attendee info for " + attendeeId, e);
                    });
        }
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
        dialogRecyclerView.setAdapter(new AttendeeAdapter(this, attendees));
        dialog.findViewById(R.id.dialogCloseButton).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
