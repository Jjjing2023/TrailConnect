package edu.northeastern.group2_project;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {
    public static final String EXTRA_USERNAME = "extra_username";

    private TextInputEditText etEmail, etPhone;
    private Button btnSave;
    private String userId;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        // Get user ID
        userId = getIntent().getStringExtra(EXTRA_USERNAME);
        if (userId == null) {
            Toast.makeText(this, "No user specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        etEmail  = findViewById(R.id.et_email);
        etPhone  = findViewById(R.id.et_phone);
        btnSave  = findViewById(R.id.btn_save_profile);

        // Load existing data
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String email = doc.getString("email");
                        String phone = doc.getString("phone");
                        if (!TextUtils.isEmpty(email)) {
                            etEmail.setText(email);
                        }
                        if (!TextUtils.isEmpty(phone)) {
                            etPhone.setText(phone);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load current info", Toast.LENGTH_SHORT).show();
                });

        // Save button listener
        btnSave.setOnClickListener(v -> {
            String newEmail = etEmail.getText().toString().trim();
            String newPhone = etPhone.getText().toString().trim();

            if (TextUtils.isEmpty(newEmail) && TextUtils.isEmpty(newPhone)) {
                Toast.makeText(this, "Enter at least one value", Toast.LENGTH_SHORT).show();
                return;
            }

            // Build update map
            Map<String,Object> updates = new HashMap<>();
            if (!TextUtils.isEmpty(newEmail)) updates.put("email", newEmail);
            if (!TextUtils.isEmpty(newPhone)) updates.put("phone", newPhone);

            // Commit to Firestore
            db.collection("users").document(userId)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show();
                    });
        });
    }
}

