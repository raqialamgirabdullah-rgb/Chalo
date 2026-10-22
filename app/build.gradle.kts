plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// GitHub Actions প্রতিবার run number বাড়ায়, সেটাই versionCode হয়।
// তাই নতুন APK সবসময় পুরোনোর ওপর ইনস্টল হয়।
val runNumber: Int = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toIntOrNull() ?: 1

android {
    namespace = "com.rahcare.rickshawride"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rahcare.rickshawride"
        minSdk = 26
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    // সাইন করার চাবি রিপোজিটরিতেই আছে (app/chalo.keystore), তাই কোনো secret লাগে না।
    // ⚠ শুধু Private রিপোজিটরির পরীক্ষার সময়ের জন্য ঠিক। জনসাধারণের জন্য প্রকাশের আগে এটি বদলাতে হবে।
    signingConfigs {
        create("release") {
            storeFile = file("chalo.keystore")
            storePassword = "SiwGrvPafTIP3DXIzoVVTEpZ"
            keyAlias = "chalo"
            keyPassword = "SiwGrvPafTIP3DXIzoVVTEpZ"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    implementation("org.osmdroid:osmdroid-android:6.1.18")
}
