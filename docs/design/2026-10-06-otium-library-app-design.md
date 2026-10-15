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
  Import and export read and write only files the owner picks in the system file picker.
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
| D25 | Reading progress has one use case, `ReadingTracker` (set the page, finish, set the total), built on the shared rules `ClampPage` and `DeriveStatus` | Separate `UpdateProgress`, `MarkFinished` and `SetTotalPages` use cases | Refines D21: one module owns the progress rules and fetches the book once per change; ViewModels depend on one interface. The two pure rules stay separate because `Book.status`, the mappers and `SaveBook` use them too (architecture review 2026-10-11) |
| D26 | Restore is one use case, `LibraryRestore` (prepare, confirm, cancel), that keeps the downloaded backup file to itself; `RestorePreview` carries only the slot and what the backup holds | `PrepareRestore` and `RestoreBackup` passing a `RestorePreview` that held the `File`, with the ViewModel deleting it | No file handle reaches UI state; the download is deleted in one place on confirm, cancel or a new prepare. The archiver and restorer ports stay separate: their data-layer implementations are separate and separately tested (architecture review 2026-10-11) |
| D27 | The book repository owns the cover files its books name: after any write it deletes a cover no book names any more (replaced, its book deleted, or brought by a write that failed). `CoverStore` only stores covers (download, gallery or camera); covers a dead process left behind are purged at start-up once a day old | Use cases deleting covers themselves through `CoverStore.delete`; moving downloads and picks into the repository too | Deleting a book used to leave its cover on disk forever, and rollback code was repeated in `SaveBook` and `RetryPendingLookups`. Downloads stay out of the Room repository, which would otherwise need the HTTP client and image decoding (architecture review 2026-10-11) |
| D28 | Import and export through files the owner picks: Goodreads and StoryGraph CSV in, Goodreads-format CSV and Otium JSON (versioned, every field but the cover file) out. `ImportLibrary` previews the file, then adds every new book straight away from what the file holds; books with an ISBN also wait for a lookup (D11). Books whose ISBN is already in the library are skipped. Progress comes from the shelf: read means finished, reading means page 1, and without a page count the shelf becomes a tag | Each row through the confirm screen; an automatic title search for rows without an ISBN; replacing the library as a restore does | Ranked first for v2 in the team session of 2026-10-10: it brings existing libraries in and lets the owner leave freely. A confirm screen per row does not scale to hundreds of books, and a title search picks wrong editions without anyone checking. `RetryPendingLookups` now ends a run at the first offline, rate-limit or timeout failure, so a large import does not keep asking a service that refuses |
| D29 | The library database stays unencrypted by the app in v1: it is protected by the Android sandbox and file-based encryption of user storage. What leaves the phone is protected where it leaves (passphrase-encrypted backups, D23); exports are plain files and the screen says so. The threat model is in 4.8 | SQLCipher now; SQLCipher in v2 with a migration; encrypting chosen fields | SQLCipher adds a native library per ABI and its own key: a lost or invalidated key would lose the whole library, a worse outcome for one owner than the threats it answers. Against a locked phone, other apps and the network, the platform already protects the file; against an unlocked phone, an app lock helps where database encryption does not. Revisit when private notes arrive or the threat model changes (team session 2026-10-10, open decision 2; review S4). Revisited for notes in D31: unchanged |
| D30 | Optional app lock, off by default, in Settings only. The system prompt (`androidx.biometric` 1.1.0, the stable line) accepts `BIOMETRIC_WEAK \| DEVICE_CREDENTIAL`: the face (often Class 2), a fingerprint, or the screen lock as fallback, without a confirmation tap. It asks on every start and after the app spent the chosen time in the background (1 minute by default, or 5), counted for the whole process on the clock since boot. Turning it on and off both ask first. While locked, the screens stay in memory but are not drawn, read aloud or focusable, and a dialog, sheet or menu that was open is taken off screen until the owner unlocks (each is a window of its own, so drawing nothing would not hide it); on Android 13 and later the recent apps preview is hidden. If the phone loses its screen lock, the app lock turns itself off and says so | `BIOMETRIC_STRONG` only; an "immediately" timeout; `FLAG_SECURE`; binding a key to the lock (`CryptoObject`); suggesting the lock when Drive connects | The lock answers the threat 4.8 left open, an unlocked phone in someone else's hands, without touching the data: the database stays as D29 decided. Class 3 only would leave out face unlock on many phones; the pair chosen is the one Android supports from API 26 (`DEVICE_CREDENTIAL` alone and `BIOMETRIC_STRONG \| DEVICE_CREDENTIAL` are not on Android 10 and lower). "Immediately" would ask again after every file pick, cover photo and Google consent screen. `FLAG_SECURE` would also block the owner's own screenshots. Turning itself off rather than shutting the owner out is safe, since a phone without a screen lock protects nothing anyway (four-role security review 2026-10-13) |
| D31 | Marginalia (notes and quotes) is stored in the same unencrypted Room database as the library, in its own `note` table; D29 stands. Notes are covered by the Android sandbox, the app lock and, once they leave the phone, the backup passphrase. Notes are released together with or after the app lock. The backup screen suggests a passphrase before the first Drive backup, because the notes are what makes an unencrypted backup costly. Notes are searchable per book first; the optional private 1 to 5 mark is not built | SQLCipher for the whole database; encrypting only the note text; a separate encrypted notes database | Notes are more personal than reading status, but they add no new threat: the threats left uncovered by D29 (an unlocked phone, a rooted phone) are met by the app lock or are out of scope, and SQLCipher answers none of them. Its cost is unchanged: a native library per ABI, and a lost or invalidated key loses the whole library, not only the notes. The one new cost of notes is on the path out of the phone, an unencrypted Drive backup, which a passphrase covers and the exports already say they do not. A separate notes database or field encryption would add a second key to lose without covering a threat. Revisit when the threat model changes, for example if notes could be shared (team session 2026-10-15) |
| D32 | Backup encryption is the suggested default (amends D23). Once Drive is connected while encryption is off, the passphrase panel opens by itself, once, as "Protect your backups", with the reason, the existing warning that a forgotten passphrase cannot be recovered, and **Not now**. The offer is remembered per device and never repeated; while off, the setting says encryption is recommended. Backups are never blocked, and backups made before stay as they are | Encryption on with no way out (forced); asking before every backup; leaving it off and unmentioned | A passphrase is what keeps a backup private if the Google account is compromised (4.8), but it cannot be recovered, so the choice stays with the owner: offered at the moment it matters, once, with the cost stated. Asking again would be nagging, which Otium avoids (four-role security review 2026-10-13) |

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
               LibraryRestorer, BackupPreferences, BackupScheduler,
               LibraryFiles, LibraryJson, LibrarySnapshot              (interfaces)
  transfer/    Csv, GoodreadsCsv, StoryGraphCsv, ImportRow (library file formats)
  usecase/     DeriveStatus, MergeMetadata, ValidateIsbn13, ClampPage,
               AddBookFromIsbn, SaveBook, SaveIsbnForLater, RetryPendingLookups,
               ReadingTracker, ReadLibraryFile, ImportLibrary, ExportLibrary, RunBackup,
               LibraryRestore, ConfigureAutoBackup, RunScheduledBackup

:app
  ui/          Compose screens + ViewModels + UI state
    library/     list, grid, search, filters
    add/         scan, manual ISBN, title search, result picker
    detail/      book detail, progress update, edit
    backup/      backup/restore, schedule settings
    transfer/    import and export
    theme/       OtiumTheme, OtiumColors, typography, shapes
  data/
    local/       Room: entities, FTS table, DAOs, row projections, mappers
    remote/      GoogleBooksApi, OpenLibraryApi (Retrofit + kotlinx.serialization), DTOs, mappers
    repo/        RoomBookRepository, RemoteMetadataRepository
    backup/      GoogleDriveBackupClient, BackupZip, ZipBackupArchiver, RoomLibraryRestorer,
                 GoogleAuthManager
    prefs/       DataStoreLibraryPreferences, DataStoreBackupPreferences
    cover/       FileCoverStore (files directory, Coil), CoverFiles, OrphanedCovers
    scan/        MlKitBarcodeScanner
    transfer/    ContentResolverLibraryFiles, JsonLibraryFormat, RoomLibrarySnapshot
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
  `updatedAt`, `userEdited`, `needsLookup`, `series` (free text, nullable) and `seriesIndex`
  (`REAL`, nullable, so a novella can be 1.5). The series columns arrived in schema version 2.
- `author`, `book_author`; `category`, `book_category`; `tag`, `book_tag`.
- `book_fts`: FTS4 over title, subtitle, authors, series.

Series:

- Neither metadata service has a structured series field. Open Library editions carry a free-text
  `series` list (seen in the recorded `edition-dune.json`: `"Dune (1); Dune Chronicles, Book 1"`);
  `ParseSeries` reads it, and otherwise guesses from the subtitle, then the title. In a subtitle
  or title a number only counts after a keyword ("Book 2", "Vol. 3", "#3"), so "1984" is never a
  series number. The guess only prefills the confirm form, which the owner edits; the subtitle is
  left as it is.
- Series names match exactly; "Dune" and "dune" are two series until the owner edits one.
- Progress through a series counts only the books in the library: "2 of 5 read", and a bar that
  averages each book's progress (a read book counts as 1, a book in progress as its share of
  pages). `BookDao.observeSeriesProgress` computes it in SQL, ignoring filters, and must agree with
  the domain `SeriesProgress` (a boundary test checks it).
- Cover files belong to the book repository (D27). A write that leaves a cover no book names
  deletes it; `OrphanedCovers` deletes, at start-up, any cover file older than a day that no book
  names, holding the backup lock so a restore never loses a cover it has just copied in.

Search and filters:

- FTS prefix match on the text input, debounced about 200 ms.
- Filters combine with AND: status, categories (multi), tags (multi), optional author, optional
  series. Within the
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
- Sort: title, author, series, recently added, last updated, progress. By author, an author's
  series stay together in reading order. By series, shelves and the index stand under one heading
  per series with its progress, books outside a series last.
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

#### Importing and exporting a library

Settings, Import and export (D28). The owner picks a file; `ImportLibrary` works out its format
from the header row (Goodreads: `Exclusive Shelf`; StoryGraph: `Read Status`) or from Otium's JSON
`format` field, and previews it: new books, books already in the library, books without a usable
ISBN, rows without a title. Nothing is written until the owner confirms. Then:

- Each book is added from the file under the backup lock, so a restore never runs alongside.
- A book with an ISBN, unless the file is Otium's own, gets `needsLookup`; one lookup run is
  scheduled for all of them and fills in covers and details as offline entries are (D11).
- Ratings, reviews and reading dates have no place in Otium and are not read.
- An import cut short (the screen left, the process killed) keeps what it added; importing the
  file again skips those books by ISBN.

Export writes every book to a file the owner creates: CSV in Goodreads' columns (categories and
tags become shelves) or Otium JSON. Covers are not exported; the JSON keeps each cover's source
address. The Goodreads and StoryGraph columns were written from their documented exports, not
checked against fresh ones; the readers look columns up by name and skip the ones they do not
know.

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
  (`AddBookFromIsbn`, `ReadingTracker`, `RunBackup`, `LibraryRestore`) against fake repositories and
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

- Privacy policy page, written from 4.8 so that it claims only what the app does:
  - The library stays on the device. The developer runs no server and receives nothing.
  - Google Books and Open Library receive the ISBN, or the title and author typed in a search,
    together with the device's IP address, as any web request does. Their own privacy policies
    apply to that request.
  - Backups go to the user's own Google Drive app data folder, and only when the user connects
    Drive. They are encrypted only when the user sets a passphrase.
  - Import and export read and write only files the user picks; an export is not encrypted.
- Data safety form: no data collected or shared by the developer. The book lookups are requests
  the user starts to a third-party service over https; say so in the form's notes rather than
  declaring them as developer collection.
- Release build with R8, signing config kept out of git, Play App Signing.
- Permissions: camera (justified in-app), `INTERNET`, `POST_NOTIFICATIONS` (backup failure notice),
  and `USE_BIOMETRIC` and `USE_FINGERPRINT` for the app lock (D30): both added by
  `androidx.biometric`, granted without asking, the second only used on Android 8.
- Follow Google Books terms: required attribution, cover caching limited to the user's own library.

### 4.8 Threat model and data protection

Otium keeps one person's catalogue of their own books: titles, authors, tags, shelves, reading
progress and dates, and, with Marginalia, their notes and quotes. It is not health or payment
data, but reading choices and the thoughts written beside them can reveal beliefs, health or
politics, so it is treated as private. Protection follows from the threats a solo, offline app
actually faces (D29, D31).

Where the data is and how it is protected:

| Data | Where | Protection | Revisit when |
|------|-------|------------|--------------|
| Library database (Room), notes and quotes included | App sandbox | Android sandbox; file-based encryption of user storage, which keeps it unreadable until the first unlock after a restart (D31: notes change nothing here) | The threat model changes, for example if notes could be shared |
| Cover pictures | App sandbox | As the database | As the database |
| Backup settings, Drive account email | DataStore in the sandbox | As the database; excluded from Android backup and device transfer | — |
| App lock choice | DataStore in the sandbox | As the database; a new phone starts with the lock off | — |
| Backup key | DataStore in the sandbox | Only stored wrapped by an AES-256-GCM key in the Android Keystore that cannot be read out; never stored with or as the passphrase | — |
| Drive backup | The user's Drive `appDataFolder` (`drive.appdata` scope) | Only Otium can open the folder. With a passphrase: Argon2id (64 MiB, 3 passes), then Tink streaming AES-256-GCM, before upload. Encryption is suggested once Drive is connected (D31) | — |
| Export file (CSV, JSON) | Wherever the user saves it | None: a plain file for other apps. The screen says so, and CSV cells are escaped against formula injection | An encrypted Otium JSON export (open) |
| Network traffic | Google Books, Open Library, Google Drive | https only, system certificate authorities, no cleartext (network security config) | — |

Threats and how they are met:

- **Lost or stolen phone, locked.** Covered by the lock screen, the sandbox and file-based
  encryption. Nothing in Otium weakens them: Android's cloud backup and device transfer exclude
  all app data.
- **Other apps on the phone.** Covered by the sandbox. Every exported component other than the
  launcher must be guarded by a permission, and the file provider shares only the camera's cache
  folder; `ManifestPolicyTest` checks both on the merged manifest.
- **Someone watching the network.** Covered by https everywhere. There are no certificate pins,
  on purpose: Google rotates its certificates, and a stale pin would break lookups and backups.
- **Someone with the user's Google account.** Covered for backups with a passphrase set. A backup
  without one can be read by anyone who signs in as the user and restores with Otium, notes and
  quotes included. This is the one risk notes add, so the backup screen suggests a passphrase
  before the first Drive backup (D31).
- **Leaks through the developer.** None possible: no backend, accounts, analytics, crash reporting
  or logging of library data.
- **An unlocked phone in someone else's hands.** Covered when the owner turns on the app lock
  (D30): Otium asks for the face, a fingerprint or the screen lock on every start and after a
  minute (or five) away, and Android 13 and later leave it out of the recent apps preview. With
  the lock off, they can browse and export the library.

Not covered, and accepted for v1:

- **A rooted or compromised phone.** Out of scope: no app-level measure holds once the sandbox
  is gone.
- **Export files once they leave the app.** The user chose where they went.

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
