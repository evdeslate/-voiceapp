package com.example.speak;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * AddStudentDialog is a DialogFragment for adding new student records.
 * Provides a form interface with validation for collecting student information.
 * 
 * Requirements: 2.2, 2.3, 2.4, 2.5, 2.6, 8.1, 8.3, 8.4
 */
public class AddStudentDialog extends DialogFragment {
    
    // UI Components
    private TextInputEditText etFullName;
    private TextInputEditText etGrade;
    private TextInputEditText etSection;
    private TextInputLayout tilFullName;
    private TextInputLayout tilGrade;
    private TextInputLayout tilSection;
    private Button btnSave;
    private Button btnCancel;
    private androidx.cardview.widget.CardView cardBoyAvatar;
    private androidx.cardview.widget.CardView cardGirlAvatar;
    private TextView tvSelectedAvatar;
    
    // Business Logic Components
    private FormValidator formValidator;
    private StudentRepository studentRepository;
    private OnStudentAddedListener listener;
    private int selectedAvatarResource = 0; // 0 means no selection
    
    /**
     * Listener interface for student addition events
     */
    public interface OnStudentAddedListener {
        /**
         * Called when a student is successfully added
         * @param student The newly created student
         */
        void onStudentAdded(Student student);
        
        /**
         * Called when an error occurs during student addition
         * @param message Error message describing the failure
         */
        void onError(String message);
    }
    
    /**
     * Sets the listener for student addition events
     * @param listener The listener to notify of events
     */
    public void setOnStudentAddedListener(OnStudentAddedListener listener) {
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        // Initialize business logic components
        formValidator = new FormValidator();
        studentRepository = new StudentRepository();
        
        // Inflate the custom layout
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_add_student, null);
        
        // Initialize views
        initializeViews(view);
        
        // Set up button listeners
        setupButtonListeners();
        
        // Build and return the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setView(view);
        
        return builder.create();
    }
    
    /**
     * Initializes all view references from the inflated layout
     * @param view The inflated dialog view
     */
    private void initializeViews(View view) {
        // Initialize EditText fields
        etFullName = view.findViewById(R.id.etFullName);
        etGrade = view.findViewById(R.id.etGrade);
        etSection = view.findViewById(R.id.etSection);
        
        // Initialize TextInputLayout wrappers for error display
        tilFullName = view.findViewById(R.id.tilFullName);
        tilGrade = view.findViewById(R.id.tilGrade);
        tilSection = view.findViewById(R.id.tilSection);
        
        // Initialize buttons
        btnSave = view.findViewById(R.id.btnSave);
        btnCancel = view.findViewById(R.id.btnCancel);
        
        // Initialize avatar selection
        cardBoyAvatar = view.findViewById(R.id.cardBoyAvatar);
        cardGirlAvatar = view.findViewById(R.id.cardGirlAvatar);
        tvSelectedAvatar = view.findViewById(R.id.tvSelectedAvatar);
        
        // Setup avatar selection listeners
        setupAvatarSelection();
    }
    
    /**
     * Sets up avatar selection click listeners
     */
    private void setupAvatarSelection() {
        cardBoyAvatar.setOnClickListener(v -> {
            selectedAvatarResource = R.drawable.boy;
            updateAvatarSelection();
        });
        
        cardGirlAvatar.setOnClickListener(v -> {
            selectedAvatarResource = R.drawable.girl;
            updateAvatarSelection();
        });
    }
    
    /**
     * Updates the visual indication of selected avatar
     */
    private void updateAvatarSelection() {
        if (selectedAvatarResource == R.drawable.boy) {
            cardBoyAvatar.setCardElevation(12f);
            cardGirlAvatar.setCardElevation(4f);
            tvSelectedAvatar.setText("Boy avatar selected");
            tvSelectedAvatar.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else if (selectedAvatarResource == R.drawable.girl) {
            cardBoyAvatar.setCardElevation(4f);
            cardGirlAvatar.setCardElevation(12f);
            tvSelectedAvatar.setText("Girl avatar selected");
            tvSelectedAvatar.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            cardBoyAvatar.setCardElevation(4f);
            cardGirlAvatar.setCardElevation(4f);
            tvSelectedAvatar.setText("No avatar selected");
            tvSelectedAvatar.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }
    }
    
    /**
     * Sets up click listeners for Save and Cancel buttons
     */
    private void setupButtonListeners() {
        // Save button - validate and save student
        btnSave.setOnClickListener(v -> saveStudent());
        
        // Cancel button - dismiss dialog
        btnCancel.setOnClickListener(v -> dismiss());
    }
    
    /**
     * Validates form input and saves the student to Firebase
     * Requirements: 2.4, 2.5, 2.6
     */
    private void saveStudent() {
        android.util.Log.d("AddStudentDialog", "=== SAVE STUDENT CALLED ===");
        
        // Clear previous errors
        clearErrors();
        
        // Validate avatar selection
        if (selectedAvatarResource == 0) {
            tvSelectedAvatar.setText("Please select an avatar");
            tvSelectedAvatar.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            android.widget.Toast.makeText(getContext(), "Please select an avatar", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Collect form data
        String fullName = etFullName.getText() != null ? etFullName.getText().toString().trim() : "";
        String grade = etGrade.getText() != null ? etGrade.getText().toString().trim() : "";
        String section = etSection.getText() != null ? etSection.getText().toString().trim() : "";
        
        android.util.Log.d("AddStudentDialog", "Form data collected:");
        android.util.Log.d("AddStudentDialog", "  Name: " + fullName);
        android.util.Log.d("AddStudentDialog", "  Grade: " + grade);
        android.util.Log.d("AddStudentDialog", "  Section: " + section);
        android.util.Log.d("AddStudentDialog", "  Avatar: " + selectedAvatarResource);
        
        // Validate all fields
        android.util.Log.d("AddStudentDialog", "Starting validation...");
        
        // Basic validation
        if (fullName.isEmpty()) {
            showValidationError("Full Name is required");
            return;
        }
        
        if (grade.isEmpty()) {
            showValidationError("Grade is required");
            return;
        }
        
        if (section.isEmpty()) {
            showValidationError("Section is required");
            return;
        }
        
        android.util.Log.d("AddStudentDialog", "Validation passed!");
        
        // Create Student object (ID will be generated by repository)
        // Parent's name is no longer required - set to empty string
        Student student = new Student(null, fullName, grade, section, "");
        student.setAvatarResource(selectedAvatarResource); // Set the selected avatar
        
        android.util.Log.d("AddStudentDialog", "Student object created");
        android.util.Log.d("AddStudentDialog", "Calling repository.createStudent()...");
        
        // Show progress toast
        android.widget.Toast.makeText(getContext(), "Saving student...", android.widget.Toast.LENGTH_SHORT).show();
        
        // Disable save button to prevent double submission
        btnSave.setEnabled(false);
        
        // Save to Firebase via repository
        try {
            studentRepository.createStudent(student, new StudentRepository.OnStudentCreatedListener() {
                @Override
                public void onSuccess(Student createdStudent) {
                    android.util.Log.d("AddStudentDialog", "=== CALLBACK: SUCCESS ===");
                    android.util.Log.d("AddStudentDialog", "Student ID: " + createdStudent.getId());
                    android.util.Log.d("AddStudentDialog", "Student name: " + createdStudent.getName());
                    
                    // Check if dialog is still attached before dismissing
                    if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                        android.widget.Toast.makeText(getContext(), "Student saved successfully!", android.widget.Toast.LENGTH_SHORT).show();
                        
                        // Notify listener of success
                        if (listener != null) {
                            android.util.Log.d("AddStudentDialog", "Notifying listener of success");
                            listener.onStudentAdded(createdStudent);
                        } else {
                            android.util.Log.w("AddStudentDialog", "Listener is null!");
                        }
                        
                        // Dismiss dialog
                        try {
                            dismiss();
                        } catch (Exception e) {
                            android.util.Log.e("AddStudentDialog", "Error dismissing dialog", e);
                        }
                    } else {
                        android.util.Log.w("AddStudentDialog", "Dialog not attached, cannot dismiss");
                    }
                }
                
                @Override
                public void onFailure(String error) {
                    android.util.Log.e("AddStudentDialog", "=== CALLBACK: FAILURE ===");
                    android.util.Log.e("AddStudentDialog", "Error: " + error);
                    
                    // Check if dialog is still attached
                    if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                        // Re-enable save button
                        btnSave.setEnabled(true);
                        
                        // Show error toast
                        android.widget.Toast.makeText(getContext(), "Error: " + error, android.widget.Toast.LENGTH_LONG).show();
                        
                        // Notify listener of error
                        if (listener != null) {
                            listener.onError(error);
                        }
                    }
                }
            });
            
            android.util.Log.d("AddStudentDialog", "createStudent() called, waiting for callback...");
            
        } catch (Exception e) {
            android.util.Log.e("AddStudentDialog", "=== EXCEPTION CALLING CREATE STUDENT ===", e);
            android.widget.Toast.makeText(getContext(), "Exception: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            btnSave.setEnabled(true);
        }
    }
    
    /**
     * Clears all error messages from input fields
     */
    private void clearErrors() {
        tilFullName.setError(null);
        tilGrade.setError(null);
        tilSection.setError(null);
    }
    
    /**
     * Shows validation error on the appropriate field
     * Requirements: 2.6 - Show error messages for validation failures
     * 
     * @param errorMessage The error message to display
     */
    private void showValidationError(String errorMessage) {
        // Determine which field has the error based on the message
        if (errorMessage.contains("Full Name") || errorMessage.contains("Name")) {
            tilFullName.setError(errorMessage);
            etFullName.requestFocus();
        } else if (errorMessage.contains("Grade")) {
            tilGrade.setError(errorMessage);
            etGrade.requestFocus();
        } else if (errorMessage.contains("Section")) {
            tilSection.setError(errorMessage);
            etSection.requestFocus();
        } else {
            // Generic error - show on first field
            tilFullName.setError(errorMessage);
        }
    }
}
