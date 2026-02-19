package com.example.speak;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

/**
 * SignUpActivity handles user registration with Firebase Authentication
 * Supports Email/Password and Google Sign-In with Bottom Sheet UI
 */
public class SignUpActivity extends AppCompatActivity {

    private static final String TAG = "SignUpActivity";
    private static final int RC_GOOGLE_SIGN_IN = 9001;

    // UI Components
    private EditText etFullName;
    private EditText etEmail;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private Button btnRegister;
    private View btnGoogleSignUp;
    private TextView tvLoginNow;
    private View bottomSheet;
    private BottomSheetBehavior<View> bottomSheetBehavior;

    // Firebase Authentication
    private FirebaseAuth mAuth;
    
    // Google Sign-In
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        
        // Configure Google Sign-In
        configureGoogleSignIn();

        // Initialize views
        initializeViews();

        // Setup listeners
        setupListeners();
    }

    /**
     * Configures Google Sign-In options
     */
    private void configureGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    /**
     * Initializes all view references
     */
    private void initializeViews() {
        etFullName = findViewById(R.id.editTextUsername);
        etEmail = findViewById(R.id.editTextTextEmailAddress);
        etPassword = findViewById(R.id.editTextTextPassword);
        etConfirmPassword = findViewById(R.id.editTextConfirmPassword);
        btnRegister = findViewById(R.id.button);
        btnGoogleSignUp = findViewById(R.id.googleSignUpBtn);
        tvLoginNow = findViewById(R.id.loginText);
        bottomSheet = findViewById(R.id.bottomSheet);
        
        // Setup bottom sheet behavior
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        bottomSheetBehavior.setPeekHeight(50);
    }

    /**
     * Sets up click listeners for all interactive elements
     */
    private void setupListeners() {
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleRegister();
            }
        });

        tvLoginNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToLogin();
            }
        });

        btnGoogleSignUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleGoogleSignUp();
            }
        });
    }

    /**
     * Handles email/password registration with Firebase
     */
    private void handleRegister() {
        String fullName = etFullName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (!validateInputs(fullName, email, password, confirmPassword)) {
            return;
        }

        btnRegister.setEnabled(false);

        // Create user with Firebase
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "createUserWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            
                            // Update user profile with full name
                            if (user != null) {
                                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                        .setDisplayName(fullName)
                                        .build();

                                user.updateProfile(profileUpdates)
                                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                                            @Override
                                            public void onComplete(@NonNull Task<Void> task) {
                                                if (task.isSuccessful()) {
                                                    Log.d(TAG, "User profile updated with name: " + fullName);
                                                }
                                            }
                                        });
                            }

                            Toast.makeText(SignUpActivity.this, "Registration successful!",
                                    Toast.LENGTH_SHORT).show();
                            navigateToLogin();
                        } else {
                            Log.w(TAG, "createUserWithEmail:failure", task.getException());
                            String errorMessage = task.getException() != null ? 
                                    task.getException().getMessage() : "Registration failed";
                            Toast.makeText(SignUpActivity.this, errorMessage,
                                    Toast.LENGTH_LONG).show();
                            btnRegister.setEnabled(true);
                        }
                    }
                });
    }

    /**
     * Handles Google Sign-In
     */
    private void handleGoogleSignUp() {
        // Revoke access to force account picker (faster than signOut)
        mGoogleSignInClient.revokeAccess().addOnCompleteListener(this, task -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Google Sign-In result
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.getId());
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Log.w(TAG, "Google sign in failed", e);
                Toast.makeText(this, "Google sign in failed: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Authenticates with Firebase using Google credentials
     */
    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "signInWithCredential:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            Toast.makeText(SignUpActivity.this, "Google sign in successful!",
                                    Toast.LENGTH_SHORT).show();
                            navigateToMain(user);
                        } else {
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            Toast.makeText(SignUpActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    /**
     * Validates all input fields
     */
    private boolean validateInputs(String fullName, String email, String password, String confirmPassword) {
        if (TextUtils.isEmpty(fullName)) {
            etFullName.setError("Full name is required");
            etFullName.requestFocus();
            return false;
        }

        if (fullName.length() < 3) {
            etFullName.setError("Full name must be at least 3 characters");
            etFullName.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            return false;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Please enter a valid email");
            etEmail.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return false;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            etConfirmPassword.setError("Please confirm your password");
            etConfirmPassword.requestFocus();
            return false;
        }

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return false;
        }

        return true;
    }

    /**
     * Navigates to the login page
     */
    private void navigateToLogin() {
        Intent intent = new Intent(SignUpActivity.this, LoginPage.class);
        startActivity(intent);
        finish();
    }

    /**
     * Navigates to main dashboard after successful authentication
     */
    private void navigateToMain(FirebaseUser user) {
        if (user != null) {
            // Default to teacher role for sign up (can be customized)
            String userRole = UserRole.ROLE_TEACHER;
            
            // Save user role to Firebase
            UserRole.saveUserRole(user.getUid(), userRole, new UserRole.OnRoleSavedListener() {
                @Override
                public void onSuccess() {
                    UserRole.cacheUserRole(SignUpActivity.this, userRole);
                    proceedToDashboard(user, userRole);
                }
                
                @Override
                public void onFailure(String error) {
                    android.util.Log.e(TAG, "Failed to save user role: " + error);
                    // Still proceed but log the error
                    UserRole.cacheUserRole(SignUpActivity.this, userRole);
                    proceedToDashboard(user, userRole);
                }
            });
        }
    }
    
    private void proceedToDashboard(FirebaseUser user, String userRole) {
        Intent intent = new Intent(SignUpActivity.this, TeacherDashboard.class);
        intent.putExtra("user_type", userRole);
        intent.putExtra("teacher_name", user.getDisplayName() != null ? user.getDisplayName() : "Teacher");
        intent.putExtra("user_email", user.getEmail());
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        navigateToLogin();
    }
}
