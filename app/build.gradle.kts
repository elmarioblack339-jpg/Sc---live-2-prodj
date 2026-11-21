plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.sclive2.dj"
    compileSdk = 35
    ndkVersion = "27.2.12479018"
    defaultConfig {
        // Separate test package so it installs beside the existing signed SC LIVE 2.
        applicationId = "com.sclive2.dj.audiofix"
        minSdk = 26
        targetSdk = 35
        versionCode = 244
        versionName = "244-audio-visual-load-test"
        // Package both 32-bit and 64-bit ARM so older phones can install the DJ app.
        // The RANK engine runs only on Android 10+ arm64 devices.
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared", "-DANDROID_PLATFORM=android-29")
            }
        }
    }
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
}
