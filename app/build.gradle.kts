import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// The TMDB key lives in local.properties (git-ignored) and never in source control.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.movies.myapplication"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.movies.myapplication"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "TMDB_API_KEY", "\"${localProperties.getProperty("TMDB_API_KEY", "")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            // R8 shrinks and optimizes the code for release builds.
            optimization {
                enable = true
            }
            // Lets the release build be installed for testing. Replace with your own upload key
            // before publishing to Google Play.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Plain Android SDK + Kotlin, plus networking, coroutines, and AndroidX Fragment (only) for the main screen's tabs.
dependencies {
    implementation(libs.androidx.fragment)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
