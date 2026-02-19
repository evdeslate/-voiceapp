package com.example.speak;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class PassageAdapter extends RecyclerView.Adapter<PassageAdapter.PassageViewHolder> {

    private List<Passage> passageList;
    private OnPassageActionListener actionListener;

    public interface OnPassageActionListener {
        void onEditClicked(Passage passage);
        void onDeleteClicked(Passage passage);
    }

    public PassageAdapter(List<Passage> passageList, OnPassageActionListener actionListener) {
        this.passageList = passageList;
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public PassageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_passage, parent, false);
        return new PassageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PassageViewHolder holder, int position) {
        Passage passage = passageList.get(position);
        holder.bind(passage, actionListener);
    }

    @Override
    public int getItemCount() {
        return passageList != null ? passageList.size() : 0;
    }

    public static class PassageViewHolder extends RecyclerView.ViewHolder {
        
        private TextView tvPassageTitle;
        private TextView tvPassageDifficulty;
        private TextView tvWordCount;
        private TextView tvPassagePreview;
        private ImageButton btnEdit;
        private ImageButton btnDelete;

        public PassageViewHolder(@NonNull View itemView) {
            super(itemView);
            
            tvPassageTitle = itemView.findViewById(R.id.tvPassageTitle);
            tvPassageDifficulty = itemView.findViewById(R.id.tvPassageDifficulty);
            tvWordCount = itemView.findViewById(R.id.tvWordCount);
            tvPassagePreview = itemView.findViewById(R.id.tvPassagePreview);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        public void bind(Passage passage, OnPassageActionListener actionListener) {
            tvPassageTitle.setText(passage.getTitle());
            tvPassageDifficulty.setText(passage.getDifficulty());
            tvWordCount.setText(passage.getWordCount() + " words");
            
            // Show preview (first 100 characters)
            String preview = passage.getContent();
            if (preview != null && preview.length() > 100) {
                preview = preview.substring(0, 100) + "...";
            }
            tvPassagePreview.setText(preview);
            
            btnEdit.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onEditClicked(passage);
                }
            });
            
            btnDelete.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onDeleteClicked(passage);
                }
            });
        }
    }
}
