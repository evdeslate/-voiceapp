"""
Calculate Min/Max values from extracted TarsosDSP features
"""

import pandas as pd
import numpy as np

print("=" * 60)
print("Calculating Min/Max from TarsosDSP Features")
print("=" * 60)

# Load the CSV
df = pd.read_csv('C:/Users/Elizha/Downloads/mfcc_features_new.csv')

print(f"\nTotal samples: {len(df)}")

# Extract features (columns f0-f38)
X = df.iloc[:, 2:41].values.astype(np.float32)

print(f"Feature matrix shape: {X.shape}")

# Calculate min and max for each feature
min_vals = X.min(axis=0)
max_vals = X.max(axis=0)

print("\n" + "=" * 60)
print("MIN VALUES (for Java array)")
print("=" * 60)
print("private static final float[] TRAINING_MIN_VALS = {")
for i in range(0, len(min_vals), 7):
    chunk = min_vals[i:i+7]
    values = ", ".join([f"{v:.6f}f" for v in chunk])
    if i + 7 < len(min_vals):
        print(f"    {values},")
    else:
        print(f"    {values}")
print("};")

print("\n" + "=" * 60)
print("MAX VALUES (for Java array)")
print("=" * 60)
print("private static final float[] TRAINING_MAX_VALS = {")
for i in range(0, len(max_vals), 7):
    chunk = max_vals[i:i+7]
    values = ", ".join([f"{v:.6f}f" for v in chunk])
    if i + 7 < len(max_vals):
        print(f"    {values},")
    else:
        print(f"    {values}")
print("};")

print("\n" + "=" * 60)
print("FEATURE RANGES")
print("=" * 60)
print(f"{'Feature':<10} {'Min':>12} {'Max':>12} {'Range':>12}")
print("-" * 50)
for i in range(len(min_vals)):
    feature_range = max_vals[i] - min_vals[i]
    print(f"f{i:<9} {min_vals[i]:>12.2f} {max_vals[i]:>12.2f} {feature_range:>12.2f}")

print("\n" + "=" * 60)
print("COPY THESE VALUES TO ONNXRandomForestScorer.java")
print("=" * 60)
