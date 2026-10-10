# Project context

A short shared baseline for planning sessions. It states facts, not instructions. Details live in
the [README](README.md), the [design document](docs/design/2026-10-06-otium-library-app-design.md)
and the [implementation plan](docs/plans/2026-10-06-otium-implementation-plan.md); when they
disagree with this file, they win.

Last reviewed: 2026-10-10.

## Name and purpose

Otium is an Android app for cataloguing and tracking a personal library of physical books: scan
or type an ISBN, fetch the details, track reading progress by page, and search the collection. It
is private and offline-first, built to feel like a quiet study.

## Tech stack

- Kotlin, Jetpack Compose with Material 3, Hilt, coroutines and Flow.
- Room with an FTS4 index as the source of truth; DataStore for preferences.
- Retrofit with kotlinx.serialization (Google Books, Open Library), CameraX and ML Kit, Coil,
  WorkManager.
- Google Drive REST (`drive.appdata`) for backup; Tink and Bouncy Castle (Argon2id) for optional
  passphrase encryption.
- Modules: `:app`, `:domain` (pure Kotlin), `:benchmark`. minSdk 26, compileSdk 37, JDK 17.
- CI on GitHub Actions: Spotless, detekt, Android Lint, unit tests with a Kover floor, gitleaks,
  OSV-Scanner, CodeQL, assemble debug and release. Dependabot weekly into `develop`.

## Current phase

- Version 1 features are built and tested on the JVM (phases 0 to 6). No release yet; version
  0.1.0.
- Open: device checks for phases 3 to 6, the Google Cloud and OAuth setup for Drive, and Phase 7
  (accessibility on device, licences and privacy policy, Play listing, baseline profile, release
  cleanup).
- Open: a repeatable release process (tag-based versioning, release workflow, changelog).
- Public repository on GitHub, maintained by one person in spare time. Work happens on `develop`;
  `main` changes only through a pull request with green CI.

## Key constraints

- No backend, accounts, analytics or ads. Data stays on the device and in the user's own Drive
  app folder.
- Calm by design: no streaks, badges or nagging notifications.
- WCAG AA contrast in light and dark themes; text up to 200%.
- `:domain` stays free of Android and framework imports; layer rules are enforced by tests.
- Solo maintenance: processes must stay light enough for one person.

## What done looks like

- Version 1 on the Play Store, passing a release gate that includes a release-build smoke test and
  a backup restore round-trip.
- Releases are tagged, versioned from the tag, built and signed by CI, with a changelog.
- A healthy public repository: clear docs, issue forms, security policy, and CI as the gate.
