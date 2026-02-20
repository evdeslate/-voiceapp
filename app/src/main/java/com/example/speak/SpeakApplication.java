package com.example.speak;

import android.app.Application;
import android.content.res.Configuration;
import android.util.Log;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;
import org.vosk.Model;

import java.io.File;

/**
 * Custom Application class for initializing Firebase and other app-wide configurations.
 * This ensures Firebase is properly initialized before any activities start.
 * 
 * Also loads Vosk model once at startup (singleton pattern) to avoid repeated loading.
 * 
 * UI Configuration:
 * - Forces light theme (ignores device dark mode)
 * - Disables font scaling (ignores device font size settings)
 */
public class SpeakApplication extends Application {
    private static final String TAG = "SpeakApplication";
    
    // Singleton Vosk model - loaded once, shared by all activities
    public static Model voskModel = null;
    public static boolean isVoskModelLoading = false;
    public static boolean isVoskModelReady = false;
    public static String voskModelError = null;
    
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
        
        // Load Vosk model in background thread (singleton pattern)
        loadVoskModelAsync();
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
    
    /**
     * Load Vosk model once at app startup (background thread)
     * This avoids repeated loading in each activity
     */
    private void loadVoskModelAsync() {
        isVoskModelLoading = true;
        
        new Thread(() -> {
            try {
                Log.d(TAG, "=== VOSK MODEL LOADING STARTED ===");
                Log.d(TAG, "🔄 Loading Vosk model (singleton)...");
                long startTime = System.currentTimeMillis();
                
                // Check if model already extracted
                File modelDir = new File(getFilesDir(), "vosk-model-en-us-0.22-lgraph");
                Log.d(TAG, "Model directory path: " + modelDir.getAbsolutePath());
                Log.d(TAG, "Model directory exists: " + modelDir.exists());
                
                if (!modelDir.exists() || !isModelComplete(modelDir)) {
                    Log.d(TAG, "📦 Extracting Vosk model from assets...");
                    extractModelFromAssets(modelDir);
                    Log.d(TAG, "✅ Model extraction complete");
                    
                    // Verify extraction was successful
                    if (!isModelComplete(modelDir)) {
                        throw new Exception("Model extraction incomplete - missing required files");
                    }
                    Log.d(TAG, "✅ Model extraction verified - all files present");
                }
                
                // Verify model directory is readable
                if (!modelDir.canRead()) {
                    throw new Exception("Model directory not readable: " + modelDir.getAbsolutePath());
                }
                
                // Load model
                String modelPath = modelDir.getAbsolutePath();
                Log.d(TAG, "📂 Loading model from: " + modelPath);
                Log.d(TAG, "📂 Model directory exists: " + modelDir.exists());
                Log.d(TAG, "📂 Model directory readable: " + modelDir.canRead());
                
                // List model directory contents for debugging
                File[] files = modelDir.listFiles();
                if (files != null) {
                    Log.d(TAG, "📂 Model directory contains " + files.length + " items:");
                    for (File file : files) {
                        Log.d(TAG, "   - " + file.getName() + (file.isDirectory() ? " (dir)" : " (file)"));
                    }
                }
                
                Log.d(TAG, "Creating Model object...");
                voskModel = new Model(modelPath);
                
                // Verify model loaded successfully
                if (voskModel == null) {
                    throw new Exception("Model object is null after loading");
                }
                
                Log.d(TAG, "✅ Model object created successfully");
                Log.d(TAG, "Model class: " + voskModel.getClass().getName());
                
                long loadTime = System.currentTimeMillis() - startTime;
                Log.d(TAG, String.format("✅ Vosk model loaded successfully in %.1f seconds", loadTime / 1000.0));
                
                isVoskModelReady = true;
                isVoskModelLoading = false;
                
                Log.d(TAG, "=== VOSK MODEL LOADING COMPLETE ===");
                
            } catch (Exception e) {
                Log.e(TAG, "=== VOSK MODEL LOADING FAILED ===");
                Log.e(TAG, "❌ Failed to load Vosk model", e);
                e.printStackTrace();
                voskModelError = e.getMessage();
                isVoskModelLoading = false;
                isVoskModelReady = false;
                
                // Log detailed error information
                Log.e(TAG, "Error type: " + e.getClass().getName());
                Log.e(TAG, "Error message: " + e.getMessage());
                if (e.getCause() != null) {
                    Log.e(TAG, "Cause: " + e.getCause().getMessage());
                    Log.e(TAG, "Cause type: " + e.getCause().getClass().getName());
                }
                
                // Check if model files exist
                File modelDir = new File(getFilesDir(), "vosk-model-en-us-0.22-lgraph");
                Log.e(TAG, "Model directory exists: " + modelDir.exists());
                if (modelDir.exists()) {
                    Log.e(TAG, "Model directory readable: " + modelDir.canRead());
                    File[] files = modelDir.listFiles();
                    if (files != null) {
                        Log.e(TAG, "Model directory contains " + files.length + " items");
                    }
                }
            }
        }).start();
    }
    
    /**
     * Check if model directory has all required files
     */
    private boolean isModelComplete(File modelDir) {
        if (!modelDir.exists()) return false;
        
        File amDir = new File(modelDir, "am");
        File confDir = new File(modelDir, "conf");
        File graphDir = new File(modelDir, "graph");
        File ivectorDir = new File(modelDir, "ivector");
        
        return amDir.exists() && confDir.exists() && graphDir.exists() && ivectorDir.exists();
    }
    
    /**
     * Extract model from assets to files directory
     */
    private void extractModelFromAssets(File targetDir) throws java.io.IOException {
        String assetPath = "sync/vosk-model-en-us-0.22-lgraph";
        
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        
        copyAssetFolder(assetPath, targetDir.getAbsolutePath());
    }
    
    /**
     * Recursively copy folder from assets
     */
    private void copyAssetFolder(String assetPath, String targetPath) throws java.io.IOException {
        android.content.res.AssetManager assetManager = getAssets();
        String[] files = assetManager.list(assetPath);
        
        if (files == null || files.length == 0) {
            copyAssetFile(assetPath, targetPath);
        } else {
            File targetDir = new File(targetPath);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            
            for (String file : files) {
                String assetFilePath = assetPath + "/" + file;
                String targetFilePath = targetPath + "/" + file;
                copyAssetFolder(assetFilePath, targetFilePath);
            }
        }
    }
    
    /**
     * Copy single file from assets
     */
    private void copyAssetFile(String assetPath, String targetPath) throws java.io.IOException {
        android.content.res.AssetManager assetManager = getAssets();
        
        java.io.InputStream in = assetManager.open(assetPath);
        File targetFile = new File(targetPath);
        
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        java.io.OutputStream out = new java.io.FileOutputStream(targetFile);
        
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        
        in.close();
        out.flush();
        out.close();
    }
}
