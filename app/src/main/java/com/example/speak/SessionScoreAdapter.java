package com.example.speak;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/**
 * Adapter for displaying reading session scores in a dialog
 * Used in parent dashboard to show detailed progress
 */
public class SessionScoreAdapter extends RecyclerView.Adapter<SessionScoreAdapter.SessionViewHolder> {
    
    private List<ReadingSession> sessions;
    
    public SessionScoreAdapter(List<ReadingSession> sessions) {
        this.sessions = sessions;
    }
    
    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_session_score, parent, false);
        return new SessionViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        ReadingSession session = sessions.get(position);
        holder.bind(session);
    }
    
    @Override
    public int getItemCount() {
        return sessions.size();
    }
    
    static class SessionViewHolder extends RecyclerView.ViewHolder {
        private TextView passageTitleText;
        private TextView sessionDateText;
        private TextView readingLevelText;
        private TextView accuracyScoreText;
        private TextView pronunciationScoreText;
        private TextView wpmText;
        
        public SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            
            passageTitleText = itemView.findViewById(R.id.passageTitleText);
            sessionDateText = itemView.findViewById(R.id.sessionDateText);
            readingLevelText = itemView.findViewById(R.id.readingLevelText);
            accuracyScoreText = itemView.findViewById(R.id.accuracyScoreText);
            pronunciationScoreText = itemView.findViewById(R.id.pronunciationScoreText);
            wpmText = itemView.findViewById(R.id.wpmText);
        }
        
        public void bind(ReadingSession session) {
            // Passage title
            passageTitleText.setText(session.getPassageTitle());
            
            // Date
            sessionDateText.setText(session.getFormattedDate());
            
            // Reading level
            String levelName = session.getReadingLevelName();
            if (levelName != null && !levelName.isEmpty()) {
                readingLevelText.setText(levelName);
                readingLevelText.setVisibility(View.VISIBLE);
            } else {
                readingLevelText.setVisibility(View.GONE);
            }
            
            // Scores
            accuracyScoreText.setText(session.getAccuracyPercent());
            pronunciationScoreText.setText(session.getPronunciationPercent());
            
            // Set score colors based on performance
            setScoreColor(accuracyScoreText, session.getAccuracy());
            setScoreColor(pronunciationScoreText, session.getPronunciation());
            
            // WPM
            if (session.getWpm() > 0) {
                wpmText.setText(String.format("Reading Speed: %.0f WPM", session.getWpm()));
                wpmText.setVisibility(View.VISIBLE);
            } else {
                wpmText.setVisibility(View.GONE);
            }
        }
        
        private void setScoreColor(TextView textView, float score) {
            int color;
            if (score >= 0.9f) {
                color = 0xFF4CAF50; // Green - Excellent
            } else if (score >= 0.7f) {
                color = 0xFF2196F3; // Blue - Good
            } else if (score >= 0.5f) {
                color = 0xFFFF9800; // Orange - Fair
            } else {
                color = 0xFFF44336; // Red - Needs Improvement
            }
            textView.setTextColor(color);
        }
    }
}
