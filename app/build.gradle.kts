plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services") // PLUGIN GOOGLE SERVICES UNTUK FIRESTORE
}

android {
    namespace = "com.smartfinance.tracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.smartfinance.tracker"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
        
        // 🔥 GROQ_API_KEY DARI GITHUB SECRET SUDAH DIHAPUS TOTAL DI SINI
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.biometric:biometric:1.1.0")

    // Navigation & Lifecycle Dropdown UI
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // Google Gemini AI SDK Fallback
    implementation("com.google.ai.client.generativeai:generativeai:0.2.2")

    // Chart Library untuk Laporan Visual
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // ========================================================
    // CLOUD BACKEND: FIREBASE FIRESTORE REAL-TIME ECOSYSTEM
    // ========================================================
    implementation(platform("com.google.firebase:firebase-bom:34.14.0"))
    implementation("com.google.firebase:firebase-firestore")

    // 🔥 TAMBAHAN MESIN WORKMANAGER UNTUK TRANSAKSI BERKALA
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
