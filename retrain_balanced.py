"""
Retrain Random Forest with Balanced Classes using SMOTE
"""

import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score
from imblearn.over_sampling import SMOTE
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType
import warnings
warnings.filterwarnings('ignore')

print("=" * 60)
print("Retraining with SMOTE Balancing")
print("=" * 60)

# Load data
df = pd.read_csv('C:/Users/Elizha/Downloads/mfcc_features_new.csv')
X = df.iloc[:, 2:41].values.astype(np.float32)
y = df['label'].values

print(f"\nOriginal dataset:")
print(f"  Total: {len(df)}")
print(f"  Class 0 (mispronounced): {(y==0).sum()} ({(y==0).sum()/len(y)*100:.1f}%)")
print(f"  Class 1 (correct): {(y==1).sum()} ({(y==1).sum()/len(y)*100:.1f}%)")

# Split BEFORE applying SMOTE (important!)
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42, stratify=y
)

print(f"\nBefore SMOTE:")
print(f"  Train - Class 0: {(y_train==0).sum()}, Class 1: {(y_train==1).sum()}")

# Apply SMOTE to training data only
smote = SMOTE(random_state=42, k_neighbors=5)
X_train_balanced, y_train_balanced = smote.fit_resample(X_train, y_train)

print(f"\nAfter SMOTE:")
print(f"  Train - Class 0: {(y_train_balanced==0).sum()}, Class 1: {(y_train_balanced==1).sum()}")
print(f"  Ratio: 50/50 (perfectly balanced)")

# Train Random Forest
print("\nTraining Random Forest...")
rf = RandomForestClassifier(
    n_estimators=200,  # More trees
    max_depth=15,      # Deeper trees
    min_samples_split=5,
    min_samples_leaf=2,
    random_state=42,
    n_jobs=-1
)

rf.fit(X_train_balanced, y_train_balanced)
print("✅ Training complete!")

# Evaluate
print("\n" + "=" * 60)
print("EVALUATION ON TEST SET")
print("=" * 60)

y_pred = rf.predict(X_test)
accuracy = accuracy_score(y_test, y_pred)

print(f"\nOverall Accuracy: {accuracy*100:.2f}%")

print("\nClassification Report:")
print(classification_report(y_test, y_pred, 
                          target_names=['Mispronounced (0)', 'Correct (1)'],
                          digits=3))

print("Confusion Matrix:")
cm = confusion_matrix(y_test, y_pred)
print(f"                 Predicted")
print(f"                 0      1")
print(f"Actual 0 (Mis)  {cm[0,0]:3d}   {cm[0,1]:3d}")
print(f"Actual 1 (Cor)  {cm[1,0]:3d}   {cm[1,1]:3d}")

# Calculate per-class accuracy
class_0_acc = cm[0,0] / (cm[0,0] + cm[0,1]) * 100
class_1_acc = cm[1,1] / (cm[1,0] + cm[1,1]) * 100
print(f"\nPer-class accuracy:")
print(f"  Mispronounced (0): {class_0_acc:.1f}%")
print(f"  Correct (1): {class_1_acc:.1f}%")

# Feature importance
print("\nTop 10 most important features:")
feature_names = [f'f{i}' for i in range(39)]
importances = rf.feature_importances_
indices = np.argsort(importances)[::-1][:10]
for i, idx in enumerate(indices, 1):
    print(f"  {i}. {feature_names[idx]}: {importances[idx]:.4f}")

# Export to ONNX
print("\n" + "=" * 60)
print("EXPORTING TO ONNX")
print("=" * 60)

initial_type = [('float_input', FloatTensorType([None, 39]))]
onnx_model = convert_sklearn(rf, initial_types=initial_type, target_opset=12)

output_path = 'random_forest_balanced_smote.onnx'
with open(output_path, 'wb') as f:
    f.write(onnx_model.SerializeToString())

print(f"✅ Model saved: {output_path}")

# Verify ONNX
print("\nVerifying ONNX model...")
import onnxruntime as ort

sess = ort.InferenceSession(output_path)
test_samples = X_test[:10]
onnx_pred = sess.run([sess.get_outputs()[0].name], 
                     {sess.get_inputs()[0].name: test_samples})[0]
sklearn_pred = rf.predict(test_samples)

print(f"ONNX predictions:    {onnx_pred}")
print(f"sklearn predictions: {sklearn_pred}")
print(f"Match: {np.array_equal(onnx_pred, sklearn_pred)}")

print("\n" + "=" * 60)
print("✅ RETRAINING COMPLETE!")
print("=" * 60)
print(f"\nNext steps:")
print(f"1. Copy {output_path} to app/src/main/assets/")
print(f"2. Rename to: random_forest_model_retrained.onnx")
print(f"3. Rebuild and test")
print(f"\nExpected improvement:")
print(f"  - Better detection of mispronunciations")
print(f"  - More balanced predictions (not always 'correct')")
print(f"  - Accuracy: {accuracy*100:.1f}%")
