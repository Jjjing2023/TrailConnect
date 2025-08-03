package edu.northeastern.group2_project;

//import edu.northeastern.group2_project.BuildConfig;



import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import android.net.Uri;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;




public class CreateEventActivity extends AppCompatActivity {

    private static final String TAG = "CreateEventActivity";
    private static final int PICK_IMAGES_REQUEST = 1;
    private ArrayList<Uri> imageUris = new ArrayList<>();
    private LinearLayout imagePreviewLayout;
    private FirebaseStorage storage;
    private StorageReference storageRef;
    private FirebaseFirestore firestore;

    // for address suggestion
    private GeoapifyService geoApi;
    private ListPopupWindow addrPopup;
    private ArrayAdapter<String> addrAdapter;
    private final Handler addrHandler = new Handler(Looper.getMainLooper());
    private Runnable addrPending;
    private static final long ADDR_DEBOUNCE = 350;

    // Keep selected coordinates from autocomplete
    private Double selectedLat = null;
    private Double selectedLon = null;

    // Keep the raw results so we can map list item -> feature
    private java.util.List<FeatureCollection.Feature> lastAddrResults = new java.util.ArrayList<>();
    
    // Flag to prevent duplicate submissions
    private boolean isSubmitting = false;
    private Button createButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_event);

        Button chooseImageButton = findViewById(R.id.btnSelectImage);
        chooseImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openImagePicker();
            }
        });

        imagePreviewLayout = findViewById(R.id.imagePreviewLayout);

        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();
        firestore = FirebaseFirestore.getInstance();

        Button btnCreate = findViewById(R.id.btnCreateEvent);
        btnCreate.setOnClickListener(v -> uploadImagesAndSaveEvent());
        
        // Store button reference for updating state
        this.createButton = btnCreate;

        EditText startDateTimeField = findViewById(R.id.startDateTime);
        EditText endDateTimeField = findViewById(R.id.endDateTime);

        startDateTimeField.setOnClickListener(v -> showDateTimePicker(startDateTimeField));
        endDateTimeField.setOnClickListener(v -> showDateTimePicker(endDateTimeField));

        geoApi = GeoapifyClient.get();
        // Address field + popup
        EditText addressEt = findViewById(R.id.eventAddress);
        addrPopup = new ListPopupWindow(this);
        addrAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        addrPopup.setAdapter(addrAdapter);
        addrPopup.setAnchorView(addressEt);
        addrPopup.setOnItemClickListener((parent, view, position, id) -> {
            FeatureCollection.Feature f = lastAddrResults.get(position);
            String label = f.properties.formatted != null ? f.properties.formatted
                    : (f.properties.name != null ? f.properties.name : addressEt.getText().toString());
            addressEt.setText(label);
            // keep coordinates
            selectedLat = f.properties.lat != null ? f.properties.lat : f.geometry.coordinates.get(1);
            selectedLon = f.properties.lon != null ? f.properties.lon : f.geometry.coordinates.get(0);
            addrPopup.dismiss();
        });

        addressEt.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().trim();
                selectedLat = null; selectedLon = null; // reset if user edits again
                if (q.length() < 2) { addrPopup.dismiss(); return; }

                if (addrPending != null) addrHandler.removeCallbacks(addrPending);
                addrPending = () -> {
                    geoApi.autocomplete(q, 8, BuildConfig.GEOAPIFY_KEY)
                            .enqueue(new retrofit2.Callback<FeatureCollection>() {
                                @Override public void onResponse(retrofit2.Call<FeatureCollection> c,
                                                                 retrofit2.Response<FeatureCollection> r) {
                                    if (!r.isSuccessful() || r.body()==null) { addrPopup.dismiss(); return; }
                                    lastAddrResults = r.body().features != null ? r.body().features : new ArrayList<>();
                                    ArrayList<String> labels = new ArrayList<>();
                                    for (FeatureCollection.Feature f : lastAddrResults) {
                                        String label = f.properties.formatted != null ? f.properties.formatted
                                                : (f.properties.name != null ? f.properties.name : "");
                                        if (!label.isEmpty()) labels.add(label);
                                    }
                                    if (labels.isEmpty()) { addrPopup.dismiss(); return; }
                                    addrAdapter.clear();
                                    addrAdapter.addAll(labels);
                                    addrAdapter.notifyDataSetChanged();
                                    addrPopup.show();
                                }
                                @Override public void onFailure(retrofit2.Call<FeatureCollection> c, Throwable t) {
                                    addrPopup.dismiss();
                                }
                            });
                };
                addrHandler.postDelayed(addrPending, ADDR_DEBOUNCE);
            }
        });
        
        // Log API key status for debugging
        Log.d(TAG, "IMGBB_API_KEY configured: " + (BuildConfig.IMGBB_API_KEY != null && !BuildConfig.IMGBB_API_KEY.isEmpty()));
        if (BuildConfig.IMGBB_API_KEY != null) {
            Log.d(TAG, "IMGBB_API_KEY length: " + BuildConfig.IMGBB_API_KEY.length());
            Log.d(TAG, "IMGBB_API_KEY starts with: " + BuildConfig.IMGBB_API_KEY.substring(0, Math.min(10, BuildConfig.IMGBB_API_KEY.length())));
            
            // Test API key validity
            testImgBBAPIKey();
        } else {
            Log.e(TAG, "IMGBB_API_KEY is null or empty - this will cause image upload to fail!");
        }
    }

    private void showDateTimePicker(EditText targetField) {
        final Calendar calendar = Calendar.getInstance();

        DatePickerDialog datePicker = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            TimePickerDialog timePicker = new TimePickerDialog(this, (timeView, hourOfDay, minute) -> {
                calendar.set(year, month, dayOfMonth, hourOfDay, minute);
                @SuppressLint("SimpleDateFormat")
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                targetField.setText(sdf.format(calendar.getTime()));
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true);
            timePicker.show();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        datePicker.show();
    }



    private void openImagePicker() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Pictures"), PICK_IMAGES_REQUEST);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Reset submitting flag when activity is destroyed
        isSubmitting = false;
        updateButtonState(false);
        Log.d(TAG, "Reset isSubmitting to false due to activity destruction");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGES_REQUEST && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri imageUri = data.getClipData().getItemAt(i).getUri();
                    try {
                        getContentResolver().takePersistableUriPermission(
                                imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                    } catch (SecurityException e) {
                        e.printStackTrace(); // 防止崩溃
                    }
                    imageUris.add(imageUri);
                }
            } else if (data.getData() != null) {
                Uri imageUri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(
                            imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
                imageUris.add(imageUri);
            }

            imagePreviewLayout.removeAllViews();

            for (int i = 0; i < imageUris.size(); i++) {
                Uri uri = imageUris.get(i);

                FrameLayout frame = new FrameLayout(this);
                LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(200, 200);
                frameParams.setMargins(8, 0, 8, 0);
                frame.setLayoutParams(frameParams);

                // Image Preview
                ImageView imageView = new ImageView(this);
                FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );
                imageView.setLayoutParams(imageParams);
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setImageURI(uri);

                // Delete Photo Button
                ImageView deleteButton = new ImageView(this);
                deleteButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(48, 48);
                deleteParams.setMargins(0, 0, 0, 0);
                deleteParams.gravity = android.view.Gravity.END | android.view.Gravity.TOP;
                deleteButton.setLayoutParams(deleteParams);
                deleteButton.setBackgroundColor(0xAA333333);
                deleteButton.setPadding(8, 8, 8, 8);

                int index = i; // for lambda
                deleteButton.setOnClickListener(v -> {
                    imageUris.remove(index);
                    refreshImagePreviews();
                });

                frame.addView(imageView);
                frame.addView(deleteButton);
                imagePreviewLayout.addView(frame);
            }

        }

    }

    private void displayImagePreviews() {
        LinearLayout container = findViewById(R.id.imagePreviewLayout);
        container.removeAllViews();

        for (Uri uri : imageUris) {
            ImageView imageView = new ImageView(this);
            imageView.setImageURI(uri);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(250, 250)); // 可按需调整大小
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setPadding(8, 8, 8, 8);
            container.addView(imageView);
        }
    }

    private void uploadImagesAndSaveEvent() {
        Log.d(TAG, "Starting uploadImagesAndSaveEvent");
        
        // Prevent duplicate submissions
        if (isSubmitting) {
            Log.d(TAG, "Already submitting, ignoring duplicate request");
            Toast.makeText(this, "Event creation in progress, please wait...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // validation
        if (!validateInputs()) {
            Log.e(TAG, "Input validation failed");
            return;
        }
        
        // Set submitting flag and update button state
        isSubmitting = true;
        updateButtonState(true);
        Log.d(TAG, "Set isSubmitting to true");

        List<String> imageUrls = new ArrayList<>();
        List<Uri> imagesToUpload = new ArrayList<>(imageUris);

        Log.d(TAG, "Number of images to upload: " + imagesToUpload.size());

        if (imagesToUpload.isEmpty()) {
            Log.d(TAG, "No images to upload, proceeding to save event");
            saveEventToFirestore(imageUrls);
            return;
        }

        final int[] uploadedCount = {0};
        final int[] failedCount = {0};
        
        for (Uri imageUri : imagesToUpload) {
            Log.d(TAG, "Uploading image: " + imageUri.toString());
            
            uploadImageToImgBB(imageUri, new OnUrlReadyCallback() {
                @Override
                public void onUrlReady(String url) {
                    if (url != null) {
                        Log.d(TAG, "Image upload successful, adding URL: " + url);
                        imageUrls.add(url);
                        uploadedCount[0]++;
                    } else {
                        Log.e(TAG, "Image upload failed for URI: " + imageUri.toString());
                        failedCount[0]++;
                        Toast.makeText(CreateEventActivity.this, "Image upload failed.", Toast.LENGTH_SHORT).show();
                    }
                    
                    Log.d(TAG, "Upload progress: " + uploadedCount[0] + " successful, " + failedCount[0] + " failed, " + imagesToUpload.size() + " total");
                    
                    if (uploadedCount[0] + failedCount[0] == imagesToUpload.size()) {
                        Log.d(TAG, "All image uploads completed. Successful: " + uploadedCount[0] + ", Failed: " + failedCount[0]);
                        if (uploadedCount[0] > 0) {
                            saveEventToFirestore(imageUrls);
                        } else {
                            Log.e(TAG, "All image uploads failed, cannot save event");
                            Toast.makeText(CreateEventActivity.this, "All image uploads failed. Please try again.", Toast.LENGTH_LONG).show();
                            // Reset submitting flag when all image uploads fail
                            isSubmitting = false;
                            updateButtonState(false);
                            Log.d(TAG, "Reset isSubmitting to false due to all image uploads failing");
                        }
                    }
                }
            });
        }
    }

    private void refreshImagePreviews() {
        imagePreviewLayout.removeAllViews();

        for (int i = 0; i < imageUris.size(); i++) {
            Uri uri = imageUris.get(i);

            FrameLayout frame = new FrameLayout(this);
            LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(200, 200);
            frameParams.setMargins(8, 0, 8, 0);
            frame.setLayoutParams(frameParams);

            ImageView imageView = new ImageView(this);
            FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            );
            imageView.setLayoutParams(imageParams);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setImageURI(uri);

            ImageView deleteButton = new ImageView(this);
            deleteButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(48, 48);
            deleteParams.setMargins(0, 0, 0, 0);
            deleteParams.gravity = android.view.Gravity.END | android.view.Gravity.TOP;
            deleteButton.setLayoutParams(deleteParams);
            deleteButton.setBackgroundColor(0xAA333333);
            deleteButton.setPadding(8, 8, 8, 8);

            int index = i;
            deleteButton.setOnClickListener(v -> {
                imageUris.remove(index);
                refreshImagePreviews();
            });

            frame.addView(imageView);
            frame.addView(deleteButton);
            imagePreviewLayout.addView(frame);
        }
    }
    private void saveEventToFirestore(List<String> imageUrls) {
        if (!validateInputs()) {
            // Reset submitting flag when validation fails
            isSubmitting = false;
            updateButtonState(false);
            Log.d(TAG, "Reset isSubmitting to false due to validation failure");
            return;
        }

        String eventName = getTextOrEmpty(R.id.eventName);
        String startTime = getTextOrEmpty(R.id.startDateTime);
        String endTime = getTextOrEmpty(R.id.endDateTime);
        String description = getTextOrEmpty(R.id.eventDescription);
        String location = getTextOrEmpty(R.id.eventLocation);
        String address = getTextOrEmpty(R.id.eventAddress);
        String price = getTextOrEmpty(R.id.eventPrice);

        double priceValue;
        if ("FREE".equalsIgnoreCase(price)) {
            priceValue = 0;
        } else {
            try {
                priceValue = Double.parseDouble(price);
            } catch (NumberFormatException e) {
                priceValue = 0; // fallback if input is not a valid number
            }
        }

        double latitude = 0.0;
        double longitude = 0.0;

        // lat & lon from Geoapify autocomplete selection
        if (selectedLat != null && selectedLon != null) {
            latitude = selectedLat;
            longitude = selectedLon;
        } else {
            // Fallback to Android Geocoder if the user typed a custom address
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocationName(address, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    latitude = addresses.get(0).getLatitude();
                    longitude = addresses.get(0).getLongitude();
                } else {
                    Log.e("CreateEventActivity", "Geocoder returned no results for address: " + address);
                }
            } catch (Exception e) {
                Log.e("CreateEventActivity", "Geocoding failed: " + e.getMessage());
            }
        }


        // saving host info for an event
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String hostId = (currentUser != null) ? currentUser.getUid() : "unknown";
        String hostName = (currentUser != null) ? currentUser.getDisplayName() : "anonymous";
        String hostEmail = (currentUser != null) ? currentUser.getEmail() : "anonymous";

        Map<String, Object> hostInfo = new HashMap<>();
        hostInfo.put("uid", hostId);
        hostInfo.put("email", hostEmail);
        hostInfo.put("name", hostName);


        Map<String, Object> event = new HashMap<>();
        event.put("name", eventName);
        event.put("startTime", startTime);
        event.put("endTime", endTime);
        event.put("description", description);
        event.put("location", location);
        event.put("address", address);
        event.put("price", priceValue);
        event.put("imageUrls", imageUrls);
        event.put("latitude", latitude);
        event.put("longitude", longitude);
        event.put("postTime", FieldValue.serverTimestamp());
        event.put("host", hostId);  // Add host ID for querying hosted events
        event.put("hostInfo", hostInfo);  // Add detailed host information

        Log.d(TAG, "Saving event with host ID: " + hostId);
        firestore.collection("events")
                .add(event)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Event created successfully with ID: " + documentReference.getId());
                    
                    // Update user's hosted events list
                    updateUserHostedEvents(hostId, documentReference.getId());
                    
                    Toast.makeText(this, "Event created!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save event", e);
                    Toast.makeText(this, "Failed to save event.", Toast.LENGTH_SHORT).show();
                    // Reset submitting flag on failure
                    isSubmitting = false;
                    updateButtonState(false);
                    Log.d(TAG, "Reset isSubmitting to false due to failure");
                });
    }

    private String getTextOrEmpty(int editTextId) {
        EditText field = findViewById(editTextId);
        return field != null ? field.getText().toString().trim() : "";
    }

    private void updateUserHostedEvents(String userId, String eventId) {
        firestore.collection("users").document(userId)
                .update("hostedEvents", FieldValue.arrayUnion(eventId))
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Successfully updated user's hosted events list");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update user's hosted events list", e);
                    // Don't show error to user as event was created successfully
                });
    }

    private void updateButtonState(boolean isSubmitting) {
        if (createButton != null) {
            if (isSubmitting) {
                createButton.setText("Creating Event...");
                createButton.setEnabled(false);
            } else {
                createButton.setText("Create Event");
                createButton.setEnabled(true);
            }
        }
    }

    // input validation
    private boolean validateInputs() {
        if (getTextOrEmpty(R.id.eventName).isEmpty()) {
            showToast("Please enter an event name.");
            return false;
        }

        if (getTextOrEmpty(R.id.startDateTime).isEmpty()) {
            showToast("Please enter a start time.");
            return false;
        }
        if (getTextOrEmpty(R.id.endDateTime).isEmpty()) {
            showToast("Please enter an end time.");
            return false;
        }

        // Validate time format and chronology
        String startTime = getTextOrEmpty(R.id.startDateTime);
        String endTime = getTextOrEmpty(R.id.endDateTime);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        try {
            Date start = sdf.parse(startTime);
            Date end = sdf.parse(endTime);
            if (start != null && end != null && start.after(end)) {
                showToast("Start time must be before end time.");
                return false;
            }
        } catch (ParseException e) {
            showToast("Invalid date format. Please use yyyy-MM-dd HH:mm.");
            return false;
        }

        if (getTextOrEmpty(R.id.eventDescription).isEmpty()) {
            showToast("Please enter a description.");
            return false;
        }
        if (getTextOrEmpty(R.id.eventLocation).isEmpty()) {
            showToast("Please enter a location.");
            return false;
        }
        if (getTextOrEmpty(R.id.eventAddress).isEmpty()) {
            showToast("Please enter an address.");
            return false;
        }

        String price = getTextOrEmpty(R.id.eventPrice).trim();
        if (price.isEmpty()) {
            showToast("Please enter a price");
            return false;}
            try {
                Double.parseDouble(price);
            } catch (NumberFormatException e) {
                showToast("Price must be a number.");
                return false;
            }

        return true;
    }

    private void showToast(String msg){
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }


    // start and end time validation
    private boolean isValidDateTimeRange(String start, String end) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US);
        sdf.setLenient(false);

        try {
            java.util.Date startDate = sdf.parse(start);
            java.util.Date endDate = sdf.parse(end);
            if (startDate == null || endDate == null) return false;

            return startDate.before(endDate);
        } catch (java.text.ParseException e) {

            return false;
        }
    }


    // Upload image to ImgBB to get url
    private void uploadImageToImgBB(Uri imageUri, OnUrlReadyCallback callback) {
        Log.d(TAG, "Starting image upload for URI: " + imageUri.toString());
        
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for URI: " + imageUri.toString());
                callback.onUrlReady(null);
                return;
            }
            
            byte[] imageBytes = new byte[inputStream.available()];
            inputStream.read(imageBytes);
            inputStream.close();
            
            Log.d(TAG, "Image size: " + imageBytes.length + " bytes");

            String base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT);
            Log.d(TAG, "Base64 encoded image length: " + base64Image.length());

            OkHttpClient client = new OkHttpClient();

            RequestBody formBody = new FormBody.Builder()
                    .add("key", BuildConfig.IMGBB_API_KEY)
                    .add("image", base64Image)
                    .build();

            Request request = new Request.Builder()
                    .url("https://api.imgbb.com/1/upload")
                    .post(formBody)
                    .build();

            Log.d(TAG, "Sending request to ImgBB API");
            Log.d(TAG, "API Key length: " + (BuildConfig.IMGBB_API_KEY != null ? BuildConfig.IMGBB_API_KEY.length() : "null"));

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Network failure during image upload", e);
                    runOnUiThread(() -> {
                        Log.e(TAG, "Image upload failed due to network error");
                        callback.onUrlReady(null);
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String json = response.body().string();
                    Log.d(TAG, "ImgBB API response: " + json);
                    
                    try {
                        JSONObject obj = new JSONObject(json);
                        if (obj.has("data") && obj.getJSONObject("data").has("url")) {
                            String url = obj.getJSONObject("data").getString("url");
                            Log.d(TAG, "Image upload successful, URL: " + url);
                            runOnUiThread(() -> {
                                callback.onUrlReady(url);
                            });
                        } else {
                            Log.e(TAG, "ImgBB API response does not contain expected data structure");
                            Log.e(TAG, "Response JSON: " + json);
                            runOnUiThread(() -> {
                                callback.onUrlReady(null);
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing ImgBB API response", e);
                        Log.e(TAG, "Response JSON: " + json);
                        runOnUiThread(() -> {
                            callback.onUrlReady(null);
                        });
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error during image upload preparation", e);
            callback.onUrlReady(null);
        }
    }




    interface OnUrlReadyCallback {
        void onUrlReady(String url);
    }

    private void testImgBBAPIKey() {
        // Create a simple test image (1x1 pixel)
        String testImageBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";
        
        OkHttpClient client = new OkHttpClient();
        RequestBody formBody = new FormBody.Builder()
                .add("key", BuildConfig.IMGBB_API_KEY)
                .add("image", testImageBase64)
                .build();

        Request request = new Request.Builder()
                .url("https://api.imgbb.com/1/upload")
                .post(formBody)
                .build();

        Log.d(TAG, "Testing ImgBB API key validity...");
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "API key test failed due to network error", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String json = response.body().string();
                Log.d(TAG, "API key test response: " + json);
                
                try {
                    JSONObject obj = new JSONObject(json);
                    if (obj.has("status_code") && obj.getInt("status_code") == 400) {
                        Log.e(TAG, "API key is INVALID: " + obj.getString("status_txt"));
                        runOnUiThread(() -> {
                            Toast.makeText(CreateEventActivity.this, 
                                "ImgBB API key is invalid. Please check your configuration.", 
                                Toast.LENGTH_LONG).show();
                        });
                    } else if (obj.has("data") && obj.getJSONObject("data").has("url")) {
                        Log.d(TAG, "API key is VALID - test upload successful");
                    } else {
                        Log.e(TAG, "Unexpected API response format: " + json);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing API test response", e);
                }
            }
        });
    }

    private String getPathFromUri(Uri uri) {
        String result = null;
        String[] proj = {MediaStore.Images.Media.DATA};
        android.database.Cursor cursor = getContentResolver().query(uri, proj, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            result = cursor.getString(column_index);
            cursor.close();
            Log.d(TAG, "Path from MediaStore: " + result);
        }
        if (result == null) {
            File file = new File(getCacheDir(), UUID.randomUUID().toString() + ".jpg");
            try (java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
                 java.io.OutputStream outputStream = new java.io.FileOutputStream(file)) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = inputStream.read(buf)) > 0) {
                    outputStream.write(buf, 0, len);
                }
                result = file.getAbsolutePath();
                Log.d(TAG, "Path from cache copy: " + result);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Error copying URI to cache: " + e.getMessage());
            }
        }
        if (result == null) {
            Log.e(TAG, "Failed to get any path from URI: " + uri.toString());
        }
        return result;
    }
}
