# TarsosDSP Library

## Required JAR File

Download **TarsosDSP-latest.jar** and place it in this directory as `TarsosDSP-2.4.jar`.

### Download Options

**Option 1: Official Release (Recommended)**
- Visit: http://0110.be/releases/TarsosDSP/TarsosDSP-latest/
- Download: `TarsosDSP-latest.jar`
- Rename to: `TarsosDSP-2.4.jar`
- Place here: `app/libs/TarsosDSP-2.4.jar`

**Option 2: Maven Central**
- Visit: https://central.sonatype.com/artifact/com.github.axet/TarsosDSP
- Download the JAR file
- Rename to: `TarsosDSP-2.4.jar`
- Place here: `app/libs/TarsosDSP-2.4.jar`

**Option 3: Build from Source**
```bash
git clone https://github.com/JorenSix/TarsosDSP.git
cd TarsosDSP
./gradlew build
# JAR will be in build/libs/
```

### Verify Installation
After placing the JAR file, you should see:
```
app/libs/
  ├── TarsosDSP-2.4.jar
  └── README.md
```

Then run:
```bash
./gradlew clean build
```

## What is TarsosDSP?

TarsosDSP is a pure Java audio processing library that provides:
- MFCC (Mel-Frequency Cepstral Coefficients) extraction
- FFT (Fast Fourier Transform)
- Audio windowing and framing
- Mel filterbank

Used in this project for extracting MFCC features from speech audio for pronunciation scoring.

## Why Local JAR?

Using a local JAR file instead of Maven/JitPack provides:
- ✅ Stable builds (no network dependency)
- ✅ Faster builds (no download time)
- ✅ Offline development
- ✅ Version control (exact version guaranteed)
