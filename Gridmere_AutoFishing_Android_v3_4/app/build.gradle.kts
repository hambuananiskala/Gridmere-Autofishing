plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gridmere.autofishing"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gridmere.autofishing"
        minSdk = 26
        targetSdk = 35
        versionCode = 34
        versionName = "3.4-android-beta"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
