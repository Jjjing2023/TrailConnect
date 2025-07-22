package edu.northeastern.group2_project;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class eventDetailHeader extends AppCompatActivity {
    private FirebaseFirestore db;
    private FirebaseUser user;
    private String userId;
    private String eventId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.event_detail_header);

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
        // todo: settext via data pulled from database
        TextView textEventTitle = findViewById(R.id.textEventTitle);

        boolean[] isFavorited = {false};

        db = FirebaseFirestore.getInstance();
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


        // place holder public images
        List<String> imageUrls = Arrays.asList(
                "https://images.unsplash.com/photo-1506744038136-46273834b3fb",
                "https://images.unsplash.com/photo-1501785888041-af3ef285b470",
                "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee"
        );

        ImageCarouselAdapter adapter = new ImageCarouselAdapter(this, imageUrls);
        viewPager.setAdapter(adapter);

        // page indicator
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            Log.d("TabLayoutMediator", "Binding tab for position " + position);
        }).attach();

        // Auto-scroll every 3 seconds
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                int current = viewPager.getCurrentItem();
                int next = (current + 1) % imageUrls.size();
                viewPager.setCurrentItem(next, true);
                handler.postDelayed(this, 3000); // 3 seconds
            }
        };
        handler.postDelayed(runnable, 3000);

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

    }
}
