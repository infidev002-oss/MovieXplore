import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.gms.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// TMDB settings and test device ids live in local.properties (git-ignored), never in source control.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.movies.moviexplore"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.movies.moviexplore"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["admobAppId"] = project.property("ADMOB_APP_ID") as String
        buildConfigField("String", "TMDB_BASE_URL", "\"${localProps.getProperty("TMDB_BASE_URL", "")}\"")
        buildConfigField("String", "TMDB_API_KEY", "\"${localProps.getProperty("TMDB_API_KEY", "")}\"")
        buildConfigField("String", "UMP_TEST_DEVICE_ID", "\"${localProps.getProperty("UMP_TEST_DEVICE_ID", "")}\"")
        buildConfigField("String", "ADS_TEST_DEVICE_ID", "\"${localProps.getProperty("ADS_TEST_DEVICE_ID", "")}\"")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.sdp); implementation(libs.ssp)                       // responsive dimensions
    implementation(libs.viewpager2)                                           // onboarding pages
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.process)                                    // WelcomeBack: app foreground / background
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.glide)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth); implementation(libs.firebase.database); implementation(libs.firebase.config)
    implementation(libs.firebase.analytics); implementation(libs.firebase.crashlytics); implementation(libs.firebase.messaging)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.credentials); implementation(libs.credentials.play); implementation(libs.googleid)
    implementation(libs.play.services.ads); implementation(libs.ump)
    implementation(libs.play.app.update); implementation(libs.play.review)   // com.google.android.play:app-update / review
    implementation(libs.room.runtime); implementation(libs.room.ktx); ksp(libs.room.compiler)

    testImplementation(libs.junit)
}
