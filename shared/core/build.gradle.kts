plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    android {
        namespace = "com.example.stickers.shared.core"
        compileSdk = 37
        minSdk = 26
        // Host (JVM) tests for androidMain code that needs no Context, e.g. AndroidFileStore.
        withHostTest {}
    }

    // JVM target: repository tests run against a real in-memory Room database.
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:db"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.turbine)
        }
    }
}

// `gradlew test` is the documented command (CLAUDE.md); KMP modules have no `test` task by default.
tasks.register("test") {
    group = "verification"
    description = "Runs this module's tests on all targets."
    dependsOn("allTests")
}
