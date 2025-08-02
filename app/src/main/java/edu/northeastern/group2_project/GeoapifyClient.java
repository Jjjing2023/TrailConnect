package edu.northeastern.group2_project;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class GeoapifyClient {
    private static GeoapifyService S;

    public static GeoapifyService get() {
        if (S != null) return S;

        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(HttpLoggingInterceptor.Level.BASIC);
        OkHttpClient ok = new OkHttpClient.Builder().addInterceptor(log).build();

        S = new Retrofit.Builder()
                .baseUrl("https://api.geoapify.com/")
                .client(ok)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GeoapifyService.class);
        return S;
    }
}
