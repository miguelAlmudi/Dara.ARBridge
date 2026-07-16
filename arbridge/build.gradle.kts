plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.compose)
}

android {
    namespace = "com.example.dara.arbridge.lib"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    // Pose is part of the public marker configuration API.
    api("com.google.ar:core:1.50.0")
    implementation(libs.sceneview)
    implementation(libs.arsceneview)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
