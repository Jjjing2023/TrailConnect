package edu.northeastern.group2_project;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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
        Button logoutButton = findViewById(R.id.logoutButton);

        // Try to get name from local storage first for faster UI loading
        UserLocalStorage localStorage = new UserLocalStorage(this);
        String userName = localStorage.getUserName();

        if (!userName.isEmpty()) {
            welcomeText.setText("Welcome to TrailConnect, " + userName + "!");
        } else {
            // Fall back to Firebase user
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                welcomeText.setText("Welcome to TrailConnect, " + user.getDisplayName() + "!");
                localStorage.storeUserName(user.getDisplayName());
            } else if (user != null && user.getEmail() != null) {
                // Use email prefix if no display name
                String emailName = user.getEmail().split("@")[0];
                welcomeText.setText("Welcome to TrailConnect, " + emailName + "!");
                localStorage.storeUserName(emailName);
            } else {
                welcomeText.setText("Welcome to TrailConnect!");
            }
        }

        // Update last login timestamp
        localStorage.updateLastLogin();

        // Set up logout button
        logoutButton.setOnClickListener(view -> confirmLogout());

        // place holder button for event detail page
        Button eventDetailPlaceHolder = findViewById(R.id.eventDetails);
        eventDetailPlaceHolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // todo: pass real data from event list
                String eventId = "test_event_001"; // temporary ID for testing
                Intent intent = new Intent(HomeActivity.this, eventDetail.class);
                intent.putExtra("EVENT_ID", eventId);
                startActivity(intent);

            }
        });
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
                    Intent intent = new Intent(HomeActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }
}