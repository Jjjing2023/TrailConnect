package edu.northeastern.group2_project;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import edu.northeastern.group2_project.UserLocalStorage;

public class HomeActivity extends AppCompatActivity {
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

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



//        // place holder button for event detail page
//        Button eventDetailPlaceHolder = findViewById(R.id.eventDetails);
//        eventDetailPlaceHolder.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                // todo: pass real data from event list
//                String eventId = "test_event_001"; // temporary ID for testing
//                Intent intent = new Intent(HomeActivity.this, eventDetail.class);
//                intent.putExtra("EVENT_ID", eventId);
//                startActivity(intent);
//
//            }
//        });

        // button to post a new event
        ImageButton postButton = findViewById(R.id.btnPost);
        postButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, CreateEventActivity.class);
            startActivity(intent);
        });

        RecyclerView eventsRecyclerView = findViewById(R.id.eventsRecyclerView);
        eventsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("events")
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.e("FirestoreDebug", "Listen failed.", error);
                        return;
                    }
                    List<Event> events = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Event event = new Event();
                        event.setId(doc.getId());
                        event.setName(doc.getString("name"));
                        event.setLocation(doc.getString("location"));
                        event.setDescription(doc.getString("description"));

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
                        }

                        // Add event to the list
                        events.add(event);
                    }

                    EventsAdapter adapter = new EventsAdapter(events, clickedEvent -> {
                        Log.d("HomeActivity", "Clicked event: " + clickedEvent.getName() + ", id: " + clickedEvent.getId());
                        Intent intent = new Intent(HomeActivity.this, eventDetail.class);
                        intent.putExtra("EVENT_ID", clickedEvent.getId());
                        startActivity(intent);
                    });
                    eventsRecyclerView.setAdapter(adapter);
                });
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