package com.example.speak;

import android.app.Application;
import android.content.res.Configuration;
import android.util.Log;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Custom Application class for initializing Firebase and other app-wide configurations.
 * This ensures Firebase is properly initialized before any activities start.
 * 
 * UI Configuration:
 * - Forces light theme (ignores device dark mode)
 * - Disables font scaling (ignores device font size settings)
 */
public class SpeakApplication extends Application {
    private static final String TAG = "SpeakApplication";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Force light theme globally (disable dark mode)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        Log.d(TAG, "✅ Forced light theme globally");
        
        Log.d(TAG, "Initializing Firebase...");
        
        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "Firebase initialized successfully");
            
            String databaseUrl = FirebaseDatabase.getInstance().getReference().toString();
            Log.d(TAG, "Firebase Database URL: " + databaseUrl);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase", e);
        }
        
        // Enable Firebase Database offline persistence
        try {
            FirebaseDatabase database = FirebaseDatabase.getInstance();
            database.setPersistenceEnabled(true);
            
            // Increase cache size to 100MB for better offline support
            database.setPersistenceCacheSizeBytes(100 * 1024 * 1024);
            
            Log.d(TAG, "Firebase persistence enabled with 100MB cache");
        } catch (Exception e) {
            Log.w(TAG, "Firebase persistence already enabled or error: " + e.getMessage());
        }
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Override font scale to prevent UI scattering
        if (newConfig.fontScale != 1.0f) {
            Log.d(TAG, "⚠️ Device font scale changed to " + newConfig.fontScale + ", forcing 1.0");
            newConfig.fontScale = 1.0f;
            
            // Apply configuration
            getResources().updateConfiguration(newConfig, getResources().getDisplayMetrics());
        }
    }
}
