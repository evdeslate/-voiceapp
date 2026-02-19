package com.example.speak;

public class Student {
    private String id;  // Changed to String for Firebase compatibility
    private String name;
    private String grade;
    private int progress;
    private int avatarResource;
    private String email;
    private boolean isActive;
    
    // New fields for student management CRUD
    private String section;
    private String parentsName;
    
    // Teacher isolation - each student belongs to a specific teacher
    private String teacherId;

    // Constructors
    public Student() {
        this.isActive = true;
    }

    public Student(int id, String name, String grade, int progress) {
        this.id = String.valueOf(id);
        this.name = name;
        this.grade = grade;
        this.progress = progress;
        this.isActive = true;
    }
    
    // Constructor with new fields for student management CRUD
    public Student(String id, String name, String grade, String section, String parentsName) {
        this.id = id;
        this.name = name;
        this.grade = grade;
        this.section = section;
        this.parentsName = parentsName;
        this.isActive = true;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(100, progress)); // Ensure progress is between 0-100
    }

    public int getAvatarResource() {
        return avatarResource;
    }

    public void setAvatarResource(int avatarResource) {
        this.avatarResource = avatarResource;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
    
    // Getters and Setters for new fields
    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getParentsName() {
        return parentsName;
    }

    public void setParentsName(String parentsName) {
        this.parentsName = parentsName;
    }
    
    public String getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(String teacherId) {
        this.teacherId = teacherId;
    }

    // Helper methods
    public String getProgressText() {
        return progress + "%";
    }

    public String getReadingLevel() {
        if (progress >= 90) {
            return "Independent Level";
        } else if (progress >= 75) {
            return "Instructional Level";
        } else {
            return "Frustration Level";
        }
    }

    @Override
    public String toString() {
        return "Student{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", grade='" + grade + '\'' +
                ", progress=" + progress +
                ", isActive=" + isActive +
                '}';
    }
}