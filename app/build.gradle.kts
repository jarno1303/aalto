plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "fi.aalto.radio"
    compileSdk = 36

    defaultConfig {
        applicationId = "fi.aalto.radio"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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
        buildConfig = true
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Manual reports that call real services stay out of the offline
            // suite unless asked for, either with -Daalto.manual=true or with
            // the AALTO_MANUAL environment variable (PowerShell mangles -D...).
            all {
                val manual = System.getProperty("aalto.manual")
                    ?: System.getenv("AALTO_MANUAL")
                    ?: "false"
                it.systemProperty("aalto.manual", manual)
                it.testLogging { showStandardStreams = true }
            }
        }
    }

    lint {
        // Kotlin FIR lint analysis currently crashes on the Room migration test fixture.
        checkTestSources = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.0")
    implementation("androidx.media3:media3-session:1.11.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")

    kapt("androidx.room:room-compiler:2.8.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    testImplementation("org.robolectric:robolectric:4.15.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}



