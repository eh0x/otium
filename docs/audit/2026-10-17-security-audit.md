# Security audit report

Date: 2026-10-17
Commit: `e5b0819` (`feat/book-format`)
Scope: Full repository (`:domain`, `:app`, `:benchmark`, Gradle build logic, CI workflows, scripts)
Related documents: [security policy](../../SECURITY.md),
[previous review](../plans/2026-10-07-architecture-security-ux-review.md),
[app design](../design/2026-10-06-otium-library-app-design.md), [style guide](../STYLE_GUIDE.md),
[release guide](../RELEASING.md)

This report presents a comprehensive security audit of the Otium Android codebase against the OWASP
Mobile Top 10 (2024), OWASP MASVS v2, OWASP API Security best practices, Android platform security
guidance, cryptographic design principles and supply-chain security standards. It covers all
features shipped through v1.1.0 as well as the unreleased features on `HEAD` (encrypted backups,
biometric/credential App Lock, book notes, the lending ledger with WorkManager reminders, and
Goodreads, StoryGraph and Otium JSON library transfer).

## 1. Executive summary

Otium has a strong, well-engineered security posture with zero Critical or High severity
vulnerabilities. The codebase benefits from a pure-Kotlin `:domain` layer, strict dependency
direction enforced by Konsist (`ArchitectureTest`), a merged-manifest policy gate
(`ManifestPolicyTest`), parameterized Room queries and FTS4 token sanitization, hardened ZIP
extraction (`BackupZip`), TLS-only networking (`network_security_config.xml`), Tink streaming
AES256-GCM-HKDF backup encryption with Argon2id key derivation, and a supply chain gated by pinned
GitHub Actions SHAs, Gitleaks, CycloneDX SBOM generation, OSV-Scanner and GitHub artifact
attestations.

The audit identified **eight findings** (one Medium, two Low-Medium, four Low, and one
Informational) concentrated in the newer library transfer, encrypted backup lifecycle, and App Lock
features:

| ID | Severity | Area | Finding | Size | Status |
|----|----------|------|---------|------|--------|
| SA-01 | Medium | Library transfer & remote mappers | CSV formula injection via unvalidated `isbn10` in Goodreads export | S | Resolved |
| SA-02 | Low-Medium | Backup cryptography | Unwiped in-memory backup keys after turning off encryption and shared HMAC/HKDF key material | S | Resolved |
| SA-03 | Low-Medium | Local storage & backup | Plaintext backup and restore staging files persist in cache after process death | S | Resolved |
| SA-04 | Low | Drive backup & restore | Unbounded download and decryption stream sizes and optional checksum verification | S | Open |
| SA-05 | Low | App Lock & privacy | App Lock privacy gaps on Android 8–12 recents, loan notifications and DataStore placement | S | Open |
| SA-06 | Low | Remote APIs & JSON import | Unvalidated remote and imported cover URLs and Open Library path identifiers | S | Open |
| SA-07 | Low | Backup UI | Passphrase plaintext retained in Compose input state during Argon2id derivation | XS | Open |
| SA-08 | Informational | CI/CD release workflow | Unescaped secret interpolation when writing `keystore.properties` in `release.yml` | XS | Open |

### Automated security tool results

All automated security gates were executed against commit `e5b0819`:

| Tool / check | Command | Target | Result |
|--------------|---------|--------|--------|
| Gitleaks | `scripts/scan-secrets.sh` | Full Git commit history | Passed (0 secrets) |
| CycloneDX + OSV-Scanner | `./gradlew scanDependencies` | Release runtime SBOM (263 packages) | Passed (0 active vulnerabilities) |
| Actionlint + Zizmor | `scripts/lint-workflows.sh` | `.github/workflows/*.yml` (5 workflows) | Passed (0 findings) |
| Detekt | `./gradlew detekt` | All Kotlin sources (`:domain`, `:app`, `:benchmark`) | Passed (0 findings) |
| Manifest policy test | `ManifestPolicyTest` | Merged Android manifest & `network_security_config.xml` | Enforced in unit tests |
| Architecture test | `ArchitectureTest` | Layering, ViewModel rules, single-activity constraint | Enforced in unit tests |

## 2. Threat model and attack surface

### Protected assets

- **User library database (`library.db`):** Books, reading dates, ratings, personal book notes
  (`notes` table), and lending history including borrower names and due dates (`loans` table).
- **Cover images (`files/covers/*.jpg`):** Downloaded book covers and user-captured camera photos
  (stripped of EXIF metadata on decode/re-encode).
- **Backup passphrase and 256-bit master key:** User-chosen passphrase derived via Argon2id into a
  32-byte key wrapped at rest by an Android Keystore AES-256-GCM key (`otium_backup_key_wrapper`).
- **Google OAuth access token:** Scoped strictly to `https://www.googleapis.com/auth/drive.appdata`
  and fetched in-memory on demand via `AuthorizationClient`.
- **Google Books API key:** Build-time `BuildConfig.GOOGLE_BOOKS_API_KEY` restricted in Google
  Cloud Console by package name (`app.otium`), signing certificate SHA-1, and API target.

### Trust boundaries and entry points

1. **Remote book catalog APIs (`googleapis.com`, `openlibrary.org`, `covers.openlibrary.org`):**
   Responses are untrusted external input parsed after ISBN barcode scanning, search queries, or
   background pending-lookup retries (`RetryPendingLookups`).
2. **Google Drive `appDataFolder` API:** Used for uploading and downloading `.zip` and `.zip.enc`
   backup archives. Neither the network nor the remote file metadata can be assumed benign if a
   user's Google account or network path is compromised.
3. **Storage Access Framework (SAF) file import and restore:** Users can import CSV or JSON files
   (`ImportLibrary`) or restore `.zip`/`.zip.enc` archives (`LibraryRestore`) received from third
   parties or downloaded from external sources.
4. **Exported CSV and JSON files:** CSV files exported by `ExportLibrary` are opened in desktop or
   mobile spreadsheet applications (Excel, LibreOffice Calc, Google Sheets) that evaluate formulas.
5. **Local device access (unlocked shared device):** Otium's optional App Lock (`AppLockController`)
   protects the library when the device owner hands an unlocked phone to someone else or steps away.

## 3. OWASP compliance matrix

### OWASP Mobile Top 10 (2024)

| Category | Status | Assessment |
|----------|--------|------------|
| M1: Improper Credential Usage | Pass | No hardcoded secrets in source or git history. Google Drive tokens are requested per operation with the minimal `drive.appdata` scope and never persisted by the app. `GOOGLE_BOOKS_API_KEY` is restricted by package/SHA-1. |
| M2: Inadequate Supply Chain Security | Pass | GitHub Actions are pinned to full 40-char commit SHAs, workflows default to `contents: read`, CycloneDX SBOM is scanned by OSV-Scanner on every PR and weekly schedule, and release APKs/AABs carry provenance attestations. |
| M3: Insecure Authentication / Authorization | Pass with notes | `BiometricPrompt` uses `BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL` via AndroidX Biometric across API 26–35. Minor privacy gaps exist on API 26–32 recents and loan notifications (SA-05). |
| M4: Insufficient Input / Output Validation | Partial | ZIP extraction (`BackupZip`), SQLite restore (`RoomLibraryRestorer`), FTS4 queries (`LibraryQueryBuilder`), and SAF file size caps (`ContentResolverLibraryFiles`) are well hardened. However, `isbn10` is not validated before unescaped CSV formula emission (SA-01), and remote/imported cover URLs lack strict scheme/host validation (SA-06). |
| M5: Insecure Communication | Pass | `network_security_config.xml` sets `cleartextTrafficPermitted="false"` and trusts only `<certificates src="system" />`. `DriveUploadAddress` validates resumable upload `Location` URLs against `https://*.googleapis.com` before sending bearer tokens. |
| M6: Inadequate Privacy Controls | Pass with notes | No analytics, ad SDKs, crash reporters, or third-party trackers. Camera photos are decoded via `BitmapFactory` and re-encoded to JPEG, stripping EXIF/GPS tags. Plaintext staging files in `cache/backup/` need startup cleanup after process death (SA-03). |
| M7: Insufficient Binary Protections | Pass | Release builds enable R8 minification (`isMinifyEnabled = true`), resource shrinking (`isShrinkResources = true`), and `proguard-android-optimize.txt` with `repackageclasses`. |
| M8: Security Misconfiguration | Pass | `android:allowBackup="false"`, `data_extraction_rules.xml` and `backup_rules.xml` exclude all domains, `FileProvider` is non-exported and scoped to `cache/camera/`, and only `MainActivity` is exported (guarded by `ManifestPolicyTest`). |
| M9: Insecure Data Storage | Pass with notes | Backup encryption uses Argon2id (64 MiB, 3 iterations, parallelism 1) + Tink `AES256_GCM_HKDF_1MB` + Android Keystore `AES/GCM/NoPadding` key wrapping. In-memory key eviction on disable (`KeystoreBackupEncryption`) and cache cleanup (`BackupFiles`) should be tightened (SA-02, SA-03). |
| M10: Insufficient Cryptography | Pass with notes | Strong primitives (`SecureRandom`, Argon2id, AES-256-GCM, SHA-256, HMAC-SHA256) are used throughout; MD5 is gone. `BackupKey.keyIdOf` reuses the raw 32-byte encryption key as an HMAC key instead of deriving separate subkeys (SA-02). |

### OWASP MASVS v2 summary

- **MASVS-STORAGE:** Compliant, with hardening recommended for stale plaintext backup staging files
  in `cache/backup/` after abnormal process termination (SA-03) and separating App Lock preferences
  into a device-local DataStore file (SA-05).
- **MASVS-CRYPTO:** Compliant, with hardening recommended to zeroize and clear cached `BackupKey`
  instances on `disable()`/`enable()` and to separate the `keyId` MAC key from the Tink streaming
  AEAD key (SA-02).
- **MASVS-AUTH:** Compliant for UI-level convenience App Lock using platform biometric/credential
  authentication (`DeviceAuthenticator`, `AppLockController`).
- **MASVS-NETWORK:** Compliant (`network_security_config.xml`, `DriveUploadAddress`, dedicated
  OkHttp timeouts, no logging interceptors).
- **MASVS-PLATFORM:** Compliant (`ManifestPolicyTest`, non-exported `FileProvider`, Compose
  navigation routes passing only integer book IDs, zero `WebView` usage).
- **MASVS-CODE:** Compliant (`RoomLibraryRestorer` schema/value bounds, `BackupZip` Zip-Slip and
  decompression-bomb defenses, Detekt, Android Lint, Kotlin explicit null safety).

## 4. Detailed findings and remediation

### SA-01. CSV formula injection via unvalidated ISBN-10 in Goodreads export (Medium)

- **Location:**
  - `app/src/main/kotlin/app/otium/data/transfer/GoodreadsCsv.kt` (lines 95–96, 114–117)
  - `app/src/main/kotlin/app/otium/data/transfer/FileValues.kt` (lines 71–85)
  - `app/src/main/kotlin/app/otium/data/remote/google/GoogleBooksMapper.kt` (lines 48–49)
  - `app/src/main/kotlin/app/otium/data/remote/openlibrary/OpenLibraryMapper.kt` (lines 57–58, 80)
  - `domain/src/main/kotlin/app/otium/domain/usecase/book/SaveBook.kt` (lines 123–128, 144, 162–163)
  - `domain/src/main/kotlin/app/otium/domain/usecase/book/RetryPendingLookups.kt` (lines 74–76)
- **Vulnerability type:** Improper Output Neutralization for Logs / Spreadsheets (CWE-1236, OWASP
  M4).
- **Description:**
  `GoodreadsCsv.record()` intentionally wraps `book.isbn10` and `book.isbn13` in Goodreads'
  `="... "` spreadsheet formula syntax so spreadsheet software preserves leading zeros, and skips
  `FileValues.spreadsheetSafe()` for the `ISBN` and `ISBN13` columns:

  ```kotlin
  ISBN to "=\"${book.isbn10.orEmpty()}\"",
  ISBN13 to "=\"${book.isbn13.orEmpty()}\"",
  // ...
  if (header == ISBN || header == ISBN13) raw else FileValues.spreadsheetSafe(raw)
  ```

  This exemption is safe only if `book.isbn10` and `book.isbn13` are guaranteed to contain solely
  ISBN characters (`0-9` and `X`). While `isbn13` is validated on most paths via `ValidateIsbn13`,
  `book.isbn10` is stored without character or checksum validation across multiple entry points:
  - `GoogleBooksMapper.kt` (line 49) accepts any non-empty string for `ISBN_10`.
  - `OpenLibraryMapper.kt` (line 58) accepts any non-blank string in `EditionDto.isbn10`, and
    `SearchResponse.toCandidates()` (line 80) checks only `it.length == 10`.
  - `SaveBook.kt` (lines 123–128, 162–163) validates only `isbn13` when `candidate.isbn13` is
    present, and `normalizeIsbn10` strips only `-` and whitespace (`value.filter { it != '-' &&
    !it.isWhitespace() }.uppercase()`).
  - `RetryPendingLookups.kt` (line 75) copies `found.isbn10` directly into `Book.isbn10`.
  - `RoomLibraryRestorer.kt` (lines 180–181) restores `isbn10` and `isbn13` directly from a backup
    database without character filtering.

  If an untrusted remote catalog response or restored database contains double quotes and formula
  operators in `isbn10`, `GoodreadsCsv.write()` interpolates them inside `="..."`, producing an
  active spreadsheet formula when the user opens the exported CSV in Excel or LibreOffice Calc.
- **Remediation:**
  1. In `GoodreadsCsv.record()`, sanitize both `book.isbn10` and `book.isbn13` through
     `FileValues.isbnOrNull()` before wrapping them in `="..."`:

     ```kotlin
     val safeIsbn10 = FileValues.isbnOrNull(book.isbn10.orEmpty()).orEmpty()
     val safeIsbn13 = FileValues.isbnOrNull(book.isbn13.orEmpty()).orEmpty()
     ```

  2. Validate `isbn10` via `ValidateIsbn13(isbn10)` in `SaveBook.resolveIsbn()`,
     `RetryPendingLookups`, `GoogleBooksMapper`, and `OpenLibraryMapper` (matching the existing
     `FileValues.isbnOrNull` logic in `GoodreadsCsv.parse()`), and enforce ISBN character checks in
     `RoomLibraryRestorer`.

### SA-02. Unwiped in-memory backup keys after turning off encryption and shared HMAC/HKDF key material (Low-Medium)

- **Location:**
  - `app/src/main/kotlin/app/otium/data/backup/crypto/KeystoreBackupEncryption.kt` (lines 64–86,
    96–112)
  - `app/src/main/kotlin/app/otium/data/backup/crypto/BackupKey.kt` (lines 28–39, 77–82)
- **Vulnerability type:** Cleartext Storage of Sensitive Information in Memory / Cryptographic Key
  Reuse (CWE-316, CWE-325, OWASP M9 / M10).
- **Description:**
  1. `KeystoreBackupEncryption` caches unlocked `BackupKey` instances in
     `private val unlocked = ConcurrentHashMap<String, BackupKey>()` (line 64). When the user turns
     off backup encryption via `disable()` (lines 73–76), the code clears the DataStore keys and
     deletes the Android Keystore wrapping key (`wrapper.delete()`), but **never clears `unlocked`
     or zeroes the `ByteArray` inside each `BackupKey`**. Because `heldKey(keyId)` checks
     `unlocked[keyId]` first (line 97), `canOpen(keyId)` and `open()` continue to decrypt backups
     using the cached key without prompting for the passphrase for the remainder of the app process
     lifetime. Similarly, `enable()` (lines 66–71) adds the new key without evicting or zeroing
     previously cached keys.
  2. In `BackupKey.kt` (lines 77–82), `keyIdOf(key)` uses the raw 32-byte master key directly as
     the `HmacSHA256` key to compute the public 16-byte `keyId` stored in the cleartext `.zip.enc`
     header (`HMAC-SHA256(key, "otium-backup-key-id-v1")`), while `BackupCipher.kt` passes the same
     raw 32-byte array as the input key material to Tink's `AesGcmHkdfStreamingKey`. Best practice
     (NIST SP 800-56C / RFC 5869) requires deriving distinct subkeys for key-confirmation MACs and
     payload encryption rather than sharing a raw key across two algorithms.
- **Remediation:**
  1. Add a `wipe()` method on `BackupKey` (`bytes.fill(0)`) and a helper in
     `KeystoreBackupEncryption` that iterates over `unlocked.values`, zeroes each key, and calls
     `unlocked.clear()` inside both `disable()` and `enable()`.
  2. When deriving a candidate key in `unlock()` (lines 80–85), zeroize `derived.bytes` on mismatch
     (`if (derived.keyId != keyId) { derived.wipe(); return Try.failure(...) }`), and zeroize the
     temporary `bytes` returned by `wrapper.unwrap(wrapped)` in `heldKey()` if not retained.

### SA-03. Plaintext backup and restore staging files persist in cache after process death (Low-Medium)

- **Location:**
  - `app/src/main/kotlin/app/otium/data/backup/BackupFiles.kt` (lines 16–30)
  - `app/src/main/kotlin/app/otium/data/backup/ZipBackupArchiver.kt` (lines 48–60, 72–85)
  - `app/src/main/kotlin/app/otium/data/backup/crypto/KeystoreBackupEncryption.kt` (lines 120–160)
  - `domain/src/main/kotlin/app/otium/domain/usecase/backup/LibraryRestore.kt` (lines 38–112)
  - `app/src/main/kotlin/app/otium/data/backup/drive/GoogleDriveBackupClient.kt` (lines 144–156)
  - `app/src/main/kotlin/app/otium/data/local/cover/CoverFiles.kt` (lines 28–33)
  - `app/src/main/kotlin/app/otium/OtiumApp.kt` (lines 39–43)
- **Vulnerability type:** Insecure Temporary File / Leftover Plaintext Data (CWE-459, OWASP M6 /
  M9).
- **Description:**
  - During an encrypted backup (`RunBackup`), `ZipBackupArchiver.create()` first builds an
    unencrypted `otium-backup*.zip` in `cacheDir/backup/`, which `KeystoreBackupEncryption.seal()`
    then encrypts into `otium-backup*.zip.enc` before deleting the plaintext `.zip`.
  - During a restore (`LibraryRestore.prepare()`), `GoogleDriveBackupClient.download()` writes the
    remote archive to `cacheDir/restore*.zip`, `KeystoreBackupEncryption.open()` decrypts it to
    a plaintext `cacheDir/backup/otium-restore*.zip`, and `ZipBackupArchiver.inspect()` extracts
    `library.db` into `cacheDir/backup/restore-<uuid>/library.db` to read the book count before
    deleting `restore-<uuid>`. The decrypted plaintext `otium-restore*.zip` stays on disk in
    `cacheDir/backup/` for the entire time the user views the `RestorePreview` confirmation sheet.
  - If the app process is killed by the OS, crashes, or is force-stopped while the preview sheet is
    open or mid-backup, `finally` blocks and `LibraryRestore.cancel()` never run. Unlike camera
    photos (`cameraPhotos.purgeStale()`) and orphaned covers (`orphanedCovers.purge()`),
    `OtiumApp.onCreate()` never cleans `cacheDir/backup/` or `cacheDir/restore*.zip`. As a result,
    an unencrypted copy of an encrypted backup can remain in the cache directory indefinitely.
  - Additionally, `CoverFiles.list()` filters out `.part` files (`!it.name.endsWith(PART_SUFFIX)`),
    so `OrphanedCovers.purgeNow()` never deletes stale `.jpg.part` files left behind in
    `files/covers/` after an interrupted cover download.
- **Remediation:**
  1. Add a startup cleanup method on `BackupFiles` (called from `OtiumApp.onCreate()` on an I/O
     dispatcher) that deletes stale files and subdirectories in `cacheDir/backup/` and any
     `cacheDir/restore*.zip` older than a short TTL (or unconditionally on process start before any
     backup worker runs).
  2. Also clean up stale `*.part` files in `files/covers/` during `OrphanedCovers.purgeNow()`.

### SA-04. Unbounded download and decryption stream sizes and optional checksum verification in backup restore (Low)

- **Location:**
  - `app/src/main/kotlin/app/otium/data/backup/drive/GoogleDriveBackupClient.kt` (lines 125–134,
    144–156)
  - `app/src/main/kotlin/app/otium/data/backup/crypto/BackupCipher.kt` (lines 109–117)
- **Vulnerability type:** Uncontrolled Resource Consumption / Improper Checksum Verification
  (CWE-400, CWE-354, OWASP M4).
- **Description:**
  1. `BackupZip.extract()` enforces `DEFAULT_MAX_UNPACKED_BYTES = 512L * 1024 * 1024` (512 MiB) when
     extracting a `.zip`, and `ContentResolverLibraryFiles.copyToCache()` caps local SAF files at
     512 MiB (`MAX_BACKUP_BYTES`). However, `GoogleDriveBackupClient.fetch()` copies the HTTP
     response stream to `cacheDir/restore*.zip` with `body.byteStream().copyTo(out)` without any
     byte limit, and `BackupCipher.decrypt()` copies the Tink plaintext stream to
     `cacheDir/backup/otium-restore*.zip` (`decrypted.copyTo(output)`) without a byte limit. A
     crafted or corrupted `.zip.enc` (either from SAF or Drive) whose inner ciphertext decompresses
     or decrypts to gigabytes of output can exhaust device storage before `BackupZip.extract()` is
     reached.
  2. In `GoogleDriveBackupClient.send()` (line 128) and `fetch()` (line 152), the SHA-256 integrity
     check is conditional on Drive returning a non-null checksum:

     ```kotlin
     if (reported != null && !reported.equals(expected, ignoreCase = true)) { ... }
     ```

     Because `FILE_FIELDS` in `DriveApi.kt` explicitly requests `sha256Checksum` for binary files in
     `appDataFolder`, a `null` checksum on a completed upload or download indicates an unexpected or
     tampered response and should fail closed rather than silently bypassing verification.
- **Remediation:**
  1. Bound the stream copy in `GoogleDriveBackupClient.fetch()` and `BackupCipher.decrypt()` to a
     maximum byte count (for example, `512L * 1024 * 1024`), throwing `IOException` if exceeded.
  2. Require `reported != null` in `GoogleDriveBackupClient.send()` and `expected != null` in
     `GoogleDriveBackupClient.fetch()`.

### SA-05. App Lock privacy gaps on Android 8–12 recents, loan notifications and DataStore placement (Low)

- **Location:**
  - `app/src/main/kotlin/app/otium/MainActivity.kt` (lines 88–97)
  - `app/src/main/kotlin/app/otium/data/notification/LoanReminderNotifier.kt` (lines 38–69)
  - `app/src/main/kotlin/app/otium/data/preferences/DataStoreAppLockPreferences.kt` (lines 19–27)
  - `app/src/main/kotlin/app/otium/di/PreferencesModule.kt` (lines 25–43)
- **Vulnerability type:** Privacy Leak via OS Surfaces (CWE-200, OWASP M3 / M6).
- **Description:**
  1. **Recent-apps screenshot on Android 8–12 (API 26–32):** `MainActivity.hideFromRecentsWhileLocked`
     (line 89) returns immediately when `Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU`
     (API 33). On API 26–32 (`minSdk = 26`), `WindowManager.LayoutParams.FLAG_SECURE` is never
     applied, so the system Recent Apps switcher captures and displays a preview of the user's
     library even when App Lock is enabled.
  2. **Loan reminder notifications reveal borrower and book title when App Lock is on:**
     `LoanReminderNotifier.notifyDueSoon()` configures `NotificationCompat.VISIBILITY_PRIVATE` with
     a redacted `setPublicVersion(hidden)` for the device lockscreen. However, Otium's App Lock is
     specifically designed for the scenario where the phone itself is unlocked (for example, handed
     to another person). When `AppLockPreferences.isEnabled` is true, background WorkManager runs of
     `LoanReminderWorker` still post the unredacted book title and borrower name in the unlocked
     notification shade.
  3. **App Lock setting stored in the main `library` DataStore:**
     `DataStoreAppLockPreferences` injects the default `DataStore<Preferences>` (`"library"` file)
     rather than a device-local store (`@BackupStateStore` or a dedicated store). Its KDoc notes
     that device transfer leaves it out so a new phone with different biometrics does not start
     locked; today that holds only because `data_extraction_rules.xml` excludes all files, and
     enabling `<device-transfer>` for the `library` DataStore (tracked in backlog item U7 / S15)
     would transfer `app_lock_enabled` unless it lives in the device-local DataStore.
- **Remediation:**
  1. On `Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU`, set `window.setFlags(FLAG_SECURE,
     FLAG_SECURE)` whenever App Lock is enabled (or while `lockState == LockState.LOCKED`).
  2. Check `AppLockPreferences.isEnabled` before posting detailed text in `LoanReminderNotifier`
     (using the generic `R.string.notification_loan_due_soon_private_text` body when App Lock is
     enabled).
  3. Annotate `DataStoreAppLockPreferences`'s constructor parameter with `@BackupStateStore` (or a
     dedicated device-local qualifier) so it never participates in future `<device-transfer>` rules.

### SA-06. Unvalidated remote and imported cover URLs and Open Library path identifiers (Low)

- **Location:**
  - `app/src/main/kotlin/app/otium/data/remote/openlibrary/OpenLibraryMapper.kt` (lines 54, 70)
  - `app/src/main/kotlin/app/otium/data/remote/openlibrary/OpenLibrarySource.kt` (line 44)
  - `app/src/main/kotlin/app/otium/data/transfer/JsonLibraryFormat.kt` (lines 127, 179)
  - `domain/src/main/kotlin/app/otium/domain/usecase/transfer/ImportLibrary.kt` (line 239)
  - `app/src/main/kotlin/app/otium/data/local/cover/FileCoverStore.kt` (lines 94–96)
- **Vulnerability type:** Improper Input Validation / Untrusted URL Fetch (CWE-20, OWASP M4).
- **Description:**
  1. In `OpenLibraryMapper.kt`, `SearchResponse.toCandidates()` validates `isbn13` with
     `ValidateIsbn13` (line 79), but `EditionDto.toCandidate()` (line 54) takes
     `val isbn13 = isbn13.firstOrNull { it.isNotBlank() }` without validation and interpolates it
     directly into `coverByIsbn(isbn13)`
     (`"https://covers.openlibrary.org/b/isbn/$isbn13-L.jpg?default=false"`).
  2. In `OpenLibrarySource.kt` (line 44), `private fun String.idOf() = substringAfterLast('/')`
     extracts work and author keys from `EditionDto` JSON references without verifying that they
     match Open Library's `OL\d+[A-Z]` identifier pattern before passing them to Retrofit `@Path`.
  3. When importing an Otium JSON export (`ImportLibrary.kt` line 239 / `JsonLibraryFormat.kt` line
     127), `row.coverUrl` is stored directly into `Book.coverSourceUrl` without validating that it
     is a well-formed `https://` URL. Although `FileCoverStore.download(url)` currently runs only
     during `SaveBook` and `RetryPendingLookups`, `FileCoverStore.download()` itself (line 95) does
     not assert `httpUrl.isHttps` in code (relying solely on `network_security_config.xml` to block
     cleartext HTTP, while allowing arbitrary `https://` hosts).
- **Remediation:**
  1. Validate `isbn13` with `ValidateIsbn13` in `EditionDto.toCandidate()`, and validate Open
     Library work/author IDs against `^OL[0-9]+[A-Z]$` in `OpenLibrarySource.idOf()`.
  2. In `FileCoverStore.download(url)`, parse the URL with `url.toHttpUrlOrNull()`, require
     `isHttps`, and restrict cover downloads to the expected cover hosts (`googleapis.com`,
     `googleusercontent.com`, `covers.openlibrary.org`, `archive.org`).
  3. Validate `coverUrl` in `JsonLibraryFormat` (requiring `https://` or dropping invalid URLs to
     `null`).

### SA-07. Passphrase plaintext retained in Compose input state during Argon2id derivation (Low)

- **Location:**
  - `app/src/main/kotlin/app/otium/ui/settings/PassphrasePanel.kt` (lines 88–94, 152–194)
- **Vulnerability type:** Exposure of Sensitive Information in Memory / UI (CWE-214, OWASP M9).
- **Description:**
  In `PassphrasePanel`, `input.clearTyped()` is invoked inside `LaunchedEffect(prompt)` when
  `prompt` transitions to a new state. While Argon2id key derivation is running (`busy == true`,
  which takes several hundred milliseconds on mobile hardware), the typed passphrase strings remain
  in `PassphraseInput` (`first` and `second`) and stay visible on screen if the user toggled "Show
  passphrase".
- **Remediation:**
  Capture the passphrase strings into local variables inside the `submit` lambda in
  `PassphrasePanel`, immediately call `input.clearTyped()`, and pass the captured values to
  `onTurnOn(passphrase)` / `onUnlock(passphrase)`.

### SA-08. Unescaped secret interpolation when writing keystore.properties in release workflow (Informational)

- **Location:**
  - `.github/workflows/release.yml` (lines 111–117)
- **Vulnerability type:** Improper Escaping of Configuration File (CWE-116, OWASP M2).
- **Description:**
  In `release.yml`, the `Write keystore.properties` step writes secrets using unescaped shell lines:

  ```bash
  {
    echo "storeFile=$KEYSTORE_FILE"
    echo "storePassword=$UPLOAD_STORE_PASSWORD"
    echo "keyAlias=$UPLOAD_KEY_ALIAS"
    echo "keyPassword=$UPLOAD_KEY_PASSWORD"
  } > keystore.properties
  ```

  `app/build.gradle.kts` loads `keystore.properties` via `java.util.Properties.load()`, which treats
  backslashes (`\`) as escape sequences and strips leading whitespace. If a keystore password
  contains `\`, `keytool -storepass:env` in the preceding step succeeds, but `Properties.load()`
  parses a different password.
- **Remediation:**
  Escape `\` as `\\` (or pass signing credentials via environment variables directly in
  `app/build.gradle.kts` when `keystore.properties` is absent in CI).

## 5. Verification of prior security backlog (S1–S22)

The previous review (`docs/plans/2026-10-07-architecture-security-ux-review.md`) tracked 22
security items (`S1`–`S22`). Their verified state on commit `e5b0819` is summarized below:

| ID | Original finding | Status on `e5b0819` | Verification notes |
|----|------------------|---------------------|--------------------|
| S1 | Build and test release variant in CI | Resolved | `.github/workflows/ci.yml` builds release, checks R8 outputs, and runs `smoke-release`. |
| S2 | Validate Drive upload session URL | Resolved | `DriveUploadAddress.kt` enforces `https` and `googleapis.com` / `*.googleapis.com`. |
| S3 | Restrict Google Books API key | Resolved | Configured in Cloud Console and documented in `docs/RELEASING.md`. |
| S4 | Encrypted backups and App Lock | Resolved | Shipped via `KeystoreBackupEncryption`, `BackupCipher`, `AppLockController`, and `DeviceAuthenticator` (see SA-02, SA-05 for minor hardening). |
| S5 | Network security config and manifest cap | Resolved | Enforced by `network_security_config.xml` and `ManifestPolicyTest.kt`. |
| S6 | Store cover file names, not absolute paths | Resolved | Database stores relative filenames; `CoverFiles.resolve()` blocks path traversal. |
| S7 | Strip metadata from camera cover photos | Resolved | `FileCoverStore.savePhoto()` decodes with `BitmapFactory` and re-encodes to JPEG. |
| S8 | Purge stale camera temp files on startup | Resolved | `CameraPhotoFiles.purgeStale()` runs on startup in `OtiumApp.onCreate()`. |
| S9 | Tighten `BackupZip` path check | Resolved | `BackupZip.kt` verifies entry allowlist, `..` segments, and `canonicalFile` containment. |
| S10 | Pin every GitHub Action to commit SHA | Resolved | Verified by `scripts/lint-workflows.sh` (`zizmor` and `actionlint` pass with 0 findings). |
| S11 | Reject non-https cover URLs in Google Books mapper | Resolved | `GoogleBooksMapper.httpsUrl()` accepts only `https://` or upgrades `http://`. |
| S12 | Validate SQLite file before replacing Room DB | Resolved | `RoomLibraryRestorer.kt` checks `PRAGMA integrity_check`, schema version, and row bounds. |
| S13 | Deny reading `local.properties` and keystores in `.claude/settings.json` | Resolved | Enforced in `.claude/settings.json` lines 31–38. |
| S14 | Upgrade Drive upload checksum from MD5 to SHA-256 | Resolved | `GoogleDriveBackupClient.kt` verifies `sha256Checksum` on upload and download (see SA-04). |
| S15 | Separate device-local backup state DataStore | Resolved | `@BackupStateStore` (`backup_state` file) separates Drive state from `library` preferences. |
| S16 | Enforce GitHub branch and tag protection | Operational | Documented in `docs/RELEASING.md` section 1. |
| S17 | Add CodeQL and OSV-Scanner workflows | Resolved | `.github/workflows/codeql.yml`, `osv-scanner.yml`, and `./gradlew scanDependencies` active. |
| S18 | Add SLSA provenance attestation to release artifacts | Resolved | `.github/workflows/release.yml` signs AAB and APK with `actions/attest-build-provenance`. |
| S19 | Enable Gradle dependency verification (`verification-metadata.xml`) | Open (Deferred) | Still deferred due to maintenance cost on Dependabot updates; mitigated by TLS + SBOM + OSV. |
| S20 | Cap FTS query length and token count | Resolved | `LibraryQueryBuilder.kt` enforces `MAX_QUERY_LENGTH = 200` and `MAX_TOKENS = 12`. |
| S21 | Reject non-ISBN characters in `OpenLibrarySource` | Resolved | `OpenLibrarySource.lookupByIsbn` returns `null` if `!ValidateIsbn13(isbn)`. |
| S22 | Cap HTTP error body read in `GoogleBooksSource` | Resolved | `GoogleBooksSource.googleReason()` reads a bounded 4 KiB buffer (`MAX_ERROR_BYTES = 4096`). |

## 6. Prioritized remediation roadmap

1. **Immediate (Resolved):**
   - **SA-01 (Resolved):** Sanitized `book.isbn10` and `book.isbn13` with `ValidateIsbn13` and
     `FileValues.isbnOrNull()` in `GoodreadsCsv.record()`, and validated `isbn10` and `isbn13`
     across `SaveBook`, `RetryPendingLookups`, `GoogleBooksMapper`, `OpenLibraryMapper`, and
     `RoomLibraryRestorer`.
   - **SA-02 (Resolved):** Added domain-separated `BackupKey.encryptionKey()` and `BackupKey.wipe()`
     and cleared/zeroized `unlocked` in `KeystoreBackupEncryption.disable()` and `enable()`.
   - **SA-03 (Resolved):** Added `BackupFiles.purgeNow()` and `CoverFiles.partials()` cleanup on
     application startup via `OrphanedCovers.purge()`.
2. **Short-term hardening:**
   - **SA-04:** Enforce a 512 MiB stream limit in `GoogleDriveBackupClient.fetch()` and
     `BackupCipher.decrypt()`, and require non-null `sha256Checksum` from Google Drive.
   - **SA-05:** Apply `FLAG_SECURE` on API 26–32 when App Lock is enabled, redact borrower/title in
     `LoanReminderNotifier` when App Lock is active, and move `DataStoreAppLockPreferences` to the
     device-local `@BackupStateStore`.
   - **SA-06:** Enforce `https://` and host allowlisting in `FileCoverStore.download()`, validate
     `isbn13` in `EditionDto.toCandidate()`, validate Open Library IDs in `OpenLibrarySource`, and
     validate `coverUrl` in `JsonLibraryFormat`.
3. **Polish:**
   - **SA-07:** Clear `PassphraseInput` immediately on submit before Argon2id derivation starts.
   - **SA-08:** Escape backslashes when generating `keystore.properties` in `release.yml`.
