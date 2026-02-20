plugins {
    alias(libs.plugins.android.application)
    // Add the Google services Gradle plugin
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.speak"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.speak"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Don't compress large Vosk model files
        packaging {
            jniLibs {
                useLegacyPackaging = true
            }
            resources {
                excludes += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE", "META-INF/NOTICE.txt")
            }
        }
        
        // Disable asset compression for large model files (Vosk)
        aaptOptions {
            noCompress("tflite", "mdl", "fst", "conf", "dubm", "ie", "mat", "stats", "bin")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    // Disable lint to allow build
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    
    // Import the Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:34.9.0"))
    
    // Firebase Realtime Database
    // When using the BoM, don't specify versions in Firebase dependencies
    implementation("com.google.firebase:firebase-database")
    
    // Firebase Analytics (optional but recommended)
    implementation("com.google.firebase:firebase-analytics")
    
    // Firebase Authentication
    implementation("com.google.firebase:firebase-auth")
    
    // Google Sign-In for Firebase Authentication
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    
    // TensorFlow Lite - using the most stable version
    implementation("org.tensorflow:tensorflow-lite:2.8.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.2")
    
    // ONNX Runtime for Android - for Random Forest models
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.3")
    
    // Vosk speech recognition - offline, lightweight (for real-time highlighting)
    implementation("com.alphacephei:vosk-android:0.3.47")
    
    // Text processing for BERT NLP
    implementation("org.apache.commons:commons-text:1.9")
    
    // JSON processing (if needed for tokenizer configs)
    implementation("com.google.code.gson:gson:2.8.9")
    
    // Security - Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}