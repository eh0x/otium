// Pure Kotlin/JVM module: models, errors, repository and port interfaces, use cases.
// Must never depend on Android, Room, Retrofit or Hilt (see DomainPurityTest).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // Style rule: the build stays free of compiler warnings.
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    // Flow appears in the public API of repository interfaces.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
}

tasks.test {
    useJUnit()
}
