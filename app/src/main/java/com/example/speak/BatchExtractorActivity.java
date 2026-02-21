package com.example.speak;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class BatchExtractorActivity extends AppCompatActivity {
    
    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private Button btnStart;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvProgress;
    
    private BatchFeatureExtractor extractor;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_batch_extractor);
        
        btnStart = findViewById(R.id.btnStart);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);
        tvProgress = findViewById(R.id.tvProgress);
        
        btnStart.setOnClickListener(v -> {
            if (checkPermissions()) {
                startExtraction();
            } else {
                requestPermissions();
            }
        });
        
        tvStatus.setText("Ready to extract features\n\n" +
            "Audio files location:\n" +
            "/sdcard/preprocessed_output_v2/\n\n" +
            "1. Tap 'Start Extraction'\n" +
            "2. Wait for completion\n" +
            "3. Copy /sdcard/mfcc_features.csv to PC");
    }

    
    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ requires MANAGE_EXTERNAL_STORAGE
            return Environment.isExternalStorageManager();
        } else {
            // Android 10 and below
            return ContextCompat.checkSelfPermission(this, 
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, 
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - request MANAGE_EXTERNAL_STORAGE
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, PERMISSION_REQUEST_CODE);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, PERMISSION_REQUEST_CODE);
            }
        } else {
            // Android 10 and below - request legacy permissions
            ActivityCompat.requestPermissions(this,
                new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                PERMISSION_REQUEST_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startExtraction();
            } else {
                tvStatus.setText("❌ Storage permission required");
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    startExtraction();
                } else {
                    tvStatus.setText("❌ All Files Access permission required");
                }
            }
        }
    }
    
    private void startExtraction() {
        btnStart.setEnabled(false);
        progressBar.setProgress(0);
        tvStatus.setText("🚀 Starting extraction...");
        tvProgress.setText("0 / 0");
        
        extractor = new BatchFeatureExtractor(this, new BatchFeatureExtractor.ProgressCallback() {
            @Override
            public void onProgress(int current, int total, String filename) {
                runOnUiThread(() -> {
                    progressBar.setMax(total);
                    progressBar.setProgress(current);
                    tvProgress.setText(current + " / " + total);
                    tvStatus.setText("Processing: " + filename);
                });
            }
            
            @Override
            public void onComplete(int processed, int skipped, String outputPath) {
                runOnUiThread(() -> {
                    btnStart.setEnabled(true);
                    tvStatus.setText("✅ Extraction complete!\n\n" +
                        "Processed: " + processed + "\n" +
                        "Skipped: " + skipped + "\n\n" +
                        "Output: " + outputPath + "\n\n" +
                        "Copy this file to your PC for training");
                    tvProgress.setText(processed + " / " + (processed + skipped));
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    btnStart.setEnabled(true);
                    tvStatus.setText("❌ Error: " + error);
                });
            }
        });
        
        extractor.extractAll();
    }
}
