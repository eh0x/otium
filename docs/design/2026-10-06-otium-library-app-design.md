# Otium: Personal Library Inventory App — Design

Date: 2026-10-06
Status: Validated through brainstorming, ready for implementation planning

## 1. Understanding Summary

- **What:** An Android-only personal library inventory app.
- **Adding books:** camera barcode scan (ISBN), manual ISBN entry, or title/author search.
- **Metadata:** auto-fetched from Google Books (primary) with Open Library as fallback, including
  cover. The user can override any field and swap the cover.
- **Tracking:** each book has a current page and a total page count. Status (not started / in
  progress / read) is derived from those two numbers. Progress is shown as a percentage bar.
- **Search and filters:** full-text on title and author. Filters for genre/category, custom tags,
  and status.
- **Storage:** local database on device, plus backup to Google Drive (manual button and optional
  configurable periodic backup).
- **Why / who:** a single owner cataloguing and tracking their own physical books.
- **Distribution:** personal project now, Play Store eventually.
- **Non-goals (v1):** iOS, multi-device live sync, own backend or accounts, cover-photo recognition,
  reading history/stats, ratings/notes, publisher/year/language filters, social features, multiple
  copies of the same ISBN.

## 2. Assumptions

- "ISIN" in the original request means ISBN.
- Native Kotlin + Jetpack Compose, minSdk 26.
- Library size up to roughly 5,000 books. Search responds in under 100 ms. Scan-to-result under
  about 3 s on a normal connection.
- Browsing, searching, and updating progress work fully offline. Adding by scan or search needs
  network; offline ISBN entries are queued.
- Privacy: data stays on the device. Outbound calls are limited to the book APIs (ISBN or
  title/author only) and the user's own Google Drive. No analytics, no developer-held accounts.
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
| D22 | Domain interfaces for scanner, Drive client, cover store, backup archiver and restorer, and dispatchers | Concrete classes injected directly | Keeps `:domain` pure and lets tests use fakes (see 4.5) |
| D23 | Optional passphrase encryption of the backup file (Argon2id, then Tink streaming AES-256-GCM), key kept wrapped by the Android Keystore for scheduled runs | SQLCipher for the database; no encryption; a key held only in the Keystore | The backup is the only copy that leaves the phone. A Keystore-only key would be lost with the phone; a passphrase lets a new phone restore (review S4) |
| D24 | Direction B "Catalogue" redesign: the single-accent Classical palette, Cormorant Garamond and Lora, a bottom bar (Library, Reading, Add, Settings) and shelves by first category | Keep Earth & Ink with EB Garamond and Source Sans 3; Direction A "Plates" | Warmer and more academic while staying modern; one accent reads calmer than two hues; Backup becomes a destination instead of a menu item. Spec: the design handoff (2026-10) |
| D26 | Restore is one use case, `LibraryRestore` (prepare, confirm, cancel), that keeps the downloaded backup file to itself; `RestorePreview` carries only the slot and what the backup holds | `PrepareRestore` and `RestoreBackup` passing a `RestorePreview` that held the `File`, with the ViewModel deleting it | No file handle reaches UI state; the download is deleted in one place on confirm, cancel or a new prepare. The archiver and restorer ports stay separate: their data-layer implementations are separate and separately tested (architecture review 2026-10-11) |

## 4. Final Design

### 4.1 Architecture

Two Gradle modules: a pure-Kotlin `:domain` module and the Android `:app` module. Clean Architecture
layering (presentation, domain, data), MVVM with repositories and unidirectional state
(`StateFlow`). Inside `:app`, code is package-by-feature for UI and package-by-role for data.

```text
:domain  (pure Kotlin/JVM, no Android or framework imports)
  model/       Book, BookSummary, BookCandidate, BookStatus, LibraryFilter, SortOrder
  error/       AppError, Try
  repo/        BookRepository, MetadataRepository                     (interfaces)
  port/        BarcodeScanner, CoverStore, DispatcherProvider,
               DriveBackupClient, DriveAuth, BackupArchiver,
               LibraryRestorer, BackupPreferences, BackupScheduler    (interfaces)
  usecase/     DeriveStatus, MergeMetadata, ValidateIsbn13, ClampPage,
               AddBookFromIsbn, SaveBook, SaveIsbnForLater, RetryPendingLookups,
               UpdateProgress, RunBackup, LibraryRestore,
               ConfigureAutoBackup, RunScheduledBackup

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
    repo/        RoomBookRepository, RemoteMetadataRepository
    backup/      GoogleDriveBackupClient, BackupZip, ZipBackupArchiver, RoomLibraryRestorer,
                 GoogleAuthManager
    prefs/       DataStoreLibraryPreferences, DataStoreBackupPreferences
    cover/       FileCoverStore (files directory, Coil)
    scan/        MlKitBarcodeScanner
  auth/        DriveConsent (what the screen does to give Drive access)
  work/        BackupWorker, BackupNotifier, LookupRetryWorker (WorkManager entry points)
  di/          Hilt modules (@Binds for every domain interface)
```

Dependency rules: `:app` depends on `:domain`; `:domain` depends on nothing but the Kotlin standard
library and `kotlinx-coroutines-core` (for `Flow`). The compiler enforces that `:domain` cannot
import Android, Room, Retrofit or Hilt.

- Room is the source of truth. The UI only observes DB flows. Network results are written into the
  DB.
- `MetadataRepository` (interface in `:domain`, implementation in `:app`) queries Google Books by
  ISBN, fills missing fields from Open Library, and returns one normalized `BookCandidate`. The
  merge rules themselves live in the `MergeMetadata` use case so they are testable as plain JVM
  code.
- Covers are downloaded once into the app files directory (Coil) and the path is stored. A
  user-picked cover overrides the remote URL.
- Stack: Hilt, coroutines/Flow, CameraX, ML Kit barcode scanning, Coil, WorkManager, Google Identity
  Services.

#### Layer rules

The UI layer and ViewModels also follow Google's Android architecture recommendations. How they
are applied, and the audit of this project against each one, is in `docs/STYLE_GUIDE.md`
section 3. Rules that can be tested are enforced by `ArchitectureTest`.

- **Domain models are plain Kotlin data classes.** No Room, serialization or Android annotations.
- **Entities and DTOs never reach the UI.** Room entities and row projections (for example the DAO's
  `BookListRow`) and network DTOs stay in `data/`. Repository implementations map them to domain
  models (`Book`, `BookSummary`, `BookCandidate`) with extension-function mappers kept next to the
  data models.
- **Interfaces belong to the consumer.** Repository and port interfaces are defined in `:domain`,
  implemented in `:app/data`, and bound in `di/` with Hilt `@Binds`. ViewModels depend on use cases
  or repository interfaces, never on implementations.
- **Use cases only where there is logic.** A use case exists when it holds a business rule or
  coordinates more than one repository (dedupe plus lookup plus merge, progress clamp plus status
  plus timestamp, backup orchestration). Pass-through reads such as "get book by id" are called
  directly on the repository from the ViewModel.
- **ViewModels hold no business rules.** They map domain results and `AppError` values to UI state
  and strings, and launch work in `viewModelScope`.
- **Workers are thin.** `BackupWorker` and `LookupRetryWorker` only call use cases and translate the
  result into `Result.success/retry/failure`.
- **Structured concurrency only.** No `GlobalScope`. Dispatchers are injected through
  `DispatcherProvider` so tests can control them.
- **Backup crosses a Room boundary.** A backup reads the whole library from one consistent view of
  the live database and writes it into a fresh stand-alone Room file (`ZipBackupArchiver`). It does
  not copy the database file or use `VACUUM INTO`, which needs SQLite 3.27 and so Android 11, while
  Otium supports Android 8. A restore never closes or swaps Room: `RoomLibraryRestorer`
  (implemented in `:app/data`) opens the downloaded database in a temporary Room instance, which
  runs any migrations, and imports its books into the live database in one transaction. The change
  is all or nothing, repositories never hold a stale instance, and the screens update on their own
  because the same database stays open. `:domain` only sees `RunBackup`, `LibraryRestore` and their
  `AppError` results.
- **A damaged backup can look empty.** Room's default error handler deletes a database it finds
  damaged and starts an empty one, so opening a corrupt `library.db` succeeds with no books. The
  restore therefore compares the book count in the manifest with the books it read, and refuses a
  backup where they differ.
- **Asking for Drive access needs the screen.** Google's consent is an Activity result, so
  `DriveConsent` (in `app.otium.auth`, which extends `DriveAuth`) hands the backup screen a
  `PendingIntent` to launch and takes the result back. ViewModels never see Android types; the
  rest of the app only sees `DriveAuth`.

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

- Data-layer code catches framework exceptions (`IOException`, `HttpException`, `SQLiteException`,
  Google auth exceptions) at the repository boundary and converts them to `AppError`. Nothing
  outside `data/` and `work/` handles framework exceptions.
- The duplicate-ISBN rule (D15) is `Failure(Duplicate(existingBookId))`; the UI uses the id to open
  the existing book.
- ViewModels map `AppError` to user-facing strings in one place per feature.

### 4.2 Data model and search

Tables:

- `book`: `id`, `isbn13` (unique, nullable), `isbn10`, `title`, `subtitle`, `authors` (display
  string), `publisher`, `publishedYear`, `language`, `description`, `totalPages`, `currentPage`
  (default 0), `coverPath` (the cover's file name in the covers directory), `coverSourceUrl`,
  `source` (google/openlibrary/manual), `addedAt`,
  `updatedAt`, `userEdited`, `needsLookup`.
- `author`, `book_author`; `category`, `book_category`; `tag`, `book_tag`.
- `book_fts`: FTS4 over title, subtitle, authors.

Search and filters:

- FTS prefix match on the text input, debounced about 200 ms.
- Filters combine with AND: status, categories (multi), tags (multi), optional author. Within the
  categories group and within the tags group a book matches when it has any of the selected values
  (OR inside a group, AND between groups).
- Text input becomes an FTS4 prefix query over letters and digits only (`the hobb` becomes
  `the* hobb*`), so FTS operator syntax typed by the user is neutralized. FTS uses the `unicode61`
  tokenizer, giving case and diacritic insensitive matching. Input with no searchable characters is
  ignored rather than matching nothing.
- Author, category and tag names are unique case-insensitively (`NOCASE`), and labels no longer used
  by any book are deleted.
- Status in SQL: not started = `currentPage = 0`; in progress = `0 < currentPage < totalPages`; read
  = `currentPage >= totalPages` (with `totalPages > 0`).
- Sort: title, author, recently added, last updated, progress.
- A single DAO query is built from a `LibraryFilter` object and returns `Flow<List<BookListRow>>` (a
  Room projection kept in `data/local/`). `RoomBookRepository` maps rows to the domain
  `BookSummary`, so the UI never sees Room types.

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
2. On the first valid read: haptic feedback, stop analysis, validate the ISBN-13 checksum, start
   lookup.
3. Camera permission denied: show a rationale and offer manual entry.

Lookup:

1. Check the local DB for the ISBN. If found, show the duplicate and stop.
2. Call Google Books (`volumes?q=isbn:X`) and Open Library (`/isbn/X.json`) in parallel.
3. Merge: Google wins for title, authors, description. Gaps (pages, cover, publisher) are filled
   from Open Library. Cover order: Google high-res, then `covers.openlibrary.org/b/isbn/X-L.jpg`.
   Placeholder covers are rejected (tiny image or 404 with `default=false`).
4. Confirm screen: all fields editable, cover swap (gallery, camera, alternate remote), tags, total
   pages. Save writes the DB row and downloads the cover.

Title/author search: Google Books `intitle:`/`inauthor:` query, up to 20 results with thumbnails.
The chosen edition goes to the same confirm screen. Open Library search is used only when Google
returns nothing.

Errors:

- No result: open the confirm screen empty with the ISBN prefilled.
- Offline: manual entry saves immediately. ISBN-only entries get `needsLookup` and are retried by
  WorkManager.
- HTTP 429/5xx: one retry with backoff, then fall back to the other API, then show a plain error.
- Timeout 8 s per API.
- Google Books API key in `local.properties` exposed via `BuildConfig`, restricted by package name
  and SHA-1, never committed. Keyless access is the low-quota fallback.

### 4.4 UI and backup

Screens:

- **Library:** grid/list toggle, search bar, filter chips (status, category, tag), sort menu, Add
  FAB, empty state prompting a scan.
- **Book detail:** large cover, metadata, collapsible description, progress card (page input, "Page
  X of Y", percentage bar, "mark finished"), tags, edit and delete in the overflow menu.
- **Add / confirm:** as in 4.3.
- **Settings > Backup:** Google sign-in, Back up now, Restore, auto-backup toggle with daily/weekly
  frequency and Wi-Fi-only / charging-only switches, last backup time and size.
- Material 3 components themed with the Otium identity (see 4.7), light and dark themes, accessible
  touch targets, content descriptions on covers.

Progress update: tap the progress card, enter a number or use the slider, confirm. Writes
`currentPage` and `updatedAt`; status is re-derived.

Drive backup:

- Auth with Google Identity Services, scope `drive.appdata`.
- Backup: copy the library into a fresh database file, zip it with the covers and a manifest, upload
  as the latest file and keep the one it replaces as the previous copy. The file lands under a
  temporary name and is renamed in only once its checksum matches, so a failed upload never
  touches an earlier backup. An empty library is never uploaded.
- Encryption (optional, D23): with a passphrase set, the zip is encrypted before upload and only
  the encrypted file leaves the phone. The file starts with a plain header (`OTIUMENC`, format
  version, Argon2id settings, salt, key id) that is bound to the data as associated data. The key
  is derived with Argon2id (64 MiB, 3 passes) and stored wrapped by an Android Keystore key in its
  own DataStore file (`backup_key`), never with the passphrase, so scheduled backups run without
  the user. If that key can no longer be unwrapped, backups stop with `PassphraseRequired` rather
  than upload in the clear. Drive records the key id, so a restore on a phone without the key
  asks for the passphrase before downloading. Plain backups made before stay readable; the zip
  inside and its manifest format are unchanged.
- Restore: two steps of `LibraryRestore`. `prepare` downloads, verifies the checksum and the
  manifest, and refuses a schema newer than the app's (before and after the download). The
  confirm dialog ("replaces current library") shows what will be restored. `confirm` then imports
  the books in one transaction and copies the covers in under their own names. The database names a
  cover by its file name only, resolved against this install's covers directory when a book is
  read, so restored rows need no rewriting.
- Auto backup: `PeriodicWorkRequest` using the user's constraints. Failures retry with backoff; a
  notification appears only after repeated failures.
- Failure cases: expired auth (prompt re-sign-in), no space, network drop (retry next run). Local
  data is never deleted on backup failure.

### 4.7 Visual identity

Reference: a bridge between the analog and digital worlds. Otium should feel like a quiet room at
the end of a long day, a "digital study", not a transactional utility: a library catalogue rather
than an app store. Direction B "Catalogue" (D24) replaced the first identity, Earth & Ink.

**Palette: Classical.** Cool neutral paper, warm ink and one gold accent, in a light theme and a
warm "lamplight" dark. Values live only in `ui/theme/Color.kt`; `PaletteContrastTest` guards every
text and graphic pair in both themes, and the contrast of cover text on every generated board.

| Role | Light | Dark |
|------|-------|------|
| Background | `#F3F2F2` | `#161513` |
| Surface (plate, mat) | `#EAE9E9` | `#242220` |
| Sheet and dialog | `#FAF9F8` | `#1D1B19` |
| Ink | `#201F1D` | `#ECE5D6` |
| Muted | `#676665` | `#A39E94` |
| Divider (hairlines) | `#D1D0D0` | `#383632` |
| Accent (strokes, rules, glyphs, large type) | `#B68235` | `#D9A85A` |
| Gold (small accent-family text, M3 `primary`) | `#7D5411` | `#E1AD66` |

- The accent is about 3:1 on the background, so it is used for graphics and for type of 24 sp and
  more, on the background or sheet only. Small text in the accent family uses gold.
- Muted is a shade darker than the handoff's `#6C6B6A`, which was 4.39:1 on the surface.
- **Status:** unread = hollow ring, reading = half-filled, read = filled, always with a label.

#### Typography

- **Cormorant Garamond** for display, titles, tabs, buttons and chips; **Lora** (with its italic)
  for running text, authors and hints; **Cinzel** for the wordmark only. All SIL OFL, bundled as
  variable fonts with their licenses in `third_party/fonts`. No runtime font download.
- Cormorant is thin when small, so its regular weight is used only at 28 sp and above. Every
  Cormorant style uses lining figures; numerals add tabular figures.

#### Shell and screens

- A bottom bar holds Library, Reading, a raised Add action and Settings. Detail, Edit and the add
  flow are pushed full screen and hide it.
- **Library:** a masthead ("Leisure for the learned"), a search row, status tabs and a meta line.
  Shelves show "On the desk" (books in progress) and then one shelf per first category; the index
  lists books under letters. Status and sort live in an Arrange sheet.
- **Book detail:** the cover in a plate, inline progress (slider, steppers, a rule with a ribbon
  bookmark) and an About tab with a ruled facts table.
- **Reading:** the books in progress with a one-tap "+10 pages", and the recently finished.
- Books without a cover get a generated typographic cover: a deep board picked by the title, the
  title and the author's surname.
- Hairlines carry structure; shadows only on covers, the Add action and menus. Buttons are never
  filled (primary = accent outline with a gold label).
- **Progress:** one thin accent rule and a calm number, no streaks, badges or confetti (D18).
- Motion: short fades between screens and a settling slide for sheets. With "remove animations"
  on, movement stops and fades remain.

#### Wordmark and logo

- The mark is the "Ribbon O": a ring with the progress bookmark hanging inside its top rim, the
  ribbon of the detail progress rule. It is the adaptive launcher icon (gold on `#1B1A18`, with a
  monochrome layer) and, as `OtiumMark`, sits above the wordmark in the library masthead.
- "OTIUM" in Cinzel, widely spaced in gold, under the mark.

#### Theme implementation

- One `OtiumTheme` composable owning the `ColorScheme`, `Typography` and `Shapes`, with a small
  `OtiumColors` extension (`MaterialTheme.otiumColors`) for the accent, muted, divider, sheet,
  status and cover tokens. All screens consume tokens only, never raw hex values;
  `ArchitectureTest` enforces that. Shared components live in `ui/components`.

### 4.5 Testing strategy

- **Unit (JUnit, Turbine, MockK):** `DeriveStatus`, ISBN-13 validation, `MergeMetadata` (gap fill,
  placeholder cover rejection), `LibraryFilter` to query, page clamping, and the use cases
  (`AddBookFromIsbn`, `UpdateProgress`, `RunBackup`, `LibraryRestore`) against fake repositories and
  ports. `:domain` tests run on the plain JVM with no Android dependencies. ViewModel tests assert
  `AppError` to UI-state mapping.
- **Data (Room in-memory, instrumented):** FTS search, combined filter and sort, duplicate ISBN
  constraint, migrations from exported schemas.
- **Remote (MockWebServer):** Google Books and Open Library parsing against fixtures, including
  missing fields, 429, 5xx, timeout, fallback.
- **UI (Compose test):** manual ISBN to saved book, filter chips, progress update. Camera scanning
  is tested manually on a device; the decoder sits behind an interface so the flow can use a fake.
- **Backup:** zip round trip (backup then restore gives an identical library), schema-version
  rejection, Drive client behind an interface with a fake.
- **CI:** GitHub Actions running lint, unit tests, and assemble. Instrumented tests on demand.

### 4.6 Play Store readiness

- Privacy policy page: data stays on device; Drive appdata only; book APIs receive ISBN or
  title/author only.
- Data safety form: no data collected by the developer.
- Release build with R8, signing config kept out of git, Play App Signing.
- Permissions: camera (justified in-app), `INTERNET`, `POST_NOTIFICATIONS` (backup failure notice).
- Follow Google Books terms: required attribution, cover caching limited to the user's own library.

## 5. Risks

- **Metadata inconsistency** between sources (wrong page counts, missing covers). Mitigated by merge
  logic and manual override.
- **Google Books quota/key abuse** once public. Mitigated by key restriction and keyless fallback.
- **Restore corrupting data.** Mitigated by schema check, confirm dialog, and the `.prev` copy.
- **Barcode scan reliability** on glossy or damaged covers. Mitigated by manual ISBN and title
  search paths.

## 6. Open Items for Implementation Planning

- Exact library versions (CameraX, ML Kit, Room, Hilt) to be pinned at project setup in a Gradle
  version catalog.
- `:domain` build setup: Kotlin/JVM module with only `kotlinx-coroutines-core` as a dependency.
  Decide whether Hilt-annotated use cases are constructor-`@Inject`ed (needs `javax.inject`, a
  pure-Java dependency, in `:domain`) or provided from `@Provides` functions in `:app/di` (keeps
  `:domain` free of any DI dependency; preferred).
- Package name and app id (needed for the API key restriction and Drive OAuth client).
- Choice of Drive client: REST via OkHttp/Retrofit versus the Google API client library.
