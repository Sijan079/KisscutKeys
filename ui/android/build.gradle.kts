plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.stickers"
    // Compose 1.13 requires compiling against Android 37.1 (a minor SDK release).
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.stickers"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared:core"))
    implementation(project(":shared:db"))

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
