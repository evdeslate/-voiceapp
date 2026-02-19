package com.example.speak;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Parent Dashboard - Allows parents to search for their child and view progress
 * Saves child information so parents don't need to re-enter it
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "ParentPrefs";
    private static final String KEY_CHILD_NAME = "child_name";
    private static final String KEY_CHILD_GRADE = "child_grade";
    private static final String KEY_CHILD_SECTION = "child_section";
    private static final String KEY_TEACHER_ID = "teacher_id";

    // Search Form
    private CardView searchCard;
    private TextInputEditText etChildName;
    private TextInputEditText etGrade;
    private TextInputEditText etSection;
    private Button btnSearch;
    
    // Header
    private TextView logoutLink;
    
    // Progress Display
    private CardView progressCard;
    private ImageView studentAvatar;
    private TextView tvStudentName;
    private TextView tvStudentGrade;
    private TextView tvProgressPercent;
    private TextView tvReadingLevel;
    private TextView tvTotalSessions;
    private TextView tvAvgAccuracy;
    private TextView tvAvgPronunciation;
    private Button btnViewDetails;
    private Button btnSwitchChild;
    
    // Loading and Error
    private ProgressBar progressBar;
    private TextView tvError;
    
    // Data
    private Student foundStudent;
    private List<ReadingSession> studentSessions;
    private String currentTeacherId;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefs = SecurePreferences.getEncryptedPreferences(this, PREFS_NAME);
        
        initializeViews();
        setupListeners();
        
        // Check if child info is already saved
        checkSavedChildInfo();
    }

    private void initializeViews() {
        // Header
        logoutLink = findViewById(R.id.logoutLink);
        
        // Search form
        searchCard = findViewById(R.id.searchCard);
        etChildName = findViewById(R.id.etChildName);
        etGrade = findViewById(R.id.etGrade);
        etSection = findViewById(R.id.etSection);
        btnSearch = findViewById(R.id.btnSearch);
        
        // Progress display
        progressCard = findViewById(R.id.progressCard);
        studentAvatar = findViewById(R.id.studentAvatar);
        tvStudentName = findViewById(R.id.tvStudentName);
        tvStudentGrade = findViewById(R.id.tvStudentGrade);
        tvProgressPercent = findViewById(R.id.tvProgressPercent);
        tvReadingLevel = findViewById(R.id.tvReadingLevel);
        tvTotalSessions = findViewById(R.id.tvTotalSessions);
        tvAvgAccuracy = findViewById(R.id.tvAvgAccuracy);
        tvAvgPronunciation = findViewById(R.id.tvAvgPronunciation);
        btnViewDetails = findViewById(R.id.btnViewDetails);
        btnSwitchChild = findViewById(R.id.btnSwitchChild);
        
        // Loading and error
        progressBar = findViewById(R.id.progressBar);
        tvError = findViewById(R.id.tvError);
    }

    private void setupListeners() {
        btnSearch.setOnClickListener(v -> searchForStudent());
        
        btnViewDetails.setOnClickListener(v -> {
            if (foundStudent != null) {
                navigateToStudentDetail();
            }
        });
        
        btnSwitchChild.setOnClickListener(v -> switchChild());
        
        logoutLink.setOnClickListener(v -> showLogoutConfirmation());
    }

    /**
     * Check if child information is already saved
     * If yes, automatically load their progress
     */
    private void checkSavedChildInfo() {
        String savedName = prefs.getString(KEY_CHILD_NAME, null);
        String savedGrade = prefs.getString(KEY_CHILD_GRADE, null);
        String savedSection = prefs.getString(KEY_CHILD_SECTION, null);
        currentTeacherId = prefs.getString(KEY_TEACHER_ID, null);
        
        if (savedName != null && savedGrade != null && savedSection != null) {
            // Child info exists, hide search form and load progress
            searchCard.setVisibility(View.GONE);
            showLoading(true);
            
            // Auto-search for the saved child
            searchForStudent(savedName, savedGrade, savedSection);
        } else {
            // No saved info, show search form
            searchCard.setVisibility(View.VISIBLE);
            progressCard.setVisibility(View.GONE);
        }
    }

    /**
     * Switch to a different child (for parents with multiple children)
     */
    private void switchChild() {
        // Clear saved info
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_CHILD_NAME);
        editor.remove(KEY_CHILD_GRADE);
        editor.remove(KEY_CHILD_SECTION);
        editor.remove(KEY_TEACHER_ID);
        editor.apply();
        
        // Reset UI
        foundStudent = null;
        studentSessions = null;
        currentTeacherId = null;
        
        // Clear form
        etChildName.setText("");
        etGrade.setText("");
        etSection.setText("");
        
        // Show search form, hide progress
        searchCard.setVisibility(View.VISIBLE);
        progressCard.setVisibility(View.GONE);
        hideError();
        
        Toast.makeText(this, "Enter another child's information", Toast.LENGTH_SHORT).show();
    }

    private void searchForStudent() {
        String childName = etChildName.getText().toString().trim();
        String grade = etGrade.getText().toString().trim();
        String section = etSection.getText().toString().trim();
        
        // Validate inputs
        if (TextUtils.isEmpty(childName)) {
            etChildName.setError("Please enter child's name");
            etChildName.requestFocus();
            return;
        }
        
        if (TextUtils.isEmpty(grade)) {
            etGrade.setError("Please enter grade");
            etGrade.requestFocus();
            return;
        }
        
        if (TextUtils.isEmpty(section)) {
            etSection.setError("Please enter section");
            etSection.requestFocus();
            return;
        }
        
        searchForStudent(childName, grade, section);
    }

    private void searchForStudent(String childName, String grade, String section) {
        // Show loading
        showLoading(true);
        hideError();
        hideProgressCard();
        
        // Search across all teachers' students
        searchStudentAcrossTeachers(childName, grade, section);
    }

    private void searchStudentAcrossTeachers(String name, String grade, String section) {
        DatabaseReference teachersRef = FirebaseDatabase.getInstance().getReference("teachers");
        
        teachersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                boolean studentFound = false;
                
                // Iterate through all teachers
                for (DataSnapshot teacherSnapshot : dataSnapshot.getChildren()) {
                    String teacherId = teacherSnapshot.getKey();
                    DataSnapshot studentsSnapshot = teacherSnapshot.child("students");
                    
                    // Search through this teacher's students
                    for (DataSnapshot studentSnapshot : studentsSnapshot.getChildren()) {
                        Student student = studentSnapshot.getValue(Student.class);
                        
                        if (student != null && matchesStudent(student, name, grade, section)) {
                            // Found matching student
                            foundStudent = student;
                            currentTeacherId = teacherId;
                            studentFound = true;
                            
                            // Save child info for future use
                            saveChildInfo(name, grade, section, teacherId);
                            
                            loadStudentSessions(teacherId, student.getId());
                            return;
                        }
                    }
                }
                
                if (!studentFound) {
                    showLoading(false);
                    showError("No student found with the provided information");
                }
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                showLoading(false);
                showError("Error searching for student: " + databaseError.getMessage());
            }
        });
    }

    private boolean matchesStudent(Student student, String name, String grade, String section) {
        // Case-insensitive matching
        String studentName = student.getName() != null ? student.getName().toLowerCase() : "";
        String studentGrade = student.getGrade() != null ? student.getGrade().toLowerCase() : "";
        String studentSection = student.getSection() != null ? student.getSection().toLowerCase() : "";
        
        return studentName.equals(name.toLowerCase()) &&
               studentGrade.equals(grade.toLowerCase()) &&
               studentSection.equals(section.toLowerCase());
    }

    private void loadStudentSessions(String teacherId, String studentId) {
        DatabaseReference sessionsRef = FirebaseDatabase.getInstance()
            .getReference("teachers")
            .child(teacherId)
            .child("reading_sessions");
        
        Query query = sessionsRef.orderByChild("studentId").equalTo(studentId);
        
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                studentSessions = new ArrayList<>();
                
                for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
                    ReadingSession session = sessionSnapshot.getValue(ReadingSession.class);
                    if (session != null) {
                        studentSessions.add(session);
                    }
                }
                
                showLoading(false);
                displayStudentProgress();
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                showLoading(false);
                showError("Error loading sessions: " + databaseError.getMessage());
            }
        });
    }

    private void displayStudentProgress() {
        if (foundStudent == null) {
            return;
        }
        
        // Show progress card
        progressCard.setVisibility(View.VISIBLE);
        
        // Set student info
        tvStudentName.setText(foundStudent.getName());
        tvStudentGrade.setText(foundStudent.getGrade() + "-" + foundStudent.getSection());
        
        // Set avatar if available, otherwise use default
        if (foundStudent.getAvatarResource() != 0) {
            studentAvatar.setImageResource(foundStudent.getAvatarResource());
        } else {
            // Use default avatar based on student name or generic
            // You can also randomly assign boy/girl avatar here
            studentAvatar.setImageResource(R.drawable.boy); // Default avatar
        }
        
        // Set progress
        int progress = foundStudent.getProgress();
        tvProgressPercent.setText(progress + "%");
        tvReadingLevel.setText(getReadingLevel(progress));
        
        // Set reading level color
        int levelColor = getReadingLevelColor(progress);
        tvReadingLevel.setTextColor(levelColor);
        
        // Calculate and display session stats
        if (studentSessions != null && !studentSessions.isEmpty()) {
            tvTotalSessions.setText(String.valueOf(studentSessions.size()));
            
            float totalAccuracy = 0;
            float totalPronunciation = 0;
            
            for (ReadingSession session : studentSessions) {
                totalAccuracy += session.getAccuracy();
                totalPronunciation += session.getPronunciation();
            }
            
            float avgAccuracy = totalAccuracy / studentSessions.size();
            float avgPronunciation = totalPronunciation / studentSessions.size();
            
            tvAvgAccuracy.setText(String.format("%.0f%%", avgAccuracy * 100));
            tvAvgPronunciation.setText(String.format("%.0f%%", avgPronunciation * 100));
        } else {
            tvTotalSessions.setText("0");
            tvAvgAccuracy.setText("0%");
            tvAvgPronunciation.setText("0%");
        }
    }

    private String getReadingLevel(int progress) {
        if (progress >= 90) {
            return "Independent Level";
        } else if (progress >= 75) {
            return "Instructional Level";
        } else {
            return "Frustration Level";
        }
    }

    private int getReadingLevelColor(int progress) {
        if (progress >= 90) {
            return 0xFF4CAF50; // Green
        } else if (progress >= 70) {
            return 0xFF2196F3; // Blue
        } else if (progress >= 50) {
            return 0xFFFFC107; // Amber
        } else {
            return 0xFFF44336; // Red
        }
    }

    private void navigateToStudentDetail() {
        // Show session scores dialog instead of navigating to StudentDetail
        showSessionScoresDialog();
    }
    
    /**
     * Show dialog with all reading session scores
     */
    private void showSessionScoresDialog() {
        if (studentSessions == null || studentSessions.isEmpty()) {
            Toast.makeText(this, "No reading sessions found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create dialog
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_session_scores);
        dialog.getWindow().setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        // Initialize views
        TextView dialogTitle = dialog.findViewById(R.id.dialogTitle);
        androidx.recyclerview.widget.RecyclerView sessionsRecyclerView = dialog.findViewById(R.id.sessionsRecyclerView);
        TextView emptyStateText = dialog.findViewById(R.id.emptyStateText);
        Button btnClose = dialog.findViewById(R.id.btnClose);
        
        // Set title
        dialogTitle.setText(foundStudent.getName() + "'s Reading Sessions");
        
        // Setup RecyclerView
        if (studentSessions.isEmpty()) {
            sessionsRecyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
        } else {
            sessionsRecyclerView.setVisibility(View.VISIBLE);
            emptyStateText.setVisibility(View.GONE);
            
            SessionScoreAdapter adapter = new SessionScoreAdapter(studentSessions);
            sessionsRecyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
            sessionsRecyclerView.setAdapter(adapter);
        }
        
        // Close button
        btnClose.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSearch.setEnabled(!show);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }

    private void hideProgressCard() {
        progressCard.setVisibility(View.GONE);
    }

    /**
     * Save child information to SharedPreferences
     */
    private void saveChildInfo(String name, String grade, String section, String teacherId) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_CHILD_NAME, name);
        editor.putString(KEY_CHILD_GRADE, grade);
        editor.putString(KEY_CHILD_SECTION, section);
        editor.putString(KEY_TEACHER_ID, teacherId);
        editor.apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Refresh data if child info is saved
        if (foundStudent != null && currentTeacherId != null) {
            showLoading(true);
            loadStudentSessions(currentTeacherId, foundStudent.getId());
        }
    }

    /**
     * Show logout confirmation dialog
     */
    private void showLogoutConfirmation() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes", (dialog, which) -> {
                performLogout();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Perform logout operation
     */
    private void performLogout() {
        // Sign out from Firebase
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
        
        // Clear cached user role
        UserRole.clearCachedUserRole(this);
        
        // Clear saved child info
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
        
        // Navigate to welcome page
        Intent intent = new Intent(MainActivity.this, WelcomePage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
    }
}
