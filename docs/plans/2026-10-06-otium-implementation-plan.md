# Otium v1 Implementation Plan

Date: 2026-10-06
Source design: `docs/design/2026-10-06-otium-library-app-design.md` (commit `c55b61b`)
Complexity: Large (8 phases, solo project)
Status: In progress. Phases 0, 1 and 2 complete; next up are Phases 3 and 4.

## 1. Summary

Build Otium, an Android-only personal library inventory app (Kotlin, Jetpack Compose, minSdk 26) in
two Gradle modules: a pure-Kotlin `:domain` module and the Android `:app` module. The app adds books
by barcode scan, manual ISBN or title search; merges Google Books and Open Library metadata; tracks
reading progress by page; supports FTS search and filters; backs up to Google Drive `appDataFolder`;
and ships with the "analog sanctity" visual identity.

The repository is greenfield (only `README.md`, `LICENSE`, `.gitignore` and the design doc exist),
so there are no existing code patterns to mirror. Conventions come from the design doc: Clean
Architecture layering, MVVM with `StateFlow`, Room as source of truth, typed `Try`/`AppError`
results, interfaces owned by `:domain`.

## 2. Decisions needed before Phase 0

| # | Decision | Recommendation | Blocks |
|---|----------|----------------|--------|
| P1 | Application id / package name | **Decided: `app.otium`** (`<pkg>` in file paths below means `app/otium`). Cannot change after Play Store publish. | Phase 0 scaffold, API key restriction, Drive OAuth client |
| P2 | Drive client | **Decided: REST via Retrofit/OkHttp.** Only about 5 calls needed (list, create, update, download, delete in `appDataFolder`); avoids the heavy Google API client library and R8 pain. | Phase 6 |
| P3 | Use case provisioning | `@Provides` in `:app/di`, so `:domain` has no `javax.inject` dependency. | Phase 0 |
| P4 | Google Cloud project | Create one now: enable Books API and Drive API, configure the OAuth consent screen (external, testing mode), register debug SHA-1. | Phase 4 (API key), Phase 6 (OAuth) |

P1 and P4 involve external setup that has lead time. Do them in parallel with Phase 0.

## 3. Phase overview and dependencies

```text
P0 Scaffold/CI ──> P1 Theme ─────────────┐
      │                                  │
      └──> P2 Domain + data core ──┬──> P3 Library/detail UI ──┐
                                   ├──> P4 Remote metadata ────┼──> P5 Add flow ──┐
                                   └──> P6 Backup ─────────────┘                  ├──> P7 Polish/Play
                                                                                  ┘
```

- P1 (theme) and P2 (domain/data) can run in parallel after P0.
- P3 and P4 can run in parallel after P2. P3 also needs P1.
- P5 needs P3 and P4. P6 needs P2 (and the P3 settings entry point, which can be a stub).
- P7 is last.

Suggested order for one person: P0, P2, P1, P3, P4, P5, P6, P7. Build restore before auto-backup
inside P6.

## 4. Conventions for every phase

- **TDD:** write the failing test first for domain logic, DAO queries, parsers and use cases (red,
  green, refactor).
- **Layer rules:** follow design section 4.1 "Layer rules". Entities and DTOs never leave `data/`.
  Repositories return `Try<T>`. No `GlobalScope`. Dispatchers via `DispatcherProvider`.
- **Commits:** small, one logical change each, conventional style (`feat:`, `test:`, `build:`,
  `docs:`). Work on `develop`, merge to `main` at phase boundaries.
- **Secrets:** API key and signing config live in `local.properties` / environment, never committed.
  Confirm `.gitignore` covers `local.properties`, `*.jks`, `*.keystore`, `google-services.json`
  before the first build.
- **Validation per phase:** `./gradlew lint testDebugUnitTest assembleDebug` must pass; instrumented
  tests where a phase lists them (`./gradlew connectedDebugAndroidTest`).
- **Done means:** tasks complete, validation green, acceptance checklist ticked.

---

## Phase 0: Scaffold and CI

Complexity: Small

### Goal

An empty app with the final module structure builds and tests in CI.

### Files to create

| File | Purpose |
|------|---------|
| `settings.gradle.kts` | Includes `:app`, `:domain` |
| `build.gradle.kts` | Root plugin declarations |
| `gradle/libs.versions.toml` | Version catalog, all versions pinned here |
| `gradle.properties` | JVM args, AndroidX flags |
| `domain/build.gradle.kts` | `kotlin("jvm")`, depends on `kotlinx-coroutines-core` only; test deps JUnit, Turbine, MockK |
| `app/build.gradle.kts` | Android application, Compose, Hilt, KSP, Room (schema export dir), `BuildConfig` field for Books API key |
| `app/src/main/AndroidManifest.xml` | `INTERNET`, application class, launcher activity |
| `app/src/main/kotlin/<pkg>/OtiumApp.kt` | `@HiltAndroidApp` application |
| `app/src/main/kotlin/<pkg>/MainActivity.kt` | `@AndroidEntryPoint`, hosts placeholder Compose screen |
| `app/src/main/kotlin/<pkg>/di/` | Empty Hilt module files, `DispatcherModule` |
| `domain/src/main/kotlin/<pkg>/domain/port/DispatcherProvider.kt` | First domain interface |
| `app/schemas/` | Room exported schemas (tracked in git) |
| `.github/workflows/ci.yml` | lint, unit tests, assemble on push and PR |
| `README.md` | Build instructions, `local.properties` keys |

### Tasks

1. **Create the Gradle skeleton** with the two modules and the version catalog. Pin versions of AGP,
   Kotlin, KSP, Compose BOM, Hilt, Room, Retrofit, OkHttp, kotlinx.serialization, Coil, CameraX, ML
   Kit barcode scanning, WorkManager, Play Services Auth, coroutines, JUnit, Turbine, MockK,
   MockWebServer. Verify each against current stable releases at setup time.
   - Validate: `./gradlew help` succeeds.
2. **Configure `:domain`** as a Kotlin/JVM module. Add an Android-import guard: a unit test or
   Gradle dependency check that fails if `:domain` gains an `android.*` or `androidx.*` dependency
   (the module setup already prevents imports; the check protects against dependency creep).
   - Validate: `./gradlew :domain:test`.
3. **Configure `:app`**: Compose, Hilt, KSP, Room schema export (`room.schemaLocation` pointing at
   `app/schemas`), `BuildConfig.BOOKS_API_KEY` read from `local.properties` with an empty-string
   default (keyless fallback).
   - Validate: `./gradlew :app:assembleDebug` with and without the key present.
4. **Add the application class, `MainActivity`, a placeholder screen** and `DispatcherProvider` with
   its Hilt binding.
   - Validate: app installs and shows the placeholder; one trivial unit test in each module passes.
5. **Add GitHub Actions CI** (JDK, Gradle cache, `lint testDebugUnitTest assembleDebug`).
   Instrumented tests stay on demand.
   - Validate: CI green on a pushed branch.
6. **Harden `.gitignore`** (see conventions) and document setup in `README.md`.

### Acceptance

- [x] Both modules build; `:domain` has no Android dependencies.
- [x] CI passes on `develop`.
- [x] Room schema directory exists and is tracked.
- [x] No secrets in git history.

---

## Phase 1: Theme and design system

Complexity: Small to Medium. Runs in parallel with Phase 2.

### Goal

`OtiumTheme` provides the Earth & Ink identity in light and dark, so every later screen consumes
tokens only.

### Files to create (`app/ui/theme/`)

| File | Purpose |
|------|---------|
| `Color.kt` | Raw palette constants (the only place hex values appear) |
| `OtiumColors.kt` | Extension for status colors (not started, in progress, read) and texture tokens, provided via `CompositionLocal` |
| `Theme.kt` | `OtiumTheme` composable: `ColorScheme`, `Typography`, `Shapes`, dynamic color off, reads system dark mode |
| `Type.kt` | Serif headline family, sans UI family |
| `Shape.kt` | Corner radii |
| `res/font/` | Bundled OFL fonts (EB Garamond or Cormorant Garamond; Inter or Source Sans 3; Cinzel for the wordmark placeholder), plus licence files under `third_party/` |
| `res/drawable/` | Placeholder arch-over-book vector, tiled paper texture (a few KB) |
| `ui/theme/ThemeCatalogScreen.kt` | Debug-only screen showing every token, text style and component in both themes |
| `ui/components/` | `OtiumTopBar`, `ProgressBar` (thin Terra), `StatusChip` (label or icon plus color), `BookCover` (placeholder, shadow, content description), `ShelfRow` (shelf line plus shadow) |

### Tasks

1. **Palette and contrast check.** Start from the proposed values (Parchment `#F4EBD9`, Sage
   `#6F8062`, Terra `#B5573A`, Ink `#1F2433`; dark background near `#1B1A17`). Compute WCAG ratios
   for Ink on Parchment, Terra on Parchment, Sage on Parchment, and the dark-theme pairs. Body text
   must reach 4.5:1, UI components and graphics 3:1. Adjust values (not the intent) until they pass;
   record final values and ratios in a comment block in `Color.kt`.
   - Validate: a unit test computes the contrast ratios from the constants and asserts the
     thresholds. Final tuning on a real device is a Phase 7 item.
2. **Typography and fonts.** Wire the serif and sans families, define the full Material 3 type
   scale, bundle fonts (no runtime download), add licence texts.
   - Validate: catalog screen shows all styles.
3. **`OtiumTheme`, `OtiumColors`, shapes.** Dynamic color off. Status colors never rely on color
   alone: `StatusChip` always includes a label or icon.
   - Validate: Compose preview and `ThemeCatalogScreen` in light and dark.
4. **Shared components** listed above, token-only (lint rule or code review check: no raw hex
   outside `Color.kt`).
   - Validate: Compose UI test that `StatusChip` exposes text for each status; screenshot review.
5. **Reduced-motion helper.** A small `rememberReducedMotion()` that reads the system
   animator-duration scale, used later by transitions.

### Acceptance

- [x] Light and dark themes render the catalog correctly. (Rendered headlessly by
  `ThemeCatalogScreenshotTest` to `app/build/screenshots`; reviewed by eye. A real-device pass comes
  in Phase 7.)
- [x] Contrast unit test passes. (Sage and Terra darkened to meet 4.5:1; see the design doc.)
- [x] No raw hex values outside `Color.kt`. (Enforced by `ArchitectureTest`; the window background
  in XML is checked against the palette.)
- [x] Fonts and licences bundled. (EB Garamond, Source Sans 3 and Cinzel, in `third_party/fonts`.)

---

## Phase 2: Domain and data core

Complexity: Medium. Strict TDD.

### Goal

All business rules exist as pure, tested Kotlin; Room stores and queries the library;
`BookRepository` works against it. No UI.

### Files to create

`:domain` (`domain/src/main/kotlin/<pkg>/domain/`):

| File | Purpose |
|------|---------|
| `model/Book.kt`, `BookSummary.kt`, `BookCandidate.kt`, `BookStatus.kt` | Domain models (plain data classes) |
| `model/LibraryFilter.kt`, `SortOrder.kt` | Filter and sort inputs |
| `error/AppError.kt`, `Try.kt` | Error model from design 4.1 |
| `repo/BookRepository.kt` | Interface: observe library, get by id, find by ISBN, save, update progress, delete, tag/category operations |
| `usecase/DeriveStatus.kt`, `ClampPage.kt`, `ValidateIsbn13.kt` | Pure rules |
| `usecase/ObserveLibrary.kt`, `UpdateProgress.kt`, `AddBookManually.kt` | Orchestrating use cases |
| `usecase/MergeMetadata.kt` | Merge rules (implemented here, wired in Phase 4) |

`:app` (`app/src/main/kotlin/<pkg>/data/local/`):

| File | Purpose |
|------|---------|
| `OtiumDatabase.kt` | Room database, version 1, exported schema |
| `entity/BookEntity.kt`, `AuthorEntity`, `CategoryEntity`, `TagEntity`, join entities | Tables from design 4.2 |
| `entity/BookFtsEntity.kt` | `@Fts4(contentEntity = BookEntity::class)` over title, subtitle, authors |
| `dao/BookDao.kt` | Filter/sort/search query, CRUD, ISBN lookup |
| `dao/LabelDao.kt` | Authors, categories, tags |
| `row/BookListRow.kt` | Room projection for the list |
| `mapper/*.kt` | Entity and row to domain mappers (extension functions) |
| `repo/RoomBookRepository.kt` | Implements `BookRepository`, converts exceptions to `AppError` |
| `di/DatabaseModule.kt`, `RepositoryModule.kt`, `UseCaseModule.kt` | Hilt bindings and `@Provides` |

### Tasks (test first in each)

1. **`DeriveStatus`.** Cases: `currentPage == 0`, `0 < current < total`, `current >= total` with
   `total > 0`, `total == 0` (not started, prompt to set pages).
   - Validate: `./gradlew :domain:test`.
2. **`ClampPage`.** Negative to 0; above total clamps to total and signals "read"; total unknown
   leaves value unchanged.
3. **`ValidateIsbn13`.** Checksum, 978/979 prefix, length, hyphens and spaces stripped; ISBN-10 to
   ISBN-13 conversion helper for manual entry.
4. **`AppError` and `Try`.** Plus small helpers (`map`, `flatMap`, `getOrNull`) with tests.
5. **`MergeMetadata`.** Google wins title, authors, description; gaps (pages, cover, publisher)
   filled from Open Library; cover order Google high-res then Open Library; placeholder cover
   rejection is expressed as a predicate input (the actual image size check lives in the data layer
   in Phase 4); `source` set correctly; `userEdited = true` fields never overwritten on refresh.
6. **Room schema and DAO.** Entities, FTS4 table, indices (unique `isbn13`, join table keys), DAO
   queries.
   - In-memory Room instrumented tests: FTS prefix search on title, subtitle and author; each filter
     alone and combined (status, multiple categories, multiple tags, author); every sort order;
     duplicate ISBN constraint; status SQL boundaries (0, mid, equal total, over total, total 0);
     delete cascades join rows.
   - Validate: `./gradlew connectedDebugAndroidTest` (or Robolectric for DAO tests if no device is
     available; decide at setup).
7. **Query builder.** `LibraryFilter` to a `SupportSQLiteQuery` or a set of DAO queries, covering
   the AND-combined filters and FTS join. Unit test the SQL string generation where practical;
   behaviour covered by task 6.
8. **Mappers and `RoomBookRepository`.** Map `BookListRow` to `BookSummary`; wrap exceptions
   (`SQLiteConstraintException` on ISBN becomes `Duplicate(existingId)`).
9. **Use cases.** `ObserveLibrary` (filter, sort, debounce stays in the ViewModel), `UpdateProgress`
   (clamp, derive, `updatedAt`), `AddBookManually` (dedupe by ISBN, normalise authors into tables).
   Tests against a fake `BookRepository`.
10. **Migration scaffolding.** Add `MigrationTestHelper` setup and a schema-version test so future
    migrations are tested from exported schemas.
11. **Debug seeder.** A debug-only source set that inserts about 5,000 synthetic books, used in
    Phase 3 for performance checks.

### Validation

```bash
./gradlew :domain:test :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

### Acceptance

- [x] Domain tests green on plain JVM.
- [x] DAO tests cover search, filters, sort, constraints. (Run under Robolectric on the JVM; an
  on-device run is still outstanding.)
- [x] FTS query on 5,000 seeded books returns well under 100 ms (host JVM regression guard: 1-6 ms;
  device measurement comes with the Phase 3 Macrobenchmark).
- [x] Exported schema `1.json` is committed.

---

## Phase 3: Library and detail UI

Complexity: Medium. Needs Phases 1 and 2.

### Goal

Browse, search, filter, sort and update progress, all offline, against seeded or manually entered
data.

### Files to create

| File | Purpose |
|------|---------|
| `ui/navigation/OtiumNavHost.kt`, `Routes.kt` | Navigation Compose graph |
| `ui/library/LibraryScreen.kt`, `LibraryViewModel.kt`, `LibraryUiState.kt` | Shelf grid, list mode, search bar, chips, sort, Add FAB, empty state |
| `ui/library/ShelfGrid.kt`, `BookListRowItem.kt` | `LazyVerticalGrid` styled as shelf rows; list-mode row |
| `ui/library/FilterSheet.kt` | Status, categories, tags, author filters |
| `ui/detail/DetailScreen.kt`, `DetailViewModel.kt`, `DetailUiState.kt` | Cover, metadata, collapsible description, tags, overflow menu |
| `ui/detail/ProgressCard.kt`, `ProgressEditSheet.kt` | "Page X of Y", bar, slider and number input, mark finished |
| `ui/common/ErrorMessages.kt` | `AppError` to string mapping per feature |
| `res/values/strings.xml` | All user-facing strings |

### Tasks

1. **Navigation shell** with Library and Detail routes and stub Add/Settings routes.
2. **`LibraryViewModel`.** Holds a `LibraryFilter` `StateFlow`; search text debounced about 200 ms;
   combines with `ObserveLibrary`; exposes immutable UI state. Turbine tests for debounce, filter
   combination and sort changes with a fake use case.
3. **Shelf grid and list mode.** Covers/spines on shelf rows with a subtle shelf line and shadow,
   built on `LazyVerticalGrid` with stable keys and `contentType`; mode toggle persisted (DataStore
   or `SharedPreferences`; per-viewer convenience only). Empty shelf state with scan prompt.
4. **Search, chips and sort.** Status chips, category and tag chips, sort menu; active filters shown
   in Terra; clear-all action.
5. **Detail screen.** Large cover on a soft shadow, serif title, collapsible description, tags,
   overflow menu with edit and delete (delete confirmation dialog; edit routes to the Phase 5
   confirm/edit screen, stub for now).
6. **Progress update.** Tap progress card, number input or slider, confirm; `UpdateProgress` clamps
   and re-derives; "mark finished" sets `currentPage = totalPages`; prompt to set total pages when
   unknown. No streaks, badges or confetti.
7. **Accessibility basics.** Content descriptions on covers, minimum 48dp touch targets, status text
   alongside color, TalkBack labels for the progress card.
8. **Transitions.** Gentle shelf-to-detail fade, disabled when reduced motion is on.
9. **Performance check** with the debug seeder: scroll 5,000 books at 60fps on a mid-range device or
   emulator; fix with stable keys, image downsampling and avoiding per-item recomposition if needed.
   Measure it with a Macrobenchmark (`:benchmark` module, `FrameTimingMetric` for scroll jank and
   search latency) rather than by eye, per the Android architecture recommendations. Needs a device
   or emulator.
10. **UI architecture rules** (docs/STYLE_GUIDE.md section 3.2): one immutable UI state per screen
    exposed as `StateFlow`, `collectAsStateWithLifecycle`, no one-off events from ViewModels
    (outcomes such as "open existing book" are state the UI reports handled), screen-level
    ViewModels only, plain state holders in reusable components. `ArchitectureTest` rules start
    applying as soon as the ViewModels exist.

### Tests

- ViewModel unit tests (Turbine, fakes).
- Compose UI tests: filter chips narrow the list; progress update changes status; empty state shown.

### Acceptance

- [ ] All screens usable offline with seeded data.
- [ ] Search feels instant on 5,000 books.
- [ ] Compose UI tests green.
- [ ] No business rules in ViewModels (review against design layer rules).

---

## Phase 4: Remote metadata

Complexity: Medium. Needs Phase 2. Parallel with Phase 3.

### Goal

`MetadataRepository` returns one merged `BookCandidate` for an ISBN or a list of candidates for a
title/author query, with fallbacks and errors handled.

### Files to create

`:domain`:

| File | Purpose |
|------|---------|
| `repo/MetadataRepository.kt` | `lookupByIsbn(isbn13)`, `searchByTitleAuthor(query)` returning `Try` |
| `usecase/AddBookFromIsbn.kt` | Dedupe check, lookup, build candidate; saving happens after the confirm screen |
| `port/CoverStore.kt` | Save, load, delete covers |

`:app`:

| File | Purpose |
|------|---------|
| `data/remote/googlebooks/GoogleBooksApi.kt`, `GoogleBooksDto.kt`, `GoogleBooksMapper.kt` | `volumes?q=isbn:X`, `intitle:`/`inauthor:` search |
| `data/remote/openlibrary/OpenLibraryApi.kt`, `OpenLibraryDto.kt`, `OpenLibraryMapper.kt` | `/isbn/X.json`, work and author lookups as needed, search fallback |
| `di/RemoteModule.kt` | OkHttp (8 s timeouts, user agent, API key interceptor), Retrofit, JSON config with `ignoreUnknownKeys` |
| `data/remote/ErrorMapping.kt` | HTTP and IO exceptions to `AppError` |
| `data/repo/RemoteMetadataRepository.kt` | Parallel calls, one retry with backoff on 429/5xx, fallback to the other API, merge via `MergeMetadata` |
| `data/cover/FileCoverStore.kt` | Coil download to files dir, placeholder rejection (tiny image or 404 with `default=false`) |
| `app/src/test/resources/fixtures/` | Recorded JSON fixtures for both APIs |

### Tasks

1. **DTOs and mappers from fixtures first.** Fixtures: full record, missing pages, missing cover,
   missing authors, empty result, malformed field types.
   - Validate: parser unit tests.
2. **API interfaces and OkHttp setup.** API key via interceptor only when present; no key still
   works (low quota).
3. **`RemoteMetadataRepository` flow.** Parallel `async` calls with 8 s timeout each; merge with
   `MergeMetadata`; on 429/5xx retry once with backoff, then rely on the other source; both fail
   yields `Offline`, `RateLimited`, `Timeout` or `NotFound` accordingly.
   - Validate with MockWebServer: success on both; Google only; Open Library only; 429 then success;
     5xx on one API; timeout; both fail; slow API does not block the fast one longer than the
     timeout.
4. **Title/author search.** Google first (up to 20 results with thumbnails); Open Library only when
   Google returns nothing.
5. **Cover pipeline.** `FileCoverStore` downloads the chosen cover once, stores the path;
   placeholder check rejects tiny images; user-picked cover overrides remote URL. Test with
   MockWebServer and temp directories.
6. **`AddBookFromIsbn` use case.** Local ISBN check returns `Duplicate`; otherwise lookup. Tests
   with fakes.
7. **Google Books terms.** Add attribution string/resource for later display (Phase 7) and ensure
   cover caching is limited to the user's own library.

### Acceptance

- [ ] All MockWebServer scenarios pass.
- [ ] A real ISBN lookup returns a merged candidate on a device or emulator (manual check).
- [ ] Missing key still works.
- [ ] No API key committed.

---

## Phase 5: Add flow

Complexity: Medium to Large. Needs Phases 3 and 4.

### Goal

Add books by scan, manual ISBN or title search, with a confirm/edit screen before every save and
offline queueing.

### Files to create

`:domain`:

| File | Purpose |
|------|---------|
| `port/BarcodeScanner.kt` | Interface producing validated ISBN reads |
| `usecase/SaveBook.kt` (or reuse `AddBookManually`) | Persist confirmed candidate, download cover, set `userEdited` |
| `usecase/RetryPendingLookups.kt` | Resolve `needsLookup` rows |

`:app`:

| File | Purpose |
|------|---------|
| `ui/add/AddSheet.kt` | Scan, Enter ISBN, Search by title |
| `ui/add/scan/ScanScreen.kt`, `ScanViewModel.kt` | CameraX preview, permission rationale, haptic on read |
| `data/scan/MlKitBarcodeScanner.kt` | ML Kit EAN-13 only, accepts 978/979, stops analysis after first valid read |
| `ui/add/isbn/IsbnEntryScreen.kt`, `IsbnEntryViewModel.kt` | Manual entry with live validation |
| `ui/add/search/TitleSearchScreen.kt`, `TitleSearchViewModel.kt` | Results list with thumbnails (max 20) |
| `ui/add/confirm/ConfirmScreen.kt`, `ConfirmViewModel.kt` | All fields editable, cover swap (gallery, camera, alternate remote), tags, total pages |
| `work/LookupRetryWorker.kt` | WorkManager entry for pending lookups |
| `data/scan/FakeBarcodeScanner.kt` (test source set) | Fake for UI tests |

### Tasks

1. **Add sheet and navigation** from the library FAB.
2. **Manual ISBN path first** (no hardware): entry, validation errors, lookup, duplicate handling
   (open existing book), confirm screen, save.
   - Validate: Compose UI test "manual ISBN to saved book" using a fake `MetadataRepository`.
3. **Confirm/edit screen.** Editable fields, cover swap options, tag editor with autocomplete from
   existing tags, total pages field with prompt, save disabled until title present; saving sets
   `userEdited` for edited fields.
4. **No-result and error states.** No result opens the confirm screen empty with ISBN prefilled;
   `RateLimited` and other errors show plain messages with retry or manual entry.
5. **Title/author search path** into the same confirm screen.
6. **Offline behaviour.** Manual entry saves immediately; ISBN-only entries save with
   `needsLookup = true`; `LookupRetryWorker` (network-connected constraint, exponential backoff)
   fills metadata later unless `userEdited`.
   - Validate: worker test with `TestListenableWorkerBuilder` and fakes.
7. **Scanner.** Implement the CameraX plus ML Kit scanner behind `BarcodeScanner`; permission
   request with rationale and manual-entry fallback when denied; haptic feedback and stop analysis
   on first valid read; checksum validation before lookup.
   - Validate: manual device test on several real books (matte, glossy, curved spine, low light);
     record results in the PR.
8. **Delete and edit from detail** now route to the confirm/edit screen.

### Acceptance

- [ ] All three add paths end on the confirm screen and save correctly.
- [ ] Duplicate ISBN opens the existing book.
- [ ] Offline ISBN entry is saved and later enriched.
- [ ] Scan-to-result under about 3 s on a normal connection (manual measurement).
- [ ] UI tests green with the fake scanner.

---

## Phase 6: Backup

Complexity: Large. Highest risk. Needs Phase 2; settings entry point from Phase 3.

### Goal

Reliable manual and periodic backup to Drive `appDataFolder`, with a safe restore.

### Files to create

`:domain`:

| File | Purpose |
|------|---------|
| `repo/BackupRepository.kt` | Backup, restore, status |
| `port/DriveBackupClient.kt` | Upload, download, list, delete, quota check, auth state |
| `port/DatabaseSwapper.kt` | Checkpoint, close, replace, reopen |
| `model/BackupInfo.kt`, `BackupSettings.kt` | Last backup time and size, schedule settings |
| `usecase/RunBackup.kt`, `RestoreBackup.kt`, `ConfigureAutoBackup.kt` | Orchestration |

`:app`:

| File | Purpose |
|------|---------|
| `data/backup/GoogleDriveBackupClient.kt` | REST via Retrofit/OkHttp (P2), `drive.appdata` scope |
| `data/backup/GoogleAuthManager.kt` | Google Identity Services authorization, token retrieval, re-sign-in signal |
| `data/backup/BackupArchiver.kt` | Zip DB plus covers plus metadata JSON; unzip and verify |
| `data/backup/RoomDatabaseSwapper.kt` | WAL checkpoint, close Room, swap files, rebuild database |
| `data/repo/DriveBackupRepository.kt` | Ties it together, error mapping |
| `work/BackupWorker.kt` | Periodic worker with user constraints and backoff |
| `ui/backup/BackupScreen.kt`, `BackupViewModel.kt` | Sign-in, Back up now, Restore, auto-backup toggle, frequency, Wi-Fi-only, charging-only, last backup time and size |
| `di/BackupModule.kt` | Bindings; Room database provided through a swappable holder |

### Tasks

1. **Prototype the Room swap first (spike).** Prove that closing Room, replacing the DB file and
   covers, and reopening works with Hilt-provided singletons (use a database holder or provider so
   repositories do not hold a stale instance). Write the finding into the design doc if it changes
   the approach.
   - Validate: instrumented test that restores a known DB and queries it.
2. **`BackupArchiver`.** Zip format: `library.db`, `covers/`, `manifest.json` (app version, schema
   version, created at, book count). Unzip to a temp directory and verify before swapping. Zip-slip
   protection when extracting.
   - Validate: round-trip test: seed library, backup to temp zip, wipe, restore, library identical
     (books, labels, progress, covers).
3. **Schema-version check.** Reject a backup whose schema version exceeds the app's with
   `IncompatibleBackup`; accept older versions and run Room migrations on open.
   - Validate: tests with crafted manifests.
4. **`DriveBackupClient` interface and fake**, then the REST implementation: create or update single
   latest file, rotate previous to `.prev`, download, list, delete. Only `appDataFolder`.
   - Validate: MockWebServer tests for upload, rotation, 401 (`AuthExpired`), 403 quota
     (`InsufficientDriveSpace`), network drop.
5. **Google authorization.** Identity Services authorization for `drive.appdata`; handle consent
   intent in the UI layer; surface `AuthExpired` as a re-sign-in prompt.
6. **`RunBackup` and `RestoreBackup` use cases.** Order for backup: checkpoint, archive, upload,
   rotate; local data untouched on any failure. Restore: download, verify, confirm (UI), swap,
   reopen. Tests against fakes covering each failure point and that local data survives.
7. **Backup screen.** Sign-in state, Back up now with progress, Restore with a confirm dialog
   ("replaces current library"), last backup time and size.
8. **Auto backup.** `PeriodicWorkRequest` (daily or weekly) with network type (unmetered when
   Wi-Fi-only) and charging constraints; exponential backoff on retry; failure notification only
   after repeated failures (add `POST_NOTIFICATIONS` runtime request on Android 13+, notification
   channel).
   - Validate: `TestListenableWorkerBuilder` and WorkManager test driver.
9. **Real-device end-to-end:** back up, uninstall, reinstall, sign in, restore; also test restore
   after the `.prev` rotation.

### Acceptance

- [ ] Round-trip test passes.
- [ ] Schema-version rejection works.
- [ ] Failure at any step never deletes local data.
- [ ] Manual device test: backup on one install, restore on a fresh install.
- [ ] Auto backup runs under constraints and notifies only after repeated failure.

---

## Phase 7: Polish and Play readiness

Complexity: Medium. Last.

### Tasks

1. **Accessibility pass.** TalkBack walk-through of all flows, contrast check on a real device in
   both themes (final colour tuning), font scale 200%, touch targets, focus order.
2. **Motion.** Verify all animations respect the system remove-animations setting.
3. **Wordmark and icon.** Replace placeholders if final assets exist; otherwise ship the typeset
   wordmark and vector icon. Adaptive icon and monochrome themed icon.
4. **Attribution and legal.** Google Books attribution displayed where required; open-source
   licences screen (fonts and libraries); privacy policy page (data stays on device, Drive appdata
   only, book APIs receive ISBN or title/author only), hosted somewhere stable (GitHub Pages is
   enough).
5. **Release build.** R8 on with keep rules verified (kotlinx.serialization, Retrofit, Room, Hilt,
   ML Kit); release signing via `keystore.properties` kept out of git; Play App Signing; version
   code and name scheme.
   - Validate: install the release APK/AAB build and repeat the smoke test: add by manual ISBN,
     scan, progress update, backup, restore.
6. **Play listing prep.** Data safety form (no data collected by the developer), content rating,
   permission justifications (camera in-app, `INTERNET`, `POST_NOTIFICATIONS`), screenshots, short
   and full description.
7. **CI additions.** Release assemble check, dependency vulnerability scan (for example Dependabot),
   optional instrumented test job on demand.
8. **Cleanup.** Remove debug seeder from release, strip logging, delete the theme catalog from
   release builds.
9. **Baseline Profile and Startup Profile.** Add a `:baselineprofile` module, generate a profile
   covering cold start, library scroll and opening a book, and ship it in the release build. Measure
   cold start and scroll jank before and after with Macrobenchmark and record the numbers. Needs a
   device or emulator.
10. **Re-run the architecture audit** (docs/STYLE_GUIDE.md section 3.3) against Google's current
    recommendations and update the matrix.

### Acceptance

- [ ] Release build passes the smoke test on a real device.
- [ ] Accessibility checks pass.
- [ ] Privacy policy URL live; data safety form drafted.
- [ ] No secrets or keystores in git.

---

## 5. Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Restore corrupts or loses data | Medium | High | Spike the Room swap first; verify archive before swapping; keep `.prev`; confirm dialog; round-trip tests; build restore before auto-backup |
| Drive OAuth setup delays (consent screen, SHA-1 per build type, testing-mode user list) | High | Medium | Create the Cloud project in Phase 0; register debug SHA-1 now and release SHA-1 (and Play App Signing SHA-1) before Phase 7 |
| Stale Room instance after restore | Medium | High | Database holder/provider pattern chosen in the spike; repositories never cache the database |
| Shelf-styled grid slows scrolling at 5,000 books | Medium | Medium | Style only, no heavy assets; stable keys; measure with seeder in Phase 3 |
| FTS4 query or filter SQL wrong in edge cases | Medium | Medium | DAO instrumented tests for every filter and boundary before UI |
| Metadata inconsistency between sources | High | Low | Merge rules, confirm screen, manual override, `userEdited` protection |
| Google Books quota or key abuse | Low | Medium | Key restricted by package and SHA-1; keyless fallback; Open Library fallback |
| Barcode scan unreliable on glossy or damaged covers | Medium | Low | Manual ISBN and title search paths; test on varied real books |
| R8 strips classes used by serialization, Retrofit or Room | Medium | Medium | Release-build smoke test in Phase 7; add keep rules as needed; consider running a release-like build earlier (end of Phase 4) |
| Hilt and KSP version incompatibilities at setup | Low | Low | Pin a known-good matrix in the version catalog during Phase 0 |
| Scope creep (stats, ratings, iOS) | Medium | Medium | Non-goals in design section 1; defer to v2 |

## 6. Milestones

| Milestone | After phase | What you can do |
|-----------|-------------|-----------------|
| M1: Skeleton | P0 | CI green, empty themed app |
| M2: Offline library | P3 | Browse, search, filter, track progress on seeded or hand-entered data |
| M3: Real books | P5 | Add books by scan, ISBN or title with real metadata |
| M4: Safe data | P6 | Backup and restore to Drive |
| M5: Ship | P7 | Release build, Play listing ready |

## 7. Validation commands (cumulative)

```bash
./gradlew lint
./gradlew :domain:test :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest   # per phase, on a device or emulator
./gradlew :app:assembleRelease             # Phase 7
```

## 8. Final acceptance (v1)

- [ ] All phase acceptance checklists complete.
- [ ] Every non-goal in the design remains out of scope.
- [ ] The design doc matches what was built (update it where implementation diverged, for example
  after the Room swap spike).
- [ ] README documents setup, keys and build.
