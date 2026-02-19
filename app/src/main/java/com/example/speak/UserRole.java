package com.example.speak;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Utility class for managing user roles (Teacher or Parent)
 * Stores roles in Firebase and enforces role-based access control
 */
public class UserRole {
    
    public static final String ROLE_TEACHER = "teacher";
    public static final String ROLE_PARENT = "parent";
    
    private static final String PREFS_NAME = "UserRolePrefs";
    private static final String KEY_USER_ROLE = "user_role";
    
    /**
     * Save user role to Firebase when they sign up or first login
     */
    public static void saveUserRole(String userId, String role, OnRoleSavedListener listener) {
        DatabaseReference userRoleRef = FirebaseDatabase.getInstance()
            .getReference("user_roles")
            .child(userId);
        
        userRoleRef.setValue(role)
            .addOnSuccessListener(aVoid -> {
                if (listener != null) {
                    listener.onSuccess();
                }
            })
            .addOnFailureListener(e -> {
                if (listener != null) {
                    listener.onFailure(e.getMessage());
                }
            });
    }
    
    /**
     * Get user role from Firebase
     */
    public static void getUserRole(String userId, OnRoleLoadedListener listener) {
        DatabaseReference userRoleRef = FirebaseDatabase.getInstance()
            .getReference("user_roles")
            .child(userId);
        
        userRoleRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String role = dataSnapshot.getValue(String.class);
                if (role != null) {
                    listener.onRoleLoaded(role);
                } else {
                    listener.onRoleNotFound();
                }
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                listener.onError(databaseError.getMessage());
            }
        });
    }
    
    /**
     * Cache user role locally for quick access
     */
    public static void cacheUserRole(Context context, String role) {
        SharedPreferences prefs = SecurePreferences.getEncryptedPreferences(context, PREFS_NAME);
        prefs.edit().putString(KEY_USER_ROLE, role).apply();
    }
    
    /**
     * Get cached user role
     */
    public static String getCachedUserRole(Context context) {
        SharedPreferences prefs = SecurePreferences.getEncryptedPreferences(context, PREFS_NAME);
        return prefs.getString(KEY_USER_ROLE, null);
    }
    
    /**
     * Clear cached user role (on logout)
     */
    public static void clearCachedUserRole(Context context) {
        SharedPreferences prefs = SecurePreferences.getEncryptedPreferences(context, PREFS_NAME);
        prefs.edit().remove(KEY_USER_ROLE).apply();
    }
    
    /**
     * Check if current user is a teacher
     */
    public static boolean isTeacher(Context context) {
        String role = getCachedUserRole(context);
        return ROLE_TEACHER.equals(role);
    }
    
    /**
     * Check if current user is a parent
     */
    public static boolean isParent(Context context) {
        String role = getCachedUserRole(context);
        return ROLE_PARENT.equals(role);
    }
    
    /**
     * Verify user has the required role, redirect if not
     */
    public static void verifyRole(Context context, String requiredRole, OnRoleVerifiedListener listener) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        
        if (currentUser == null) {
            listener.onUnauthorized("User not authenticated");
            return;
        }
        
        // Check cached role first
        String cachedRole = getCachedUserRole(context);
        if (cachedRole != null) {
            if (cachedRole.equals(requiredRole)) {
                listener.onAuthorized();
            } else {
                listener.onUnauthorized("Access denied: Insufficient permissions");
            }
            return;
        }
        
        // If not cached, fetch from Firebase
        getUserRole(currentUser.getUid(), new OnRoleLoadedListener() {
            @Override
            public void onRoleLoaded(String role) {
                cacheUserRole(context, role);
                if (role.equals(requiredRole)) {
                    listener.onAuthorized();
                } else {
                    listener.onUnauthorized("Access denied: Insufficient permissions");
                }
            }
            
            @Override
            public void onRoleNotFound() {
                listener.onUnauthorized("User role not found");
            }
            
            @Override
            public void onError(String error) {
                listener.onUnauthorized("Error verifying role: " + error);
            }
        });
    }
    
    // Callback interfaces
    public interface OnRoleSavedListener {
        void onSuccess();
        void onFailure(String error);
    }
    
    public interface OnRoleLoadedListener {
        void onRoleLoaded(String role);
        void onRoleNotFound();
        void onError(String error);
    }
    
    public interface OnRoleVerifiedListener {
        void onAuthorized();
        void onUnauthorized(String reason);
    }
}
