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

## 4. Final Design

### 4.1 Architecture

Single Gradle module `app`, package-by-feature, MVVM with repositories and unidirectional state (`StateFlow`).

```
ui/        Compose screens + ViewModels
  library/   list, grid, search, filters
  add/       scan, manual ISBN, title search, result picker
  detail/    book detail, progress update, edit
  backup/    backup/restore, schedule settings
data/
  local/     Room: entities, FTS table, DAOs
  remote/    GoogleBooksApi, OpenLibraryApi (Retrofit + kotlinx.serialization)
  repo/      BookRepository, MetadataRepository (merge logic), BackupRepository
  backup/    DriveBackupClient, BackupWorker (WorkManager)
domain/    pure models + use cases (DeriveStatus, MergeMetadata)
di/        Hilt modules
```

- Room is the source of truth. The UI only observes DB flows. Network results are written into the DB.
- `MetadataRepository` queries Google Books by ISBN, fills missing fields from Open Library, and returns one normalized `BookCandidate`.
- Covers are downloaded once into the app files directory (Coil) and the path is stored. A user-picked cover overrides the remote URL.
- Stack: Hilt, coroutines/Flow, CameraX, ML Kit barcode scanning, Coil, WorkManager, Google Identity Services.

### 4.2 Data model and search

Tables:
- `book`: `id`, `isbn13` (unique, nullable), `isbn10`, `title`, `subtitle`, `authors` (display string), `publisher`, `publishedYear`, `language`, `description`, `totalPages`, `currentPage` (default 0), `coverPath`, `coverSourceUrl`, `source` (google/openlibrary/manual), `addedAt`, `updatedAt`, `userEdited`, `needsLookup`.
- `author`, `book_author`; `category`, `book_category`; `tag`, `book_tag`.
- `book_fts`: FTS4 over title, subtitle, authors.

Search and filters:
- FTS prefix match on the text input, debounced about 200 ms.
- Filters combine with AND: status, categories (multi), tags (multi), optional author.
- Status in SQL: not started = `currentPage = 0`; in progress = `0 < currentPage < totalPages`; read = `currentPage >= totalPages` (with `totalPages > 0`).
- Sort: title, author, recently added, last updated, progress.
- A single DAO query is built from a `LibraryFilter` object and returns `Flow<List<BookListItem>>`.

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

**Palette: Earth & Ink.** Hex values are proposals to tune on a real device.

| Token | Role | Proposed light value |
|-------|------|----------------------|
| Parchment | App background, cards slightly darker or lighter | `#F4EBD9` |
| Tiber Sage | Primary (muted green): calm actions, "read" status | `#6F8062` |
| Terra | Accent (rust terracotta): reading progress, active filters, milestones | `#B5573A` |
| Ink | Text and icons (deep indigo / dark gray) | `#1F2433` |

- **Status colors:** not started = muted ink outline, in progress = Terra, read = Sage. Status is never conveyed by color alone (label or icon too).
- **Dark theme:** a warm dark, "lamp-lit study" rather than pure black. Background around `#1B1A17`, text in parchment tones, Sage and Terra lightened to keep contrast.
- **Accessibility constraint:** "low-contrast lighting" applies to surfaces and shadows only. Body text and controls must still meet WCAG AA (4.5:1 text, 3:1 UI). Verify Ink on Parchment, Terra on Parchment, and the dark equivalents before locking values.

**Typography**
- Headlines and the wordmark: a high-contrast Roman-style serif. Candidates, all open-licensed and safe for Play Store: Cormorant Garamond or EB Garamond for headlines; Cinzel as a wordmark placeholder.
- Interface text: a modern, quiet sans-serif for lists, filters and forms (candidates: Inter or Source Sans 3).
- Fonts bundled in the app (SIL OFL), no runtime font download.

**Wordmark and logo**
- "OTIUM" in a Roman-inspired serif, with a stylized "O" containing a classical arch over an open book.
- The final mark is a design asset, not a coding task. v1 development uses a typeset placeholder wordmark and a simple arch-over-book vector icon, swappable later without code changes.

**Screen treatment**
- Backgrounds use a very subtle paper texture, a small tiled low-resolution asset (a few KB) with a flat Parchment fallback. Soft, low-elevation shadows. No heavy imagery.
- **Library ("the vault"):** default view renders books as spines/covers on shelf rows, with a subtle shelf line and shadow per row, built on a `LazyVerticalGrid` so it stays fast. A secondary list mode (cover, title, author, progress) is kept for dense browsing. Empty state is an empty shelf with a scan prompt.
- **Book detail:** cover on a soft shadow like a book resting on a desk, serif title, quiet Terra progress bar and "Page X of Y".
- **Progress:** one thin Terra bar and a calm number, no streaks, badges or confetti (D18).
- Motion: slow, minimal fades and gentle shelf-to-detail transitions. Respect the system "remove animations" setting.

**Theme implementation**
- One `OtiumTheme` composable owning the `ColorScheme`, `Typography` and `Shapes`, with a small `OtiumColors` extension for status colors and texture tokens. All screens consume tokens only, never raw hex values.

### 4.5 Testing strategy

- **Unit (JUnit, Turbine, MockK):** `DeriveStatus`, ISBN-13 validation, `MergeMetadata` (gap fill, placeholder cover rejection), `LibraryFilter` to query, page clamping.
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

- Exact library versions (CameraX, ML Kit, Room, Hilt) to be pinned at project setup.
- Package name and app id (needed for the API key restriction and Drive OAuth client).
- Choice of Drive client: REST via OkHttp/Retrofit versus the Google API client library.
