package com.example.speak;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Secure SharedPreferences wrapper using EncryptedSharedPreferences
 * Encrypts both keys and values using AES256-GCM
 * 
 * Usage:
 * - Replaces standard SharedPreferences with encrypted version
 * - Transparent encryption/decryption
 * - Backward compatible API
 */
public class SecurePreferences {
    private static final String TAG = "SecurePreferences";
    private static final String DEFAULT_PREFS_NAME = "secure_prefs";
    
    /**
     * Get encrypted SharedPreferences instance
     * 
     * @param context Application context
     * @param prefsName Preferences file name
     * @return Encrypted SharedPreferences or fallback to regular if encryption fails
     */
    public static SharedPreferences getEncryptedPreferences(Context context, String prefsName) {
        try {
            // Create or retrieve master key for encryption
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            // Create encrypted SharedPreferences
            SharedPreferences encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    prefsName,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            
            Log.d(TAG, "✅ Encrypted SharedPreferences created: " + prefsName);
            return encryptedPrefs;
            
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "❌ Failed to create encrypted preferences, falling back to standard: " + e.getMessage());
            // Fallback to regular SharedPreferences if encryption fails
            return context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        }
    }
    
    /**
     * Get encrypted SharedPreferences with default name
     */
    public static SharedPreferences getEncryptedPreferences(Context context) {
        return getEncryptedPreferences(context, DEFAULT_PREFS_NAME);
    }
    
    /**
     * Migrate data from plain SharedPreferences to encrypted version
     * Call this once during app upgrade
     * 
     * @param context Application context
     * @param oldPrefsName Old preferences file name
     * @param newPrefsName New encrypted preferences file name
     */
    public static void migrateToEncrypted(Context context, String oldPrefsName, String newPrefsName) {
        try {
            // Get old plain preferences
            SharedPreferences oldPrefs = context.getSharedPreferences(oldPrefsName, Context.MODE_PRIVATE);
            
            // Get new encrypted preferences
            SharedPreferences newPrefs = getEncryptedPreferences(context, newPrefsName);
            
            // Check if migration already done
            if (newPrefs.getBoolean("migration_complete", false)) {
                Log.d(TAG, "Migration already completed");
                return;
            }
            
            // Copy all data
            SharedPreferences.Editor editor = newPrefs.edit();
            for (String key : oldPrefs.getAll().keySet()) {
                Object value = oldPrefs.getAll().get(key);
                
                if (value instanceof String) {
                    editor.putString(key, (String) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Float) {
                    editor.putFloat(key, (Float) value);
                } else if (value instanceof Long) {
                    editor.putLong(key, (Long) value);
                }
            }
            
            // Mark migration as complete
            editor.putBoolean("migration_complete", true);
            editor.apply();
            
            // Clear old preferences
            oldPrefs.edit().clear().apply();
            
            Log.d(TAG, "✅ Successfully migrated preferences to encrypted storage");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to migrate preferences: " + e.getMessage(), e);
        }
    }
    
    /**
     * Clear all encrypted preferences
     * Use with caution - this will delete all stored data
     */
    public static void clearAll(Context context, String prefsName) {
        SharedPreferences prefs = getEncryptedPreferences(context, prefsName);
        prefs.edit().clear().apply();
        Log.d(TAG, "Cleared all encrypted preferences: " + prefsName);
    }
}
