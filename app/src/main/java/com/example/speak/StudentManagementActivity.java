package com.example.speak;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * StudentManagementActivity provides the main interface for managing student records.
 * Displays a list of students and provides CRUD operations through dialogs.
 * 
 * Requirements: 1.2, 1.3, 2.1, 2.2, 2.5, 2.6, 4.4, 4.5, 5.2, 5.4, 5.5, 5.6, 
 *               6.3, 7.5, 7.6, 10.1, 10.2, 10.3, 10.4
 */
public class StudentManagementActivity extends AppCompatActivity {
    
    // UI Components
    private RecyclerView recyclerViewStudents;
    private StudentAdapter studentAdapter;
    private FloatingActionButton fabAddStudent;
    private TextView tvEmptyState;
    private ImageButton btnBack;
    private ImageButton btnFilter;
    private View rootView;
    
    // Business Logic Components
    private StudentRepository studentRepository;
    private List<Student> studentList;
    private List<Student> filteredStudentList;
    
    // Filter state
    private String selectedGrade = null;
    private String selectedSection = null;
    
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
                Toast.makeText(StudentManagementActivity.this, 
                    "Access Denied: Teachers only", 
                    Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }
    
    private void initializeActivity() {
        setContentView(R.layout.activity_student_management);
        
        // Initialize components
        initializeViews();
        initializeRepository();
        initializeRecyclerView();
        setupListeners();
        
        // Load students from Firebase
        loadStudents();
    }
    
    /**
     * Initializes all view references
     */
    private void initializeViews() {
        recyclerViewStudents = findViewById(R.id.recyclerViewStudents);
        fabAddStudent = findViewById(R.id.fabAddStudent);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        btnBack = findViewById(R.id.btnBack);
        btnFilter = findViewById(R.id.btnFilter);
        rootView = findViewById(android.R.id.content);
    }
    
    /**
     * Initializes the StudentRepository
     */
    private void initializeRepository() {
        studentRepository = new StudentRepository();
        studentList = new ArrayList<>();
        filteredStudentList = new ArrayList<>();
    }
    
    /**
     * Initializes the RecyclerView with LinearLayoutManager and StudentAdapter
     * Requirements: 1.2, 6.1
     */
    private void initializeRecyclerView() {
        // Set up LinearLayoutManager
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerViewStudents.setLayoutManager(layoutManager);
        
        // Initialize StudentAdapter with OnStudentActionListener using filteredStudentList
        studentAdapter = new StudentAdapter(filteredStudentList, new StudentAdapter.OnStudentActionListener() {
            @Override
            public void onEditClicked(Student student) {
                showEditStudentDialog(student);
            }
            
            @Override
            public void onDeleteClicked(Student student) {
                showDeleteConfirmation(student);
            }
        });
        
        recyclerViewStudents.setAdapter(studentAdapter);
    }
    
    /**
     * Sets up click listeners for UI components
     * Requirements: 2.1, 2.2
     */
    private void setupListeners() {
        // FloatingActionButton click listener to open AddStudentDialog
        fabAddStudent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddStudentDialog();
            }
        });
        
        // Back button click listener
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        
        // Filter button click listener
        btnFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFilterDialog();
            }
        });
    }
    
    /**
     * Loads all students from the repository
     * Requirements: 1.2, 6.1, 6.3
     */
    private void loadStudents() {
        studentRepository.loadAllStudents(new StudentRepository.OnStudentsLoadedListener() {
            @Override
            public void onSuccess(List<Student> students) {
                studentList.clear();
                studentList.addAll(students);
                
                // Apply current filter
                applyFilter();
                
                // Show/hide empty state based on student list size
                updateEmptyState();
            }
            
            @Override
            public void onFailure(String error) {
                // Display error message
                showError(error);
                
                // Show empty state if load fails
                updateEmptyState();
            }
        });
    }
    
    /**
     * Shows or hides the empty state message based on student list size
     * Requirements: 1.3
     */
    private void updateEmptyState() {
        if (filteredStudentList.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerViewStudents.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerViewStudents.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * Opens the AddStudentDialog for creating a new student
     * Requirements: 2.2
     */
    private void showAddStudentDialog() {
        AddStudentDialog dialog = new AddStudentDialog();
        dialog.setOnStudentAddedListener(new AddStudentDialog.OnStudentAddedListener() {
            @Override
            public void onStudentAdded(Student student) {
                // Callback when student is successfully added
                onStudentAdded(student);
            }
            
            @Override
            public void onError(String message) {
                // Display error message
                showError(message);
            }
        });
        
        dialog.show(getSupportFragmentManager(), "AddStudentDialog");
    }
    
    /**
     * Opens the EditStudentDialog for editing an existing student
     * Requirements: 4.1, 4.2
     * 
     * @param student The student to edit
     */
    private void showEditStudentDialog(Student student) {
        EditStudentDialog dialog = EditStudentDialog.newInstance(student);
        dialog.setOnStudentUpdatedListener(new EditStudentDialog.OnStudentUpdatedListener() {
            @Override
            public void onStudentUpdated(Student updatedStudent) {
                // Callback when student is successfully updated
                onStudentUpdated(updatedStudent);
            }
            
            @Override
            public void onError(String message) {
                // Display error message
                showError(message);
            }
        });
        
        dialog.show(getSupportFragmentManager(), "EditStudentDialog");
    }
    
    /**
     * Shows a confirmation dialog before deleting a student
     * Requirements: 5.2, 5.6
     * 
     * @param student The student to delete
     */
    private void showDeleteConfirmation(Student student) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Student");
        builder.setMessage("Are you sure you want to delete " + student.getName() + "?");
        
        // Confirm deletion
        builder.setPositiveButton("Delete", (dialog, which) -> {
            // User confirmed deletion
            deleteStudent(student);
        });
        
        // Cancel deletion - Requirements: 5.6
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            // User cancelled - dialog dismisses, student remains unchanged
            dialog.dismiss();
        });
        
        builder.show();
    }
    
    /**
     * Deletes a student from Firebase
     * Requirements: 5.3, 5.4, 5.5
     * 
     * @param student The student to delete
     */
    private void deleteStudent(Student student) {
        studentRepository.deleteStudent(student.getId(), new StudentRepository.OnStudentDeletedListener() {
            @Override
            public void onSuccess() {
                // Callback when student is successfully deleted
                onStudentDeleted(student.getId());
            }
            
            @Override
            public void onFailure(String error) {
                // Display error message
                showError(error);
            }
        });
    }
    
    /**
     * Callback invoked when a student is successfully added
     * Requirements: 2.5, 2.6
     * 
     * @param student The newly added student
     */
    private void onStudentAdded(Student student) {
        // Add student to list
        studentList.add(student);
        
        // Notify adapter
        studentAdapter.notifyItemInserted(studentList.size() - 1);
        
        // Update empty state
        updateEmptyState();
        
        // Scroll to the newly added student
        recyclerViewStudents.smoothScrollToPosition(studentList.size() - 1);
        
        // Show success message
        showSuccess("Student added successfully");
    }
    
    /**
     * Callback invoked when a student is successfully updated
     * Requirements: 4.4, 4.5
     * 
     * @param updatedStudent The updated student
     */
    private void onStudentUpdated(Student updatedStudent) {
        // Find the student in the list and update it
        for (int i = 0; i < studentList.size(); i++) {
            if (studentList.get(i).getId().equals(updatedStudent.getId())) {
                studentList.set(i, updatedStudent);
                
                // Notify adapter
                studentAdapter.notifyItemChanged(i);
                
                // Show success message
                showSuccess("Student updated successfully");
                
                return;
            }
        }
        
        // If student not found in list, refresh the entire list
        loadStudents();
        showSuccess("Student updated successfully");
    }
    
    /**
     * Callback invoked when a student is successfully deleted
     * Requirements: 5.4, 5.5
     * 
     * @param studentId The ID of the deleted student
     */
    private void onStudentDeleted(String studentId) {
        // Find and remove student from list
        for (int i = 0; i < studentList.size(); i++) {
            if (studentList.get(i).getId().equals(studentId)) {
                studentList.remove(i);
                
                // Notify adapter
                studentAdapter.notifyItemRemoved(i);
                
                // Update empty state
                updateEmptyState();
                
                // Show success message
                showSuccess("Student deleted successfully");
                
                return;
            }
        }
    }
    
    /**
     * Displays an error message to the user using Snackbar
     * Requirements: 7.5, 7.6, 10.1, 10.2, 10.3, 10.4
     * 
     * @param message The error message to display
     */
    private void showError(String message) {
        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(getResources().getColor(android.R.color.holo_red_dark))
                .setTextColor(getResources().getColor(android.R.color.white))
                .show();
    }
    
    /**
     * Displays a success message to the user using Snackbar
     * Requirements: 2.6, 4.5, 5.5
     * 
     * @param message The success message to display
     */
    private void showSuccess(String message) {
        Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(getResources().getColor(android.R.color.holo_green_dark))
                .setTextColor(getResources().getColor(android.R.color.white))
                .show();
    }
    
    /**
     * Shows filter dialog for grade-section combinations
     */
    private void showFilterDialog() {
        // Get unique grade-section combinations from student list
        List<String> gradeSectionOptions = getUniqueGradeSectionCombinations();
        
        // Build filter options
        List<String> filterOptions = new ArrayList<>();
        filterOptions.add("All Students");
        filterOptions.addAll(gradeSectionOptions);
        
        // Show dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Filter by Grade & Section");
        
        String[] options = filterOptions.toArray(new String[0]);
        builder.setItems(options, (dialog, which) -> {
            String selected = options[which];
            
            if (selected.equals("All Students")) {
                selectedGrade = null;
                selectedSection = null;
            } else {
                // Parse grade-section (e.g., "1-A" -> grade="1", section="A")
                String[] parts = selected.split("-");
                if (parts.length == 2) {
                    selectedGrade = parts[0];
                    selectedSection = parts[1];
                }
            }
            
            applyFilter();
        });
        
        builder.show();
    }
    
    /**
     * Gets unique grade-section combinations from student list
     * Format: "1-A", "1-B", "2-A", etc.
     */
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
    
    /**
     * Applies the current filter to the student list
     */
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
        
        studentAdapter.notifyDataSetChanged();
        updateEmptyState();
    }
}
