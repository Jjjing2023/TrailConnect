package edu.northeastern.group2_project;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int RC_SIGN_IN = 9001;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        TextInputEditText emailInput = findViewById(R.id.emailInput);
        TextInputEditText passwordInput = findViewById(R.id.passwordInput);
        Button emailSignIn = findViewById(R.id.emailSignIn);
        SignInButton googleSignInButton = findViewById(R.id.googleSignInButton);
        TextView signUpText = findViewById(R.id.signUpText);

        emailSignIn.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Sign in with email
            signInWithEmail(email, password);
        });

        // Set up Google Sign-In button
        googleSignInButton.setOnClickListener(v -> {
            // Show a toast to indicate button was clicked
            Toast.makeText(this, "Starting Google Sign-In...", Toast.LENGTH_SHORT).show();
            signInWithGoogle();
        });

        signUpText.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SignUpActivity.class);
            startActivity(intent);
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is already signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // Instead of automatically logging in, show a dialog asking user if they want to stay logged in
            showLoginChoiceDialog(currentUser);
        }
    }

    private void showLoginChoiceDialog(FirebaseUser user) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Welcome Back!")
                .setMessage("You are already logged in as " + user.getEmail() + ". Would you like to continue or sign in with a different account?")
                .setPositiveButton("Continue", (dialog, which) -> {
                    updateUI(user);
                })
                .setNegativeButton("Sign in with different account", (dialog, which) -> {
                    // Sign out and stay on login screen
                    mAuth.signOut();
                    mGoogleSignInClient.signOut();
                    UserLocalStorage localStorage = new UserLocalStorage(this);
                    localStorage.clearUserData();
                    Toast.makeText(this, "Please sign in with your preferred account", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
    }

    private void signInWithEmail(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        updateUI(mAuth.getCurrentUser());
                    } else {
                        // Sign in fails
                        Log.w(TAG, "signInWithEmail:failure", task.getException());
                        Toast.makeText(MainActivity.this, "Authentication failed: " +
                                task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void signInWithGoogle() {
        // First sign out from Google to allow account selection
        mGoogleSignInClient.signOut()
                .addOnCompleteListener(this, task -> {
                    // After signing out, start the sign-in process
                    Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                    startActivityForResult(signInIntent, RC_SIGN_IN);
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            Log.d(TAG, "Google Sign-In result received");
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                // Google Sign In was successful, authenticate with Firebase
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.getId());
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                // Google Sign In failed
                Log.w(TAG, "Google sign in failed", e);
                Toast.makeText(this, "Google Sign-In Failed: " + e.getStatusCode() + " " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Firebase auth with Google failed", task.getException());
                        Toast.makeText(this,
                                "Authentication failed",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) return;

                    String userId = user.getUid();
                    String email = user.getEmail();
                    String displayName = user.getDisplayName();
                    Uri      photoUri    = user.getPhotoUrl();
                    // split display name
                    String firstName;
                    String lastName;
                    if (displayName != null) {
                        String[] parts = displayName.trim().split(" ", 2);
                        firstName = parts[0];
                        if (parts.length > 1) lastName = parts[1];
                        else {
                            lastName = "";
                        }
                    } else {
                        lastName = "";
                        firstName = "";
                    }

                    FirebaseFirestore db     = FirebaseFirestore.getInstance();
                    DocumentReference docRef = db.collection("users").document(userId);

                    // 1) read existing doc
                    docRef.get()
                            .addOnSuccessListener(snapshot -> {
                                Map<String,Object> updates = new HashMap<>();
                                updates.put("firstName", firstName);
                                updates.put("lastName",  lastName);
                                updates.put("email",     email);
                                updates.put("name",      displayName != null
                                        ? displayName
                                        : firstName + " " + lastName);
                                // only set Google photo if user never set their own
                                if (!snapshot.contains("profileImageUrl") && photoUri != null) {
                                    updates.put("profileImageUrl", photoUri.toString());
                                }

                                // 2) merge back
                                docRef.set(updates, SetOptions.merge())
                                        .addOnSuccessListener(a -> {
                                            Log.d(TAG, "User info merged");
                                            updateUI(user);
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.w(TAG, "Failed to merge user info", e);
                                            Toast.makeText(this,
                                                    "Failed to save user info",
                                                    Toast.LENGTH_SHORT).show();
                                            updateUI(user);
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Log.w(TAG, "Could not read user doc", e);
                                // fallback just merge names & email
                                Map<String,Object> fallback = new HashMap<>();
                                fallback.put("firstName", firstName);
                                fallback.put("lastName",  lastName);
                                fallback.put("email",     email);
                                fallback.put("name",      displayName);
                                docRef.set(fallback, SetOptions.merge())
                                        .addOnCompleteListener(ignored -> updateUI(user));
                            });
                });
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            // Store user data locally
            storeUserLocally(user);

            // Navigate to home screen
            Intent intent = new Intent(MainActivity.this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    private void storeUserLocally(FirebaseUser user) {
        UserLocalStorage localStorage = new UserLocalStorage(this);

        // Store user's display name
        String displayName = user.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            localStorage.storeUserName(displayName);
        } else {
            // If no display name, use email as name (without domain part)
            String email = user.getEmail();
            if (email != null && !email.isEmpty()) {
                String emailName = email.split("@")[0];
                localStorage.storeUserName(emailName);
            }
        }

        // Store user ID for future reference
        localStorage.storeUserId(user.getUid());

        // Log successful storage
        Log.d(TAG, "User data stored locally. User ID: " + user.getUid());
    }
}