# Otium Modernization & Completion Plan

This plan was generated based on the architecture document and a local feasibility spike. Otium is
a modern, actively-developed greenfield application rather than a legacy rescue project. Therefore,
this plan outlines the strategy for completing the remaining implementation phases while preserving
the existing, high-fidelity safety net.

## Phase 1: Assessment of Current State

- **Tech Stack:** Modern Android development stack (Kotlin 2.4.20, Jetpack Compose, Room FTS4,
Hilt, Gradle 9.8).
- **Domain Map:** The application logic is strictly separated into a `:domain` module (pure
Kotlin), while `:app` handles the Android lifecycle, UI, Room DB, and Hilt DI.
- **Identified Progress:** Foundational rules, Room data layer, Library screens, Metadata lookups,
and Add flows are largely complete (`README.md#L59-L69`).
- **Remaining Work:** Finalizing device integrations for Drive backup, polishing the
UI/accessibility, and Play Store release preparation.

## Phase 2: Feasibility Spike, Strategy Fork & Safety Ladder

### 2a. Feasibility Spike Results (Time-boxed)

A local feasibility spike (`./gradlew assembleDebug testDebugUnitTest :domain:test`) yielded the
following:

- **Dependencies install?** Yes, cleanly from `gradle/libs.versions.toml`.
- **Native/build steps succeed?** Yes, under Temurin JDK 17.
- **Does it boot / start?** Yes, the debug APK builds successfully.
- **Does the test runner execute and pass?** Yes, all tests pass (including Konsist architecture
rules and domain tests).

### 2b. Testability Milestone

The Testability Milestone marks when a component can build, run, and pass a test on a supported
runtime.

- **Result:** **Already achieved across the board.** Both `:domain` and `:app` modules cross the
Testability Milestone immediately at checkout (Phase 0).

### 2c. Strategy Fork

- **Strategy:** **Expand-and-Secure.** Since the app is greenfield and already runs on the modern
target stack, there is no legacy corpse to net. We can confidently build out the remaining features
(Drive Backup, UI polish) using the existing test harness.

### 2d. Safety Ladder Rung

- **Current Rung:** **L4 — Full automated gate.**
- **Reasoning:** The app boasts a comprehensive suite of unit tests, Robolectric data tests,
architecture enforcement tests (Konsist), and CI coverage guardrails (Kover). This top-tier
regression safety net is already active and must be maintained for all future phases.

### 2e. CI Milestone

- **Current State:** **CI is already stood up.** The GitHub Actions workflow
(`.github/workflows/ci.yml`) triggers on `main`, `develop`, and `pull_request`, running linting,
unit tests, and build verification.
- **User Action Item:** _Enforce the CI gate._ While the workflow runs, an agent cannot enable
branch protection rules. A project owner must manually configure the repository settings to require
the "Static Analysis & Docs", "Secret Scan", "Android Lint", and "Unit Tests" jobs to pass before
merging PRs.

---

## Phase 3: Completion Roadmap

Since the app is lit (post-testability), all remaining feature work operates in the lit regime
under the L4 safety ladder.

### Phase 3.1: Finalize Drive Backup & Restore (Phase 6 of Implementation Plan)

- **Goal:** Complete the integration with Google Drive (`drive.appdata` scope) for library backups.
- **Safety / Verification:**
  - Write domain-level tests for backup validation and conflict rules.
  - Implement the UI flow adhering to established Compose patterns and tests.
- **Exit Criteria:** `./gradlew testDebugUnitTest` remains green, and a manual device test of Drive
Backup succeeds.

### Phase 3.2: Accessibility, Polish, and Device Timings (Phase 7)

- **Goal:** Audit contrast, semantics, and real-device timings to ensure the app handles 5,000-book
loads efficiently.
- **Safety / Verification:**
  - Run `./gradlew :benchmark:connectedBenchmarkAndroidTest` on an emulator/device to capture
scroll and search timings.
  - Validate UI changes against existing screenshot tests (`app/build/screenshots`).
- **Exit Criteria:** Benchmarks pass without regressions; manual accessibility audit complete.

### Phase 3.3: Play Store Release Preparation

- **Goal:** Finalize the application for initial release.
- **Safety / Verification:**
  - Run the release smoke test: `./gradlew :benchmark:connectedBenchmarkAndroidTest
-Pandroid.testInstrumentationRunnerArguments.class=app.otium.benchmark.ReleaseSmokeTest`.
  - Ensure the R8-shrunk APK builds cleanly via `./gradlew assembleRelease`.
- **Exit Criteria:** `assembleRelease` produces a valid APK ready for testing in the Play Console.

---

## Open Questions & Required Decisions

1. **CI Enforcement:** Are the GitHub repository settings configured to make the `ci.yml` workflows
**required status checks** for pull requests targeting `develop` and `main`?
2. **API Keys:** Has the Google Books API Key been configured securely in `local.properties` or via
the CI environment for any end-to-end device testing?
3. **Backup Integration:** Does the Google Cloud Console project have the necessary OAuth scopes
(`drive.appdata`) approved for the Google Drive backup integration?
