# Architecture, security and UX review: findings to apply

Date: 2026-10-07, second pass 2026-10-08, security pass 2026-10-08
Status: In progress; see the Status column in section 2
Related documents: [app design](../design/2026-10-06-otium-library-app-design.md),
[implementation plan](2026-10-06-otium-implementation-plan.md), [roadmap](../ROADMAP.md),
[style guide](../STYLE_GUIDE.md), [security policy](../../SECURITY.md)

This is the backlog from a review of the codebase on `develop` (commit `e6e65ed`) against Android
Clean Architecture practice, security guidance and UI/UX guidance. It is for whoever applies the
changes later. Each finding names the files involved, the change to make and how to tell it is
done. Findings are grouped by area and ranked within each.

The review read code and documents only. The release variant was not built and the app was not
run, so findings marked _unverified_ need a check before work starts.

A second pass on 2026-10-08 (commit `024487e`) added the findings in section 6. That pass also
read the merged release manifest and the release APK, which by then CI built (S1).

A testing review the same day measured coverage and added a coverage gate, a manifest policy
test and a release smoke test. What it left open is in section 7 (T1 to T7).

A security pass the same day (commit `57bef05`) went over the whole repository: app code, build,
CI, git hooks, the Claude Code configuration and the git history, against the OWASP Mobile Top 10
(2024), the OWASP MASVS and Android's app security guidance. It rechecked S3 to S9 and added S10
to S22 in section 8. It found no critical or high problem and no committed secret.

## 1. Verdict

The architecture is sound and needs no restructuring. Most findings are small. A few should be
done before the first release because they get more expensive once users hold data.

Already right, and to keep as it is:

- **Layering:** `:domain` is pure Kotlin. Ports are bound with Hilt `@Binds`. `Try` and `AppError`
  are mapped to strings in one place (`ErrorMessages.kt`). Workers are thin. `ArchitectureTest`
  (Konsist) guards the rules.
- **Backup safety:** The Drive upload lands under a temporary name, is checked by MD5 and is then
  renamed. Zip extraction accepts only known entry names, checks canonical paths and caps entry
  count and unpacked bytes. A restore is all or nothing and compares the book count.
- **Privacy:** `allowBackup="false"` with every domain excluded in both backup XML files, the
  `drive.appdata` scope only, and Google's `http://` cover URLs upgraded to https in
  `GoogleBooksMapper.kt` (other schemes still pass through; see S11).
- **Supply chain:** CI actions are pinned by commit SHA with read-only permissions, and Dependabot
  is on. The CI refactor in `57bef05` added four steps pinned only by tag (S10).
- **Secrets:** nothing committed, now or in the history (section 8).
- **Accessibility:** Palette contrast is unit-tested, reduced motion is respected and screens use
  semantics widely.

## 2. Priority summary

| ID | Area | Change | Size | Status |
|----|------|--------|------|--------|
| S1 | Security | Build and test the release variant in CI | M | CI and smoke test done; scan, backup, restore by hand open |
| S2 | Security | Validate the Drive upload session URL | S | Done |
| S3 | Security | Restrict the Google Books API key | S | Documented; Cloud console steps Done |
| S4 | Security | Encrypted backups, then app lock; defer SQLCipher | L | Encrypted backups done; app lock open |
| S5 | Security | Network security config, manifest cap, dependency verification | S | Done; dependency verification deferred |
| S6 | Security | Store cover file names, not absolute paths | M | Done |
| A1 | Architecture | Keep the draft out of `ConfirmRoute` | M | Open |
| A2 | Architecture | Keep library controls across process death | S | Open |
| A3 | Architecture | Settle the schema before the first release | S | Open |
| A4 | Architecture | Module split: not now, with a trigger | - | Open |
| U1 | UX | Simplify the library header | M | Open |
| U2 | UX | Add a Settings screen and a backup nudge | M | Open |
| U3 | UX | Undo for destructive actions | M | Open |
| U4 | UX | Adaptive layout | M | Open |
| U5 | UX | Copy and accessibility fixes | S | Open |
| U6 | UX | Play listing privacy material | S | Open |
| U7 | UX | Keep the library when moving to a new phone | M | Open |
| S7 | Security | Recover from a revoked Drive token without a stale screen | S | Done |
| S8 | Security | Delete cover photos left in the camera cache | S | Done |
| B1 | Build | Build an app bundle in CI and keep it with the R8 mapping | S | Open |
| S9 | Security | Pin the Gradle distribution checksum | S | Done |
| B2 | Build | Build the release variant in a parallel CI job | S | Open |
| U8 | Feature | Launcher shortcuts for adding books | S | Open |
| U9 | Feature | Add a book shared from another app | M | Open |
| P1 | Platform | Set the keyboard resize mode on the activity | S | Open |
| P2 | Platform | Give the themed icon its own monochrome drawable | S | Open |
| B3 | Build | StrictMode in debug builds; try the configuration cache | S | Open |
| T1 | Testing | Test `GoogleAuthManager` through a seam over the authorization client | S | Open |
| T2 | Testing | Move Robolectric closer to the target SDK | S | Open |
| T3 | Testing | Run search, filters and migrations on a device's SQLite | M | Open |
| T4 | Testing | Stable selectors for UI tests; move the benchmark to the new API | M | Open |
| T5 | Testing | Run the release smoke test in CI on demand | S | Open |
| T6 | Testing | Compose tests for the untested UI branches | M | Open |
| T7 | Testing | Screenshot tests for both themes and large fonts | M | Open |
| S10 | Security | Pin `upload-artifact` by commit SHA (Medium) | S | Done |
| S11 | Security | Accept only https cover URLs from the book services | S | Done |
| S12 | Security | Filter dependency repositories by group | S | Done |
| S13 | Security | Keep coding agents away from local secrets | S | Done |
| S14 | Security | Check a restored database before reading it | S | Done |
| S15 | Security | Keep the account email apart from library settings | S | Done |
| S16 | Security | Make debug-key release builds an explicit choice | S | Done |
| S17 | Security | Pass workflow values to CI scripts through the environment | S | Done |
| S18 | Security | Escape `BuildConfig` string values | S | Done |
| S19 | Security | Lower the backup unpack limit | S | Done |
| S20 | Security | Use a portable path in `.claude/settings.json` | S | Done |
| S21 | Security | Register the theme catalog route only in debug builds | S | Done |
| S22 | Security | Scan dependencies for known vulnerabilities | S | Done |

Sizes: S is under a day, M is one to three days, L is a phase of its own.

Suggested first batch: S1, S2, S3, U1 and U2. They are small and worth doing before first release.

Suggested second batch: U7, S7, S8, B1 and S9. U7 closes a data loss path; the rest are small.
Do U8 and U9 together with the Settings screen (U2).

Testing: do T1 with S7, which changes the same class, and T3's migration test with A3. T4 makes T5
and T6 cheaper, so it comes first of those.

Security pass: S10, S11 and S13 first (small, and S10 is the only Medium besides S4), then S9
with S5's dependency verification, then S12 and S16. The rest ride along with related work: S14
and S5's manifest cap with S4, S15 with U7, S8 with S6.

## 3. Security

### S1. Build and test the release variant in CI

`app/build.gradle.kts` sets `isMinifyEnabled = true` and `isShrinkResources = true` for release.
`app/proguard-rules.pro` holds only comments (the file says Phase 7 will fill it), and there is no
release signing config. CI (`.github/workflows/ci.yml`) runs `assembleDebug` only. A crash that
appears only under R8 would be found late.

Change:

- Add `assembleRelease` to the CI Gradle line. Read the signing key from `keystore.properties`
  (already ignored by `.gitignore`) and fall back to the debug key when the file is missing, so CI
  needs no secret.
- Check the Retrofit, kotlinx.serialization, Room, Hilt and ML Kit paths on a release build on a
  device. Add keep rules only for what actually fails, each with a comment giving the reason.
- Keep the `benchmark` build type as it is.

Done when: CI builds a minified release, and a manual run of scan, add, search, backup and restore
passes on that build.

### S2. Validate the Drive upload session URL

`DriveApi.uploadContent` takes `@Url sessionUrl`, and `GoogleDriveBackupClient.send` fills it with
the `Location` header of the previous response. The `Authorization: Bearer` header is sent to
that address. The header comes from Google, so this is defence in depth, but the check is cheap.

Change: before the upload, require that the URL is https and its host is `www.googleapis.com` or
ends with `.googleapis.com`. Otherwise fail with `AppError.Unexpected`. Add a unit test with a
mock server that answers with a foreign `Location`.

### S3. Restrict the Google Books API key

`BOOKS_API_KEY` is compiled into `BuildConfig`, so anyone can extract it from the APK. It is not
a secret, but unrestricted use could burn the quota.

Change, in the Google Cloud console:

- Restrict the key to the Books API and set a daily quota cap.
- If the key is also restricted to the Android app, add `X-Android-Package` and `X-Android-Cert`
  headers in `ApiKeyInterceptor` (`data/remote/RemoteClients.kt`). Without them restricted
  requests are rejected. The simpler option is an API restriction only.
- Record the choice in [SECURITY.md](../../SECURITY.md) under "Keys and secrets".

### S4. Encrypted backups first, app lock second, defer SQLCipher

The roadmap plans SQLCipher (D-RM2), Keystore key management (D-RM3) and encrypted Drive backups
(D-RM4). The order and scope below change that plan. Update the [roadmap](../ROADMAP.md) decision
log when this is accepted.

Reasoning:

- The backup is the only place the library leaves the device, so encrypting it is the largest
  gain.
- Android file-based encryption already protects the database at rest on the device.
- SQLCipher adds native libraries per ABI, makes a lost key mean lost data, and touches the
  restorer (`RoomLibraryRestorer` opens a temporary Room instance) and the benchmark module.
- A biometric app lock is cheap and covers shoulder-surfing and a borrowed phone, which is the
  realistic threat for a reading list.

Design points for encrypted backups:

- Derive the key from a user passphrase with a memory-hard function (scrypt or Argon2id) and
  encrypt the zip with AES-256-GCM. Put the salt, parameters and format version in the header.
- Scheduled backups run without the user, so keep the derived key wrapped by an Android Keystore
  key for the worker. A restore on a new device cannot use that key, so it asks for the
  passphrase.
- Raise `BackupManifest.FORMAT`. Keep reading format 1 (plain) so existing backups restore.
- Warn plainly in the UI that a forgotten passphrase cannot be recovered.

Revisit SQLCipher only if the threat model changes, for example when private notes arrive in
roadmap Phase 3.

Status (2026-10-09): encrypted backups are done, as designed above, with one change. Instead of
raising `BackupManifest.FORMAT`, the whole zip is wrapped in an encrypted envelope with its own
versioned header (`OTIUMENC`), so the zip and manifest inside are unchanged and plain backups
still restore. Encryption is Tink's streaming AES-256-GCM (HKDF-SHA256 per file, 256 KiB
segments, the header bound as associated data); the key comes from Argon2id at 64 MiB and three
passes, and a header asking for settings outside a fixed range is treated as damaged. The key is
wrapped by an Android Keystore key and kept in its own DataStore file, apart from the library
settings (see S15). A lost Keystore key stops backups with a request for the passphrase; it never
falls back to an unencrypted upload. Restoring an encrypted backup on a new phone keeps
encryption on with the same key. The app lock is still open.

### S5. Small hardening items

- Add `res/xml/network_security_config.xml` with cleartext traffic off and reference it from the
  manifest. The default already blocks cleartext at the current target SDK; this makes the intent
  explicit and lintable. Do not pin certificates, because Google rotates them.
- In `data/backup/archive/BackupZip.kt`, `text()` reads an entry with `readBytes()` and no limit.
  Cap the manifest and reject anything larger than a few kilobytes as `CorruptBackup`. The
  security pass confirmed this is still open: the read happens in both `readManifest` and
  `unpack`, and the manifest is not counted against the unpack budget.
- Add Gradle dependency verification (`gradle/verification-metadata.xml`, generated with
  `./gradlew --write-verification-metadata sha256 help`) and keep it current when Dependabot
  bumps. Do it together with S9.
- The integrity check on the extracted `library.db` is now part of S14.

Status (2026-10-09): the network security config is in place (cleartext off, system authorities
only, no pins) and `ManifestPolicyTest` reads it. `BackupZip` reads the manifest only up to
16 KiB and treats a larger one as `CorruptBackup`, in both `readManifest` and `extract`.

Dependency verification is deferred. Dependabot does not update `verification-metadata.xml`, so
every Gradle bump it opens would fail CI until someone regenerates the file on that branch, and
the file has to carry the Linux and Windows `aapt2` checksums by hand when it is generated on
macOS. Until then the wrapper checksum (S9), group filtering (S12) and the OSV-Scanner gate (S22)
carry the load. Revisit when Dependabot supports the file, or with a workflow that regenerates it
on Dependabot branches.

### S6. Store cover file names, not absolute paths

`FileCoverStore.store` returns `finished.absolutePath` and the database keeps it as `coverPath`.
`RoomLibraryRestorer` has to rewrite these paths on restore, which shows the fragility: any move
of `filesDir` breaks every cover.

Change: store the file name, resolve it against `coversDirectory(context)` at read time (in the
mapper or a small resolver), and add a migration that strips the directory from existing rows.
Do this together with A3 so it ships in the first schema.

Status (2026-10-09): done without a schema change. `CoverFiles` turns a stored name into a path
in `RoomBookRepository`'s mappers, and the domain still sees full paths. Writes keep the name
only. There is no migration: a row written before holds a full path, and only its file name is
used, so old rows and old backups resolve into the current covers directory. The restorer copies
covers in under their own names and no longer rewrites rows.

## 4. Architecture

### A1. Keep the draft out of `ConfirmRoute`

`ui/navigation/Routes.kt` flattens a whole `BookCandidate` into the route: description,
categories, tags and cover URLs, with `0` standing for an unknown number. That makes long route
strings and sentinel values.

Change: hold the draft in a `SavedStateHandle`-backed holder or a small draft store keyed by an
id, and let `ConfirmRoute` carry only that id (plus `bookId` for edits). The draft must still
survive process death, which is the reason for the current shape. Nullable fields become
nullable again. Update `ConfirmViewModel` and the add-flow tests.

### A2. Keep library controls across process death

`LibraryViewModel` keeps query, status, categories, tags and sort in a plain `MutableStateFlow`.
They are lost when the process dies, and sort is not persisted although view mode is.

Change: back `controls` with `SavedStateHandle`. Persist sort in `LibraryPreferences` next to
view mode, because it is a presentation choice and should survive a restart.

### A3. Settle the schema before the first release

`OtiumDatabase.VERSION` is 1 and `migrations` is empty. Until a release exists, any schema change
is free. Collect the changes the roadmap needs and make them in version 1: reading formats,
lending and notes tables if they are certain, the cover file-name change (S6) and any index the
5,000-book benchmark asks for. After the first release every change needs a migration and a
`MigrationTestHelper` test.

### A4. Module split: not now

Keep the two modules (design decision D19). The Drive and backup code (`data/backup`, `auth`,
`work`, about 20 files) is the part most likely to be extracted. Split it into its own module
only when one of these happens:

- the roadmap's widget, OCR or lending work adds a second large area to `:app`, or
- incremental build time for `:app` becomes a problem.

## 5. UI and UX

### U1. Simplify the library header

`LibraryContent` stacks a search field, status chips, a "Sort" text button, a "Filters" text
button and a "List"/"Shelf" text button above the first book. On a phone that is a lot of chrome,
and the view toggle is labelled with where it goes, which reads ambiguously.

Change:

- Replace the view toggle with an icon button that has a state description ("Shelf view",
  "List view").
- Move sort into the filter sheet, or make it an icon menu, so the controls row holds one filter
  entry point and the book count.
- Keep the status chips and the search field.
- Check the result at large font scale and in landscape.

### U2. Add a Settings screen and a backup nudge

Backup is reachable only from the overflow menu (`MoreMenu` in `LibraryScreen.kt`). The library
lives only on the phone until a backup runs, which is the largest risk to a user's data.

Change:

- Add a `SettingsRoute` with backup, view options, a short privacy note, licenses and the version.
  The roadmap needs this screen anyway for app lock and export.
- Show one quiet, dismissible notice on the library screen once the library has a few books and no
  backup exists ("Your books are only on this phone"). It appears once, never as a notification,
  to fit the calm tone of the app (design decision D18).

### U3. Undo for destructive actions

Deleting a book and restoring a backup both use a confirmation dialog that says "This cannot be
undone".

Change:

- Book delete: delete after a Snackbar with an Undo action times out, or soft-delete and purge
  later. Drop the dialog.
- Restore replaces the whole library, so keep the dialog. Also keep a local copy of the library
  from just before the restore for a few days and offer "Undo restore" from the backup screen.

### U4. Adaptive layout (unverified)

No use of window size classes was found. Check landscape, tablets and foldables. Use adaptive grid
cells for the shelf view, constrain the width of forms, and consider a list-detail layout on
expanded widths.

### U5. Copy and accessibility fixes

- `error_not_found` ("We could not find that.") is vague. Say what was not found, per screen.
- Name the Drive actions consistently: "Connect Google Drive", then "Reconnect" (now "Sign in
  again"), then "Disconnect". `error_auth_expired` should use the same word.
- `ui/add/search/TitleSearchScreen.kt` (around line 187) uses `clickable(onClick = onClick)`
  without a role. Give it `Role.Button` or make the row a Material list item.
- Add Compose tests at 200% font scale for the library, detail and backup screens.
- Confirm that the toolbar text buttons meet the 48dp touch target.

### U6. Play listing privacy material

Before publishing, prepare a privacy policy URL and the Data safety form. The facts are simple:
no analytics and no accounts, ISBN and title queries go to Google Books and Open Library, and
backups go to the user's own Drive. Link the policy from the Settings screen (U2).

## 6. Second pass: data safety, release build and small features

Checked in this pass and found sound, so not findings: every exported component in the merged
manifest comes from a library and is guarded by a permission (`BIND_JOB_SERVICE`, `DUMP`, Google's
revocation permission); there is no `AD_ID` permission; the backup notification uses an immutable
`PendingIntent` on a low-importance channel; covers are picked with the Photo Picker, so no storage
permission is needed; the camera permission is asked in context with a rationale; the app logs
nothing; every native library in the release APK passes `zipalign -c -P 16` (16 KB pages).

### U7. Keep the library when moving to a new phone

`res/xml/data_extraction_rules.xml` excludes every domain from `<device-transfer>` as well as
from `<cloud-backup>`. A user who moves to a new phone with Android's transfer tool, and has not
connected Drive, arrives with an empty library. This is the most likely way a user loses data.

Device transfer is started by the user, goes directly between the two phones and is encrypted on
Android 12 and later, so allowing it does not conflict with the privacy promise. Android's cloud
backup stays excluded: the app's own Drive backup is the only cloud copy.

Change:

- In `<device-transfer>`, include the database (`otium.db` and its `-wal` and `-shm` files), the
  covers directory under `files/`, and the library preferences.
- Keep the Drive and backup state out of the transfer: the connected flag, the account email, the
  expired flag, the last backup and the failure count. The grant does not move with the phone, so
  the new phone should start disconnected. If these share a DataStore file with the library
  preferences (`di/PreferencesModule.kt`), give the backup state its own file first so it can be
  excluded on its own.
- After a transfer, scheduled backups are not planned on the new phone. Nothing needs to change
  for that: the user connects Drive again, which plans them.
- Update `backup_rules.xml` only if Android 11 and lower should behave the same. Its
  `<full-backup-content>` cannot tell transfer from cloud backup, so keep it excluding everything.

Done when: a Robolectric test reads `data_extraction_rules.xml` and checks what is included and
excluded, and a manual transfer between two devices with Android's setup transfer brings the
books and covers across and shows Drive as disconnected.

### S7. Recover from a revoked Drive token without a stale screen

`DriveErrorMapping.kt` maps a 401 to `AuthExpired`, but only `GoogleAuthManager.accessToken()`
calls `markExpired()`, and only when Google asks for the consent screen. If Google Identity
Services hands back a cached token that was revoked in the meantime, the upload fails with
"sign in again" while the Backup screen still shows the account as connected, and the same stale
token can come back on the next attempt.

Change:

- On a 401, clear the cached token (`AuthorizationClient.clearToken`; confirm it is available in
  play-services-auth 22.0.0) and retry the call once with a fresh token.
- If the retry also gets a 401, mark the grant expired so the screen offers "Reconnect" (U5).
- Test both paths with the fake Drive in `GoogleDriveBackupClientTest`.

Status (2026-10-09): done. `AccessTokenProvider` gained `discard` and `revoked`.
`GoogleAuthManager` clears a refused token with `AuthorizationClient.clearToken` (present in
play-services-auth 22.0.0) and marks the grant expired on `revoked`. `GoogleDriveBackupClient`
reruns the whole call once with a fresh token after a 401; an upload cut off part way is cleared
up by the settled layout before it is sent again. The fake Drive tests cover the retry, a second
refusal, no fresh token and an upload refused part way. T1 is still open.

### S8. Delete cover photos left in the camera cache

`ui/add/confirm/CoverSection.kt` has the camera write `cache/camera/cover-<nanos>.jpg`. When the
book is saved the cover store copies the photo, and when the user backs out it is not used, but
no code was found that deletes the file in `cache/camera/`, so it stays until Android trims the
cache. These are the user's own photos.

Change: delete the camera file once the cover store has copied it or the user has cancelled, and
empty `cache/camera/` when the app starts, for files left by a process that died.

Status (2026-10-09): done. `ConfirmViewModel` remembers every photo picked on the screen and
hands them to the `CapturedPhotos` port when it is cleared, which happens after a save and after
backing out alike. `CameraPhotoFiles` deletes only addresses with the app's FileProvider
authority, the `camera` folder and a `cover-<digits>.jpg` name. At start it deletes photos older
than a day instead of emptying the folder: after process death the camera app may have just
written a photo that the restored screen still needs.

### B1. Build an app bundle in CI and keep it with the R8 mapping

CI runs `assembleRelease`, which makes a universal APK of about 25 MB, most of it ML Kit's
`libbarhopper_v3.so` for four ABIs. Play needs an app bundle, and from a bundle it delivers only
the ABI a phone uses, around 7 MB.

Change: run `bundleRelease` in CI next to `assembleRelease` (the APK stays for installing on a
device), and keep the bundle and `app/build/outputs/mapping/release/mapping.txt` as workflow
artifacts for a few days. The mapping is what makes Android vitals stack traces readable. Record
the bundle size per build so a jump is noticed.

### S9. Pin the Gradle distribution checksum

`gradle/wrapper/gradle-wrapper.properties` has no `distributionSha256Sum`. `setup-gradle`
validates the wrapper JAR, but not the distribution zip that the wrapper downloads.

Change: add `distributionSha256Sum` for the Gradle 9.8.0 binary distribution (from
services.gradle.org), for example with
`./gradlew wrapper --gradle-version 9.8.0 --gradle-distribution-sha256-sum <sum>`. Dependabot
updates the sum together with the version. This complements dependency verification (S5).

Status (2026-10-09): done. `distributionSha256Sum` is set for Gradle 9.8.0; the value from
services.gradle.org matched the hash of the locally downloaded distribution.

### B2. Build the release variant in a parallel CI job

Adding `assembleRelease` (S1) took CI from about 12 to about 18 minutes, because it also runs R8
and `lintVitalRelease`. Move the release build (and B1's bundle) into a second job that runs
next to the existing one, with the same pinned actions and read-only permissions.

### U8. Launcher shortcuts for adding books

Adding a book is the most frequent action. Add static shortcuts (`res/xml/shortcuts.xml`,
referenced from `MainActivity`): "Scan a book" and "Add by ISBN", each opening its add screen
through the navigation graph. Not in the roadmap; cheap.

### U9. Add a book shared from another app

Let `MainActivity` receive `ACTION_SEND` with `text/plain`, find an ISBN in the text (a bare
ISBN, or one inside a bookshop or Open Library link) with the existing ISBN check, and open the
confirm flow for it. Text without an ISBN opens the title search with the text filled in. This
lets people add a book from a browser or a friend's message. Treat the shared text as untrusted
input: cap its length and never open the links in it. Not in the roadmap.

### P1. Set the keyboard resize mode on the activity

Set `android:windowSoftInputMode="adjustResize"` on `MainActivity`. With `enableEdgeToEdge()` the
confirm screen's `imePadding()` already works, but at target SDK 37 the explicit mode is the
recommended baseline and keeps new text fields from being covered by the keyboard.

### P2. Give the themed icon its own monochrome drawable

`res/mipmap-anydpi/ic_launcher.xml` uses the full-colour foreground as the `<monochrome>` layer.
Android tints that layer as a single colour, so a design with fills of different colours turns
into a flat shape. Draw a single-colour silhouette for it and check it with themed icons on.
Fits implementation plan Phase 7, task 3.

### B3. StrictMode in debug builds; try the configuration cache

- Turn on `StrictMode` thread and VM policies (disk and network on the main thread, unclosed
  resources) in debug builds only, logging the violations. The backup and restore code does a lot
  of file work and this catches a regression early.
- Try `org.gradle.configuration-cache=true` in `gradle.properties`. Keep it only if Spotless,
  detekt and the docs check all support it.

## 7. Testing

The suite is in good shape: 68 test files on JUnit 4, Robolectric, MockK, Turbine and Konsist,
with shared fakes in `:domain` test fixtures. It stays on JUnit 4; moving to another framework
would touch every file and catch nothing new.

Applied on 2026-10-08:

- **Coverage gate.** Kover measures `:domain` and the `:app` debug unit tests in one report,
  leaving out generated code, previews, debug tools, test fixtures and Hilt modules. Line coverage
  was 93.3%, and CI now fails below 90% (`koverVerifyCoverage`). Branch coverage (about 61%) is
  reported but not gated, because kotlinx.serialization generates branches in DTOs and routes
  that no test reaches. Rules are in the [style guide](../STYLE_GUIDE.md), section Tests.
- **Manifest policy.** `ManifestPolicyTest` checks the merged manifest: an allowlist of
  permissions, only `MainActivity` of ours exported, every exported library component guarded by
  a permission, no exported provider, Android backup and cleartext traffic off.
- **Release smoke test.** `ReleaseSmokeTest` in `:benchmark` drives the shrunk build with
  UiAutomator 2.4: add a book by hand, set pages, update progress, delete it, and look up a real
  ISBN online. It passed three runs in a row on a clean install on an API 37 emulator. This covers
  the automated part of S1; Drive backup and restore stay a manual check.

### T1. Test `GoogleAuthManager` through a seam over the authorization client

`data/backup/GoogleAuthManager.kt` has no coverage (42 lines, 18 branches), although it holds the
connected, expired and signed-out state the Backup screen shows. `Identity.getAuthorizationClient`
cannot run under Robolectric.

Change: put the few calls it makes (authorize, read the result from the consent intent, revoke)
behind a small internal interface with a Play services implementation, and test the manager
against a fake: consent granted, consent cancelled, silent token, grant gone (marked expired),
sign-out while offline. Do it with S7, which changes the same class.

### T2. Move Robolectric closer to the target SDK

`app/src/test/resources/robolectric.properties` runs the JVM tests as API 35, while the app
targets 37. Behaviour that changed in 36 and 37 is not exercised. Robolectric 4.16 and later can
simulate 36 but need JDK 21 for it, so the tests' toolchain would move to 21 while the app keeps
compiling for Java 17. Move to 37 when a Robolectric release supports it.

### T3. Run search, filters and migrations on a device's SQLite

Under Robolectric, Room runs on the computer's SQLite, which is newer than the SQLite on the
oldest supported phones (Android 8). The FTS4 search and the filter SQL are the queries most likely
to differ. Add a small instrumented test in `app/src/androidTest` for search and the filters, run
on an API 26 emulator. When the schema reaches version 2 (A3), add `MigrationTestHelper` tests for
every migration in the same place.

### T4. Stable selectors for UI tests; move the benchmark to the new API

`ReleaseSmokeTest` and `LibraryBenchmark` find elements by English text and content descriptions,
so a copy change or a translation breaks them. `LibraryBenchmark` also uses the older UiAutomator
API (`UiDevice`, `By`, `Until`) and `Thread.sleep`.

Change: turn on `testTagsAsResourceId` at the root of the Compose tree, give the elements the
tests use a `testTag`, and match on `viewIdResourceName`. Move `LibraryBenchmark` to
`uiAutomator { }` with `onElement` and `waitForStableInActiveWindow()`.

### T5. Run the release smoke test in CI on demand

The smoke test needs an emulator. Add a workflow that runs on `workflow_dispatch` and before a
release (or nightly), using a pinned emulator action, and uploads the test report on failure. Keep
it out of the pull request gate: the online lookup depends on Google Books and Open Library.

### T6. Compose tests for the untested UI branches

The largest uncovered UI code is in `BackupScreen.kt` (32 lines), `TitleSearchScreen.kt`,
`CoverSection.kt` (camera permission denied, no camera) and `ScanScreen.kt`. Add Compose UI tests
for those branches, and run the library, detail and backup screens at 200% font scale (U5).
`CameraPreview.kt` and `MlKitBarcodeScanner` need a camera and stay covered by the fake scanner
and the manual scan check.

### T7. Screenshot tests for both themes and large fonts

The palette contrast is unit-tested, but nothing catches a layout that breaks visually. Add
screenshot tests (Roborazzi runs them on the JVM) for the main screens in light and dark themes
and at 200% font scale, and record the reference images in the repository.

## 8. Security pass

A full security pass on 2026-10-08 (commit `57bef05`). It read code and configuration and ran
AgentShield; it did not build or run the app, run a dependency vulnerability scan, or check the
Google Cloud console. Severity is for this app's threat model: one user's reading library, on
their own phone, backed up to their own Drive.

Verdict: no critical or high finding. The attack surface is small: one exported activity, no
exported provider, service or receiver of its own, no WebView, no dynamic code loading, no
logging, no SQL built from user text, and one OAuth scope. What is left is hardening. The only
Medium findings are S4 (backups are plain zips in Drive) and S10.

### What was checked

| Area | How |
|------|-----|
| Secrets in files | `.gitignore`, `keystore.properties.example`, `local.properties` (values hidden), build scripts |
| Secrets in history | `git log --all -p` (100 commits) searched for key, token and password patterns, Google API keys (`AIza...`), private key headers and GitHub tokens; file names searched for keystores, `google-services.json`, `.env` and `.pem` |
| Manifest and resources | `AndroidManifest.xml`, `backup_rules.xml`, `data_extraction_rules.xml`, `file_paths.xml` |
| Network | OkHttp and Retrofit setup, API key handling, URL handling, redirects, timeouts, size limits |
| Auth | `GoogleAuthManager`, the Drive client, token handling, the upload address check |
| Storage | Room setup, SQL building, DataStore, cover files, the camera cache, backup and restore |
| Build and supply chain | Gradle files, version catalog, repositories, wrapper, signing, R8, Dependabot, CI |
| Developer tooling | Git hooks, scripts, `.claude/settings.json` and its hooks; AgentShield |

### Secrets

Clean. No API key, token, password or private key in any tracked file or in any commit on any
ref, and no keystore, `google-services.json`, `.env` or `.pem` file was ever committed.
`keystore.properties.example` holds only `change-me` placeholders. `local.properties` (Books API
key) and `keystore.properties` (signing passwords) exist locally and are ignored by git; the
upload key lives in `~/.keys`, outside the repository. The Books API key is compiled into
`BuildConfig` and readable from any APK, as [SECURITY.md](../../SECURITY.md) documents; its safety
rests on S3.

To keep it this way, consider a secret scanner on pull requests (gitleaks, or GitHub's push
protection, which is free for public repositories).

### Earlier items rechecked

- **S3:** the API restriction and quota cap live in the Cloud console and cannot be checked from
  the repository. Confirm once, and again whenever the key is rotated.
- **S4:** encrypted backups done on 2026-10-09 (see S4). With a passphrase set, Drive holds only
  ciphertext. Encryption is opt-in, so a user who never sets a passphrase still has a readable
  zip in Drive. The app lock is still open.
- **S5:** all parts still open at the time. At target SDK 37 cleartext is already off and
  `ManifestPolicyTest` checks it, so the network security config is documentation of intent.
  Done on 2026-10-09 except dependency verification (see S5).
- **S6, S7, S8, S9:** still open at the time. S8 confirmed: nothing deleted `cache/camera/`.
  All four done on 2026-10-09.

### S10. Pin `upload-artifact` by commit SHA (Medium)

`.github/workflows/ci.yml`, lines 41, 76, 103 and 111, use `actions/upload-artifact@v4`. Every
other action is pinned by full commit SHA (section 1). A tag can be moved: if the action's
repository were compromised, a moved `v4` tag would run new code in every job, next to the
checked-out code and the Gradle cache. The jobs have read-only permissions and no secrets, which
limits the damage, but the cache and the uploaded reports could still be poisoned.

Change: pin all four to the SHA of the current v4 release, with the version in a trailing comment
like the other steps. Dependabot keeps SHA pins current.

Done when: every `uses:` line in `.github/workflows/ci.yml` carries a 40-character SHA.

### S11. Accept only https cover URLs from the book services

`GoogleBooksMapper.kt` (`secureCover`, line 58) turns a leading `http://` into `https://` and
passes every other value through. The confirm screen hands remote cover URLs straight to Coil
(`CoverSection.kt`, `RemoteCoverOptions` and `previewModel`), and Coil also loads `file:`,
`content:` and `android.resource:` URIs. A response carrying such a value would make the app read
and show a local file or another app's content as a cover. It needs a compromised or impersonated
service, and display is all it does: `FileCoverStore.saveFromUrl` goes through OkHttp, which
refuses any scheme but http and https.

Change: in both mappers, parse the URL with `toHttpUrlOrNull()`, upgrade http to https, and drop
anything that does not end up as https. Add mapper tests with `file:///`, `content://` and
`javascript:` values.

### S12. Filter dependency repositories by group

`settings.gradle.kts` filters `google()` by group for plugins (lines 3 to 10) but not for
dependencies (lines 18 and 19), where `google()` and `mavenCentral()` are both searched for every
artifact. A package published to Maven Central under a Google group name would be a candidate
wherever Google's repository does not answer first. Dependency verification (S5) closes most of
this; filtering keeps resolution honest on its own.

Change: give `google()` in `dependencyResolutionManagement` the same `content { }` block as in
`pluginManagement`, and use `exclusiveContent` where a group must come from one place only.

Status (2026-10-09): done. AndroidX, `com.android.*`, `com.google.android.*`, ML Kit and Firebase
come only from Google's repository (`exclusiveContent`); Google's repository is asked for no
group outside `com.google.*`, and everything else comes from Maven Central. Every configuration
of the three modules resolved with `--refresh-dependencies`.

### S13. Keep coding agents away from local secrets

AgentShield reported that `.claude/settings.json` has no `permissions` block. Most of its advice
is generic, but one part matters: `local.properties` holds the Books API key and
`keystore.properties` the signing passwords, and nothing stops a coding agent from reading them
and sending them to its provider.

Change: add deny rules, for example:

```json
{
  "permissions": {
    "deny": [
      "Read(./local.properties)",
      "Read(./keystore.properties)",
      "Read(**/*.jks)",
      "Read(**/*.keystore)",
      "Read(~/.keys/**)"
    ]
  }
}
```

`Read` rules do not cover shell commands such as `cat`, so add matching `Bash` deny rules, or
accept that limit and say so in the file.

### S14. Check a restored database before reading it

`RoomLibraryRestorer.readRows` (line 128) opens the backup's `library.db` with Room and reads
every row. Room checks the identity hash, but the file is otherwise trusted: it could hold extra
triggers or views, or be damaged in ways that make SQLite misbehave. The book count check catches
a file Room silently replaced with an empty one, not a file with odd schema objects. A backup only
arrives from the user's own `appDataFolder`, which only this OAuth client can write, so this is
defence in depth.

Change, before `readAll()`:

- Run `PRAGMA integrity_check` (or `quick_check`) and treat anything but `ok` as `CorruptBackup`.
- Compare `sqlite_master` with the schema Room exports (`app/schemas/`), and reject a file with
  any table, view, index or trigger the schema does not have.
- Do it in the same change as S4, which touches this class.

Status (2026-10-09): done. Before Room opens the unpacked file, `PRAGMA integrity_check` must
answer `ok`. It runs read-write, because SQLite can only check an FTS4 index with write access,
and the file is the restore's own copy. After Room has opened (and, later, migrated) it, the
`sqlite_master` rows (type, name, table and SQL) must equal those of a fresh in-memory database
of the current schema. That compares against what Room creates rather than the exported JSON,
which is not in the APK. Any other result is `CorruptBackup`; a test adds a trigger, a view, a
table and an index in turn.

### S15. Keep the account email apart from library settings

`GoogleAuthManager` stores the Drive account email (`drive_account`, line 149) in the same
DataStore file (`library`, `di/PreferencesModule.kt`) as the view mode and other library
settings. It is sandboxed and excluded from Android backup, so it is not exposed today, but U7
opens device transfer for library settings, and with one shared file the email and Drive state
would travel too.

Change: give the Drive and backup state its own DataStore file before U7, and exclude it from
device transfer. Consider dropping the email: the screen already falls back to "Connected".

Status (2026-10-09): done. `GoogleAuthManager` and `DataStoreBackupPreferences` use the
`backup_state` DataStore file (qualifier `BackupStateStore`); the `library` file keeps only
library settings. A DataStore migration moves the `drive_*` and `backup_*` keys out of `library`
on first read, keeping any value already in the new file. The email stays, for display. Android
backup and device transfer still exclude every file; U7 can now open transfer for `library`
alone.

### S16. Make debug-key release builds an explicit choice

`app/build.gradle.kts` (line 69) signs release builds with the debug key when
`keystore.properties` is missing and only logs a warning. Play rejects such a build, so the risk
is an APK shared another way (a sideload, a GitHub release) that anyone could replace with their
own build signed by the well-known debug key.

Change: require an opt-in for the fallback, such as `-Potium.allowDebugSigning=true` passed by
CI, and fail the build without it.

Status (2026-10-09): done. It had been marked done in the table while the build still fell back
with only a warning. Now, without `keystore.properties` and without the flag, release has no
signing config and `packageRelease` and `bundleRelease` fail with a message naming both options.
Lint, tests and the debug and benchmark builds are unaffected. CI and the README pass the flag.
Checked in a copy of the repository without the key file.

### S17. Pass workflow values to CI scripts through the environment

The "Documentation impact" step (`.github/workflows/ci.yml`, lines 51 and 53) puts
`${{ github.base_ref }}` and `${{ github.event.before }}` straight into the shell script. Neither
can be set by an outside contributor today (the base branch must exist in this repository, and
`before` is a commit SHA), so it is not exploitable. It is the pattern GitHub warns against, and
the next value added the same way may not be safe.

Change: set them as `env:` on the step and use `"$BASE_REF"` and `"$BEFORE"` in the script.

### S18. Escape `BuildConfig` string values

`app/build.gradle.kts` (lines 49 and 50) writes `books.api.key` and `openlibrary.contact` into
Java source as `"\"$value\""`. A value containing `"` or `\` breaks the build or adds code to
`BuildConfig`. The values come from the developer's own `local.properties`, so this is a
robustness fix: escape `\` and `"`, or reject values outside an expected character set.

### S19. Lower the backup unpack limit

`BackupZip` stops unpacking at 2 GB and 50,000 entries (line 19). That stops a zip bomb, but 2 GB
in the cache can still push a phone into low storage, and a 5,000-book library with covers is far
below 1 GB. Lower the default (for example to 512 MB) or derive it from the manifest's book count.

### S20. Use a portable path in `.claude/settings.json`

The two `PreToolUse` hooks call `/Users/ehoxhaj/.local/bin/graphify`. That puts the maintainer's
macOS user name in a public repository, and on any other machine the hooks point at nothing. Use
`graphify` from `PATH`, as `graphify-refresh.sh` already does, or move these hooks to
`.claude/settings.local.json`, which git ignores.

### S21. Register the theme catalog route only in debug builds

`ui/navigation/OtiumNavHost.kt` (line 64) registers `CatalogRoute` in every build. The menu entry
is hidden in release (`DebugTools.isAvailable`) and the activity has no deep links, so the screen
cannot be reached today. Registering the route only when debug tools are available keeps it that
way once deep links or shortcuts arrive (U8, U9).

Status (2026-10-09): already done in the code, but the table still said Open. `OtiumNavHost`
registers `CatalogRoute` only when `BuildConfig.DEBUG` is true, so release and benchmark builds
have no such destination.

### S22. Scan dependencies for known vulnerabilities

CI runs no vulnerability scan. Dependabot alerts and security updates are on (checked through the
GitHub API on 2026-10-08), and `.github/dependabot.yml` already covers Gradle and GitHub Actions,
but the dependency graph they read held no Gradle library: GitHub cannot read version catalogs or
transitive dependencies from the build files, so the alerts were blind to the whole Gradle tree.
GitHub's dependency review action is not an option while the repository is private without
Advanced Security (its API answers 403); the same goes for uploading SARIF to code scanning.

Change:

- `.github/workflows/dependency-submission.yml` submits the resolved Gradle tree, transitive
  dependencies and plugins included, on every push to `main` and `develop`, so Dependabot alerts
  and security updates cover it.
- CI gates every pull request and push with OSV-Scanner on a CycloneDX SBOM of the release
  runtime classpath (`scripts/scan-dependencies.sh`, `./gradlew scanDependencies`). The pinned
  binary is checked by SHA256, like gitleaks; findings fail the job and are kept as a SARIF
  artifact. Accepted findings go in `osv-scanner.toml` with a reason and an expiry date.
- If the repository goes public, the dependency review action becomes free and can be added on
  pull requests; the dependency submission keeps feeding it.

### AgentShield results

`npx ecc-agentshield scan --path .` graded the agent configuration B (76 of 100): 2 critical,
4 high, 7 medium and 1 low. Thirteen are "missing prompt defense" checks on `CLAUDE.md`
(instruction override, data leakage, role escape and so on). Those are meant for a system prompt
facing untrusted users; `CLAUDE.md` is developer guidance for an agent the maintainer runs, so
they do not apply and need no change. The redirect to `/dev/null` it flagged in
`.claude/hooks/graphify-refresh.sh` (line 22) is deliberate and documented in the script. The
missing permissions block is valid and is S13.

### Checked and found sound

So the next review does not repeat the work:

- **Components:** only `MainActivity` is exported. Both providers are not exported, and the
  FileProvider shares only `cache/camera/`. The notification's `PendingIntent` is immutable.
- **Backup at rest:** `allowBackup="false"`, every domain excluded from cloud backup and device
  transfer.
- **SQL:** the library query binds all user text, and FTS input is reduced to letters and digits.
  Every other query is a Room `@Query`.
- **Network:** every base URL is https. The API key goes only to Google Books, on its own client;
  Drive and cover downloads use the client without it. OkHttp drops `Authorization` on redirects
  to another host. Timeouts everywhere; cover downloads capped at 5 MB.
- **Drive:** upload address checked on the parsed host (https on `googleapis.com`), uploads and
  downloads checked by MD5, `drive.appdata` scope only, revoked on sign-out.
- **Backup zip:** entry names allowlisted, paths checked against the target folder, entry count,
  unpacked bytes and duplicate names limited; a failed restore leaves the library as it was.
- **Covers:** deletion limited to direct children of the covers directory; photos come through
  the Photo Picker and a FileProvider, so no storage permission; device pictures decoded with a
  sample size, so a huge image cannot exhaust memory.
- **Errors and logs:** user-facing text comes from string resources only; exception messages
  never reach the screen or a notification. No `Log` calls.
- **Release build:** R8 with resource shrinking; debug seeding lives only in the debug source
  set, and release binds a no-op.
- **CI:** read-only `contents`, `persist-credentials: false`, no secrets, no
  `pull_request_target`.
- **Kotlin:** no `!!` in production code, coroutine cancellation re-thrown in the Drive client,
  remote JSON decoded field by field so one malformed field costs only that field.

## 9. Applying these changes

- Follow the [style guide](../STYLE_GUIDE.md): run `./gradlew spotlessApply detekt` before each
  commit, and `./gradlew lint` when touching resources or the manifest.
- Commit one finding per commit with a Conventional Commits message, for example
  `fix(backup): check the upload session address`.
- A change that alters behavior updates its document in the same commit: the design doc for S4,
  A1 and A3, the roadmap for S4, and [SECURITY.md](../../SECURITY.md) for S3.
- Mark a finding done in the table in section 2 as it lands, and remove this document or mark it
  superseded once the table is clear.
