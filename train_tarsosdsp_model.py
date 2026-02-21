"""
Train Random Forest Model Using TarsosDSP Features
This uses the features extracted directly from the Android app
"""

import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType
import warnings
warnings.filterwarnings('ignore')

print("=" * 60)
print("Training Random Forest with TarsosDSP Features")
print("=" * 60)

# Load the CSV
print("\n1. Loading data...")
df = pd.read_csv('C:/Users/Elizha/Downloads/mfcc_features_new.csv')

print(f"   Total samples: {len(df)}")
print(f"   Total features: {df.shape[1] - 3}")  # Exclude filename, word, label

# Check label distribution
print(f"\n2. Label distribution:")
label_counts = df['label'].value_counts().sort_index()
print(f"   Class 0 (mispronounced): {label_counts[0]} ({label_counts[0]/len(df)*100:.1f}%)")
print(f"   Class 1 (correct): {label_counts[1]} ({label_counts[1]/len(df)*100:.1f}%)")

# Extract features and labels
X = df.iloc[:, 2:41].values.astype(np.float32)  # Columns 2-40 (f0-f38)
y = df['label'].values

print(f"\n3. Feature matrix shape: {X.shape}")
print(f"   Feature range: [{X.min():.2f}, {X.max():.2f}]")

# Check for NaN or Inf
if np.isnan(X).any():
    print(f"   ⚠️  WARNING: {np.isnan(X).sum()} NaN values found!")
if np.isinf(X).any():
    print(f"   ⚠️  WARNING: {np.isinf(X).sum()} Inf values found!")

# Split data
print("\n4. Splitting data (80% train, 20% test)...")
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42, stratify=y
)

print(f"   Train: {len(X_train)} samples")
print(f"   Test: {len(X_test)} samples")

# Train Random Forest
print("\n5. Training Random Forest...")
rf = RandomForestClassifier(
    n_estimators=100,
    max_depth=10,
    min_samples_split=5,
    min_samples_leaf=2,
    random_state=42,
    n_jobs=-1,
    class_weight='balanced'  # Handle class imbalance
)

rf.fit(X_train, y_train)
print("   ✅ Training complete!")

# Evaluate on test set
print("\n6. Evaluating on test set...")
y_pred = rf.predict(X_test)
accuracy = accuracy_score(y_test, y_pred)

print(f"\n   Overall Accuracy: {accuracy*100:.2f}%")

print("\n   Classification Report:")
print(classification_report(y_test, y_pred, 
                          target_names=['Mispronounced (0)', 'Correct (1)'],
                          digits=3))

print("   Confusion Matrix:")
cm = confusion_matrix(y_test, y_pred)
print(f"   [[TN={cm[0,0]:3d}  FP={cm[0,1]:3d}]")
print(f"    [FN={cm[1,0]:3d}  TP={cm[1,1]:3d}]]")

# Feature importance
print("\n7. Top 10 most important features:")
feature_names = [f'f{i}' for i in range(39)]
importances = rf.feature_importances_
indices = np.argsort(importances)[::-1][:10]
for i, idx in enumerate(indices, 1):
    print(f"   {i}. {feature_names[idx]}: {importances[idx]:.4f}")

# Export to ONNX
print("\n8. Exporting to ONNX...")
initial_type = [('float_input', FloatTensorType([None, 39]))]
onnx_model = convert_sklearn(rf, initial_types=initial_type, target_opset=12)

output_path = 'random_forest_tarsosdsp.onnx'
with open(output_path, 'wb') as f:
    f.write(onnx_model.SerializeToString())

print(f"   ✅ Model saved: {output_path}")

# Test ONNX model
print("\n9. Verifying ONNX model...")
import onnxruntime as ort

sess = ort.InferenceSession(output_path)
input_name = sess.get_inputs()[0].name
output_name = sess.get_outputs()[0].name

# Test on a few samples
test_samples = X_test[:5]
onnx_pred = sess.run([output_name], {input_name: test_samples})[0]
sklearn_pred = rf.predict(test_samples)

print(f"   ONNX predictions: {onnx_pred}")
print(f"   sklearn predictions: {sklearn_pred}")
print(f"   Match: {np.array_equal(onnx_pred, sklearn_pred)}")

print("\n" + "=" * 60)
print("✅ TRAINING COMPLETE!")
print("=" * 60)
print(f"\nNext steps:")
print(f"1. Copy {output_path} to app/src/main/assets/")
print(f"2. Rename to: random_forest_model_retrained.onnx")
print(f"3. Rebuild and test the app")
print(f"\nExpected accuracy in production: {accuracy*100:.1f}%")
