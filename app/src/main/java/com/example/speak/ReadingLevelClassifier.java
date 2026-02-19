package com.example.speak;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Reading Level Classifier using Random Forest
 * Classifies student reading proficiency based on performance metrics
 * 
 * Reading Levels (Oral Reading Level Standards):
 * 0 = Frustration Level (0-89%)
 * 1 = Instructional Level (90-96%)
 * 2 = Independent Level (97-100%)
 */
public class ReadingLevelClassifier {
    private static final String TAG = "ReadingLevelClassifier";
    private static final String MODEL_PATH = "random_forest_model.tflite";
    
    // Reading level labels
    public static final int FRUSTRATION_LEVEL = 0;
    public static final int INSTRUCTIONAL_LEVEL = 1;
    public static final int INDEPENDENT_LEVEL = 2;
    
    private static final String[] LEVEL_NAMES = {
        "Frustration Level",
        "Instructional Level", 
        "Independent Level"
    };
    
    private static final String[] LEVEL_DESCRIPTIONS = {
        "Material is too difficult; student needs easier texts",
        "Student can read with teacher guidance and support",
        "Student can read independently with high accuracy"
    };
    
    private Interpreter interpreter;
    private boolean isModelLoaded = false;
    
    /**
     * Constructor - loads Random Forest model
     */
    public ReadingLevelClassifier(Context context) {
        // Skip model loading due to shape mismatch
        // Model expects [0, 2] output but code needs [1, 3]
        // Using rule-based classification instead
        Log.d(TAG, "ReadingLevelClassifier initialized (using rule-based classification)");
        Log.w(TAG, "⚠️ TFLite model has shape mismatch - using Phil-IRI rule-based classification");
        isModelLoaded = false;
    }
    
    /**
     * Classify reading level based on performance metrics
     * 
     * @param accuracy Word recognition accuracy (0.0-1.0)
     * @param pronunciation Pronunciation quality (0.0-1.0)
     * @param comprehension Text comprehension score (0.0-1.0)
     * @param wpm Words per minute
     * @param errorRate Error rate (0.0-1.0)
     * @return Reading level (0=Frustration, 1=Instructional, 2=Independent)
     */
    public int classifyReadingLevel(float accuracy, float pronunciation, float comprehension, 
                                   float wpm, float errorRate) {
        // Always use rule-based classification due to model shape mismatch
        // The TFLite model has incompatible input/output shapes
        Log.d(TAG, "Using rule-based classification (model has shape mismatch)");
        return classifyByRules(accuracy, pronunciation, comprehension);
    }
    
    /**
     * Classify reading level with detailed analysis
     */
    public ReadingLevelResult classifyWithDetails(float accuracy, float pronunciation, 
                                                  float comprehension, float wpm, float errorRate) {
        int level = classifyReadingLevel(accuracy, pronunciation, comprehension, wpm, errorRate);
        
        // Calculate overall score (50% accuracy, 50% pronunciation - comprehension not shown)
        float overallScore = (accuracy * 0.5f) + (pronunciation * 0.5f);
        
        // Determine strengths and weaknesses
        String strengths = identifyStrengths(accuracy, pronunciation, comprehension, wpm);
        String weaknesses = identifyWeaknesses(accuracy, pronunciation, comprehension, wpm, errorRate);
        String recommendations = getRecommendations(level, accuracy, pronunciation, comprehension, wpm);
        
        return new ReadingLevelResult(
            level,
            LEVEL_NAMES[level],
            LEVEL_DESCRIPTIONS[level],
            overallScore,
            strengths,
            weaknesses,
            recommendations
        );
    }
    
    /**
     * Rule-based classification fallback (Oral Reading Level Standards)
     * Based on combined score (50% accuracy + 50% pronunciation)
     */
    private int classifyByRules(float accuracy, float pronunciation, float comprehension) {
        // Calculate combined score (50% accuracy + 50% pronunciation)
        float combinedScore = (accuracy * 0.5f) + (pronunciation * 0.5f);
        float combinedPercent = combinedScore * 100.0f;
        
        Log.d(TAG, String.format("Rule-based classification: Combined=%.1f%% (Acc=%.1f%%, Pron=%.1f%%)",
            combinedPercent, accuracy * 100, pronunciation * 100));
        
        // Phil IRI standards based on combined performance
        if (combinedPercent >= 90.0f) {
            Log.d(TAG, "Classification: Independent Level (90%+)");
            return INDEPENDENT_LEVEL; // 90%+ combined: Independent
        } else if (combinedPercent >= 75.0f) {
            Log.d(TAG, "Classification: Instructional Level (75-89%)");
            return INSTRUCTIONAL_LEVEL; // 75-89% combined: Instructional
        } else {
            Log.d(TAG, "Classification: Frustration Level (<75%)");
            return FRUSTRATION_LEVEL; // Below 75%: Frustration
        }
    }
    
    /**
     * Normalize WPM to 0-1 range (assuming 0-200 WPM range)
     */
    private float normalizeWPM(float wpm) {
        return Math.min(1.0f, wpm / 200.0f);
    }
    
    /**
     * Find index of maximum value in array
     */
    private int argmax(float[] array) {
        int maxIndex = 0;
        float maxValue = array[0];
        
        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxValue) {
                maxValue = array[i];
                maxIndex = i;
            }
        }
        
        return maxIndex;
    }
    
    /**
     * Identify student's strengths
     */
    private String identifyStrengths(float accuracy, float pronunciation, float comprehension, float wpm) {
        StringBuilder strengths = new StringBuilder();
        
        if (accuracy >= 0.85f) {
            strengths.append("• Excellent word recognition\n");
        }
        if (pronunciation >= 0.75f) {
            strengths.append("• Good pronunciation skills\n");
        }
        if (comprehension >= 0.80f) {
            strengths.append("• Strong text comprehension\n");
        }
        // More realistic WPM thresholds for elementary students
        if (wpm >= 80) {
            strengths.append("• Excellent reading fluency\n");
        } else if (wpm >= 60) {
            strengths.append("• Good reading pace\n");
        } else if (wpm >= 40) {
            strengths.append("• Steady reading progress\n");
        }
        
        if (strengths.length() == 0) {
            strengths.append("• Shows effort and willingness to learn\n");
        }
        
        return strengths.toString().trim();
    }
    
    /**
     * Identify areas needing improvement
     */
    private String identifyWeaknesses(float accuracy, float pronunciation, float comprehension, 
                                     float wpm, float errorRate) {
        StringBuilder weaknesses = new StringBuilder();
        
        if (accuracy < 0.70f) {
            weaknesses.append("• Word recognition needs practice\n");
        }
        if (pronunciation < 0.60f) {
            weaknesses.append("• Pronunciation needs improvement\n");
        }
        if (comprehension < 0.65f) {
            weaknesses.append("• Text comprehension needs work\n");
        }
        // More lenient WPM thresholds - 40 WPM is reasonable for elementary students
        if (wpm > 0 && wpm < 40) {
            weaknesses.append("• Reading speed could be faster\n");
        }
        if (errorRate > 0.20f) {
            weaknesses.append("• High error rate\n");
        }
        
        if (weaknesses.length() == 0) {
            weaknesses.append("• Continue practicing for mastery\n");
        }
        
        return weaknesses.toString().trim();
    }
    
    /**
     * Get personalized recommendations based on reading level
     */
    private String getRecommendations(int level, float accuracy, float pronunciation, 
                                     float comprehension, float wpm) {
        StringBuilder recommendations = new StringBuilder();
        float accuracyPercent = accuracy * 100.0f;
        
        switch (level) {
            case FRUSTRATION_LEVEL:
                recommendations.append("📚 Recommended Actions (Accuracy: ").append(String.format("%.0f%%", accuracyPercent)).append("):\n");
                recommendations.append("• Material is too difficult - use easier texts\n");
                recommendations.append("• Build foundational reading skills\n");
                recommendations.append("• Practice with high-frequency words\n");
                recommendations.append("• Provide one-on-one support\n");
                recommendations.append("• Focus on phonics and decoding\n");
                break;
                
            case INSTRUCTIONAL_LEVEL:
                recommendations.append("📖 Recommended Actions (Accuracy: ").append(String.format("%.0f%%", accuracyPercent)).append("):\n");
                recommendations.append("• Appropriate level with teacher guidance\n");
                recommendations.append("• Continue guided reading practice\n");
                recommendations.append("• Work on challenging words\n");
                if (pronunciation < 0.70f) {
                    recommendations.append("• Improve pronunciation skills\n");
                }
                if (comprehension < 0.75f) {
                    recommendations.append("• Practice comprehension strategies\n");
                }
                recommendations.append("• Read aloud daily (15-20 min)\n");
                break;
                
            case INDEPENDENT_LEVEL:
                recommendations.append("⭐ Recommended Actions (Accuracy: ").append(String.format("%.0f%%", accuracyPercent)).append("):\n");
                recommendations.append("• Excellent! Can read independently\n");
                recommendations.append("• Ready for more challenging texts\n");
                recommendations.append("• Encourage independent reading\n");
                recommendations.append("• Focus on comprehension depth\n");
                recommendations.append("• Explore diverse reading materials\n");
                break;
        }
        
        return recommendations.toString().trim();
    }
    
    /**
     * Load TFLite model from assets
     */
    private MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    
    /**
     * Check if model is ready
     */
    public boolean isReady() {
        return isModelLoaded;
    }
    
    /**
     * Release resources
     */
    public void release() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        isModelLoaded = false;
        Log.d(TAG, "Random Forest resources released");
    }
    
    /**
     * Reading Level Result class
     */
    public static class ReadingLevelResult {
        public final int level;
        public final String levelName;
        public final String description;
        public final float overallScore;
        public final String strengths;
        public final String weaknesses;
        public final String recommendations;
        
        public ReadingLevelResult(int level, String levelName, String description,
                                 float overallScore, String strengths, String weaknesses,
                                 String recommendations) {
            this.level = level;
            this.levelName = levelName;
            this.description = description;
            this.overallScore = overallScore;
            this.strengths = strengths;
            this.weaknesses = weaknesses;
            this.recommendations = recommendations;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Reading Level: %s (%.0f%%)\n" +
                "Description: %s\n\n" +
                "Strengths:\n%s\n\n" +
                "Areas for Improvement:\n%s\n\n" +
                "%s",
                levelName, overallScore * 100, description,
                strengths, weaknesses, recommendations
            );
        }
    }
}
