package com.example.speak;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for managing reading session data with offline support
 * Stores student reading performance for progress tracking and reports
 * Each teacher has isolated data - sessions are stored under teachers/{teacherId}/reading_sessions/
 * 
 * AUTO-DELETION: Sessions older than 90 days are automatically deleted when loading data
 */
public class ReadingSessionRepository {
    private DatabaseReference sessionsRef;
    private String teacherId;
    private static final String TEACHERS_NODE = "teachers";
    private static final String SESSIONS_NODE = "reading_sessions";
    private static final long SESSION_RETENTION_DAYS = 90; // Auto-delete sessions older than 90 days
    private static final long SESSION_RETENTION_MS = SESSION_RETENTION_DAYS * 24 * 60 * 60 * 1000L;
    
    public interface OnSessionSavedListener {
        void onSuccess(ReadingSession session);
        void onFailure(String error);
    }
    
    public interface OnSessionsLoadedListener {
        void onSuccess(List<ReadingSession> sessions);
        void onFailure(String error);
    }
    
    public ReadingSessionRepository() {
        // Get current teacher ID from Firebase Auth
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            android.util.Log.e("ReadingSessionRepo", "No authenticated user found!");
            throw new IllegalStateException("User must be authenticated to access reading session data");
        }
        
        teacherId = currentUser.getUid();
        android.util.Log.d("ReadingSessionRepo", "Teacher ID: " + teacherId);
        
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        
        // Use teacher-scoped path: teachers/{teacherId}/reading_sessions
        sessionsRef = database.getReference(TEACHERS_NODE).child(teacherId).child(SESSIONS_NODE);
        
        // Enable offline sync for reading sessions
        sessionsRef.keepSynced(true);
        android.util.Log.d("ReadingSessionRepo", "Offline sync enabled for reading sessions at path: " + sessionsRef.toString());
    }
    
    /**
     * Save a reading session to Firebase and update student progress
     */
    public void saveSession(ReadingSession session, OnSessionSavedListener listener) {
        android.util.Log.d("ReadingSessionRepo", "Saving reading session for student: " + session.getStudentName());
        
        // Generate unique ID
        String sessionId = sessionsRef.push().getKey();
        
        if (sessionId == null) {
            listener.onFailure("Failed to generate session ID");
            return;
        }
        
        session.setId(sessionId);
        session.setTeacherId(teacherId);
        
        // Save to Firebase
        sessionsRef.child(sessionId).setValue(session)
            .addOnSuccessListener(aVoid -> {
                android.util.Log.d("ReadingSessionRepo", "✅ Session saved successfully: " + sessionId);
                
                // IMPORTANT: Call listener immediately so UI can show results
                // This ensures offline mode works (Firebase queues the write)
                listener.onSuccess(session);
                
                // Update student progress in background (don't wait for it)
                // This prevents blocking the UI when offline
                updateStudentProgressInBackground(session);
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("ReadingSessionRepo", "Failed to save session: " + e.getMessage());
                listener.onFailure("Failed to save session: " + e.getMessage());
            });
    }
    
    /**
     * Update student progress after saving a reading session (background operation)
     * This runs asynchronously and doesn't block the UI
     */
    private void updateStudentProgressInBackground(ReadingSession session) {
        String studentId = session.getStudentId();
        
        if (studentId == null || studentId.isEmpty()) {
            android.util.Log.w("ReadingSessionRepo", "No student ID in session, skipping progress update");
            return;
        }
        
        // Load all sessions for this student to calculate average
        loadStudentSessions(studentId, new OnSessionsLoadedListener() {
            @Override
            public void onSuccess(List<ReadingSession> sessions) {
                // Calculate average overall score
                float totalScore = 0.0f;
                for (ReadingSession s : sessions) {
                    totalScore += s.getOverallScore();
                }
                float averageScore = totalScore / sessions.size();
                
                android.util.Log.d("ReadingSessionRepo", String.format(
                    "Updating student %s progress: %.1f%% (average of %d sessions)",
                    studentId, averageScore * 100, sessions.size()));
                
                // Update student progress
                StudentRepository studentRepo = new StudentRepository();
                studentRepo.updateStudentProgress(studentId, averageScore, new StudentRepository.OnStudentUpdatedListener() {
                    @Override
                    public void onSuccess(Student student) {
                        android.util.Log.d("ReadingSessionRepo", "✅ Student progress updated successfully");
                    }
                    
                    @Override
                    public void onFailure(String error) {
                        android.util.Log.w("ReadingSessionRepo", "⚠️ Failed to update student progress: " + error);
                    }
                });
            }
            
            @Override
            public void onFailure(String error) {
                android.util.Log.w("ReadingSessionRepo", "⚠️ Failed to load sessions for progress update: " + error);
            }
        });
    }
    
    /**
     * Load all sessions for a specific student
     * Automatically deletes sessions older than 90 days
     */
    public void loadStudentSessions(String studentId, OnSessionsLoadedListener listener) {
        android.util.Log.d("ReadingSessionRepo", "Loading sessions for student: " + studentId);
        
        Query query = sessionsRef.orderByChild("studentId").equalTo(studentId);
        
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<ReadingSession> sessions = new ArrayList<>();
                List<String> oldSessionIds = new ArrayList<>();
                long currentTime = System.currentTimeMillis();
                long cutoffTime = currentTime - SESSION_RETENTION_MS;
                
                for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
                    ReadingSession session = sessionSnapshot.getValue(ReadingSession.class);
                    if (session != null) {
                        // Check if session is older than 90 days
                        if (session.getTimestamp() < cutoffTime) {
                            oldSessionIds.add(session.getId());
                            android.util.Log.d("ReadingSessionRepo", 
                                "Session " + session.getId() + " is older than " + SESSION_RETENTION_DAYS + " days - marking for deletion");
                        } else {
                            sessions.add(session);
                        }
                    }
                }
                
                // Delete old sessions in background
                if (!oldSessionIds.isEmpty()) {
                    deleteOldSessionsInBackground(oldSessionIds);
                }
                
                // Sort by timestamp (most recent first)
                sessions.sort((s1, s2) -> Long.compare(s2.getTimestamp(), s1.getTimestamp()));
                
                android.util.Log.d("ReadingSessionRepo", 
                    "Loaded " + sessions.size() + " sessions, deleted " + oldSessionIds.size() + " old sessions");
                listener.onSuccess(sessions);
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                android.util.Log.e("ReadingSessionRepo", "Failed to load sessions: " + databaseError.getMessage());
                listener.onFailure("Failed to load sessions: " + databaseError.getMessage());
            }
        });
    }
    
    /**
     * Load all sessions (for progress reports)
     * Automatically deletes sessions older than 90 days
     */
    public void loadAllSessions(OnSessionsLoadedListener listener) {
        android.util.Log.d("ReadingSessionRepo", "Loading all reading sessions...");
        
        sessionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<ReadingSession> sessions = new ArrayList<>();
                List<String> oldSessionIds = new ArrayList<>();
                long currentTime = System.currentTimeMillis();
                long cutoffTime = currentTime - SESSION_RETENTION_MS;
                
                for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
                    ReadingSession session = sessionSnapshot.getValue(ReadingSession.class);
                    if (session != null) {
                        // Check if session is older than 90 days
                        if (session.getTimestamp() < cutoffTime) {
                            oldSessionIds.add(session.getId());
                            android.util.Log.d("ReadingSessionRepo", 
                                "Session " + session.getId() + " is older than " + SESSION_RETENTION_DAYS + " days - marking for deletion");
                        } else {
                            sessions.add(session);
                        }
                    }
                }
                
                // Delete old sessions in background
                if (!oldSessionIds.isEmpty()) {
                    deleteOldSessionsInBackground(oldSessionIds);
                }
                
                // Sort by timestamp (most recent first)
                sessions.sort((s1, s2) -> Long.compare(s2.getTimestamp(), s1.getTimestamp()));
                
                android.util.Log.d("ReadingSessionRepo", 
                    "Loaded " + sessions.size() + " total sessions, deleted " + oldSessionIds.size() + " old sessions");
                listener.onSuccess(sessions);
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                android.util.Log.e("ReadingSessionRepo", "Failed to load sessions: " + databaseError.getMessage());
                listener.onFailure("Failed to load sessions: " + databaseError.getMessage());
            }
        });
    }
    
    /**
     * Load recent sessions (last N sessions)
     * Automatically deletes sessions older than 90 days
     */
    public void loadRecentSessions(int limit, OnSessionsLoadedListener listener) {
        android.util.Log.d("ReadingSessionRepo", "Loading " + limit + " recent sessions...");
        
        Query query = sessionsRef.orderByChild("timestamp").limitToLast(limit * 2); // Load extra to account for deletions
        
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<ReadingSession> sessions = new ArrayList<>();
                List<String> oldSessionIds = new ArrayList<>();
                long currentTime = System.currentTimeMillis();
                long cutoffTime = currentTime - SESSION_RETENTION_MS;
                
                for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
                    ReadingSession session = sessionSnapshot.getValue(ReadingSession.class);
                    if (session != null) {
                        // Check if session is older than 90 days
                        if (session.getTimestamp() < cutoffTime) {
                            oldSessionIds.add(session.getId());
                            android.util.Log.d("ReadingSessionRepo", 
                                "Session " + session.getId() + " is older than " + SESSION_RETENTION_DAYS + " days - marking for deletion");
                        } else {
                            sessions.add(session);
                        }
                    }
                }
                
                // Delete old sessions in background
                if (!oldSessionIds.isEmpty()) {
                    deleteOldSessionsInBackground(oldSessionIds);
                }
                
                // Sort by timestamp (most recent first) and limit
                sessions.sort((s1, s2) -> Long.compare(s2.getTimestamp(), s1.getTimestamp()));
                if (sessions.size() > limit) {
                    sessions = sessions.subList(0, limit);
                }
                
                android.util.Log.d("ReadingSessionRepo", 
                    "Loaded " + sessions.size() + " recent sessions, deleted " + oldSessionIds.size() + " old sessions");
                listener.onSuccess(sessions);
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                android.util.Log.e("ReadingSessionRepo", "Failed to load sessions: " + databaseError.getMessage());
                listener.onFailure("Failed to load sessions: " + databaseError.getMessage());
            }
        });
    }
    
    /**
     * Delete a reading session
     */
    public void deleteSession(String sessionId, OnSessionSavedListener listener) {
        if (sessionId == null || sessionId.isEmpty()) {
            listener.onFailure("Session ID is required for deletion");
            return;
        }
        
        sessionsRef.child(sessionId).removeValue()
            .addOnSuccessListener(aVoid -> {
                android.util.Log.d("ReadingSessionRepo", "Session deleted successfully");
                listener.onSuccess(null);
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("ReadingSessionRepo", "Failed to delete session: " + e.getMessage());
                listener.onFailure("Failed to delete session: " + e.getMessage());
            });
    }
    
    /**
     * Delete old sessions in background (doesn't block UI)
     * Called automatically when loading sessions
     */
    private void deleteOldSessionsInBackground(List<String> sessionIds) {
        android.util.Log.d("ReadingSessionRepo", "🗑️ Deleting " + sessionIds.size() + " old sessions in background");
        
        for (String sessionId : sessionIds) {
            sessionsRef.child(sessionId).removeValue()
                .addOnSuccessListener(aVoid -> 
                    android.util.Log.d("ReadingSessionRepo", "✅ Deleted old session: " + sessionId))
                .addOnFailureListener(e -> 
                    android.util.Log.w("ReadingSessionRepo", "⚠️ Failed to delete old session " + sessionId + ": " + e.getMessage()));
        }
    }
    
    /**
     * Manually trigger cleanup of old sessions (can be called from settings or admin panel)
     * This is useful for one-time cleanup or scheduled maintenance
     */
    public void cleanupOldSessions(OnSessionsLoadedListener listener) {
        android.util.Log.d("ReadingSessionRepo", "🧹 Starting manual cleanup of sessions older than " + SESSION_RETENTION_DAYS + " days");
        
        sessionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<String> oldSessionIds = new ArrayList<>();
                long currentTime = System.currentTimeMillis();
                long cutoffTime = currentTime - SESSION_RETENTION_MS;
                
                for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
                    ReadingSession session = sessionSnapshot.getValue(ReadingSession.class);
                    if (session != null && session.getTimestamp() < cutoffTime) {
                        oldSessionIds.add(session.getId());
                    }
                }
                
                if (oldSessionIds.isEmpty()) {
                    android.util.Log.d("ReadingSessionRepo", "✅ No old sessions to delete");
                    listener.onSuccess(new ArrayList<>());
                } else {
                    android.util.Log.d("ReadingSessionRepo", "🗑️ Found " + oldSessionIds.size() + " old sessions to delete");
                    deleteOldSessionsInBackground(oldSessionIds);
                    listener.onSuccess(new ArrayList<>());
                }
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                android.util.Log.e("ReadingSessionRepo", "Failed to cleanup old sessions: " + databaseError.getMessage());
                listener.onFailure("Failed to cleanup old sessions: " + databaseError.getMessage());
            }
        });
    }
}
