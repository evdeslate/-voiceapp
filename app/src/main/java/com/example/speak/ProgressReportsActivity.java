package com.example.speak;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Activity for displaying progress reports for all students
 * Shows reading session history and performance metrics
 */
public class ProgressReportsActivity extends AppCompatActivity {
    
    private RecyclerView progressRecyclerView;
    private ProgressReportAdapter progressAdapter;
    private List<StudentProgressReport> progressReports;
    private ImageButton btnBack;
    private ProgressBar loadingProgress;
    private TextView emptyStateText;
    
    private ReadingSessionRepository sessionRepository;
    private StudentRepository studentRepository;
    
    private boolean isLoading = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Verify user has teacher role
        verifyTeacherRole();
    }
    
    private void verifyTeacherRole() {
        UserRole.verifyRole(this, UserRole.ROLE_TEACHER, new UserRole.OnRoleVerifiedListener() {
            @Override
            public void onAuthorized() {
                initializeActivity();
            }
            
            @Override
            public void onUnauthorized(String reason) {
                Toast.makeText(ProgressReportsActivity.this, 
                    "Access Denied: Teachers only", 
                    Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }
    
    private void initializeActivity() {
        setContentView(R.layout.activity_progress_reports);
        
        initializeViews();
        setupRecyclerView();
        loadProgressReports();
    }
    
    private void initializeViews() {
        progressRecyclerView = findViewById(R.id.progressRecyclerView);
        btnBack = findViewById(R.id.btnBack);
        loadingProgress = findViewById(R.id.loadingProgress);
        emptyStateText = findViewById(R.id.emptyStateText);
        
        btnBack.setOnClickListener(v -> finish());
        
        sessionRepository = new ReadingSessionRepository();
        studentRepository = new StudentRepository();
    }
    
    private void setupRecyclerView() {
        progressReports = new ArrayList<>();
        progressAdapter = new ProgressReportAdapter(progressReports, this);
        
        progressRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        progressRecyclerView.setAdapter(progressAdapter);
    }
    
    private void loadProgressReports() {
        if (isLoading) {
            android.util.Log.d("ProgressReports", "Already loading, skipping duplicate load");
            return;
        }
        
        isLoading = true;
        showLoading(true);
        
        // Load all students first
        studentRepository.loadAllStudents(new StudentRepository.OnStudentsLoadedListener() {
            @Override
            public void onSuccess(List<Student> students) {
                if (students.isEmpty()) {
                    isLoading = false;
                    showLoading(false);
                    showEmptyState(true);
                    return;
                }
                
                // Load sessions for each student
                loadSessionsForStudents(students);
            }
            
            @Override
            public void onFailure(String error) {
                isLoading = false;
                showLoading(false);
                Toast.makeText(ProgressReportsActivity.this, 
                    "Error loading students: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void loadSessionsForStudents(List<Student> students) {
        final int[] loadedCount = {0};
        final int totalStudents = students.size();
        
        for (Student student : students) {
            sessionRepository.loadStudentSessions(student.getId(), 
                new ReadingSessionRepository.OnSessionsLoadedListener() {
                @Override
                public void onSuccess(List<ReadingSession> sessions) {
                    // Create progress report for this student
                    StudentProgressReport report = new StudentProgressReport(student, sessions);
                    progressReports.add(report);
                    
                    loadedCount[0]++;
                    
                    // Check if all students loaded
                    if (loadedCount[0] == totalStudents) {
                        isLoading = false;
                        showLoading(false);
                        
                        if (progressReports.isEmpty()) {
                            showEmptyState(true);
                        } else {
                            showEmptyState(false);
                            // Sort by student name
                            progressReports.sort((r1, r2) -> 
                                r1.getStudent().getName().compareTo(r2.getStudent().getName()));
                            progressAdapter.notifyDataSetChanged();
                        }
                    }
                }
                
                @Override
                public void onFailure(String error) {
                    android.util.Log.e("ProgressReports", 
                        "Error loading sessions for " + student.getName() + ": " + error);
                    
                    // Still create report with no sessions
                    StudentProgressReport report = new StudentProgressReport(student, new ArrayList<>());
                    progressReports.add(report);
                    
                    loadedCount[0]++;
                    
                    if (loadedCount[0] == totalStudents) {
                        isLoading = false;
                        showLoading(false);
                        showEmptyState(progressReports.isEmpty());
                        progressAdapter.notifyDataSetChanged();
                    }
                }
            });
        }
    }
    
    private void showLoading(boolean show) {
        loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        progressRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }
    
    private void showEmptyState(boolean show) {
        emptyStateText.setVisibility(show ? View.VISIBLE : View.GONE);
        progressRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Reload data when returning to this activity
        progressReports.clear();
        progressAdapter.notifyDataSetChanged();
        loadProgressReports();
    }
    
    /**
     * Data class to hold student progress report information
     */
    public static class StudentProgressReport {
        private Student student;
        private List<ReadingSession> sessions;
        private float averageAccuracy;
        private float averagePronunciation;
        private float averageComprehension;
        private float overallAverage;
        private int totalSessions;
        private ReadingSession latestSession;
        
        public StudentProgressReport(Student student, List<ReadingSession> sessions) {
            this.student = student;
            this.sessions = sessions;
            this.totalSessions = sessions.size();
            
            calculateAverages();
            
            if (!sessions.isEmpty()) {
                latestSession = sessions.get(0); // Already sorted by timestamp
            }
        }
        
        private void calculateAverages() {
            if (sessions.isEmpty()) {
                averageAccuracy = 0;
                averagePronunciation = 0;
                averageComprehension = 0;
                overallAverage = 0;
                return;
            }
            
            float totalAccuracy = 0;
            float totalPronunciation = 0;
            float totalComprehension = 0;
            float totalOverall = 0;
            int validSessionCount = 0;
            
            for (ReadingSession session : sessions) {
                // Log session scores for debugging
                android.util.Log.d("ProgressReports", String.format(
                    "Session for %s: Accuracy=%.2f, Pronunciation=%.2f, Comprehension=%.2f",
                    student.getName(), session.getAccuracy(), session.getPronunciation(), session.getComprehension()));
                
                totalAccuracy += session.getAccuracy();
                totalPronunciation += session.getPronunciation();
                totalComprehension += session.getComprehension();
                totalOverall += session.getOverallScore();
                validSessionCount++;
            }
            
            if (validSessionCount > 0) {
                averageAccuracy = totalAccuracy / validSessionCount;
                averagePronunciation = totalPronunciation / validSessionCount;
                averageComprehension = totalComprehension / validSessionCount;
                overallAverage = totalOverall / validSessionCount;
                
                android.util.Log.d("ProgressReports", String.format(
                    "Averages for %s: Accuracy=%.2f%%, Pronunciation=%.2f%%, Comprehension=%.2f%%, Overall=%.2f%%",
                    student.getName(), averageAccuracy * 100, averagePronunciation * 100, 
                    averageComprehension * 100, overallAverage * 100));
            } else {
                averageAccuracy = 0;
                averagePronunciation = 0;
                averageComprehension = 0;
                overallAverage = 0;
            }
        }
        
        // Getters
        public Student getStudent() { return student; }
        public List<ReadingSession> getSessions() { return sessions; }
        public float getAverageAccuracy() { return averageAccuracy; }
        public float getAveragePronunciation() { return averagePronunciation; }
        public float getAverageComprehension() { return averageComprehension; }
        public float getOverallAverage() { return overallAverage; }
        public int getTotalSessions() { return totalSessions; }
        public ReadingSession getLatestSession() { return latestSession; }
        
        public String getAverageAccuracyPercent() {
            return String.format("%.0f%%", averageAccuracy * 100);
        }
        
        public String getAveragePronunciationPercent() {
            return String.format("%.0f%%", averagePronunciation * 100);
        }
        
        public String getAverageComprehensionPercent() {
            return String.format("%.0f%%", averageComprehension * 100);
        }
        
        public String getOverallAveragePercent() {
            return String.format("%.0f%%", overallAverage * 100);
        }
    }
}
