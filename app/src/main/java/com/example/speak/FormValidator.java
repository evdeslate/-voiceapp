package com.example.speak;

/**
 * FormValidator provides validation methods for student form input fields.
 * Validates Full Name, Age, Birthday, and Parents' Name according to business rules.
 * 
 * Validation Rules:
 * - Full Name: Non-empty, not only whitespace
 * - Age: Numeric, range 1-150
 * - Birthday: Non-empty
 * - Parents' Name: Non-empty, not only whitespace
 */
public class FormValidator {
    
    /**
     * ValidationResult encapsulates the result of a validation operation.
     * Contains a boolean indicating validity and an error message if invalid.
     */
    public static class ValidationResult {
        private boolean isValid;
        private String errorMessage;
        
        /**
         * Creates a new ValidationResult.
         * 
         * @param isValid true if validation passed, false otherwise
         * @param errorMessage descriptive error message (null or empty if valid)
         */
        public ValidationResult(boolean isValid, String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }
        
        /**
         * @return true if validation passed, false otherwise
         */
        public boolean isValid() {
            return isValid;
        }
        
        /**
         * @return error message describing validation failure, or null/empty if valid
         */
        public String getErrorMessage() {
            return errorMessage;
        }
    }
    
    /**
     * Validates the Full Name field.
     * Requirements: 3.1 - Must not be empty or contain only whitespace
     * 
     * @param fullName the full name to validate
     * @return ValidationResult indicating success or failure with error message
     */
    public ValidationResult validateFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return new ValidationResult(false, "Full Name is required and cannot be empty");
        }
        return new ValidationResult(true, "");
    }
    
    /**
     * Validates the Age field.
     * Requirements: 3.2, 3.3, 3.6, 3.7 - Must be numeric and within range 1-150
     * 
     * @param ageStr the age string to validate
     * @return ValidationResult indicating success or failure with error message
     */
    public ValidationResult validateAge(String ageStr) {
        // Check if empty or null
        if (ageStr == null || ageStr.trim().isEmpty()) {
            return new ValidationResult(false, "Age is required and cannot be empty");
        }
        
        // Check if numeric
        int age;
        try {
            age = Integer.parseInt(ageStr.trim());
        } catch (NumberFormatException e) {
            return new ValidationResult(false, "Age must be a valid number");
        }
        
        // Check if within valid range (1-150)
        if (age < 1) {
            return new ValidationResult(false, "Age must be at least 1");
        }
        if (age > 150) {
            return new ValidationResult(false, "Age cannot be greater than 150");
        }
        
        return new ValidationResult(true, "");
    }
    
    /**
     * Validates the Birthday field.
     * Requirements: 3.4 - Must not be empty
     * 
     * @param birthday the birthday string to validate
     * @return ValidationResult indicating success or failure with error message
     */
    public ValidationResult validateBirthday(String birthday) {
        if (birthday == null || birthday.trim().isEmpty()) {
            return new ValidationResult(false, "Birthday is required and cannot be empty");
        }
        return new ValidationResult(true, "");
    }
    
    /**
     * Validates the Parents' Name field.
     * Requirements: 3.5 - Must not be empty or contain only whitespace
     * 
     * @param parentsName the parents' name to validate
     * @return ValidationResult indicating success or failure with error message
     */
    public ValidationResult validateParentsName(String parentsName) {
        if (parentsName == null || parentsName.trim().isEmpty()) {
            return new ValidationResult(false, "Parents' Name is required and cannot be empty");
        }
        return new ValidationResult(true, "");
    }
    
    /**
     * Validates all student form fields.
     * Returns the first validation error encountered, or success if all fields are valid.
     * 
     * @param fullName the student's full name
     * @param age the student's age as a string
     * @param birthday the student's birthday
     * @param parentsName the parents' name
     * @return ValidationResult indicating success or the first validation failure
     */
    public ValidationResult validateAll(String fullName, String age, 
                                       String birthday, String parentsName) {
        // Validate Full Name
        ValidationResult nameResult = validateFullName(fullName);
        if (!nameResult.isValid()) {
            return nameResult;
        }
        
        // Validate Age
        ValidationResult ageResult = validateAge(age);
        if (!ageResult.isValid()) {
            return ageResult;
        }
        
        // Validate Birthday
        ValidationResult birthdayResult = validateBirthday(birthday);
        if (!birthdayResult.isValid()) {
            return birthdayResult;
        }
        
        // Validate Parents' Name
        ValidationResult parentsNameResult = validateParentsName(parentsName);
        if (!parentsNameResult.isValid()) {
            return parentsNameResult;
        }
        
        // All validations passed
        return new ValidationResult(true, "");
    }
}
