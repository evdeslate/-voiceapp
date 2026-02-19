package com.example.speak;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for managing passage data with offline support
 * Each teacher has isolated data - passages are stored under teachers/{teacherId}/passages/
 */
public class PassageRepository {
    private DatabaseReference passagesRef;
    private String teacherId;
    private static final String TEACHERS_NODE = "teachers";
    private static final String PASSAGES_NODE = "passages";
    
    public interface OnPassageCreatedListener {
        void onSuccess(Passage passage);
        void onFailure(String error);
    }
    
    public interface OnPassagesLoadedListener {
        void onSuccess(List<Passage> passages);
        void onFailure(String error);
    }
    
    public interface OnPassageUpdatedListener {
        void onSuccess(Passage passage);
        void onFailure(String error);
    }
    
    public interface OnPassageDeletedListener {
        void onSuccess();
        void onFailure(String error);
    }
    
    public PassageRepository() {
        // Get current teacher ID from Firebase Auth
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            android.util.Log.e("PassageRepository", "No authenticated user found!");
            throw new IllegalStateException("User must be authenticated to access passage data");
        }
        
        teacherId = currentUser.getUid();
        android.util.Log.d("PassageRepository", "Teacher ID: " + teacherId);
        
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        
        // Use teacher-scoped path: teachers/{teacherId}/passages
        passagesRef = database.getReference(TEACHERS_NODE).child(teacherId).child(PASSAGES_NODE);
        
        // Keep data synced for offline access
        passagesRef.keepSynced(true);
        android.util.Log.d("PassageRepository", "Offline sync enabled for passages at path: " + passagesRef.toString());
    }
    
    public void createPassage(Passage passage, OnPassageCreatedListener listener) {
        String passageId = passagesRef.push().getKey();
        
        if (passageId == null) {
            listener.onFailure("Failed to generate passage ID");
            return;
        }
        
        passage.setId(passageId);
        passage.setTeacherId(teacherId);
        
        passagesRef.child(passageId).setValue(passage)
            .addOnSuccessListener(aVoid -> listener.onSuccess(passage))
            .addOnFailureListener(e -> listener.onFailure("Failed to save passage: " + e.getMessage()));
    }
    
    public void loadAllPassages(OnPassagesLoadedListener listener) {
        passagesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<Passage> passages = new ArrayList<>();
                
                for (DataSnapshot passageSnapshot : dataSnapshot.getChildren()) {
                    Passage passage = passageSnapshot.getValue(Passage.class);
                    if (passage != null) {
                        passages.add(passage);
                    }
                }
                
                listener.onSuccess(passages);
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                listener.onFailure("Failed to load passages: " + databaseError.getMessage());
            }
        });
    }
    
    public void updatePassage(Passage passage, OnPassageUpdatedListener listener) {
        String passageId = passage.getId();
        
        if (passageId == null || passageId.isEmpty()) {
            listener.onFailure("Passage ID is required for update");
            return;
        }
        
        passagesRef.child(passageId).setValue(passage)
            .addOnSuccessListener(aVoid -> listener.onSuccess(passage))
            .addOnFailureListener(e -> listener.onFailure("Failed to update passage: " + e.getMessage()));
    }
    
    public void deletePassage(String passageId, OnPassageDeletedListener listener) {
        if (passageId == null || passageId.isEmpty()) {
            listener.onFailure("Passage ID is required for deletion");
            return;
        }
        
        passagesRef.child(passageId).removeValue()
            .addOnSuccessListener(aVoid -> listener.onSuccess())
            .addOnFailureListener(e -> listener.onFailure("Failed to delete passage: " + e.getMessage()));
    }
}
