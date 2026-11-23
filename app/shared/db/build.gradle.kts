plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    android {
        namespace = "app.kisscut.keys.shared.db"
        compileSdk = 37
        minSdk = 26
    }

    // JVM target: runs the database tests on the host with Room in-memory + BundledSQLiteDriver
    // (docs/TESTING.md), and proves commonMain has no Android imports.
    jvm()

    compilerOptions {
        // Room's generated database constructor is an expect/actual object.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.room.testing)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Schema directory for migration tests, independent of the working directory.
tasks.withType<Test>().configureEach {
    systemProperty("room.schemaDir", "$projectDir/schemas")
}

// `gradlew test` is the documented command (CLAUDE.md); KMP modules have no `test` task by default.
tasks.register("test") {
    group = "verification"
    description = "Runs this module's tests on all targets."
    dependsOn("allTests")
}
