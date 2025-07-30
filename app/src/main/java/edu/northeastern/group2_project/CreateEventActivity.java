package edu.northeastern.group2_project;

//import edu.northeastern.group2_project.BuildConfig;



import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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

    private static final int PICK_IMAGES_REQUEST = 1;
    private ArrayList<Uri> imageUris = new ArrayList<>();
    private LinearLayout imagePreviewLayout;
    private FirebaseStorage storage;
    private StorageReference storageRef;
    private FirebaseFirestore firestore;

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

        EditText startDateTimeField = findViewById(R.id.startDateTime);
        EditText endDateTimeField = findViewById(R.id.endDateTime);

        startDateTimeField.setOnClickListener(v -> showDateTimePicker(startDateTimeField));
        endDateTimeField.setOnClickListener(v -> showDateTimePicker(endDateTimeField));

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
        // validation
        if (!validateInputs()) return;

        List<String> imageUrls = new ArrayList<>();
        List<Uri> imagesToUpload = new ArrayList<>(imageUris);

        if (imagesToUpload.isEmpty()) {
            saveEventToFirestore(imageUrls);
            return;
        }

        for (Uri imageUri : imagesToUpload) {
            uploadImageToImgBB(imageUri, new OnUrlReadyCallback() {
                @Override
                public void onUrlReady(String url) {
                    if (url != null) {
                        imageUrls.add(url);
                        if (imageUrls.size() == imagesToUpload.size()) {
                            saveEventToFirestore(imageUrls);
                        }
                    } else {
                        Toast.makeText(CreateEventActivity.this, "Image upload failed.", Toast.LENGTH_SHORT).show();
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
        if (!validateInputs()) return;

        String eventName = getTextOrEmpty(R.id.eventName);
        String startTime = getTextOrEmpty(R.id.startDateTime);
        String endTime = getTextOrEmpty(R.id.endDateTime);
        String description = getTextOrEmpty(R.id.eventDescription);
        String location = getTextOrEmpty(R.id.eventLocation);
        String address = getTextOrEmpty(R.id.eventAddress);
        String price = getTextOrEmpty(R.id.eventPrice);


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
        event.put("price", price);
        event.put("imageUrls", imageUrls);

        firestore.collection("events")
                .add(event)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Event created!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to save event.", Toast.LENGTH_SHORT).show();
                });
    }

    private String getTextOrEmpty(int editTextId) {
        EditText field = findViewById(editTextId);
        return field != null ? field.getText().toString().trim() : "";
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
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            byte[] imageBytes = new byte[inputStream.available()];
            inputStream.read(imageBytes);
            inputStream.close();

            String base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT);

            OkHttpClient client = new OkHttpClient();

            RequestBody formBody = new FormBody.Builder()
                    .add("key", BuildConfig.IMGBB_API_KEY)
                    .add("image", base64Image)
                    .build();

            Request request = new Request.Builder()
                    .url("https://api.imgbb.com/1/upload")
                    .post(formBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        callback.onUrlReady(null);
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String json = response.body().string();
                    try {
                        JSONObject obj = new JSONObject(json);
                        String url = obj.getJSONObject("data").getString("url");
                        runOnUiThread(() -> {
                            callback.onUrlReady(url);
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            callback.onUrlReady(null);
                        });
                    }
                }
            });

        } catch (Exception e) {
            callback.onUrlReady(null);
        }
    }




    interface OnUrlReadyCallback {
        void onUrlReady(String url);
    }

    private String getPathFromUri(Uri uri) {
        String result = null;
        String[] proj = {MediaStore.Images.Media.DATA};
        android.database.Cursor cursor = getContentResolver().query(uri, proj, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            result = cursor.getString(column_index);
            cursor.close();
            Log.d("CreateEventActivity", "Path from MediaStore: " + result);
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
                Log.d("CreateEventActivity", "Path from cache copy: " + result);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e("CreateEventActivity", "Error copying URI to cache: " + e.getMessage());
            }
        }
        if (result == null) {
            Log.e("CreateEventActivity", "Failed to get any path from URI: " + uri.toString());
        }
        return result;
    }
}
