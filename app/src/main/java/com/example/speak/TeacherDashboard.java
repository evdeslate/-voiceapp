package com.example.speak;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.List;

public class TeacherDashboard extends AppCompatActivity {

    // UI Components
    private TextView welcomeText;
    private TextView logoutLink;
    private ImageView speakLogo;
    
    // Action Buttons
    private ConstraintLayout addMaterialsBtn;
    private ConstraintLayout manageStudentsBtn;
    private ConstraintLayout progressReportsBtn;
    
    // Class Selection
    private ConstraintLayout classSelectionLayout;
    private TextView selectClassText;
    private ImageView dropdownIcon;
    
    // Student List
    private RecyclerView studentsRecyclerView;
    private StudentAdapter studentAdapter;
    private List<Student> studentList;
    private List<Student> filteredStudentList;
    
    // Repository
    private StudentRepository studentRepository;
    
    // Teacher info
    private String teacherName;
    private String userType;
    
    // Filter state
    private String selectedGrade = null;
    private String selectedSection = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Verify user has teacher role before allowing access
        verifyTeacherRole();
    }
    
    private void verifyTeacherRole() {
        UserRole.verifyRole(this, UserRole.ROLE_TEACHER, new UserRole.OnRoleVerifiedListener() {
            @Override
            public void onAuthorized() {
                // User is authorized, continue with initialization
                initializeActivity();
            }
            
            @Override
            public void onUnauthorized(String reason) {
                // User is not authorized, show error and redirect
                Toast.makeText(TeacherDashboard.this, 
                    "Access Denied: This area is for teachers only", 
                    Toast.LENGTH_LONG).show();
                
                // Redirect to parent dashboard
                Intent intent = new Intent(TeacherDashboard.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
    
    private void initializeActivity() {
        setContentView(R.layout.activity_teacher_dashboard);

        // Get teacher info from intent
        getTeacherInfo();
        
        // Initialize views
        initializeViews();
        
        // Setup click listeners
        setupClickListeners();
        
        // Setup student list
        setupStudentList();
        
        // Update UI with teacher info
        updateUI();
    }

    private void getTeacherInfo() {
        // Get user type and other info from intent
        userType = getIntent().getStringExtra("user_type");
        teacherName = getIntent().getStringExtra("teacher_name");
        
        // Default values if not provided
        if (teacherName == null || teacherName.isEmpty()) {
            teacherName = "Anne"; // Default teacher name
        }
    }

    private void initializeViews() {
        try {
            // Header components
            welcomeText = findViewById(R.id.welcomeText);
            logoutLink = findViewById(R.id.logoutLink);
            speakLogo = findViewById(R.id.speakLogo);
            
            // Action buttons
            addMaterialsBtn = findViewById(R.id.addMaterialsBtn);
            manageStudentsBtn = findViewById(R.id.manageStudentsBtn);
            progressReportsBtn = findViewById(R.id.progressReportsBtn);
            
            // Class selection
            classSelectionLayout = findViewById(R.id.classSelectionLayout);
            selectClassText = findViewById(R.id.selectClassText);
            dropdownIcon = findViewById(R.id.dropdownIcon);
            
            // Student list
            studentsRecyclerView = findViewById(R.id.studentsRecyclerView);
            
            // Verify critical views are not null
            if (studentsRecyclerView == null) {
                android.util.Log.e("TeacherDashboard", "StudentsRecyclerView not found in layout");
            }
        } catch (Exception e) {
            android.util.Log.e("TeacherDashboard", "Error initializing views: " + e.getMessage());
        }
    }

    private void setupClickListeners() {
        // Action button listeners with null checks
        if (addMaterialsBtn != null) {
            addMaterialsBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleAddMaterials();
                }
            });
        } else {
            android.util.Log.e("TeacherDashboard", "Add materials button is null");
        }

        if (manageStudentsBtn != null) {
            manageStudentsBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleManageStudents();
                }
            });
        } else {
            android.util.Log.e("TeacherDashboard", "Manage students button is null");
        }

        if (progressReportsBtn != null) {
            progressReportsBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleProgressReports();
                }
            });
        } else {
            android.util.Log.e("TeacherDashboard", "Progress reports button is null");
        }

        // Class selection listener with null check
        if (classSelectionLayout != null) {
            classSelectionLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleClassSelection();
                }
            });
        } else {
            android.util.Log.e("TeacherDashboard", "Class selection layout is null");
        }

        // Logout link listener
        if (logoutLink != null) {
            logoutLink.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showLogoutConfirmation();
                }
            });
        }
    }

    private void setupStudentList() {
        // Initialize student list and repository
        studentList = new ArrayList<>();
        filteredStudentList = new ArrayList<>();
        studentRepository = new StudentRepository();
        
        // Setup RecyclerView with only click listener (no action buttons for dashboard)
        studentAdapter = new StudentAdapter(filteredStudentList, new StudentAdapter.OnStudentClickListener() {
            @Override
            public void onStudentClick(Student student) {
                // Navigate to student detail when clicking on the student item
                navigateToStudentDetail(student);
            }
        });
        
        studentsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        studentsRecyclerView.setAdapter(studentAdapter);
        
        // Load students from Firebase
        loadStudentsFromFirebase();
    }

    private void loadStudentsFromFirebase() {
        android.util.Log.d("TeacherDashboard", "Loading students from Firebase (offline-enabled)...");
        
        studentRepository.loadAllStudents(new StudentRepository.OnStudentsLoadedListener() {
            @Override
            public void onSuccess(List<Student> students) {
                android.util.Log.d("TeacherDashboard", "Successfully loaded " + students.size() + " students");
                
                for (Student s : students) {
                    android.util.Log.d("TeacherDashboard", "  - " + s.getName() + " (Grade: " + s.getGrade() + ", Section: " + s.getSection() + ")");
                }
                
                studentList.clear();
                studentList.addAll(students);
                
                // Apply current filter
                applyFilter();
                
                if (students.isEmpty()) {
                    android.util.Log.d("TeacherDashboard", "No students found");
                    Toast.makeText(TeacherDashboard.this, "No students found. Add students to get started.", Toast.LENGTH_SHORT).show();
                } else {
                    // Show offline indicator if needed
                    android.util.Log.d("TeacherDashboard", "Students loaded (may be from offline cache)");
                }
            }
            
            @Override
            public void onFailure(String error) {
                android.util.Log.e("TeacherDashboard", "Error loading students: " + error);
                // Don't show error toast - Firebase offline persistence will handle it
                // Data will be loaded from cache if available
                android.util.Log.d("TeacherDashboard", "Attempting to load from offline cache...");
            }
        });
    }
    
    private void showEditStudentDialog(Student student) {
        EditStudentDialog dialog = EditStudentDialog.newInstance(student);
        dialog.setOnStudentUpdatedListener(new EditStudentDialog.OnStudentUpdatedListener() {
            @Override
            public void onStudentUpdated(Student updatedStudent) {
                // Update student in list
                for (int i = 0; i < studentList.size(); i++) {
                    if (studentList.get(i).getId().equals(updatedStudent.getId())) {
                        studentList.set(i, updatedStudent);
                        studentAdapter.notifyItemChanged(i);
                        break;
                    }
                }
                Toast.makeText(TeacherDashboard.this, "Student updated successfully", Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onError(String message) {
                Toast.makeText(TeacherDashboard.this, "Error: " + message, Toast.LENGTH_SHORT).show();
            }
        });
        
        dialog.show(getSupportFragmentManager(), "EditStudentDialog");
    }
    
    private void showDeleteConfirmation(Student student) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Student");
        builder.setMessage("Are you sure you want to delete " + student.getName() + "?");
        
        builder.setPositiveButton("Delete", (dialog, which) -> {
            deleteStudent(student);
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.dismiss();
        });
        
        builder.show();
    }
    
    private void deleteStudent(Student student) {
        studentRepository.deleteStudent(student.getId(), new StudentRepository.OnStudentDeletedListener() {
            @Override
            public void onSuccess() {
                // Remove student from list
                for (int i = 0; i < studentList.size(); i++) {
                    if (studentList.get(i).getId().equals(student.getId())) {
                        studentList.remove(i);
                        studentAdapter.notifyItemRemoved(i);
                        break;
                    }
                }
                Toast.makeText(TeacherDashboard.this, "Student deleted successfully", Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onFailure(String error) {
                Toast.makeText(TeacherDashboard.this, "Error deleting student: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void generateSampleStudents() {
        // This method is no longer needed - students are loaded from Firebase
    }

    private void updateUI() {
        // Update welcome text with teacher name
        welcomeText.setText("Hello " + teacherName + "!");
    }

    // Action button handlers
    private void handleAddMaterials() {
        // Navigate to Passage Management Activity
        Intent intent = new Intent(TeacherDashboard.this, PassageManagementActivity.class);
        startActivity(intent);
    }

    private void handleManageStudents() {
        // Navigate to Student Management Activity
        Intent intent = new Intent(TeacherDashboard.this, StudentManagementActivity.class);
        startActivity(intent);
    }

    private void handleProgressReports() {
        // Navigate to Progress Reports Activity
        Intent intent = new Intent(TeacherDashboard.this, ProgressReportsActivity.class);
        startActivity(intent);
    }

    private void handleClassSelection() {
        // Get unique grade-section combinations from student list
        List<String> gradeSectionOptions = getUniqueGradeSectionCombinations();
        
        // Build filter options
        List<String> filterOptions = new ArrayList<>();
        filterOptions.add("All Students");
        filterOptions.addAll(gradeSectionOptions);
        
        // Show dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Filter Students");
        
        String[] options = filterOptions.toArray(new String[0]);
        builder.setItems(options, (dialog, which) -> {
            String selected = options[which];
            
            if (selected.equals("All Students")) {
                selectedGrade = null;
                selectedSection = null;
                selectClassText.setText("All Students");
            } else {
                // Parse grade-section (e.g., "1-A" -> grade="1", section="A")
                String[] parts = selected.split("-");
                if (parts.length == 2) {
                    selectedGrade = parts[0];
                    selectedSection = parts[1];
                    selectClassText.setText(selected);
                }
            }
            
            applyFilter();
        });
        
        builder.show();
    }
    
    private List<String> getUniqueGradeSectionCombinations() {
        List<String> combinations = new ArrayList<>();
        
        for (Student student : studentList) {
            String grade = student.getGrade();
            String section = student.getSection();
            
            if (grade != null && !grade.isEmpty() && section != null && !section.isEmpty()) {
                // Extract just the number from grade (e.g., "Grade 1" -> "1")
                String gradeNumber = grade.replaceAll("[^0-9]", "");
                String combination = gradeNumber + "-" + section;
                
                if (!combinations.contains(combination)) {
                    combinations.add(combination);
                }
            }
        }
        
        // Sort combinations
        java.util.Collections.sort(combinations, new java.util.Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                // Extract grade numbers for comparison
                String[] parts1 = s1.split("-");
                String[] parts2 = s2.split("-");
                
                try {
                    int grade1 = Integer.parseInt(parts1[0]);
                    int grade2 = Integer.parseInt(parts2[0]);
                    
                    if (grade1 != grade2) {
                        return Integer.compare(grade1, grade2);
                    } else {
                        // Same grade, sort by section
                        return parts1[1].compareTo(parts2[1]);
                    }
                } catch (NumberFormatException e) {
                    return s1.compareTo(s2);
                }
            }
        });
        
        return combinations;
    }
    
    private List<String> getUniqueGrades() {
        List<String> grades = new ArrayList<>();
        for (Student student : studentList) {
            String grade = student.getGrade();
            if (grade != null && !grade.isEmpty() && !grades.contains(grade)) {
                grades.add(grade);
            }
        }
        java.util.Collections.sort(grades);
        return grades;
    }
    
    private List<String> getUniqueSections() {
        List<String> sections = new ArrayList<>();
        for (Student student : studentList) {
            String section = student.getSection();
            if (section != null && !section.isEmpty() && !sections.contains(section)) {
                sections.add(section);
            }
        }
        java.util.Collections.sort(sections);
        return sections;
    }
    
    private boolean hasStudentsInGradeSection(String grade, String section) {
        for (Student student : studentList) {
            if (grade.equals(student.getGrade()) && section.equals(student.getSection())) {
                return true;
            }
        }
        return false;
    }
    
    private void applyFilter() {
        filteredStudentList.clear();
        
        if (selectedGrade == null && selectedSection == null) {
            // Show all students
            filteredStudentList.addAll(studentList);
        } else {
            // Filter by grade and section
            for (Student student : studentList) {
                String studentGrade = student.getGrade();
                String studentSection = student.getSection();
                
                // Extract grade number from student's grade
                String studentGradeNumber = studentGrade != null ? studentGrade.replaceAll("[^0-9]", "") : "";
                
                if (selectedGrade.equals(studentGradeNumber) && selectedSection.equals(studentSection)) {
                    filteredStudentList.add(student);
                }
            }
        }
        
        android.util.Log.d("TeacherDashboard", "Filter applied: " + filteredStudentList.size() + " students shown");
        studentAdapter.notifyDataSetChanged();
    }

    private void handleHomeNavigation() {
        // Navigate back to main activity or home screen
        Intent intent = new Intent(TeacherDashboard.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void navigateToStudentDetail(Student student) {
        Intent intent = new Intent(TeacherDashboard.this, StudentDetail.class);
        intent.putExtra("student_id", student.getId());
        intent.putExtra("student_name", student.getName());
        intent.putExtra("student_grade", student.getGrade());
        intent.putExtra("student_progress", student.getProgress());
        intent.putExtra("student_avatar", student.getAvatarResource());
        intent.putExtra("teacher_name", teacherName);
        startActivity(intent);
    }

    // Refresh student list (can be called after data changes)
    public void refreshStudentList() {
        loadStudentsFromFirebase();
    }

    // Update student progress (example method)
    public void updateStudentProgress(int studentId, int newProgress) {
        for (Student student : studentList) {
            if (student.getId().equals(String.valueOf(studentId))) {
                student.setProgress(newProgress);
                studentAdapter.notifyDataSetChanged();
                break;
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        android.util.Log.d("TeacherDashboard", "onResume called - reloading students");
        // Reload students when returning to this activity
        if (studentRepository != null) {
            loadStudentsFromFirebase();
        } else {
            android.util.Log.e("TeacherDashboard", "studentRepository is null in onResume!");
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
        
        // Navigate to welcome page
        Intent intent = new Intent(TeacherDashboard.this, WelcomePage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
    }
}
