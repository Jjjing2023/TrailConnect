package edu.northeastern.group2_project;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface GeoapifyService {
    @GET("v1/geocode/autocomplete")
    Call<FeatureCollection> autocomplete(
            @Query("text") String text,
            @Query("limit") int limit,
            @Query("apiKey") String apiKey);

    @GET("v1/geocode/reverse")
    Call<FeatureCollection> reverse(
            @Query("lat") double lat,
            @Query("lon") double lon,
            @Query("limit") int limit,
            @Query("apiKey") String apiKey);
}
