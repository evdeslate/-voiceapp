package com.example.speak;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.textfield.TextInputEditText;

public class EditPassageDialog extends DialogFragment {
    
    private static final String ARG_PASSAGE_ID = "passage_id";
    private static final String ARG_PASSAGE_TITLE = "passage_title";
    private static final String ARG_PASSAGE_CONTENT = "passage_content";
    private static final String ARG_PASSAGE_DIFFICULTY = "passage_difficulty";
    
    private TextInputEditText etTitle;
    private TextInputEditText etContent;
    private Spinner spinnerDifficulty;
    private Button btnSave;
    private Button btnCancel;
    
    private PassageRepository passageRepository;
    private OnPassageUpdatedListener listener;
    private Passage passage;
    
    public interface OnPassageUpdatedListener {
        void onPassageUpdated(Passage passage);
        void onError(String message);
    }
    
    public static EditPassageDialog newInstance(Passage passage) {
        EditPassageDialog dialog = new EditPassageDialog();
        Bundle args = new Bundle();
        args.putString(ARG_PASSAGE_ID, passage.getId());
        args.putString(ARG_PASSAGE_TITLE, passage.getTitle());
        args.putString(ARG_PASSAGE_CONTENT, passage.getContent());
        args.putString(ARG_PASSAGE_DIFFICULTY, passage.getDifficulty());
        dialog.setArguments(args);
        return dialog;
    }
    
    public void setOnPassageUpdatedListener(OnPassageUpdatedListener listener) {
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        passageRepository = new PassageRepository();
        
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_add_passage, null);
        
        etTitle = view.findViewById(R.id.etTitle);
        etContent = view.findViewById(R.id.etContent);
        spinnerDifficulty = view.findViewById(R.id.spinnerDifficulty);
        btnSave = view.findViewById(R.id.btnSave);
        btnCancel = view.findViewById(R.id.btnCancel);
        
        // Setup difficulty spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.difficulty_levels, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDifficulty.setAdapter(adapter);
        
        // Pre-fill data
        if (getArguments() != null) {
            etTitle.setText(getArguments().getString(ARG_PASSAGE_TITLE));
            etContent.setText(getArguments().getString(ARG_PASSAGE_CONTENT));
            
            String difficulty = getArguments().getString(ARG_PASSAGE_DIFFICULTY);
            String[] difficulties = getResources().getStringArray(R.array.difficulty_levels);
            for (int i = 0; i < difficulties.length; i++) {
                if (difficulties[i].equals(difficulty)) {
                    spinnerDifficulty.setSelection(i);
                    break;
                }
            }
        }
        
        btnSave.setOnClickListener(v -> updatePassage());
        btnCancel.setOnClickListener(v -> dismiss());
        
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setView(view);
        
        return builder.create();
    }
    
    private void updatePassage() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String content = etContent.getText() != null ? etContent.getText().toString().trim() : "";
        String difficulty = spinnerDifficulty.getSelectedItem().toString();
        
        if (title.isEmpty()) {
            etTitle.setError("Title is required");
            return;
        }
        
        if (content.isEmpty()) {
            etContent.setError("Content is required");
            return;
        }
        
        String passageId = getArguments() != null ? getArguments().getString(ARG_PASSAGE_ID) : null;
        Passage passage = new Passage(passageId, title, content, difficulty);
        
        btnSave.setEnabled(false);
        
        passageRepository.updatePassage(passage, new PassageRepository.OnPassageUpdatedListener() {
            @Override
            public void onSuccess(Passage updatedPassage) {
                if (isAdded() && getActivity() != null) {
                    if (listener != null) {
                        listener.onPassageUpdated(updatedPassage);
                    }
                    dismiss();
                }
            }
            
            @Override
            public void onFailure(String error) {
                if (isAdded() && getActivity() != null) {
                    btnSave.setEnabled(true);
                    if (listener != null) {
                        listener.onError(error);
                    }
                }
            }
        });
    }
}
