plugins {
    alias(libs.plugins.android.test)
}

// Test-only module: it never ships, so it stays out of the dependency scan's SBOM.
tasks.named("cyclonedxDirectBom") { enabled = false }

android {
    namespace = "app.otium.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Measures the app module's release-like "benchmark" build type.
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // Style rule: the build stays free of compiler warnings.
        allWarningsAsErrors.set(true)
    }
}

// Only the benchmark variant is useful: it is the one that targets the app's benchmark build.
androidComponents {
    beforeVariants(selector().all()) { variant ->
        variant.enable = variant.buildType == "benchmark"
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.junit)
}
