// Root build file: declares plugins once so modules can apply them without versions,
// and hosts the repository-wide style and static analysis checks (see docs/STYLE_GUIDE.md).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

val generatedAndBuildDirs =
    arrayOf("**/build/**", "**/.gradle/**", "graphify-out/**", "app/schemas/**")

// Formatting. Run ./gradlew spotlessApply to fix, spotlessCheck to verify.
// Kotlin and Kotlin DSL use ktlint with the android_studio code style, which implements the
// Android Kotlin style guide (settings live in .editorconfig).
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude(*generatedAndBuildDirs)
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude(*generatedAndBuildDirs)
        ktlint(libs.versions.ktlint.get())
    }
    // Android XML resources: whitespace hygiene (indentation style comes from .editorconfig).
    format("xml") {
        target("**/src/**/*.xml")
        targetExclude(*generatedAndBuildDirs)
        trimTrailingWhitespace()
        leadingTabsToSpaces(4)
        endWithNewline()
    }
    // Everything else that is text.
    format("misc") {
        target("**/*.md", "**/*.yml", "**/*.yaml", "**/*.toml", "**/*.properties", "**/*.pro")
        target(".gitignore", ".editorconfig", ".githooks/*")
        targetExclude(*generatedAndBuildDirs)
        targetExclude("local.properties", "CLAUDE.md")
        trimTrailingWhitespace()
        leadingTabsToSpaces(4)
        endWithNewline()
    }
}

// Static analysis for Kotlin sources. Formatting is left to ktlint.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    source.setFrom(files("app/src", "domain/src"))
    parallel = true
}

// Points git at the tracked hooks in .githooks (formatting, commit message and push checks).
tasks.register<Exec>("installGitHooks") {
    group = "verification"
    description = "Configures git to use the hooks in .githooks."
    commandLine("git", "config", "core.hooksPath", ".githooks")
}
