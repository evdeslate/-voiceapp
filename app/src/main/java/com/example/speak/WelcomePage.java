package com.example.speak;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class WelcomePage extends AppCompatActivity {

    private static final String TAG = "WelcomePage";
    
    private ImageButton teacherButton;
    private ImageButton parentButton;
    private TextView titleTextView;
    private TextView sloganTextView;
    private TextView loginAsTextView;
    private TextView teacherLabelTextView;
    private TextView parentLabelTextView;
    private ImageView logoImageView;
    private TextView errorTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.welcome);

        // Initialize views
        initializeViews();

        // Set up click listeners
        setupClickListeners();
    }

    private void initializeViews() {
        try {
            // Initialize ImageButtons
            teacherButton = findViewById(R.id.imageButton);
            parentButton = findViewById(R.id.imagebutton2);

            // Initialize TextViews
            titleTextView = findViewById(R.id.textView2);
            sloganTextView = findViewById(R.id.textView3);
            loginAsTextView = findViewById(R.id.textView4);
            teacherLabelTextView = findViewById(R.id.textView5);
            parentLabelTextView = findViewById(R.id.textView6);
            errorTextView = findViewById(R.id.errorText);

            // Initialize ImageView
            logoImageView = findViewById(R.id.imageView);
            
            // Verify critical views are not null
            if (teacherButton == null || parentButton == null) {
                android.util.Log.e(TAG, "Critical views not found in layout");
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error initializing views: " + e.getMessage());
        }
    }

    private void setupClickListeners() {
        // Teacher button click listener with null check
        if (teacherButton != null) {
            teacherButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleTeacherLogin();
                }
            });
        } else {
            android.util.Log.e(TAG, "Teacher button is null - cannot set click listener");
        }

        // Parent button click listener with null check
        if (parentButton != null) {
            parentButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleParentLogin();
                }
            });
        } else {
            android.util.Log.e(TAG, "Parent button is null - cannot set click listener");
        }
    }

    private void handleTeacherLogin() {
        android.util.Log.d(TAG, "Teacher button clicked");
        clearErrorMessage();
        
        // Check if user is already logged in
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        
        if (currentUser != null) {
            android.util.Log.d(TAG, "User already logged in: " + currentUser.getEmail());
            
            // Get cached role
            String cachedRole = UserRole.getCachedUserRole(this);
            android.util.Log.d(TAG, "Cached role: " + cachedRole);
            
            if (cachedRole != null) {
                // Check if cached role matches selected role
                if (cachedRole.equals(UserRole.ROLE_TEACHER)) {
                    android.util.Log.d(TAG, "Role matches - navigating to Teacher Dashboard");
                    navigateToTeacherDashboard(currentUser);
                    return;
                } else {
                    android.util.Log.d(TAG, "Role mismatch - showing error");
                    showErrorMessage("You are logged in as a parent. Please select 'Login as Parent' or logout first.");
                    return;
                }
            } else {
                android.util.Log.d(TAG, "No cached role - fetching from Firebase");
                // No cached role, fetch from Firebase
                UserRole.getUserRole(currentUser.getUid(), new UserRole.OnRoleLoadedListener() {
                    @Override
                    public void onRoleLoaded(String role) {
                        android.util.Log.d(TAG, "Role loaded from Firebase: " + role);
                        UserRole.cacheUserRole(WelcomePage.this, role);
                        
                        if (role.equals(UserRole.ROLE_TEACHER)) {
                            navigateToTeacherDashboard(currentUser);
                        } else {
                            showErrorMessage("You are logged in as a parent. Please select 'Login as Parent' or logout first.");
                        }
                    }
                    
                    @Override
                    public void onRoleNotFound() {
                        android.util.Log.d(TAG, "Role not found - proceeding to login");
                        // Role not found, proceed to login page
                        navigateToLoginPage(UserRole.ROLE_TEACHER);
                    }
                    
                    @Override
                    public void onError(String error) {
                        android.util.Log.e(TAG, "Error loading role: " + error);
                        // Error loading role, proceed to login page
                        navigateToLoginPage(UserRole.ROLE_TEACHER);
                    }
                });
                return;
            }
        }
        
        // User not logged in, proceed to login page
        android.util.Log.d(TAG, "User not logged in - navigating to login page");
        navigateToLoginPage(UserRole.ROLE_TEACHER);
    }

    private void handleParentLogin() {
        android.util.Log.d(TAG, "Parent button clicked");
        clearErrorMessage();
        
        // Check if user is already logged in
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        
        if (currentUser != null) {
            android.util.Log.d(TAG, "User already logged in: " + currentUser.getEmail());
            
            // Get cached role
            String cachedRole = UserRole.getCachedUserRole(this);
            android.util.Log.d(TAG, "Cached role: " + cachedRole);
            
            if (cachedRole != null) {
                // Check if cached role matches selected role
                if (cachedRole.equals(UserRole.ROLE_PARENT)) {
                    android.util.Log.d(TAG, "Role matches - navigating to Parent Dashboard");
                    navigateToParentDashboard(currentUser);
                    return;
                } else {
                    android.util.Log.d(TAG, "Role mismatch - showing error");
                    showErrorMessage("You are logged in as a teacher. Please select 'Login as Teacher' or logout first.");
                    return;
                }
            } else {
                android.util.Log.d(TAG, "No cached role - fetching from Firebase");
                // No cached role, fetch from Firebase
                UserRole.getUserRole(currentUser.getUid(), new UserRole.OnRoleLoadedListener() {
                    @Override
                    public void onRoleLoaded(String role) {
                        android.util.Log.d(TAG, "Role loaded from Firebase: " + role);
                        UserRole.cacheUserRole(WelcomePage.this, role);
                        
                        if (role.equals(UserRole.ROLE_PARENT)) {
                            navigateToParentDashboard(currentUser);
                        } else {
                            showErrorMessage("You are logged in as a teacher. Please select 'Login as Teacher' or logout first.");
                        }
                    }
                    
                    @Override
                    public void onRoleNotFound() {
                        android.util.Log.d(TAG, "Role not found - proceeding to login");
                        // Role not found, proceed to login page
                        navigateToLoginPage(UserRole.ROLE_PARENT);
                    }
                    
                    @Override
                    public void onError(String error) {
                        android.util.Log.e(TAG, "Error loading role: " + error);
                        // Error loading role, proceed to login page
                        navigateToLoginPage(UserRole.ROLE_PARENT);
                    }
                });
                return;
            }
        }
        
        // User not logged in, proceed to login page
        android.util.Log.d(TAG, "User not logged in - navigating to login page");
        navigateToLoginPage(UserRole.ROLE_PARENT);
    }
    
    private void navigateToLoginPage(String userType) {
        Intent intent = new Intent(WelcomePage.this, LoginPage.class);
        intent.putExtra("user_type", userType);
        startActivity(intent);
    }
    
    private void navigateToTeacherDashboard(com.google.firebase.auth.FirebaseUser user) {
        Intent intent = new Intent(WelcomePage.this, TeacherDashboard.class);
        intent.putExtra("user_type", UserRole.ROLE_TEACHER);
        intent.putExtra("teacher_name", user.getDisplayName() != null ? user.getDisplayName() : extractNameFromEmail(user.getEmail()));
        intent.putExtra("user_email", user.getEmail());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void navigateToParentDashboard(com.google.firebase.auth.FirebaseUser user) {
        Intent intent = new Intent(WelcomePage.this, MainActivity.class);
        intent.putExtra("user_type", UserRole.ROLE_PARENT);
        intent.putExtra("user_name", user.getDisplayName());
        intent.putExtra("user_email", user.getEmail());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private String extractNameFromEmail(String email) {
        if (email != null && email.contains("@")) {
            String name = email.substring(0, email.indexOf("@"));
            if (name.length() > 0) {
                return name.substring(0, 1).toUpperCase() + name.substring(1);
            }
        }
        return "User";
    }
    
    private void showErrorMessage(String message) {
        if (errorTextView != null) {
            errorTextView.setText(message);
            errorTextView.setVisibility(View.VISIBLE);
        } else {
            // Fallback to Toast if TextView not found
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
        }
    }
    
    private void clearErrorMessage() {
        if (errorTextView != null) {
            errorTextView.setVisibility(View.GONE);
        }
    }
}
