import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Secrets come from local.properties (never committed). A missing key means
// keyless Google Books access with the low public quota.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val booksApiKey: String = localProperties.getProperty("books.api.key", "")

// Open Library asks every client to identify itself with a contact. Without a setting, the
// project page is used.
val openLibraryContact: String =
    localProperties.getProperty("openlibrary.contact", "https://github.com/eh0x/otium")

android {
    namespace = "app.otium"
    // Latest AndroidX releases require compiling against API 37. Greenfield app, so it
    // targets the latest API too (lint enforces this via OldTargetApi).
    compileSdk = 37

    defaultConfig {
        applicationId = "app.otium"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "BOOKS_API_KEY", "\"$booksApiKey\"")
        buildConfigField("String", "OPEN_LIBRARY_CONTACT", "\"$openLibraryContact\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // Release-like and not debuggable, so the :benchmark module measures real speed. Signed
        // with the debug key so it installs without a keystore.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        // Style rule: lint findings fail the build. Fix them or suppress with a reason.
        abortOnError = true
        warningsAsErrors = true
        // Dependency freshness is tracked by Dependabot, not by failing local builds.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    sourceSets {
        // Exported Room schemas for MigrationTestHelper under Robolectric. AGP does not merge
        // test source set assets, so the schemas ride along in debug assets (debug builds only).
        getByName("debug").assets.directories.add("$projectDir/schemas")
        // The benchmark build reuses the debug seeder to fill the library with 5,000 books.
        getByName("benchmark").kotlin.directories.add("src/debug/kotlin")
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // Style rule: the build stays free of compiler warnings.
        allWarningsAsErrors.set(true)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.exifinterface)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(libs.konsist)
    // End-to-end flows run the real activity and graph under Robolectric with fake bindings.
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
