// Pure Kotlin/JVM module: models, errors, repository and port interfaces, use cases.
// Must never depend on Android, Room, Retrofit or Hilt (see DomainPurityTest).
plugins {
    alias(libs.plugins.kotlin.jvm)
    // Shared fakes (FakeBookRepository, FixedClock) for the tests of both modules.
    `java-test-fixtures`
    alias(libs.plugins.kover)
}

// SBOM for the dependency scan (scanDependencies in the root build file): what ships only.
tasks.named<org.cyclonedx.gradle.CyclonedxDirectTask>("cyclonedxDirectBom") {
    includeConfigs = listOf("runtimeClasspath")
}

// Feeds the merged coverage report in the root build file.
kover {
    currentProject {
        createVariant("coverage") { add("jvm") }
    }
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
}

tasks.test {
    useJUnit()
}
