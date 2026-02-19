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

public class AddPassageDialog extends DialogFragment {
    
    private TextInputEditText etTitle;
    private TextInputEditText etContent;
    private Spinner spinnerDifficulty;
    private Button btnSave;
    private Button btnCancel;
    
    private PassageRepository passageRepository;
    private OnPassageAddedListener listener;
    
    public interface OnPassageAddedListener {
        void onPassageAdded(Passage passage);
        void onError(String message);
    }
    
    public void setOnPassageAddedListener(OnPassageAddedListener listener) {
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
        
        btnSave.setOnClickListener(v -> savePassage());
        btnCancel.setOnClickListener(v -> dismiss());
        
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setView(view);
        
        return builder.create();
    }
    
    private void savePassage() {
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
        
        Passage passage = new Passage(null, title, content, difficulty);
        
        btnSave.setEnabled(false);
        
        passageRepository.createPassage(passage, new PassageRepository.OnPassageCreatedListener() {
            @Override
            public void onSuccess(Passage createdPassage) {
                if (isAdded() && getActivity() != null) {
                    if (listener != null) {
                        listener.onPassageAdded(createdPassage);
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
