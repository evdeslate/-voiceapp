package com.example.speak;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.List;

public class PassageManagementActivity extends AppCompatActivity {

    private RecyclerView passagesRecyclerView;
    private PassageAdapter passageAdapter;
    private List<Passage> passageList;
    private PassageRepository passageRepository;
    private FloatingActionButton fabAddPassage;
    private ImageButton btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Verify user has teacher role
        verifyTeacherRole();
    }
    
    private void verifyTeacherRole() {
        UserRole.verifyRole(this, UserRole.ROLE_TEACHER, new UserRole.OnRoleVerifiedListener() {
            @Override
            public void onAuthorized() {
                initializeActivity();
            }
            
            @Override
            public void onUnauthorized(String reason) {
                Toast.makeText(PassageManagementActivity.this, 
                    "Access Denied: Teachers only", 
                    Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }
    
    private void initializeActivity() {
        setContentView(R.layout.activity_passage_management);

        initializeViews();
        setupRecyclerView();
        setupClickListeners();
        loadPassages();
    }

    private void initializeViews() {
        passagesRecyclerView = findViewById(R.id.passagesRecyclerView);
        fabAddPassage = findViewById(R.id.fabAddPassage);
        btnBack = findViewById(R.id.btnBack);
    }

    private void setupRecyclerView() {
        passageList = new ArrayList<>();
        passageRepository = new PassageRepository();
        
        passageAdapter = new PassageAdapter(passageList, new PassageAdapter.OnPassageActionListener() {
            @Override
            public void onEditClicked(Passage passage) {
                showEditPassageDialog(passage);
            }
            
            @Override
            public void onDeleteClicked(Passage passage) {
                showDeleteConfirmation(passage);
            }
        });
        
        passagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        passagesRecyclerView.setAdapter(passageAdapter);
    }

    private void setupClickListeners() {
        fabAddPassage.setOnClickListener(v -> showAddPassageDialog());
        btnBack.setOnClickListener(v -> finish());
    }

    private void loadPassages() {
        android.util.Log.d("PassageManagement", "Loading passages (offline-enabled)...");
        
        passageRepository.loadAllPassages(new PassageRepository.OnPassagesLoadedListener() {
            @Override
            public void onSuccess(List<Passage> passages) {
                android.util.Log.d("PassageManagement", "Successfully loaded " + passages.size() + " passages");
                
                passageList.clear();
                passageList.addAll(passages);
                passageAdapter.notifyDataSetChanged();
                
                // If no passages exist, initialize default passages
                if (passages.isEmpty()) {
                    android.util.Log.d("PassageManagement", "No passages found, initializing defaults");
                    initializeDefaultPassages();
                } else {
                    android.util.Log.d("PassageManagement", "Passages loaded (may be from offline cache)");
                }
            }
            
            @Override
            public void onFailure(String error) {
                android.util.Log.e("PassageManagement", "Error loading passages: " + error);
                // Don't show error toast - Firebase offline persistence will handle it
                // Data will be loaded from cache if available
                android.util.Log.d("PassageManagement", "Attempting to load from offline cache...");
            }
        });
    }
    
    private void initializeDefaultPassages() {
        // Create default passages from the existing content
        Passage[] defaultPassages = {
            new Passage(null, "Test", 
                "Maria woke up early and looked outside the window. The sun was bright, and the birds were singing in the trees. She washed her face, ate breakfast, and packed her bag for school. On the way, she met her friend and they walked together with happy smiles.", 
                "Easy"),
            
            new Passage(null, "Cat and Mouse",
                "A mouse and a cat lived in an old house. The mouse stayed in a hole while the cat slept under the table. One night, the mouse got out of its hole. \"Mmm, Cheese!\" it thought, as it went up the table. As it started nibbling the cheese, a fork fell. It woke the cat up so it ran up the table. But the mouse was too fast for the cat. It quickly dashed to its hole. Safe at last!",
                "Easy"),
            
            new Passage(null, "Marian's Experiment",
                "Marian came home from school. She went to the kitchen and saw her mother cooking. \"Mama, do we have mongo seeds?\" asked Marian. \"I will do an experiment.\" \"Yes, we have some in the cabinet,\" answered Mama. Marian got some seeds and planted them in a wooden box. She watered the seeds every day. She made sure they got enough sun. After three days, Marian was happy to see stems and leaves sprouting. Her mongo seeds grew into young plants.",
                "Medium"),
            
            new Passage(null, "The Snail with the Biggest House",
                "A little snail told his father, \"I want to have the biggest house.\" \"Keep your house light and easy to carry,\" said his father. But, the snail ate a lot until his house grew enormous. \"You now have the biggest house,\" said the snails. After a while, the snails have eaten all the grass in the farm. They decided to move to another place. \"Help! I cannot move,\" said the snail with the biggest house. The snails tried to help but the house was too heavy. So the snail with the biggest house was left behind.",
                "Medium"),
            
            new Passage(null, "The Tricycle Man",
                "Nick is a tricycle man. He waits for riders every morning. \"Please take me to the bus station,\" says Mr. Perez. \"Please take me to the market,\" says Mrs. Pardo. \"Please take us to school,\" say Mike and Kris. \"But I can take only one of you,\" says Nick to the children. \"Oh, I can sit behind you Nick,\" says Mr. Perez. \"Kris or Mike can take my seat.\" \"Thank you, Mr. Perez,\" say Mike and Kris.",
                "Medium"),
            
            new Passage(null, "Anansi's Web",
                "Anansi was tired of her web. So one day, she said \"I will go live with the ant.\" Now, the ant lived in a small hill. Once in the hill Anansi cried, \"This place is too dark! I will go live with the bees.\" When she got to the beehive, Anansi cried, \"This place is too hot and sticky! I will go live with the beetle.\" But on her way to beetle's home she saw her web. \"Maybe a web is the best place after all.\"",
                "Hard")
        };
        
        // Save each default passage to Firebase
        for (Passage passage : defaultPassages) {
            passageRepository.createPassage(passage, new PassageRepository.OnPassageCreatedListener() {
                @Override
                public void onSuccess(Passage createdPassage) {
                    passageList.add(createdPassage);
                    passageAdapter.notifyItemInserted(passageList.size() - 1);
                }
                
                @Override
                public void onFailure(String error) {
                    // Silently fail for initialization
                    android.util.Log.e("PassageManagement", "Failed to create default passage: " + error);
                }
            });
        }
        
        Toast.makeText(this, "Initialized default passages", Toast.LENGTH_SHORT).show();
    }

    private void showAddPassageDialog() {
        AddPassageDialog dialog = new AddPassageDialog();
        dialog.setOnPassageAddedListener(new AddPassageDialog.OnPassageAddedListener() {
            @Override
            public void onPassageAdded(Passage passage) {
                passageList.add(passage);
                passageAdapter.notifyItemInserted(passageList.size() - 1);
                Toast.makeText(PassageManagementActivity.this, "Passage added successfully", Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onError(String message) {
                Toast.makeText(PassageManagementActivity.this, "Error: " + message, Toast.LENGTH_SHORT).show();
            }
        });
        
        dialog.show(getSupportFragmentManager(), "AddPassageDialog");
    }

    private void showEditPassageDialog(Passage passage) {
        EditPassageDialog dialog = EditPassageDialog.newInstance(passage);
        dialog.setOnPassageUpdatedListener(new EditPassageDialog.OnPassageUpdatedListener() {
            @Override
            public void onPassageUpdated(Passage updatedPassage) {
                for (int i = 0; i < passageList.size(); i++) {
                    if (passageList.get(i).getId().equals(updatedPassage.getId())) {
                        passageList.set(i, updatedPassage);
                        passageAdapter.notifyItemChanged(i);
                        break;
                    }
                }
                Toast.makeText(PassageManagementActivity.this, "Passage updated successfully", Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onError(String message) {
                Toast.makeText(PassageManagementActivity.this, "Error: " + message, Toast.LENGTH_SHORT).show();
            }
        });
        
        dialog.show(getSupportFragmentManager(), "EditPassageDialog");
    }

    private void showDeleteConfirmation(Passage passage) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Passage");
        builder.setMessage("Are you sure you want to delete \"" + passage.getTitle() + "\"?");
        
        builder.setPositiveButton("Delete", (dialog, which) -> deletePassage(passage));
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        builder.show();
    }

    private void deletePassage(Passage passage) {
        passageRepository.deletePassage(passage.getId(), new PassageRepository.OnPassageDeletedListener() {
            @Override
            public void onSuccess() {
                for (int i = 0; i < passageList.size(); i++) {
                    if (passageList.get(i).getId().equals(passage.getId())) {
                        passageList.remove(i);
                        passageAdapter.notifyItemRemoved(i);
                        break;
                    }
                }
                Toast.makeText(PassageManagementActivity.this, "Passage deleted successfully", Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onFailure(String error) {
                Toast.makeText(PassageManagementActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPassages();
    }
}
