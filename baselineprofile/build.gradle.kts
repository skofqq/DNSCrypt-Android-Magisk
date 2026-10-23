plugins {
    // Same artifact as com.android.application, already on the classpath: no version here.
    id("com.android.test")
    alias(libs.plugins.baselineprofile)
}

// Generates app/src/release/generated/baselineProfiles on a connected device:
//   ./gradlew :app:generateReleaseBaselineProfile
// The app needs root, so grant it once in the Magisk prompt on the first run.
android {
    namespace = "io.github.skofqq.dnscryptmanager.baselineprofile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 31
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
