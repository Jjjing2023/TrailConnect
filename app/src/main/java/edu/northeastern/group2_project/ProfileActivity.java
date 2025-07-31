package edu.northeastern.group2_project;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {
    public static final String EXTRA_USERNAME = "extra_username";

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_profile);

        // Get username from Intent
        String username = getIntent().getStringExtra(EXTRA_USERNAME);

        // Wire up views
        ImageView avatar = findViewById(R.id.profile_avatar);
        TextView nameTv = findViewById(R.id.profile_name);
        TextView countTv = findViewById(R.id.profile_attended_count);

        // Populate name
        nameTv.setText(username);

        // Load real avatar otherwise default
        // For now use ic_default_avatar.

        // (Optional) Fetch how many events attended from Firebase
        // set countTv.setText("Events attended: " + count);

        // Back arrow in action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(username);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        // Set up logout button
//        Button logoutButton = findViewById(R.id.logoutButton);
//        logoutButton.setOnClickListener(view -> confirmLogout());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
//    private void confirmLogout() {
//        new AlertDialog.Builder(this)
//                .setTitle("Logout")
//                .setMessage("Are you sure you want to logout from TrailConnect?")
//                .setPositiveButton("Yes", (dialog, which) -> logout())
//                .setNegativeButton("No", null)
//                .show();
//    }
}

