package com.example.speak;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for FormValidator class.
 * Tests validation logic for student form fields according to requirements 3.1-3.7.
 */
public class FormValidatorTest {
    
    private FormValidator validator;
    
    @Before
    public void setUp() {
        validator = new FormValidator();
    }
    
    // ========== Full Name Validation Tests ==========
    
    @Test
    public void testValidateFullName_ValidName_ReturnsValid() {
        FormValidator.ValidationResult result = validator.validateFullName("John Doe");
        assertTrue("Valid name should pass validation", result.isValid());
        assertTrue("Valid result should have empty error message", 
                   result.getErrorMessage().isEmpty());
    }
    
    @Test
    public void testValidateFullName_EmptyString_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateFullName("");
        assertFalse("Empty name should fail validation", result.isValid());
        assertNotNull("Error message should not be null", result.getErrorMessage());
        assertFalse("Error message should not be empty", result.getErrorMessage().isEmpty());
    }
    
    @Test
    public void testValidateFullName_NullString_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateFullName(null);
        assertFalse("Null name should fail validation", result.isValid());
        assertNotNull("Error message should not be null", result.getErrorMessage());
    }
    
    @Test
    public void testValidateFullName_WhitespaceOnly_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateFullName("   ");
        assertFalse("Whitespace-only name should fail validation", result.isValid());
        assertNotNull("Error message should not be null", result.getErrorMessage());
    }
    
    @Test
    public void testValidateFullName_NameWithSpaces_ReturnsValid() {
        FormValidator.ValidationResult result = validator.validateFullName("Mary Jane Watson");
        assertTrue("Name with spaces should pass validation", result.isValid());
    }
    
    // ========== Age Validation Tests ==========
    
    @Test
    public void testValidateAge_ValidAge_ReturnsValid() {
        FormValidator.ValidationResult result = validator.validateAge("25");
        assertTrue("Valid age should pass validation", result.isValid());
        assertTrue("Valid result should have empty error message", 
                   result.getErrorMessage().isEmpty());
    }
    
    @Test
    public void testValidateAge_EmptyString_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateAge("");
        assertFalse("Empty age should fail validation", result.isValid());
        assertNotNull("Error message should not be null", result.getErrorMessage());
    }
    
    @Test
    public void testValidateAge_NullString_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateAge(null);
        assertFalse("Null age should fail validation", result.isValid());
        assertNotNull("Error message should not be null", result.getErrorMessage());
    }
    
    @Test
    public void testValidateAge_NonNumeric_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateAge("abc");
        assertFalse("Non-numeric age should fail validation", result.isValid());
        assertTrue("Error message should mention numeric requirement", 
                   result.getErrorMessage().toLowerCase().contains("number"));
    }
    
    @Test
    public void testValidateAge_NegativeAge_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateAge("-5");
        assertFalse("Negative age should fail validation", result.isValid());
        assertNotNull("Error message should not be null", result.getErrorMessage());
    }
    
    @Test
    public void testValidateAge_Zero_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateAge("0");
        assertFalse("Age 0 should fail validation", result.isValid());
        assertTrue("Error message should mention minimum age", 
                   result.getErrorMessage().toLowerCase().contains("at least"));
    }
    
    @Test
    public void testValidateAge_BoundaryMinimum_ReturnsValid() {
        FormValidator.ValidationResult result = validator.validateAge("1");
        assertTrue("Age 1 (minimum boundary) should pass validation", result.isValid());
    }
    
    @Test
    public void testValidateAge_BoundaryMaximum_ReturnsValid() {
        FormValidator.ValidationResult result = validator.validateAge("150");
        assertTrue("Age 150 (maximum boundary) should pass validation", result.isValid());
    }
    
    @Test
    public void testValidateAge_AboveMaximum_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateAge("151");
        assertFalse("Age 151 should fail validation", result.isValid());
        assertTrue("Error message should mention maximum age", 
                   result.getErrorMessage().toLowerCase().contains("150"));
    }
    
    @Test
    public void testValidateAge_DecimalNumber_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateAge("25.5");
        assertFalse("Decimal age should fail validation", result.isValid());
    }
    
    @Test
    public void testValidateAge_WithWhitespace_ReturnsValid() {
        FormValidator.ValidationResult result = validator.validateAge("  25  ");
        assertTrue("Age with surrounding whitespace should pass validation", result.isValid());
    }
    
    // ========== Birthday Validation Tests ==========
    
    @Test
    public void testValidateBirthday_ValidDate_ReturnsValid() {
        FormValidator.ValidationResult result = validator.validateBirthday("05/15/2014");
        assertTrue("Valid birthday should pass validation", result.isValid());
        assertTrue("Valid result should have empty error message", 
                   result.getErrorMessage().isEmpty());
    }
    
    @Test
    public void testValidateBirthday_EmptyString_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateBirthday("");
        assertFalse("Empty birthday should fail validation", result.isValid());
        assertNotNull("Error message should not be null", result.getErrorMessage());
    }
    
    @Test
    public void testValidateBirthday_NullString_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateBirthday(null);
        assertFalse("Null birthday should fail validation", result.isValid());
        assertNotNull("Error message should not be null", result.getErrorMessage());
    }
    
    @Test
    public void testValidateBirthday_WhitespaceOnly_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateBirthday("   ");
        assertFalse("Whitespace-only birthday should fail validation", result.isValid());
    }
    
    // ========== Parents' Name Validation Tests ==========
    
    @Test
    public void testValidateParentsName_ValidName_ReturnsValid() {
        FormValidator.ValidationResult result = validator.validateParentsName("Jane and Robert Doe");
        assertTrue("Valid parents' name should pass validation", result.isValid());
        assertTrue("Valid result should have empty error message", 
                   result.getErrorMessage().isEmpty());
    }
    
    @Test
    public void testValidateParentsName_EmptyString_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateParentsName("");
        assertFalse("Empty parents' name should fail validation", result.isValid());
        assertNotNull("Error message should not be null", result.getErrorMessage());
    }
    
    @Test
    public void testValidateParentsName_NullString_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateParentsName(null);
        assertFalse("Null parents' name should fail validation", result.isValid());
        assertNotNull("Error message should not be null", result.getErrorMessage());
    }
    
    @Test
    public void testValidateParentsName_WhitespaceOnly_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateParentsName("   ");
        assertFalse("Whitespace-only parents' name should fail validation", result.isValid());
    }
    
    @Test
    public void testValidateParentsName_SingleParent_ReturnsValid() {
        FormValidator.ValidationResult result = validator.validateParentsName("Michael Smith");
        assertTrue("Single parent name should pass validation", result.isValid());
    }
    
    // ========== Validate All Fields Tests ==========
    
    @Test
    public void testValidateAll_AllValidFields_ReturnsValid() {
        FormValidator.ValidationResult result = validator.validateAll(
            "John Doe",
            "10",
            "05/15/2014",
            "Jane and Robert Doe"
        );
        assertTrue("All valid fields should pass validation", result.isValid());
        assertTrue("Valid result should have empty error message", 
                   result.getErrorMessage().isEmpty());
    }
    
    @Test
    public void testValidateAll_InvalidFullName_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateAll(
            "",
            "10",
            "05/15/2014",
            "Jane Doe"
        );
        assertFalse("Invalid full name should fail validation", result.isValid());
        assertTrue("Error message should mention full name", 
                   result.getErrorMessage().toLowerCase().contains("full name"));
    }
    
    @Test
    public void testValidateAll_InvalidAge_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateAll(
            "John Doe",
            "abc",
            "05/15/2014",
            "Jane Doe"
        );
        assertFalse("Invalid age should fail validation", result.isValid());
        assertTrue("Error message should mention age", 
                   result.getErrorMessage().toLowerCase().contains("age"));
    }
    
    @Test
    public void testValidateAll_InvalidBirthday_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateAll(
            "John Doe",
            "10",
            "",
            "Jane Doe"
        );
        assertFalse("Invalid birthday should fail validation", result.isValid());
        assertTrue("Error message should mention birthday", 
                   result.getErrorMessage().toLowerCase().contains("birthday"));
    }
    
    @Test
    public void testValidateAll_InvalidParentsName_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateAll(
            "John Doe",
            "10",
            "05/15/2014",
            ""
        );
        assertFalse("Invalid parents' name should fail validation", result.isValid());
        assertTrue("Error message should mention parents", 
                   result.getErrorMessage().toLowerCase().contains("parents"));
    }
    
    @Test
    public void testValidateAll_MultipleInvalidFields_ReturnsFirstError() {
        FormValidator.ValidationResult result = validator.validateAll(
            "",
            "abc",
            "",
            ""
        );
        assertFalse("Multiple invalid fields should fail validation", result.isValid());
        // Should return the first error (Full Name)
        assertTrue("Should return first validation error", 
                   result.getErrorMessage().toLowerCase().contains("full name"));
    }
    
    @Test
    public void testValidateAll_AgeOutOfRange_ReturnsInvalid() {
        FormValidator.ValidationResult result = validator.validateAll(
            "John Doe",
            "200",
            "05/15/2014",
            "Jane Doe"
        );
        assertFalse("Age out of range should fail validation", result.isValid());
        assertTrue("Error message should mention age limit", 
                   result.getErrorMessage().contains("150"));
    }
}
