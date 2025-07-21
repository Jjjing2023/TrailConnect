package edu.northeastern.group2_project;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Utility class for storing and retrieving user data locally
 */
public class UserLocalStorage {
    // Constants
    private static final String PREFS_NAME = "TrailConnectUserPrefs";
    private static final String KEY_USER_NAME = "userName";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_EMAIL = "userEmail";
    private static final String KEY_LAST_LOGIN = "lastLogin";

    private SharedPreferences sharedPreferences;

    public UserLocalStorage(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Store the user's name locally
     * @param userName The name to store
     */
    public void storeUserName(String userName) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_USER_NAME, userName);
        editor.apply();
    }

    /**
     * Store the user's unique ID
     * @param userId Firebase UID
     */
    public void storeUserId(String userId) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_USER_ID, userId);
        editor.apply();
    }

    /**
     * Store the user's email
     * @param email User's email address
     */
    public void storeUserEmail(String email) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_USER_EMAIL, email);
        editor.apply();
    }

    /**
     * Update the last login timestamp
     */
    public void updateLastLogin() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(KEY_LAST_LOGIN, System.currentTimeMillis());
        editor.apply();
    }

    /**
     * Retrieve the stored user's name
     * @return The stored name, or empty string if not found
     */
    public String getUserName() {
        return sharedPreferences.getString(KEY_USER_NAME, "");
    }

    /**
     * Retrieve the stored user ID
     * @return The user ID, or empty string if not found
     */
    public String getUserId() {
        return sharedPreferences.getString(KEY_USER_ID, "");
    }

    /**
     * Retrieve the stored user email
     * @return The user email, or empty string if not found
     */
    public String getUserEmail() {
        return sharedPreferences.getString(KEY_USER_EMAIL, "");
    }

    /**
     * Get the last login timestamp
     * @return The timestamp of last login, or 0 if not available
     */
    public long getLastLogin() {
        return sharedPreferences.getLong(KEY_LAST_LOGIN, 0);
    }

    /**
     * Check if user data is stored
     * @return true if user ID exists in storage
     */
    public boolean isUserLoggedIn() {
        return sharedPreferences.contains(KEY_USER_ID);
    }

    /**
     * Clear stored user data (for logout)
     */
    public void clearUserData() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }
}