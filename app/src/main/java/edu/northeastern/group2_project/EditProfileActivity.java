package edu.northeastern.group2_project;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class EditProfileActivity extends AppCompatActivity {
    private static final String TAG = "EditProfileActivity";
    public static final String EXTRA_USERNAME = "extra_username";
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();


    private ImageView ivAvatar;
    private Button btnChangeAvatar;
    private TextInputEditText etName, etEmail, etPhone;
    private Button btnSave;
    private String userId;
    private FirebaseFirestore db;

    // Upload progress UI elements
    private LinearLayout uploadProgressContainer;
    private TextView tvUploadStatus;
    private ProgressBar pbUploadProgress;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    Log.d(TAG, "Picked image URI: " + uri);
                    
                    // Show upload progress UI
                    showUploadProgress(true, "Uploading image...");
                    
                    uploadImageToImgBB(uri, url -> {
                        if (url != null) {
                            Log.d(TAG, "ImgBB returned URL: " + url);
                            runOnUiThread(() -> {
                                // Hide progress UI
                                showUploadProgress(false, "");
                                
                                // Update avatar image
                                Glide.with(this)
                                        .load(url)
                                        .circleCrop()
                                        .into(ivAvatar);
                                
                                Toast.makeText(this, "Avatar updated successfully", Toast.LENGTH_SHORT).show();
                            });
                            
                            // Save URL back to Firestore
                            Map<String,Object> updates = new HashMap<>();
                            updates.put("profileImageUrl", url);
                            db.collection("users")
                                    .document(userId)
                                    .update(updates)
                                    .addOnSuccessListener(a -> {
                                        Log.d(TAG, "Saved new avatar URL to Firestore");
                                        // Set result to indicate successful update
                                        setResult(RESULT_OK);
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to save avatar URL", e);
                                        runOnUiThread(() ->
                                                Toast.makeText(this, "Failed to save avatar URL", Toast.LENGTH_SHORT).show()
                                        );
                                    });
                        } else {
                            Log.e(TAG, "ImgBB upload callback gave null URL");
                            runOnUiThread(() -> {
                                // Hide progress UI and show error
                                showUploadProgress(false, "");
                                tvUploadStatus.setText("Upload failed");
                                
                                Toast.makeText(this, "Avatar upload failed", Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                }
            });

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

        // Initialize UI elements
        ivAvatar = findViewById(R.id.iv_edit_avatar);
        btnChangeAvatar = findViewById(R.id.btn_change_avatar);
        etName = findViewById(R.id.et_name);
        etEmail = findViewById(R.id.et_email);
        etPhone = findViewById(R.id.et_phone);
        btnSave = findViewById(R.id.btn_save_profile);

        // Initialize upload progress UI
        uploadProgressContainer = findViewById(R.id.upload_progress_container);
        tvUploadStatus = findViewById(R.id.tv_upload_status);
        pbUploadProgress = findViewById(R.id.pb_upload_progress);

        loadExistingProfile();

        btnChangeAvatar.setOnClickListener(v ->
                pickImageLauncher.launch("image/*")
        );

        btnSave.setOnClickListener(v -> saveNameEmailPhone());

        // Test ImgBB API key
        testImgBBAPIKey();
    }

    private void loadExistingProfile() {
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String avatarUrl = doc.getString("profileImageUrl");
                        String name = doc.getString("name");
                        String email = doc.getString("email");
                        String phone = doc.getString("phone");

                        Log.d(TAG, "Existing avatarUrl=" + avatarUrl
                                + ", email=" + email + ", phone=" + phone);

                        if (!TextUtils.isEmpty(avatarUrl)) {
                            Glide.with(this)
                                    .load(avatarUrl)
                                    .placeholder(R.drawable.ic_default_avatar)
                                    .circleCrop()
                                    .into(ivAvatar);
                        }
                        if (!TextUtils.isEmpty(name)) {
                            etName.setText(name);
                        }
                        if (!TextUtils.isEmpty(email)) etEmail.setText(email);
                        if (!TextUtils.isEmpty(phone)) etPhone.setText(phone);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load profile", e);
                    Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveNameEmailPhone() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("email", email);
        updates.put("phone", phone);

        // Commit to Firestore
        db.collection("users").document(userId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
                    // Set result and finish activity
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update profile", e);
                    Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show();
                });
    }

    interface OnUrlReadyCallback { void onUrlReady(String url); }

    private void uploadImageToImgBB(Uri imageUri, OnUrlReadyCallback callback) {
        new Thread(() -> {
            try (InputStream in = getContentResolver().openInputStream(imageUri)) {
                if (in == null) {
                    Log.e(TAG, "Failed to open input stream");
                    callback.onUrlReady(null);
                    return;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) {
                    baos.write(buf, 0, len);
                }
                byte[] bytes = baos.toByteArray();

                String base64 = Base64.encodeToString(bytes, Base64.DEFAULT);
                Log.d(TAG, "Encoded image size: " + base64.length());

                OkHttpClient client = HTTP_CLIENT;

                RequestBody body = new FormBody.Builder()
                        .add("key", BuildConfig.IMGBB_API_KEY)
                        .add("image", base64)
                        .build();

                Request req = new Request.Builder()
                        .url("https://api.imgbb.com/1/upload")
                        .post(body)
                        .build();

                Log.d(TAG, "Sending ImgBB request");
                client.newCall(req).enqueue(new Callback() {
                    @Override public void onFailure(Call call, java.io.IOException e) {
                        Log.e(TAG, "ImgBB network failure", e);
                        callback.onUrlReady(null);
                    }

                    @Override public void onResponse(Call call, Response response) throws java.io.IOException {
                        String json = response.body().string();
                        Log.d(TAG, "ImgBB response: " + json);
                        try {
                            JSONObject data = new JSONObject(json).getJSONObject("data");
                            String url = data.getString("url");
                            callback.onUrlReady(url);
                        } catch (Exception ex) {
                            Log.e(TAG, "Failed to parse ImgBB JSON", ex);
                            callback.onUrlReady(null);
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error preparing upload", e);
                callback.onUrlReady(null);
            }
        }).start();
    }

    private void testImgBBAPIKey() {
        Log.d(TAG, "Testing ImgBB API key: " + BuildConfig.IMGBB_API_KEY);
        OkHttpClient client = new OkHttpClient();
        RequestBody body = new FormBody.Builder()
                .add("key", BuildConfig.IMGBB_API_KEY)
                .add("image", "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ")  // tiny test PNG
                .build();
        Request req = new Request.Builder()
                .url("https://api.imgbb.com/1/upload")
                .post(body)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, java.io.IOException e) {
                Log.e(TAG, "API key test network failure", e);
            }
            @Override public void onResponse(Call call, Response resp) throws java.io.IOException {
                Log.d(TAG, "API key test response code: " + resp.code());
                String json = resp.body().string();
                Log.d(TAG, "API key test response: " + json);
            }
        });
    }

    /**
     * Helper method to manage upload progress UI
     */
    private void showUploadProgress(boolean show, String status) {
        runOnUiThread(() -> {
            if (show) {
                uploadProgressContainer.setVisibility(View.VISIBLE);
                tvUploadStatus.setText(status);
                btnChangeAvatar.setEnabled(false);
            } else {
                uploadProgressContainer.setVisibility(View.GONE);
                btnChangeAvatar.setEnabled(true);
            }
        });
    }
}
