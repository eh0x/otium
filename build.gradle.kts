import kotlinx.kover.gradle.plugin.dsl.CoverageUnit


// The plugins below share this build classpath. Some bring libraries with known vulnerabilities;
// these constraints raise them to patched versions (gradle/libs.versions.toml, "Build classpath
// only"). They change what the build runs on, never what the app ships.
buildscript {
    dependencies {
        classpath(platform(libs.build.jackson.bom))
        constraints {
            classpath(libs.build.freemarker)
            classpath(libs.build.jose4j)
            classpath(libs.build.jdom2)
            classpath(libs.build.commons.lang3)
            classpath(libs.build.bcprov)
            classpath(libs.build.bcpkix)
        }
    }
}

// Root build file: declares plugins once so modules can apply them without versions,
// and hosts the repository-wide style and static analysis checks (see docs/STYLE_GUIDE.md).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    alias(libs.plugins.cyclonedx)
}

// Android Lint runs on a classpath of its own (androidLintTool) in each Android module, with older
// commons-lang3, httpclient and Bouncy Castle. Same idea as the buildscript constraints: patched
// versions, for the build only.
val lintToolConstraints =
    listOf(libs.build.commons.lang3, libs.build.httpclient, libs.build.bcprov, libs.build.bcpkix)
subprojects {
    val moduleDependencies = dependencies
    configurations.matching { it.name == "androidLintTool" }.configureEach {
        lintToolConstraints.forEach { moduleDependencies.constraints.add(name, it) }
    }
}

// Coverage: one report over :domain's tests and :app's debug unit tests (Robolectric included).
// Run ./gradlew koverHtmlReportCoverage or koverVerifyCoverage. Generated code is left out, so the
// numbers describe code someone wrote. Rules and targets: docs/STYLE_GUIDE.md, Testing.
dependencies {
    kover(project(":domain"))
    kover(project(":app"))
}

kover {
    currentProject {
        createVariant("coverage") {}
    }
    reports {
        filters {
            excludes {
                // Dagger and Hilt output.
                classes("*_Factory", "*_Factory$*", "*_MembersInjector", "*_HiltModules*")
                classes("Hilt_*", "*_Hilt*", "dagger.hilt.*", "hilt_aggregated_deps.*")
                // Room, Compose compiler and Android build output.
                classes(
                    "*_Impl",
                    "*_Impl$*",
                    "*ComposableSingletons*",
                    "*.BuildConfig",
                    "*.R",
                    "*.R$*",
                )
                // Previews and debug-only tools are not shipped behaviour.
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
                packages("app.otium.debug", "app.otium.ui.debug", "app.otium.ui.catalog")
                // Test fixtures (shared fakes) and Hilt wiring: covered by the tests that use
                // them, not something to measure as production logic.
                packages("app.otium.domain.fakes", "app.otium.di")
            }
        }
        // A floor, not a target: CI fails when coverage drops below what the suite already
        // reaches. Raise it as coverage grows; never lower it to make a change pass.
        verify {
            rule("Line coverage") {
                bound {
                    minValue = 90
                    coverageUnits = CoverageUnit.LINE
                }
            }
        }
    }
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
    // Report paths relative to the repository, so CI's SARIF upload maps findings to files.
    basePath = rootDir.absolutePath
}

// Documentation drift check (see gradle/docs-check.gradle.kts). Part of `check`.
apply(from = "gradle/docs-check.gradle.kts")
tasks.matching { it.name == "check" }.configureEach { dependsOn("checkDocs") }

// Markdown lint (scripts/lint-markdown.sh, rules in docs/MARKDOWN_GUIDE.md). Part of `check`.
tasks.register<Exec>("lintMarkdown") {
    group = "verification"
    description = "Lints Markdown files with markdownlint."
    commandLine("sh", "scripts/lint-markdown.sh")
}
tasks.matching { it.name == "check" }.configureEach { dependsOn("lintMarkdown") }

// Secret scan of the git history with gitleaks (scripts/scan-secrets.sh). Part of `check`.
tasks.register<Exec>("scanSecrets") {
    group = "verification"
    description = "Scans the git history for secrets with gitleaks."
    commandLine("sh", "scripts/scan-secrets.sh")
}
tasks.matching { it.name == "check" }.configureEach { dependsOn("scanSecrets") }

// GitHub Actions workflows: actionlint and zizmor (scripts/lint-workflows.sh). Part of `check`.
tasks.register<Exec>("lintWorkflows") {
    group = "verification"
    description = "Lints the GitHub Actions workflows with actionlint and zizmor."
    commandLine("sh", "scripts/lint-workflows.sh")
}
tasks.matching { it.name == "check" }.configureEach { dependsOn("lintWorkflows") }

// Known vulnerabilities in the shipped dependencies: OSV-Scanner on the CycloneDX SBOM that
// `cyclonedxBom` builds from each module's runtime classpath (scripts/scan-dependencies.sh). Not
// part of `check`: it needs the network. CI runs it on every pull request and push.
tasks.cyclonedxDirectBom {
    // The root project ships nothing; its build tooling is covered by the dependency graph.
    includeConfigs = listOf("runtimeClasspath")
}
tasks.register<Exec>("scanDependencies") {
    group = "verification"
    description = "Scans the shipped dependencies for known vulnerabilities with OSV-Scanner."
    dependsOn(tasks.cyclonedxBom)
    commandLine("sh", "scripts/scan-dependencies.sh")
}

// Points git at the tracked hooks in .githooks (formatting, commit message and push checks).
tasks.register<Exec>("installGitHooks") {
    group = "verification"
    description = "Configures git to use the hooks in .githooks."
    commandLine("git", "config", "core.hooksPath", ".githooks")
}
