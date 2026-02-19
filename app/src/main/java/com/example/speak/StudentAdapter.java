package com.example.speak;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.StudentViewHolder> {

    private List<Student> studentList;
    private OnStudentClickListener listener;
    private OnStudentActionListener actionListener;
    private boolean showActionButtons;

    // Interface for handling student clicks
    public interface OnStudentClickListener {
        void onStudentClick(Student student);
    }

    // Interface for handling edit and delete actions
    public interface OnStudentActionListener {
        void onEditClicked(Student student);
        void onDeleteClicked(Student student);
    }

    // Constructor for dashboard (no action buttons)
    public StudentAdapter(List<Student> studentList, OnStudentClickListener listener) {
        this.studentList = studentList;
        this.listener = listener;
        this.showActionButtons = false;
    }

    // Constructor for management (with action buttons)
    public StudentAdapter(List<Student> studentList, OnStudentActionListener actionListener) {
        this.studentList = studentList;
        this.actionListener = actionListener;
        this.showActionButtons = true;
    }

    @NonNull
    @Override
    public StudentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student, parent, false);
        return new StudentViewHolder(view, showActionButtons);
    }

    @Override
    public void onBindViewHolder(@NonNull StudentViewHolder holder, int position) {
        Student student = studentList.get(position);
        holder.bind(student, listener, actionListener);
    }

    @Override
    public int getItemCount() {
        return studentList != null ? studentList.size() : 0;
    }

    // ViewHolder class
    public static class StudentViewHolder extends RecyclerView.ViewHolder {
        
        private ImageView studentAvatar;
        private TextView studentName;
        private TextView tvStudentGrade;
        private TextView tvStudentSection;
        private ImageButton btnEdit;
        private ImageButton btnDelete;
        private View progressContainer;
        private ConstraintLayout progressCircle;
        private TextView tvProgress;
        private TextView tvReadingLevel;
        private boolean hasActionButtons;

        public StudentViewHolder(@NonNull View itemView, boolean hasActionButtons) {
            super(itemView);
            this.hasActionButtons = hasActionButtons;
            
            // Initialize all views
            studentAvatar = itemView.findViewById(R.id.studentAvatar);
            studentName = itemView.findViewById(R.id.tvStudentName);
            tvStudentGrade = itemView.findViewById(R.id.tvStudentGrade);
            tvStudentSection = itemView.findViewById(R.id.tvStudentSection);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            progressContainer = itemView.findViewById(R.id.progressContainer);
            progressCircle = itemView.findViewById(R.id.progressCircle);
            tvProgress = itemView.findViewById(R.id.tvProgress);
            tvReadingLevel = itemView.findViewById(R.id.tvReadingLevel);
            
            // Show/hide elements based on context
            if (hasActionButtons) {
                // Management view - show buttons, hide progress
                btnEdit.setVisibility(View.VISIBLE);
                btnDelete.setVisibility(View.VISIBLE);
                progressContainer.setVisibility(View.GONE);
            } else {
                // Dashboard view - hide buttons, show progress
                btnEdit.setVisibility(View.GONE);
                btnDelete.setVisibility(View.GONE);
                progressContainer.setVisibility(View.VISIBLE);
            }
        }

        public void bind(Student student, OnStudentClickListener listener, OnStudentActionListener actionListener) {
            android.util.Log.d("StudentAdapter", "Binding student: " + student.getName());
            
            // Set student name
            studentName.setText(student.getName());
            
            // Set avatar
            if (student.getAvatarResource() != 0) {
                studentAvatar.setImageResource(student.getAvatarResource());
            } else {
                studentAvatar.setImageResource(R.drawable.ic_launcher_background);
            }
            
            if (hasActionButtons) {
                // Management view - show grade and section
                if (student.getGrade() != null && !student.getGrade().isEmpty()) {
                    tvStudentGrade.setText("Grade: " + student.getGrade());
                    tvStudentGrade.setVisibility(View.VISIBLE);
                } else {
                    tvStudentGrade.setVisibility(View.GONE);
                }
                
                if (student.getSection() != null && !student.getSection().isEmpty()) {
                    tvStudentSection.setText("Section: " + student.getSection());
                    tvStudentSection.setVisibility(View.VISIBLE);
                } else {
                    tvStudentSection.setVisibility(View.GONE);
                }
                
                // Set up edit button
                btnEdit.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onEditClicked(student);
                    }
                });
                
                // Set up delete button
                btnDelete.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onDeleteClicked(student);
                    }
                });
            } else {
                // Dashboard view - hide grade/section, show progress
                tvStudentGrade.setVisibility(View.GONE);
                tvStudentSection.setVisibility(View.GONE);
                
                // Set progress
                tvProgress.setText(student.getProgressText());
                tvReadingLevel.setText(student.getReadingLevel());
                
                // Set progress circle color
                setProgressCircleColor(student.getProgress());
            }
            
            // Set click listener for entire item
            if (listener != null) {
                itemView.setOnClickListener(v -> listener.onStudentClick(student));
            }
        }

        private void setProgressCircleColor(int progress) {
            int color;
            if (progress >= 90) {
                color = 0xFF4CAF50; // Green
            } else if (progress >= 80) {
                color = 0xFF9C27B0; // Purple
            } else if (progress >= 70) {
                color = 0xFF2196F3; // Blue
            } else if (progress >= 60) {
                color = 0xFFFF9800; // Orange
            } else {
                color = 0xFFF44336; // Red
            }
            progressCircle.setBackgroundColor(color);
        }
    }

    // Method to update the student list
    public void updateStudentList(List<Student> newStudentList) {
        this.studentList = newStudentList;
        notifyDataSetChanged();
    }

    // Method to add a student
    public void addStudent(Student student) {
        if (studentList != null) {
            studentList.add(student);
            notifyItemInserted(studentList.size() - 1);
        }
    }

    // Method to remove a student
    public void removeStudent(int position) {
        if (studentList != null && position >= 0 && position < studentList.size()) {
            studentList.remove(position);
            notifyItemRemoved(position);
        }
    }

    // Method to update a specific student
    public void updateStudent(int position, Student updatedStudent) {
        if (studentList != null && position >= 0 && position < studentList.size()) {
            studentList.set(position, updatedStudent);
            notifyItemChanged(position);
        }
    }
}
