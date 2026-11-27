plugins {
    id("com.google.gms.google-services")
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "my.edu.um.rainready"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "my.edu.um.rainready"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Demo builds: ./gradlew assembleDebug -Prainready.sampleData=true shows the sample zones
        // (one route-check trip per verdict) instead of the live Firestore data.
        val sampleData = providers.gradleProperty("rainready.sampleData").orNull == "true"
        buildConfigField("boolean", "SAMPLE_DATA", sampleData.toString())
        // Testing: -Prainready.firestoreEmulator=127.0.0.1 (after adb reverse tcp:8080 tcp:8080) reads the local emulator
        // (firebase emulators:start --only firestore) instead of the real project. Debug builds only.
        val emulatorHost = providers.gradleProperty("rainready.firestoreEmulator").orNull.orEmpty()
            .filter { it.isLetterOrDigit() || it == '.' || it == '-' }
        buildConfigField("String", "FIRESTORE_EMULATOR", "\"$emulatorHost\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
            // A release build always reads the real Firestore project.
            buildConfigField("String", "FIRESTORE_EMULATOR", "\"\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.maplibre.android)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}