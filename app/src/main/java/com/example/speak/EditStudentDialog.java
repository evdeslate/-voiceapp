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
 * EditStudentDialog is a DialogFragment for editing existing student records.
 * Provides a form interface pre-filled with existing data and validation for updating student information.
 * 
 * Requirements: 4.2, 4.3, 4.4, 4.5, 8.2, 8.3, 8.4
 */
public class EditStudentDialog extends DialogFragment {
    
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
    private Student studentToEdit;
    private FormValidator formValidator;
    private StudentRepository studentRepository;
    private OnStudentUpdatedListener listener;
    private int selectedAvatarResource = 0; // 0 means no selection
    
    // Bundle keys for passing student data
    private static final String ARG_STUDENT_ID = "student_id";
    private static final String ARG_STUDENT_NAME = "student_name";
    private static final String ARG_STUDENT_GRADE = "student_grade";
    private static final String ARG_STUDENT_SECTION = "student_section";
    private static final String ARG_STUDENT_AVATAR = "student_avatar";
    
    /**
     * Listener interface for student update events
     */
    public interface OnStudentUpdatedListener {
        /**
         * Called when a student is successfully updated
         * @param student The updated student
         */
        void onStudentUpdated(Student student);
        
        /**
         * Called when an error occurs during student update
         * @param message Error message describing the failure
         */
        void onError(String message);
    }
    
    /**
     * Creates a new instance of EditStudentDialog with pre-filled student data
     * Requirements: 4.2 - Pass student data to dialog
     * 
     * @param student The student to edit
     * @return A new EditStudentDialog instance
     */
    public static EditStudentDialog newInstance(Student student) {
        EditStudentDialog dialog = new EditStudentDialog();
        Bundle args = new Bundle();
        args.putString(ARG_STUDENT_ID, student.getId());
        args.putString(ARG_STUDENT_NAME, student.getName());
        args.putString(ARG_STUDENT_GRADE, student.getGrade());
        args.putString(ARG_STUDENT_SECTION, student.getSection());
        args.putInt(ARG_STUDENT_AVATAR, student.getAvatarResource());
        dialog.setArguments(args);
        return dialog;
    }
    
    /**
     * Sets the listener for student update events
     * @param listener The listener to notify of events
     */
    public void setOnStudentUpdatedListener(OnStudentUpdatedListener listener) {
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        // Initialize business logic components
        formValidator = new FormValidator();
        studentRepository = new StudentRepository();
        
        // Retrieve student data from arguments
        retrieveStudentData();
        
        // Inflate the custom layout
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_edit_student, null);
        
        // Initialize views
        initializeViews(view);
        
        // Pre-fill fields with existing student data
        // Requirements: 4.2 - Pre-fill fields with existing student data
        preFillFields();
        
        // Set up button listeners
        setupButtonListeners();
        
        // Build and return the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setView(view);
        
        return builder.create();
    }
    
    /**
     * Retrieves student data from the dialog arguments
     */
    private void retrieveStudentData() {
        Bundle args = getArguments();
        if (args != null) {
            studentToEdit = new Student();
            studentToEdit.setId(args.getString(ARG_STUDENT_ID));
            studentToEdit.setName(args.getString(ARG_STUDENT_NAME));
            studentToEdit.setGrade(args.getString(ARG_STUDENT_GRADE));
            studentToEdit.setSection(args.getString(ARG_STUDENT_SECTION));
        }
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
     * Pre-fills form fields with existing student data
     * Requirements: 4.2 - Pre-fill fields with existing student data
     */
    private void preFillFields() {
        if (studentToEdit != null) {
            etFullName.setText(studentToEdit.getName());
            etGrade.setText(studentToEdit.getGrade());
            etSection.setText(studentToEdit.getSection());
            
            // Pre-select avatar
            selectedAvatarResource = studentToEdit.getAvatarResource();
            if (selectedAvatarResource == 0) {
                // If no avatar set, try to get from arguments
                if (getArguments() != null) {
                    selectedAvatarResource = getArguments().getInt(ARG_STUDENT_AVATAR, 0);
                }
            }
            updateAvatarSelection();
        }
    }
    
    /**
     * Sets up click listeners for Save and Cancel buttons
     */
    private void setupButtonListeners() {
        // Save button - validate and update student
        btnSave.setOnClickListener(v -> updateStudent());
        
        // Cancel button - dismiss dialog
        btnCancel.setOnClickListener(v -> dismiss());
    }
    
    /**
     * Validates form input and updates the student in Firebase
     * Requirements: 4.3, 4.4, 4.5
     */
    private void updateStudent() {
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
        
        // Update Student object with new values
        studentToEdit.setName(fullName);
        studentToEdit.setGrade(grade);
        studentToEdit.setSection(section);
        studentToEdit.setAvatarResource(selectedAvatarResource); // Update avatar
        
        // Disable save button to prevent double submission
        btnSave.setEnabled(false);
        
        // Update in Firebase via repository
        studentRepository.updateStudent(studentToEdit, new StudentRepository.OnStudentUpdatedListener() {
            @Override
            public void onSuccess(Student updatedStudent) {
                // Notify listener of success
                if (listener != null) {
                    listener.onStudentUpdated(updatedStudent);
                }
                // Dismiss dialog
                dismiss();
            }
            
            @Override
            public void onFailure(String error) {
                // Re-enable save button
                btnSave.setEnabled(true);
                
                // Notify listener of error
                if (listener != null) {
                    listener.onError(error);
                }
            }
        });
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
     * Requirements: 4.5 - Show error messages for validation failures
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
