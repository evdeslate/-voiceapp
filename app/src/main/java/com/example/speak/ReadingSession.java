package com.example.speak;

import java.util.Date;

/**
 * Model class for storing reading session data
 * Tracks student reading performance for progress reports
 */
public class ReadingSession {
    private String id;
    private String studentId;
    private String studentName;
    private String passageTitle;
    private String passageText;
    private long timestamp;
    
    // Teacher isolation - each session belongs to a specific teacher
    private String teacherId;
    
    // Performance metrics
    private float accuracy;
    private float pronunciation;
    private float comprehension;
    private float wpm;
    private int correctWords;
    private int totalWords;
    
    // Reading level classification
    private int readingLevel; // 0=Frustration, 1=Instructional, 2=Independent
    private String readingLevelName;
    private String readingLevelDescription;
    
    // Additional details
    private String strengths;
    private String weaknesses;
    private String recommendations;
    
    // Constructors
    public ReadingSession() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public ReadingSession(String studentId, String studentName, String passageTitle, String passageText) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.passageTitle = passageTitle;
        this.passageText = passageText;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getStudentId() {
        return studentId;
    }
    
    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }
    
    public String getStudentName() {
        return studentName;
    }
    
    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }
    
    public String getPassageTitle() {
        return passageTitle;
    }
    
    public void setPassageTitle(String passageTitle) {
        this.passageTitle = passageTitle;
    }
    
    public String getPassageText() {
        return passageText;
    }
    
    public void setPassageText(String passageText) {
        this.passageText = passageText;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public float getAccuracy() {
        return accuracy;
    }
    
    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }
    
    public float getPronunciation() {
        return pronunciation;
    }
    
    public void setPronunciation(float pronunciation) {
        this.pronunciation = pronunciation;
    }
    
    public float getComprehension() {
        return comprehension;
    }
    
    public void setComprehension(float comprehension) {
        this.comprehension = comprehension;
    }
    
    public float getWpm() {
        return wpm;
    }
    
    public void setWpm(float wpm) {
        this.wpm = wpm;
    }
    
    public int getCorrectWords() {
        return correctWords;
    }
    
    public void setCorrectWords(int correctWords) {
        this.correctWords = correctWords;
    }
    
    public int getTotalWords() {
        return totalWords;
    }
    
    public void setTotalWords(int totalWords) {
        this.totalWords = totalWords;
    }
    
    public int getReadingLevel() {
        return readingLevel;
    }
    
    public void setReadingLevel(int readingLevel) {
        this.readingLevel = readingLevel;
    }
    
    public String getReadingLevelName() {
        return readingLevelName;
    }
    
    public void setReadingLevelName(String readingLevelName) {
        this.readingLevelName = readingLevelName;
    }
    
    public String getReadingLevelDescription() {
        return readingLevelDescription;
    }
    
    public void setReadingLevelDescription(String readingLevelDescription) {
        this.readingLevelDescription = readingLevelDescription;
    }
    
    public String getStrengths() {
        return strengths;
    }
    
    public void setStrengths(String strengths) {
        this.strengths = strengths;
    }
    
    public String getWeaknesses() {
        return weaknesses;
    }
    
    public void setWeaknesses(String weaknesses) {
        this.weaknesses = weaknesses;
    }
    
    public String getRecommendations() {
        return recommendations;
    }
    
    public void setRecommendations(String recommendations) {
        this.recommendations = recommendations;
    }
    
    public String getTeacherId() {
        return teacherId;
    }
    
    public void setTeacherId(String teacherId) {
        this.teacherId = teacherId;
    }
    
    // Helper methods
    public Date getDate() {
        return new Date(timestamp);
    }
    
    public String getFormattedDate() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
    
    public float getOverallScore() {
        return (accuracy * 0.5f) + (pronunciation * 0.5f);
    }
    
    public String getAccuracyPercent() {
        return String.format("%.0f%%", accuracy * 100);
    }
    
    public String getPronunciationPercent() {
        return String.format("%.0f%%", pronunciation * 100);
    }
    
    public String getComprehensionPercent() {
        return String.format("%.0f%%", comprehension * 100);
    }
    
    @Override
    public String toString() {
        return "ReadingSession{" +
                "id='" + id + '\'' +
                ", studentId='" + studentId + '\'' +
                ", passageTitle='" + passageTitle + '\'' +
                ", accuracy=" + accuracy +
                ", readingLevel=" + readingLevel +
                ", timestamp=" + timestamp +
                '}';
    }
}
