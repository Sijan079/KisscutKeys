plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    android {
        namespace = "com.example.stickers.shared.core"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:db"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// `gradlew test` is the documented command (CLAUDE.md); KMP modules have no `test` task by default.
tasks.register("test") {
    group = "verification"
    description = "Runs this module's tests on all targets."
    dependsOn("allTests")
}
