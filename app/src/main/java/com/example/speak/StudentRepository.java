package com.example.speak;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository class for managing Student CRUD operations with Firebase Realtime Database.
 * Handles all data persistence operations for student records.
 * Each teacher has isolated data - students are stored under teachers/{teacherId}/students/
 */
public class StudentRepository {
    private DatabaseReference studentsRef;
    private String teacherId;
    private static final String TEACHERS_NODE = "teachers";
    private static final String STUDENTS_NODE = "students";
    
    /**
     * Callback interface for student creation operations
     */
    public interface OnStudentCreatedListener {
        void onSuccess(Student student);
        void onFailure(String error);
    }
    
    /**
     * Callback interface for loading all students
     */
    public interface OnStudentsLoadedListener {
        void onSuccess(List<Student> students);
        void onFailure(String error);
    }
    
    /**
     * Callback interface for student update operations
     */
    public interface OnStudentUpdatedListener {
        void onSuccess(Student student);
        void onFailure(String error);
    }
    
    /**
     * Callback interface for student deletion operations
     */
    public interface OnStudentDeletedListener {
        void onSuccess();
        void onFailure(String error);
    }
    
    /**
     * Constructor initializes Firebase Database reference with offline persistence
     * Gets current teacher ID from Firebase Auth for data isolation
     */
    public StudentRepository() {
        try {
            android.util.Log.d("StudentRepository", "=== INITIALIZING REPOSITORY ===");
            
            // Get current teacher ID from Firebase Auth
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                android.util.Log.e("StudentRepository", "No authenticated user found!");
                throw new IllegalStateException("User must be authenticated to access student data");
            }
            
            teacherId = currentUser.getUid();
            android.util.Log.d("StudentRepository", "Teacher ID: " + teacherId);
            
            FirebaseDatabase database = FirebaseDatabase.getInstance();
            android.util.Log.d("StudentRepository", "FirebaseDatabase instance obtained");
            
            // Enable offline persistence (must be called before any other Firebase operations)
            try {
                database.setPersistenceEnabled(true);
                android.util.Log.d("StudentRepository", "Offline persistence enabled");
            } catch (Exception e) {
                // Persistence may already be enabled
                android.util.Log.d("StudentRepository", "Persistence already enabled or error: " + e.getMessage());
            }
            
            // Use teacher-scoped path: teachers/{teacherId}/students
            studentsRef = database.getReference(TEACHERS_NODE).child(teacherId).child(STUDENTS_NODE);
            android.util.Log.d("StudentRepository", "Students reference created");
            
            android.util.Log.d("StudentRepository", "Database URL: " + database.getReference().toString());
            android.util.Log.d("StudentRepository", "Students reference path: " + studentsRef.toString());
            
            // Keep data synced for offline access
            studentsRef.keepSynced(true);
            android.util.Log.d("StudentRepository", "Keep synced enabled for offline access");
            
            android.util.Log.d("StudentRepository", "=== REPOSITORY INITIALIZED SUCCESSFULLY ===");
        } catch (Exception e) {
            android.util.Log.e("StudentRepository", "=== ERROR INITIALIZING REPOSITORY ===", e);
            android.util.Log.e("StudentRepository", "Error message: " + e.getMessage());
            android.util.Log.e("StudentRepository", "Error class: " + e.getClass().getName());
            throw e;
        }
    }
    
    /**
     * Creates a new student record in Firebase
     * 
     * @param student The student object to create
     * @param listener Callback for success/failure
     */
    public void createStudent(Student student, OnStudentCreatedListener listener) {
        android.util.Log.d("StudentRepository", "=== CREATE STUDENT CALLED ===");
        android.util.Log.d("StudentRepository", "Student name: " + student.getName());
        android.util.Log.d("StudentRepository", "Student grade: " + student.getGrade());
        android.util.Log.d("StudentRepository", "Student section: " + student.getSection());
        android.util.Log.d("StudentRepository", "Student parents: " + student.getParentsName());
        
        try {
            // Generate unique ID using Firebase push()
            android.util.Log.d("StudentRepository", "Generating ID...");
            String studentId = studentsRef.push().getKey();
            
            if (studentId == null) {
                android.util.Log.e("StudentRepository", "Failed to generate student ID - push().getKey() returned null");
                listener.onFailure("Failed to generate student ID");
                return;
            }
            
            android.util.Log.d("StudentRepository", "Generated ID: " + studentId);
            
            // Set the generated ID and teacher ID on the student object
            student.setId(studentId);
            student.setTeacherId(teacherId);
            android.util.Log.d("StudentRepository", "ID and teacher ID set on student object");
            
            // Save to Firebase
            android.util.Log.d("StudentRepository", "Calling setValue on Firebase...");
            android.util.Log.d("StudentRepository", "Path: students/" + studentId);
            
            studentsRef.child(studentId).setValue(student)
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("StudentRepository", "=== SAVE SUCCESS ===");
                    android.util.Log.d("StudentRepository", "Student ID: " + student.getId());
                    android.util.Log.d("StudentRepository", "Student name: " + student.getName());
                    listener.onSuccess(student);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("StudentRepository", "=== SAVE FAILED ===");
                    android.util.Log.e("StudentRepository", "Error class: " + e.getClass().getName());
                    android.util.Log.e("StudentRepository", "Error message: " + e.getMessage());
                    android.util.Log.e("StudentRepository", "Stack trace:", e);
                    
                    String errorMessage = "Failed to save student: " + e.getMessage();
                    listener.onFailure(errorMessage);
                });
                
            android.util.Log.d("StudentRepository", "setValue() called, waiting for callback...");
            
        } catch (Exception e) {
            android.util.Log.e("StudentRepository", "=== EXCEPTION IN CREATE STUDENT ===", e);
            android.util.Log.e("StudentRepository", "Exception class: " + e.getClass().getName());
            android.util.Log.e("StudentRepository", "Exception message: " + e.getMessage());
            listener.onFailure("Exception: " + e.getMessage());
        }
    }
    
    /**
     * Loads all student records from Firebase
     * 
     * @param listener Callback for success/failure with list of students
     */
    public void loadAllStudents(OnStudentsLoadedListener listener) {
        android.util.Log.d("StudentRepository", "Loading all students from Firebase...");
        
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<Student> students = new ArrayList<>();
                
                try {
                    android.util.Log.d("StudentRepository", "DataSnapshot exists: " + dataSnapshot.exists());
                    android.util.Log.d("StudentRepository", "Children count: " + dataSnapshot.getChildrenCount());
                    
                    // Iterate through all student records
                    for (DataSnapshot studentSnapshot : dataSnapshot.getChildren()) {
                        Student student = studentSnapshot.getValue(Student.class);
                        if (student != null) {
                            android.util.Log.d("StudentRepository", "Loaded student: " + student.getName() + " (ID: " + student.getId() + ")");
                            students.add(student);
                        }
                    }
                    
                    android.util.Log.d("StudentRepository", "Successfully loaded " + students.size() + " students");
                    listener.onSuccess(students);
                } catch (Exception e) {
                    android.util.Log.e("StudentRepository", "Failed to parse student data", e);
                    listener.onFailure("Failed to parse student data: " + e.getMessage());
                }
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                android.util.Log.e("StudentRepository", "Failed to load students: " + databaseError.getMessage());
                listener.onFailure("Failed to load students: " + databaseError.getMessage());
            }
        });
    }
    
    /**
     * Updates an existing student record in Firebase
     * 
     * @param student The student object with updated data
     * @param listener Callback for success/failure
     */
    public void updateStudent(Student student, OnStudentUpdatedListener listener) {
        String studentId = student.getId();
        
        if (studentId == null || studentId.isEmpty()) {
            listener.onFailure("Student ID is required for update");
            return;
        }
        
        // Update the student record in Firebase
        studentsRef.child(studentId).setValue(student)
            .addOnSuccessListener(aVoid -> {
                listener.onSuccess(student);
            })
            .addOnFailureListener(e -> {
                String errorMessage = "Failed to update student: " + e.getMessage();
                listener.onFailure(errorMessage);
            });
    }
    
    /**
     * Deletes a student record from Firebase
     * 
     * @param studentId The ID of the student to delete
     * @param listener Callback for success/failure
     */
    public void deleteStudent(String studentId, OnStudentDeletedListener listener) {
        if (studentId == null || studentId.isEmpty()) {
            listener.onFailure("Student ID is required for deletion");
            return;
        }
        
        // Remove the student record from Firebase
        studentsRef.child(studentId).removeValue()
            .addOnSuccessListener(aVoid -> {
                listener.onSuccess();
            })
            .addOnFailureListener(e -> {
                String errorMessage = "Failed to delete student: " + e.getMessage();
                listener.onFailure(errorMessage);
            });
    }
    
    /**
     * Update student progress based on reading session score
     * Calculates average of all reading sessions for the student
     * 
     * @param studentId The ID of the student
     * @param sessionScore The score from the latest reading session (0.0-1.0)
     * @param listener Callback for success/failure
     */
    public void updateStudentProgress(String studentId, float sessionScore, OnStudentUpdatedListener listener) {
        if (studentId == null || studentId.isEmpty()) {
            listener.onFailure("Student ID is required");
            return;
        }
        
        // Convert session score to percentage (0-100)
        int scorePercent = Math.round(sessionScore * 100);
        
        android.util.Log.d("StudentRepository", "Updating progress for student " + studentId + " with score: " + scorePercent + "%");
        
        // Get current student data
        studentsRef.child(studentId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Student student = dataSnapshot.getValue(Student.class);
                
                if (student == null) {
                    listener.onFailure("Student not found");
                    return;
                }
                
                // Update progress with the new score
                student.setProgress(scorePercent);
                
                // Save updated student
                studentsRef.child(studentId).setValue(student)
                    .addOnSuccessListener(aVoid -> {
                        android.util.Log.d("StudentRepository", "Student progress updated to: " + scorePercent + "%");
                        listener.onSuccess(student);
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("StudentRepository", "Failed to update progress: " + e.getMessage());
                        listener.onFailure("Failed to update progress: " + e.getMessage());
                    });
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                android.util.Log.e("StudentRepository", "Failed to load student: " + databaseError.getMessage());
                listener.onFailure("Failed to load student: " + databaseError.getMessage());
            }
        });
    }
    
    /**
     * Calculate and update student progress based on average of all reading sessions
     * This provides a more accurate overall progress metric
     * 
     * @param studentId The ID of the student
     * @param readingSessions List of all reading sessions for the student
     * @param listener Callback for success/failure
     */
    public void updateProgressFromSessions(String studentId, java.util.List<ReadingSession> readingSessions, OnStudentUpdatedListener listener) {
        if (studentId == null || studentId.isEmpty()) {
            listener.onFailure("Student ID is required");
            return;
        }
        
        if (readingSessions == null || readingSessions.isEmpty()) {
            android.util.Log.d("StudentRepository", "No reading sessions found for student " + studentId);
            listener.onFailure("No reading sessions available");
            return;
        }
        
        // Calculate average overall score from all sessions
        float totalScore = 0.0f;
        for (ReadingSession session : readingSessions) {
            totalScore += session.getOverallScore();
        }
        float averageScore = totalScore / readingSessions.size();
        int progressPercent = Math.round(averageScore * 100);
        
        android.util.Log.d("StudentRepository", "Calculated average progress for student " + studentId + 
                          ": " + progressPercent + "% (from " + readingSessions.size() + " sessions)");
        
        // Get current student data
        studentsRef.child(studentId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Student student = dataSnapshot.getValue(Student.class);
                
                if (student == null) {
                    listener.onFailure("Student not found");
                    return;
                }
                
                // Update progress with calculated average
                student.setProgress(progressPercent);
                
                // Save updated student
                studentsRef.child(studentId).setValue(student)
                    .addOnSuccessListener(aVoid -> {
                        android.util.Log.d("StudentRepository", "Student progress updated to: " + progressPercent + "%");
                        listener.onSuccess(student);
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("StudentRepository", "Failed to update progress: " + e.getMessage());
                        listener.onFailure("Failed to update progress: " + e.getMessage());
                    });
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                android.util.Log.e("StudentRepository", "Failed to load student: " + databaseError.getMessage());
                listener.onFailure("Failed to load student: " + databaseError.getMessage());
            }
        });
    }
}
