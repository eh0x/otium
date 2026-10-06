# Otium

Your personal Alexandria library.

Otium is an Android app for cataloguing and tracking your own physical books: scan an ISBN, fetch metadata, track reading progress by page, search and filter your shelves, and back up to your own Google Drive. Data stays on your device.

Design: [`docs/design/2026-10-06-otium-library-app-design.md`](docs/design/2026-10-06-otium-library-app-design.md)
Plan: [`docs/plans/2026-10-06-otium-implementation-plan.md`](docs/plans/2026-10-06-otium-implementation-plan.md)

## Project layout

| Module | Purpose |
|--------|---------|
| `:domain` | Pure Kotlin/JVM: models, errors, repository and port interfaces, use cases. No Android dependencies (enforced by `DomainPurityTest`). |
| `:app` | Android app: Compose UI, Room, Retrofit, Hilt wiring, WorkManager. |

Application id: `app.otium`. minSdk 26.

## Requirements

- JDK 17
- Android SDK with platform 37 and build-tools 37 (Android Studio installs these; command-line tools work too)

## Setup

1. Create `local.properties` in the repo root (it is git-ignored):

   ```properties
   sdk.dir=/path/to/Android/sdk
   # Optional. Without a key the app uses Google Books with the low keyless quota.
   books.api.key=YOUR_GOOGLE_BOOKS_API_KEY
   ```

2. Install the git hooks (formatting, commit message and push checks):

   ```bash
   ./gradlew installGitHooks
   ```

3. Build and verify:

   ```bash
   ./gradlew spotlessCheck detekt lint testDebugUnitTest :domain:test assembleDebug
   ```

   Fix formatting with `./gradlew spotlessApply`.

   Instrumented tests (device or emulator required):

   ```bash
   ./gradlew :app:connectedDebugAndroidTest
   ```

## Code style

Kotlin follows the [Android Kotlin style guide](https://developer.android.com/kotlin/style-guide).
The rules, project decisions and how each one is enforced are in
[`docs/STYLE_GUIDE.md`](docs/STYLE_GUIDE.md).

## Secrets

Never commit `local.properties`, `keystore.properties`, keystores, or `google-services.json`. The Google Books API key should be restricted by package name (`app.otium`) and SHA-1 in the Google Cloud console.

## License

See [LICENSE](LICENSE).
