# Otium: Personal Library Inventory App — Design

Date: 2026-10-06
Status: Validated through brainstorming, ready for implementation planning

## 1. Understanding Summary

- **What:** An Android-only personal library inventory app.
- **Adding books:** camera barcode scan (ISBN), manual ISBN entry, or title/author search.
- **Metadata:** auto-fetched from Google Books (primary) with Open Library as fallback, including cover. The user can override any field and swap the cover.
- **Tracking:** each book has a current page and a total page count. Status (not started / in progress / read) is derived from those two numbers. Progress is shown as a percentage bar.
- **Search and filters:** full-text on title and author. Filters for genre/category, custom tags, and status.
- **Storage:** local database on device, plus backup to Google Drive (manual button and optional configurable periodic backup).
- **Why / who:** a single owner cataloguing and tracking their own physical books.
- **Distribution:** personal project now, Play Store eventually.
- **Non-goals (v1):** iOS, multi-device live sync, own backend or accounts, cover-photo recognition, reading history/stats, ratings/notes, publisher/year/language filters, social features, multiple copies of the same ISBN.

## 2. Assumptions

- "ISIN" in the original request means ISBN.
- Native Kotlin + Jetpack Compose, minSdk 26.
- Library size up to roughly 5,000 books. Search responds in under 100 ms. Scan-to-result under about 3 s on a normal connection.
- Browsing, searching, and updating progress work fully offline. Adding by scan or search needs network; offline ISBN entries are queued.
- Privacy: data stays on the device. Outbound calls are limited to the book APIs (ISBN or title/author only) and the user's own Google Drive. No analytics, no developer-held accounts.
- Backup is stored in the Drive `appDataFolder` (scope `drive.appdata` only).
- Solo hobby project, maintained by the owner.

## 3. Decision Log

| # | Decision | Alternatives | Why |
|---|----------|--------------|-----|
| D1 | Android-only | iOS, cross-platform | User choice; avoids paying for a platform not needed |
| D2 | Local Room DB + Google Drive backup | Local-only; cloud sync backend | Privacy, offline, no backend to run; backup covers device loss |
| D3 | Google Books primary, Open Library fallback | Either one alone | Better coverage and cover quality combined; both free |
| D4 | Native Kotlin + Compose | Flutter, React Native | Camera/ML Kit, WorkManager and Drive are native-first; iOS reuse is a non-goal (YAGNI) |
| D5 | Status derived from page, not stored | Stored status enum | Avoids drift between status and progress |
| D6 | Status computed in SQL for filtering | Filter in memory | Keeps filtering in one DAO query, scales fine |
| D7 | FTS4 for title/author search | `LIKE` queries | Fast prefix search, built into Room |
| D8 | Normalized author, category, tag tables | Single joined string columns | Enables filter and browse by author/category/tag |
| D9 | Parallel lookup with field-level merge | Sequential fallback only | Fills gaps (pages, cover) from the second source and cuts latency |
| D10 | Always show a confirm/edit screen before saving | Auto-save on scan | API data is often wrong; user wants correct data |
| D11 | Offline ISBN entries saved with a "needs lookup" flag, retried by WorkManager | Block adding when offline | Better UX, little extra cost |
| D12 | `drive.appdata` scope only | Full Drive scope | Non-sensitive scope, no Google verification burden for Play Store |
| D13 | Single zip backup, keep one previous copy | Incremental, versioned history | Simple and safe restore |
| D14 | Material 3 components with a custom brand `ColorScheme` and typography; dynamic color off | Pure Material dynamic color; fully custom components | Keeps accessible, tested components while the Otium identity (parchment, sage, terra, ink) stays intact. Dynamic color would override the palette |
| D15 | Duplicate ISBN: open existing, no second copy | Allow multiple copies | YAGNI |
| D16 | Visual identity "analog sanctity" (see 4.7) | Neutral Material look | User-provided brand reference; the app should feel like a quiet study, not a utility |
| D17 | Shelf-styled library view as a presentation of the existing lazy grid, plus a plain list mode | Illustrated 3D bookshelves | Keeps scrolling fast at 5,000 books; shelf look comes from styling, not heavy assets |
| D18 | Quiet progress only (percentage bar, "Page X of Y"), no streaks, goals or gamification | Streaks, daily targets | Matches the brand ("art of reading leisure") and the v1 non-goal of reading stats |
| D19 | Two Gradle modules: pure-Kotlin `:domain` plus `:app` | One module with package boundaries; full multi-module split (data, presentation, design-system) | Compiler-enforced "no Android in domain" for the price of one extra build file. A full split costs build time and boilerplate a solo app does not need; it can be done later if needed |
| D20 | Typed `Try<T>` / `AppError` returned from repositories and use cases | Exceptions; `kotlin.Result` | The failure cases in 4.3 and 4.4 (offline, 429, duplicate, expired auth, incompatible backup) are expected outcomes the UI handles differently. A sealed type makes each one explicit and testable |
| D21 | Use cases only where logic exists; ViewModels call repositories directly for plain reads | A use case per repository call | Avoids pass-through classes while keeping business rules out of ViewModels |
| D22 | Domain interfaces for scanner, Drive client, cover store, database swapper and dispatchers | Concrete classes injected directly | Keeps `:domain` pure and lets tests use fakes (see 4.5) |

## 4. Final Design

### 4.1 Architecture

Two Gradle modules: a pure-Kotlin `:domain` module and the Android `:app` module. Clean Architecture layering (presentation, domain, data), MVVM with repositories and unidirectional state (`StateFlow`). Inside `:app`, code is package-by-feature for UI and package-by-role for data.

```
:domain  (pure Kotlin/JVM, no Android or framework imports)
  model/       Book, BookSummary, BookCandidate, BookStatus, LibraryFilter, SortOrder
  error/       AppError, Try
  repo/        BookRepository, MetadataRepository, BackupRepository   (interfaces)
  port/        BarcodeScanner, DriveBackupClient, CoverStore,
               DatabaseSwapper, DispatcherProvider                    (interfaces)
  usecase/     DeriveStatus, MergeMetadata, ValidateIsbn13, ClampPage,
               ObserveLibrary, AddBookFromIsbn, AddBookManually,
               UpdateProgress, RunBackup, RestoreBackup

:app
  ui/          Compose screens + ViewModels + UI state
    library/     list, grid, search, filters
    add/         scan, manual ISBN, title search, result picker
    detail/      book detail, progress update, edit
    backup/      backup/restore, schedule settings
    theme/       OtiumTheme, OtiumColors, typography, shapes
  data/
    local/       Room: entities, FTS table, DAOs, row projections, mappers
    remote/      GoogleBooksApi, OpenLibraryApi (Retrofit + kotlinx.serialization), DTOs, mappers
    repo/        RoomBookRepository, RemoteMetadataRepository, DriveBackupRepository
    backup/      GoogleDriveBackupClient, RoomDatabaseSwapper
    cover/       FileCoverStore (files directory, Coil)
    scan/        MlKitBarcodeScanner
  work/        BackupWorker, LookupRetryWorker (WorkManager entry points)
  di/          Hilt modules (@Binds for every domain interface)
```

Dependency rules: `:app` depends on `:domain`; `:domain` depends on nothing but the Kotlin standard library and `kotlinx-coroutines-core` (for `Flow`). The compiler enforces that `:domain` cannot import Android, Room, Retrofit or Hilt.

- Room is the source of truth. The UI only observes DB flows. Network results are written into the DB.
- `MetadataRepository` (interface in `:domain`, implementation in `:app`) queries Google Books by ISBN, fills missing fields from Open Library, and returns one normalized `BookCandidate`. The merge rules themselves live in the `MergeMetadata` use case so they are testable as plain JVM code.
- Covers are downloaded once into the app files directory (Coil) and the path is stored. A user-picked cover overrides the remote URL.
- Stack: Hilt, coroutines/Flow, CameraX, ML Kit barcode scanning, Coil, WorkManager, Google Identity Services.

#### Layer rules

The UI layer and ViewModels also follow Google's Android architecture recommendations. How they
are applied, and the audit of this project against each one, is in `docs/STYLE_GUIDE.md`
section 3. Rules that can be tested are enforced by `ArchitectureTest`.

- **Domain models are plain Kotlin data classes.** No Room, serialization or Android annotations.
- **Entities and DTOs never reach the UI.** Room entities and row projections (for example the DAO's `BookListRow`) and network DTOs stay in `data/`. Repository implementations map them to domain models (`Book`, `BookSummary`, `BookCandidate`) with extension-function mappers kept next to the data models.
- **Interfaces belong to the consumer.** Repository and port interfaces are defined in `:domain`, implemented in `:app/data`, and bound in `di/` with Hilt `@Binds`. ViewModels depend on use cases or repository interfaces, never on implementations.
- **Use cases only where there is logic.** A use case exists when it holds a business rule or coordinates more than one repository (dedupe plus lookup plus merge, progress clamp plus status plus timestamp, backup orchestration). Pass-through reads such as "get book by id" are called directly on the repository from the ViewModel.
- **ViewModels hold no business rules.** They map domain results and `AppError` values to UI state and strings, and launch work in `viewModelScope`.
- **Workers are thin.** `BackupWorker` and `LookupRetryWorker` only call use cases and translate the result into `Result.success/retry/failure`.
- **Structured concurrency only.** No `GlobalScope`. Dispatchers are injected through `DispatcherProvider` so tests can control them.
- **Backup crosses a Room boundary.** `DriveBackupRepository` uses a `DatabaseSwapper` port (implemented in `:app/data`) to checkpoint the WAL, close Room, replace files and reopen it. `:domain` only sees `RunBackup`, `RestoreBackup` and their `AppError` results.

#### Error model

`:domain` defines a typed error instead of exceptions or the untyped `kotlin.Result`:

```kotlin
sealed interface Try<out T> {
    data class Success<T>(val value: T) : Try<T>
    data class Failure(val error: AppError) : Try<Nothing>
}

sealed interface AppError {
    data object Offline : AppError
    data object RateLimited : AppError
    data object Timeout : AppError
    data object NotFound : AppError
    data class Duplicate(val existingBookId: Long) : AppError
    data class InvalidIsbn(val input: String) : AppError
    data class InvalidInput(val field: String) : AppError
    data object AuthExpired : AppError
    data object InsufficientDriveSpace : AppError
    data class IncompatibleBackup(val backupSchema: Int, val appSchema: Int) : AppError
    data class Storage(val message: String) : AppError
    data class Unexpected(val message: String) : AppError
}
```

- Data-layer code catches framework exceptions (`IOException`, `HttpException`, `SQLiteException`, Google auth exceptions) at the repository boundary and converts them to `AppError`. Nothing outside `data/` and `work/` handles framework exceptions.
- The duplicate-ISBN rule (D15) is `Failure(Duplicate(existingBookId))`; the UI uses the id to open the existing book.
- ViewModels map `AppError` to user-facing strings in one place per feature.

### 4.2 Data model and search

Tables:
- `book`: `id`, `isbn13` (unique, nullable), `isbn10`, `title`, `subtitle`, `authors` (display string), `publisher`, `publishedYear`, `language`, `description`, `totalPages`, `currentPage` (default 0), `coverPath`, `coverSourceUrl`, `source` (google/openlibrary/manual), `addedAt`, `updatedAt`, `userEdited`, `needsLookup`.
- `author`, `book_author`; `category`, `book_category`; `tag`, `book_tag`.
- `book_fts`: FTS4 over title, subtitle, authors.

Search and filters:
- FTS prefix match on the text input, debounced about 200 ms.
- Filters combine with AND: status, categories (multi), tags (multi), optional author. Within the categories group and within the tags group a book matches when it has any of the selected values (OR inside a group, AND between groups).
- Text input becomes an FTS4 prefix query over letters and digits only (`the hobb` becomes `the* hobb*`), so FTS operator syntax typed by the user is neutralized. FTS uses the `unicode61` tokenizer, giving case and diacritic insensitive matching. Input with no searchable characters is ignored rather than matching nothing.
- Author, category and tag names are unique case-insensitively (`NOCASE`), and labels no longer used by any book are deleted.
- Status in SQL: not started = `currentPage = 0`; in progress = `0 < currentPage < totalPages`; read = `currentPage >= totalPages` (with `totalPages > 0`).
- Sort: title, author, recently added, last updated, progress.
- A single DAO query is built from a `LibraryFilter` object and returns `Flow<List<BookListRow>>` (a Room projection kept in `data/local/`). `RoomBookRepository` maps rows to the domain `BookSummary`, so the UI never sees Room types.

Edge cases:
- Unknown or zero `totalPages`: status shows "not started" and the user is prompted to set pages.
- Page above total is clamped to total and the book is marked read.
- Duplicate ISBN: warn and open the existing book.
- `userEdited = true` prevents later metadata refresh from overwriting fields.
- Room schemas are exported from day one. The backup file records the schema version.

### 4.3 Add-book flow and metadata

Entry: one Add FAB opening a sheet with Scan, Enter ISBN, Search by title.

Scan:
1. CameraX preview with ML Kit scanner, EAN-13 only, accepting codes starting 978/979.
2. On the first valid read: haptic feedback, stop analysis, validate the ISBN-13 checksum, start lookup.
3. Camera permission denied: show a rationale and offer manual entry.

Lookup:
1. Check the local DB for the ISBN. If found, show the duplicate and stop.
2. Call Google Books (`volumes?q=isbn:X`) and Open Library (`/isbn/X.json`) in parallel.
3. Merge: Google wins for title, authors, description. Gaps (pages, cover, publisher) are filled from Open Library. Cover order: Google high-res, then `covers.openlibrary.org/b/isbn/X-L.jpg`. Placeholder covers are rejected (tiny image or 404 with `default=false`).
4. Confirm screen: all fields editable, cover swap (gallery, camera, alternate remote), tags, total pages. Save writes the DB row and downloads the cover.

Title/author search: Google Books `intitle:`/`inauthor:` query, up to 20 results with thumbnails. The chosen edition goes to the same confirm screen. Open Library search is used only when Google returns nothing.

Errors:
- No result: open the confirm screen empty with the ISBN prefilled.
- Offline: manual entry saves immediately. ISBN-only entries get `needsLookup` and are retried by WorkManager.
- HTTP 429/5xx: one retry with backoff, then fall back to the other API, then show a plain error.
- Timeout 8 s per API.
- Google Books API key in `local.properties` exposed via `BuildConfig`, restricted by package name and SHA-1, never committed. Keyless access is the low-quota fallback.

### 4.4 UI and backup

Screens:
- **Library:** grid/list toggle, search bar, filter chips (status, category, tag), sort menu, Add FAB, empty state prompting a scan.
- **Book detail:** large cover, metadata, collapsible description, progress card (page input, "Page X of Y", percentage bar, "mark finished"), tags, edit and delete in the overflow menu.
- **Add / confirm:** as in 4.3.
- **Settings > Backup:** Google sign-in, Back up now, Restore, auto-backup toggle with daily/weekly frequency and Wi-Fi-only / charging-only switches, last backup time and size.
- Material 3 components themed with the Otium identity (see 4.7), light and dark themes, accessible touch targets, content descriptions on covers.

Progress update: tap the progress card, enter a number or use the slider, confirm. Writes `currentPage` and `updatedAt`; status is re-derived.

Drive backup:
- Auth with Google Identity Services, scope `drive.appdata`.
- Backup: checkpoint the Room WAL, zip DB + covers, upload as the single latest file and keep one previous copy as `.prev`. Metadata records app version and schema version.
- Restore: download, verify backup schema version is at most the app's, confirm dialog ("replaces current library"), swap DB and covers, reopen Room.
- Auto backup: `PeriodicWorkRequest` using the user's constraints. Failures retry with backoff; a notification appears only after repeated failures.
- Failure cases: expired auth (prompt re-sign-in), no space, network drop (retry next run). Local data is never deleted on backup failure.

### 4.7 Visual identity

Reference: a bridge between the analog and digital worlds. Otium should feel like a quiet room at the end of a long day, a "digital study", not a transactional utility. No cold tech gradients, no gamified pop colors. Natural textures, quiet layouts, soft shadows, warm low-contrast lighting that evokes book pages, linen covers and wooden desks.

**Palette: Earth & Ink.** Final light values below. Sage and Terra were darkened from the first proposal (`#6F8062` and `#B5573A`) after the WCAG check: as text on Parchment they reached only 3.6:1 and 4.0:1, short of the 4.5:1 this design requires. `PaletteContrastTest` now guards every text and graphic pair in both themes. Real-device tuning is still a Phase 7 task.

| Token | Role | Proposed light value |
|-------|------|----------------------|
| Parchment | App background, cards slightly darker or lighter | `#F4EBD9` |
| Tiber Sage | Primary (muted green): calm actions, "read" status | `#566A4B` |
| Terra | Accent (rust terracotta): reading progress, active filters, milestones | `#A24A2D` |
| Ink | Text and icons (deep indigo / dark gray) | `#1F2433` |

- **Status colors:** not started = muted ink outline, in progress = Terra, read = Sage. Status is never conveyed by color alone (label or icon too).
- **Dark theme:** a warm dark, "lamp-lit study" rather than pure black. Background around `#1B1A17`, text in parchment tones, Sage and Terra lightened to keep contrast.
- **Accessibility constraint:** "low-contrast lighting" applies to surfaces and shadows only. Body text and controls must still meet WCAG AA (4.5:1 text, 3:1 UI). Verify Ink on Parchment, Terra on Parchment, and the dark equivalents before locking values.

**Typography**
- Headlines and screen titles: **EB Garamond**, a high-contrast Roman-style serif that stays legible at title sizes. The wordmark placeholder uses **Cinzel**. Both are open-licensed (SIL OFL) and safe for Play Store.
- Interface text: **Source Sans 3**, a quiet humanist sans for lists, filters and forms (chosen over Inter for its smaller file size and warmer feel next to Garamond).
- Fonts are bundled as variable font files (one file per family, all weights), with their licenses in `third_party/fonts`. No runtime font download.

**Wordmark and logo**
- "OTIUM" in a Roman-inspired serif, with a stylized "O" containing a classical arch over an open book.
- The final mark is a design asset, not a coding task. v1 development uses a typeset placeholder wordmark and a simple arch-over-book vector icon, swappable later without code changes.

**Screen treatment**
- Backgrounds use a very subtle paper texture, a 64 by 64 grain tile generated once in code (no image asset) and repeated, over a flat Parchment background that is already correct on its own. Soft, low-elevation shadows. No heavy imagery.
- **Library ("the vault"):** default view renders books as spines/covers on shelf rows, with a subtle shelf line and shadow per row, built on a `LazyVerticalGrid` so it stays fast. A secondary list mode (cover, title, author, progress) is kept for dense browsing. Empty state is an empty shelf with a scan prompt.
- **Book detail:** cover on a soft shadow like a book resting on a desk, serif title, quiet Terra progress bar and "Page X of Y".
- **Progress:** one thin Terra bar and a calm number, no streaks, badges or confetti (D18).
- Motion: slow, minimal fades and gentle shelf-to-detail transitions. Respect the system "remove animations" setting.

**Theme implementation**
- One `OtiumTheme` composable owning the `ColorScheme`, `Typography` and `Shapes`, with a small `OtiumColors` extension (`MaterialTheme.otiumColors`) for status colors, shelf and texture tokens. All screens consume tokens only, never raw hex values; `ArchitectureTest` enforces that. `PaperBackground` provides the textured surface and the matching content color, the way `Surface` does.

### 4.5 Testing strategy

- **Unit (JUnit, Turbine, MockK):** `DeriveStatus`, ISBN-13 validation, `MergeMetadata` (gap fill, placeholder cover rejection), `LibraryFilter` to query, page clamping, and the use cases (`AddBookFromIsbn`, `UpdateProgress`, `RunBackup`, `RestoreBackup`) against fake repositories and ports. `:domain` tests run on the plain JVM with no Android dependencies. ViewModel tests assert `AppError` to UI-state mapping.
- **Data (Room in-memory, instrumented):** FTS search, combined filter and sort, duplicate ISBN constraint, migrations from exported schemas.
- **Remote (MockWebServer):** Google Books and Open Library parsing against fixtures, including missing fields, 429, 5xx, timeout, fallback.
- **UI (Compose test):** manual ISBN to saved book, filter chips, progress update. Camera scanning is tested manually on a device; the decoder sits behind an interface so the flow can use a fake.
- **Backup:** zip round trip (backup then restore gives an identical library), schema-version rejection, Drive client behind an interface with a fake.
- **CI:** GitHub Actions running lint, unit tests, and assemble. Instrumented tests on demand.

### 4.6 Play Store readiness

- Privacy policy page: data stays on device; Drive appdata only; book APIs receive ISBN or title/author only.
- Data safety form: no data collected by the developer.
- Release build with R8, signing config kept out of git, Play App Signing.
- Permissions: camera (justified in-app), `INTERNET`, `POST_NOTIFICATIONS` (backup failure notice).
- Follow Google Books terms: required attribution, cover caching limited to the user's own library.

## 5. Risks

- **Metadata inconsistency** between sources (wrong page counts, missing covers). Mitigated by merge logic and manual override.
- **Google Books quota/key abuse** once public. Mitigated by key restriction and keyless fallback.
- **Restore corrupting data.** Mitigated by schema check, confirm dialog, and the `.prev` copy.
- **Barcode scan reliability** on glossy or damaged covers. Mitigated by manual ISBN and title search paths.

## 6. Open Items for Implementation Planning

- Exact library versions (CameraX, ML Kit, Room, Hilt) to be pinned at project setup in a Gradle version catalog.
- `:domain` build setup: Kotlin/JVM module with only `kotlinx-coroutines-core` as a dependency. Decide whether Hilt-annotated use cases are constructor-`@Inject`ed (needs `javax.inject`, a pure-Java dependency, in `:domain`) or provided from `@Provides` functions in `:app/di` (keeps `:domain` free of any DI dependency; preferred).
- Package name and app id (needed for the API key restriction and Drive OAuth client).
- Choice of Drive client: REST via OkHttp/Retrofit versus the Google API client library.
