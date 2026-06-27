// this is the first line of dat code (yay)

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
}

android {
    namespace = "android.kimyona.jammer"
    compileSdk = 34 // That's my phone API!

    defaultConfig {
        applicationId = "android.kimyona.jammer"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a") // Compatibility for all existent ARM versions
        }
    }

// TestKey (gotta change that later)
    signingConfigs {
        create("release") {
            storeFile = file("keystore/testkey.jks")
            storePassword = "android"
            keyAlias = "testkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // FIX: Grant .so Rust files goes to APK
    sourceSets["main"].jniLibs.srcDir("src/main/jniLibs")
}

dependencies {
    // Room (SQLite)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    // Warning for the stupid Android 2026 September update [DO NOT REMOVE]
    implementation("com.github.woheller69:FreeDroidWarn:1.+")
    

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    // LiveData
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    // Lifecycle Runtime
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Activity/Fragment KTX
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // ViewPager2
    implementation("androidx.viewpager2:viewpager2:1.0.0")

    // Android Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Shizuku
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // FFmpeg
    implementation("io.github.jamaismagic.ffmpeg:ffmpeg-kit-lts-16kb:6.1.7")
    
    // ExoPlayer Media3 (playback)
implementation("androidx.media3:media3-exoplayer:1.6.0")
implementation("androidx.media3:media3-session:1.6.0")
implementation("androidx.media3:media3-ui:1.6.0")
implementation("androidx.media3:media3-common:1.6.0")

    // JSON parsing (for Rust)
    implementation("org.json:json:20231013")

    // Glide - images load
    implementation("com.github.bumptech.glide:glide:4.16.0")
    ksp("com.github.bumptech.glide:ksp:4.16.0")
}

// le end