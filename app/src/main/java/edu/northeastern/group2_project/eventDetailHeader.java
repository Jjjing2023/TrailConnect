package edu.northeastern.group2_project;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import java.util.Arrays;
import java.util.List;

public class eventDetailHeader extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.event_detail_header);

        ViewPager2 viewPager = findViewById(R.id.viewPagerImages);

        // place holder public images
        List<String> imageUrls = Arrays.asList(
                "https://images.unsplash.com/photo-1506744038136-46273834b3fb",
                "https://images.unsplash.com/photo-1523413651479-597eb2da0ad6",
                "https://images.unsplash.com/photo-1532202802379-dcb5f1f7c03c"
        );

        ImageCarouselAdapter adapter = new ImageCarouselAdapter(this, imageUrls);
        viewPager.setAdapter(adapter);
    }
}
