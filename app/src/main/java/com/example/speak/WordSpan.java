package com.example.speak;

/**
 * Represents a word's position in the passage text
 * Used for accurate index-based highlighting
 */
public class WordSpan {
    public String word;
    public int start;  // Character offset where word starts
    public int end;    // Character offset where word ends
    
    public WordSpan(String word, int start, int end) {
        this.word = word;
        this.start = start;
        this.end = end;
    }
    
    @Override
    public String toString() {
        return String.format("WordSpan{word='%s', start=%d, end=%d}", word, start, end);
    }
}
