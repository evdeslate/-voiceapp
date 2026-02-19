package com.example.speak;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

public class LoginPage extends AppCompatActivity {

    private static final String TAG = "LoginPage";
    private static final int RC_GOOGLE_SIGN_IN = 9001;

    // UI Components
    private EditText emailEditText;
    private EditText passwordEditText;
    private Button loginButton;
    private View googleLoginButton;
    private TextView welcomeTextView;
    private TextView sloganTextView;
    private TextView orLoginTextView;
    private TextView signUpTextView;
    private ImageView logoImageView;
    private ImageView rabbitImageView;
    private View bottomSheet;
    private BottomSheetBehavior<View> bottomSheetBehavior;

    // Firebase Authentication
    private FirebaseAuth mAuth;
    
    // Google Sign-In
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);

        Log.d(TAG, "=== LoginPage onCreate ===");
        
        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        Log.d(TAG, "Firebase Auth initialized");
        
        // Configure Google Sign-In
        configureGoogleSignIn();

        // Initialize views
        initializeViews();

        // Set up click listeners
        setupClickListeners();

        // Get user type from intent (teacher or parent)
        handleUserType();
        
        Log.d(TAG, "LoginPage initialization complete");
    }

    private void configureGoogleSignIn() {
        Log.d(TAG, "Configuring Google Sign-In");
        
        try {
            String webClientId = getString(R.string.default_web_client_id);
            Log.d(TAG, "Google Web Client ID: " + webClientId);
            
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build();

            mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
            Log.d(TAG, "Google Sign-In client configured successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error configuring Google Sign-In", e);
        }
    }

    private void initializeViews() {
        emailEditText = findViewById(R.id.editTextTextEmailAddress);
        passwordEditText = findViewById(R.id.editTextTextPassword);
        loginButton = findViewById(R.id.button);
        googleLoginButton = findViewById(R.id.googleLoginBtn);
        welcomeTextView = findViewById(R.id.textView2);
        sloganTextView = findViewById(R.id.textView3);
        orLoginTextView = findViewById(R.id.textView7);
        signUpTextView = findViewById(R.id.signUpText);
        logoImageView = findViewById(R.id.imageView2);
        rabbitImageView = findViewById(R.id.imageView3);
        bottomSheet = findViewById(R.id.bottomSheet);
        
        // Setup bottom sheet behavior
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        bottomSheetBehavior.setPeekHeight(50);
    }

    private void setupClickListeners() {
        Log.d(TAG, "Setting up click listeners");
        
        loginButton.setOnClickListener(v -> {
            Log.d(TAG, "Login button clicked");
            handleLogin();
        });
        
        googleLoginButton.setOnClickListener(v -> {
            Log.d(TAG, "Google login button clicked");
            handleGoogleLogin();
        });
        
        signUpTextView.setOnClickListener(v -> {
            Log.d(TAG, "Sign up text clicked");
            handleSignUp();
        });
    }

    private void handleUserType() {
        String userType = getIntent().getStringExtra("user_type");
        if (userType != null) {
            if (userType.equals("teacher")) {
                welcomeTextView.setText("Welcome Back Teacher!\nGlad to see you again.");
            } else if (userType.equals("parent")) {
                welcomeTextView.setText("Welcome Back Parent!\nGlad to see you again.");
            }
        }
    }

    private void handleLogin() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            emailEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            passwordEditText.requestFocus();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Please enter a valid email");
            emailEditText.requestFocus();
            return;
        }

        loginButton.setEnabled(false);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "signInWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            Toast.makeText(LoginPage.this, "Login successful!",
                                    Toast.LENGTH_SHORT).show();
                            navigateToMainActivity(user);
                        } else {
                            Log.w(TAG, "signInWithEmail:failure", task.getException());
                            String errorMessage = task.getException() != null ? 
                                    task.getException().getMessage() : "Authentication failed";
                            Toast.makeText(LoginPage.this, errorMessage,
                                    Toast.LENGTH_LONG).show();
                            loginButton.setEnabled(true);
                        }
                    }
                });
    }

    private void handleGoogleLogin() {
        // Revoke access to force account picker (faster than signOut)
        mGoogleSignInClient.revokeAccess().addOnCompleteListener(this, task -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "=== onActivityResult ===");
        Log.d(TAG, "Request Code: " + requestCode);
        Log.d(TAG, "Result Code: " + resultCode + " (RESULT_OK=" + RESULT_OK + ", RESULT_CANCELED=" + RESULT_CANCELED + ")");

        if (requestCode == RC_GOOGLE_SIGN_IN) {
            Log.d(TAG, "Processing Google Sign-In result");
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d(TAG, "Google sign in success: " + account.getId());
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Log.e(TAG, "Google sign in failed with code: " + e.getStatusCode());
                Log.e(TAG, "Error message: " + e.getMessage());
                Toast.makeText(this, "Google sign in failed: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "signInWithCredential:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            Toast.makeText(LoginPage.this, "Google sign in successful!",
                                    Toast.LENGTH_SHORT).show();
                            navigateToMainActivity(user);
                        } else {
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            Toast.makeText(LoginPage.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void handleSignUp() {
        Intent intent = new Intent(LoginPage.this, SignUpActivity.class);
        startActivity(intent);
    }

    private void navigateToMainActivity(FirebaseUser user) {
        if (user == null) {
            return;
        }

        String userType = getIntent().getStringExtra("user_type");
        
        // Save user role to Firebase and cache it
        if (userType != null) {
            UserRole.saveUserRole(user.getUid(), userType, new UserRole.OnRoleSavedListener() {
                @Override
                public void onSuccess() {
                    UserRole.cacheUserRole(LoginPage.this, userType);
                    proceedToAppropriateActivity(user, userType);
                }
                
                @Override
                public void onFailure(String error) {
                    android.util.Log.e(TAG, "Failed to save user role: " + error);
                    // Still proceed but log the error
                    UserRole.cacheUserRole(LoginPage.this, userType);
                    proceedToAppropriateActivity(user, userType);
                }
            });
        } else {
            // If no user type in intent, check Firebase
            UserRole.getUserRole(user.getUid(), new UserRole.OnRoleLoadedListener() {
                @Override
                public void onRoleLoaded(String role) {
                    UserRole.cacheUserRole(LoginPage.this, role);
                    proceedToAppropriateActivity(user, role);
                }
                
                @Override
                public void onRoleNotFound() {
                    Toast.makeText(LoginPage.this, "User role not found. Please contact support.", Toast.LENGTH_LONG).show();
                    finish();
                }
                
                @Override
                public void onError(String error) {
                    Toast.makeText(LoginPage.this, "Error loading user role: " + error, Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        }
    }
    
    private void proceedToAppropriateActivity(FirebaseUser user, String userType) {
        if (userType != null && userType.equals("teacher")) {
            Intent intent = new Intent(LoginPage.this, TeacherDashboard.class);
            intent.putExtra("user_type", userType);
            intent.putExtra("teacher_name", user.getDisplayName() != null ? user.getDisplayName() : extractNameFromEmail(user.getEmail()));
            intent.putExtra("user_email", user.getEmail());
            startActivity(intent);
        } else {
            Intent intent = new Intent(LoginPage.this, MainActivity.class);
            intent.putExtra("user_type", userType);
            intent.putExtra("user_name", user.getDisplayName());
            intent.putExtra("user_email", user.getEmail());
            startActivity(intent);
        }
        finish();
    }

    private String extractNameFromEmail(String email) {
        if (email != null && email.contains("@")) {
            String name = email.substring(0, email.indexOf("@"));
            if (name.length() > 0) {
                return name.substring(0, 1).toUpperCase() + name.substring(1);
            }
        }
        return "Teacher";
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            navigateToMainActivity(currentUser);
        }
    }
}
