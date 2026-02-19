package com.example.speak;

public class Passage {
    private String id;
    private String title;
    private String content;
    private String difficulty; // Easy, Medium, Hard
    private int wordCount;
    
    // Teacher isolation - each passage belongs to a specific teacher
    private String teacherId;

    // No-arg constructor for Firebase
    public Passage() {
    }

    public Passage(String id, String title, String content, String difficulty) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.difficulty = difficulty;
        this.wordCount = content != null ? content.split("\\s+").length : 0;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.wordCount = content != null ? content.split("\\s+").length : 0;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public int getWordCount() {
        return wordCount;
    }

    public void setWordCount(int wordCount) {
        this.wordCount = wordCount;
    }
    
    public String getTeacherId() {
        return teacherId;
    }
    
    public void setTeacherId(String teacherId) {
        this.teacherId = teacherId;
    }
}
