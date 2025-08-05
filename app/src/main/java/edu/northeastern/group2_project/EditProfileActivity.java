package edu.northeastern.group2_project;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;

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
    private TextInputEditText etEmail, etPhone;
    private Button btnSave;
    private String userId;
    private FirebaseFirestore db;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    Log.d(TAG, "Picked image URI: " + uri);
                    uploadImageToImgBB(uri, url -> {
                        if (url != null) {
                            Log.d(TAG, "ImgBB returned URL: " + url);
                            runOnUiThread(() -> {
                                Glide.with(this)
                                        .load(url)
                                        .circleCrop()
                                        .into(ivAvatar);
                            });
                            // Save URL back to Firestore
                            Map<String,Object> updates = new HashMap<>();
                            updates.put("profileImageUrl", url);
                            db.collection("users")
                                    .document(userId)
                                    .update(updates)
                                    .addOnSuccessListener(a -> {
                                        Log.d(TAG, "Saved new avatar URL to Firestore");
                                        runOnUiThread(() ->
                                                Toast.makeText(this, "Avatar updated", Toast.LENGTH_SHORT).show()
                                        );
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to save avatar URL", e);
                                        runOnUiThread(() ->
                                                Toast.makeText(this, "Failed to save avatar URL", Toast.LENGTH_SHORT).show()
                                        );
                                    });
                        } else {
                            Log.e(TAG, "ImgBB upload callback gave null URL");
                            runOnUiThread(() ->
                                    Toast.makeText(this, "Avatar upload failed", Toast.LENGTH_SHORT).show()
                            );
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

        ivAvatar = findViewById(R.id.iv_edit_avatar);
        btnChangeAvatar = findViewById(R.id.btn_change_avatar);
        etEmail  = findViewById(R.id.et_email);
        etPhone  = findViewById(R.id.et_phone);
        btnSave  = findViewById(R.id.btn_save_profile);

        loadExistingProfile();

        btnChangeAvatar.setOnClickListener(v ->
                pickImageLauncher.launch("image/*")
        );

        btnSave.setOnClickListener(v -> saveEmailPhone());

        // Test ImgBB API key
        testImgBBAPIKey();
    }

    private void loadExistingProfile() {
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String avatarUrl = doc.getString("profileImageUrl");
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
                        if (!TextUtils.isEmpty(email)) etEmail.setText(email);
                        if (!TextUtils.isEmpty(phone)) etPhone.setText(phone);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load profile", e);
                    Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveEmailPhone() {
        String newEmail = etEmail.getText().toString().trim();
        String newPhone = etPhone.getText().toString().trim();
        if (TextUtils.isEmpty(newEmail) && TextUtils.isEmpty(newPhone)) {
            Toast.makeText(this, "Enter at least one field", Toast.LENGTH_SHORT).show();
            return;
        }
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

                in.read(bytes);

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
}
