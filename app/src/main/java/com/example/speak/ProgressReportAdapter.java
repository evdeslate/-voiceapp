package com.example.speak;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/**
 * Adapter for displaying student progress reports in a RecyclerView
 */
public class ProgressReportAdapter extends RecyclerView.Adapter<ProgressReportAdapter.ProgressReportViewHolder> {
    
    private List<ProgressReportsActivity.StudentProgressReport> progressReports;
    private Context context;
    
    public ProgressReportAdapter(List<ProgressReportsActivity.StudentProgressReport> progressReports, Context context) {
        this.progressReports = progressReports;
        this.context = context;
    }
    
    @NonNull
    @Override
    public ProgressReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_progress_report, parent, false);
        return new ProgressReportViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ProgressReportViewHolder holder, int position) {
        ProgressReportsActivity.StudentProgressReport report = progressReports.get(position);
        holder.bind(report);
    }
    
    @Override
    public int getItemCount() {
        return progressReports.size();
    }
    
    class ProgressReportViewHolder extends RecyclerView.ViewHolder {
        private TextView studentNameText;
        private TextView gradeText;
        private TextView totalSessionsText;
        private TextView overallAverageText;
        private TextView accuracyText;
        private TextView pronunciationText;
        private TextView latestSessionText;
        private View progressIndicator;
        
        public ProgressReportViewHolder(@NonNull View itemView) {
            super(itemView);
            
            studentNameText = itemView.findViewById(R.id.studentNameText);
            gradeText = itemView.findViewById(R.id.gradeText);
            totalSessionsText = itemView.findViewById(R.id.totalSessionsText);
            overallAverageText = itemView.findViewById(R.id.overallAverageText);
            accuracyText = itemView.findViewById(R.id.accuracyText);
            pronunciationText = itemView.findViewById(R.id.pronunciationText);
            latestSessionText = itemView.findViewById(R.id.latestSessionText);
            progressIndicator = itemView.findViewById(R.id.progressIndicator);
        }
        
        public void bind(ProgressReportsActivity.StudentProgressReport report) {
            Student student = report.getStudent();
            
            // Student info
            studentNameText.setText(student.getName());
            gradeText.setText(student.getGrade() + " - " + student.getSection());
            
            // Session count
            int sessionCount = report.getTotalSessions();
            totalSessionsText.setText(sessionCount + " session" + (sessionCount != 1 ? "s" : ""));
            
            if (sessionCount == 0) {
                // No sessions yet
                overallAverageText.setText("No data");
                accuracyText.setText("--");
                pronunciationText.setText("--");
                latestSessionText.setText("No reading sessions yet");
                setProgressIndicatorColor(0);
            } else {
                // Show averages
                overallAverageText.setText(report.getOverallAveragePercent());
                accuracyText.setText("Accuracy: " + report.getAverageAccuracyPercent());
                pronunciationText.setText("Pronunciation: " + report.getAveragePronunciationPercent());
                
                // Latest session info
                ReadingSession latest = report.getLatestSession();
                if (latest != null) {
                    latestSessionText.setText("Latest: " + latest.getPassageTitle() + 
                        " (" + latest.getFormattedDate() + ")");
                }
                
                // Set progress indicator color
                int overallPercent = Math.round(report.getOverallAverage() * 100);
                setProgressIndicatorColor(overallPercent);
            }
            
            // Click to view detailed progress - show session scores dialog
            itemView.setOnClickListener(v -> {
                showSessionScoresDialog(report);
            });
        }
        
        /**
         * Show dialog with all reading session scores for this student
         */
        private void showSessionScoresDialog(ProgressReportsActivity.StudentProgressReport report) {
            List<ReadingSession> sessions = report.getSessions();
            
            if (sessions == null || sessions.isEmpty()) {
                android.widget.Toast.makeText(context, "No reading sessions found", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Create dialog
            android.app.Dialog dialog = new android.app.Dialog(context);
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.dialog_session_scores);
            dialog.getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            
            // Initialize views
            TextView dialogTitle = dialog.findViewById(R.id.dialogTitle);
            androidx.recyclerview.widget.RecyclerView sessionsRecyclerView = dialog.findViewById(R.id.sessionsRecyclerView);
            TextView emptyStateText = dialog.findViewById(R.id.emptyStateText);
            android.widget.Button btnClose = dialog.findViewById(R.id.btnClose);
            
            // Set title
            dialogTitle.setText(report.getStudent().getName() + "'s Reading Sessions");
            
            // Setup RecyclerView
            if (sessions.isEmpty()) {
                sessionsRecyclerView.setVisibility(View.GONE);
                emptyStateText.setVisibility(View.VISIBLE);
            } else {
                sessionsRecyclerView.setVisibility(View.VISIBLE);
                emptyStateText.setVisibility(View.GONE);
                
                SessionScoreAdapter adapter = new SessionScoreAdapter(sessions);
                sessionsRecyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(context));
                sessionsRecyclerView.setAdapter(adapter);
            }
            
            // Close button
            btnClose.setOnClickListener(v -> dialog.dismiss());
            
            dialog.show();
        }
        
        private void setProgressIndicatorColor(int progress) {
            int color;
            if (progress >= 90) {
                color = 0xFF4CAF50; // Green
            } else if (progress >= 70) {
                color = 0xFF2196F3; // Blue
            } else if (progress >= 50) {
                color = 0xFFFF9800; // Orange
            } else {
                color = 0xFFF44336; // Red
            }
            progressIndicator.setBackgroundColor(color);
        }
    }
}
