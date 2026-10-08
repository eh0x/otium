# Architecture, security and UX review: findings to apply

Date: 2026-10-07, second pass 2026-10-08
Status: In progress; see the Status column in section 2
Related documents: [app design](../design/2026-10-06-otium-library-app-design.md),
[implementation plan](2026-10-06-otium-implementation-plan.md), [roadmap](../ROADMAP.md),
[style guide](../STYLE_GUIDE.md)

This is the backlog from a review of the codebase on `develop` (commit `e6e65ed`) against Android
Clean Architecture practice, security guidance and UI/UX guidance. It is for whoever applies the
changes later. Each finding names the files involved, the change to make and how to tell it is
done. Findings are grouped by area and ranked within each.

The review read code and documents only. The release variant was not built and the app was not
run, so findings marked _unverified_ need a check before work starts.

A second pass on 2026-10-08 (commit `024487e`) added the findings in section 6. That pass also
read the merged release manifest and the release APK, which by then CI built (S1).

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
  `drive.appdata` scope only, and cover URLs forced to https in `GoogleBooksMapper.kt`.
- **Supply chain:** CI actions are pinned by commit SHA with read-only permissions, and Dependabot
  is on.
- **Accessibility:** Palette contrast is unit-tested, reduced motion is respected and screens use
  semantics widely.

## 2. Priority summary

| ID | Area | Change | Size | Status |
|----|------|--------|------|--------|
| S1 | Security | Build and test the release variant in CI | M | CI part done; device check open |
| S2 | Security | Validate the Drive upload session URL | S | Done |
| S3 | Security | Restrict the Google Books API key | S | Documented; Cloud console steps open |
| S4 | Security | Encrypted backups, then app lock; defer SQLCipher | L | Open |
| S5 | Security | Network security config, manifest cap, dependency verification | S | Open |
| S6 | Security | Store cover file names, not absolute paths | M | Open |
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
| S7 | Security | Recover from a revoked Drive token without a stale screen | S | Open |
| S8 | Security | Delete cover photos left in the camera cache | S | Open |
| B1 | Build | Build an app bundle in CI and keep it with the R8 mapping | S | Open |
| S9 | Security | Pin the Gradle distribution checksum | S | Open |
| B2 | Build | Build the release variant in a parallel CI job | S | Open |
| U8 | Feature | Launcher shortcuts for adding books | S | Open |
| U9 | Feature | Add a book shared from another app | M | Open |
| P1 | Platform | Set the keyboard resize mode on the activity | S | Open |
| P2 | Platform | Give the themed icon its own monochrome drawable | S | Open |
| B3 | Build | StrictMode in debug builds; try the configuration cache | S | Open |

Sizes: S is under a day, M is one to three days, L is a phase of its own.

Suggested first batch: S1, S2, S3, U1 and U2. They are small and worth doing before first release.

Suggested second batch: U7, S7, S8, B1 and S9. U7 closes a data loss path; the rest are small.
Do U8 and U9 together with the Settings screen (U2).

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

### S5. Small hardening items

- Add `res/xml/network_security_config.xml` with cleartext traffic off and reference it from the
  manifest. The default already blocks cleartext at the current target SDK; this makes the intent
  explicit and lintable. Do not pin certificates, because Google rotates them.
- In `data/backup/archive/BackupZip.kt`, `text()` reads an entry with `readBytes()` and no limit.
  Cap the manifest and reject anything larger than a few kilobytes as `CorruptBackup`.
- Add Gradle dependency verification (`gradle/verification-metadata.xml`, generated with
  `./gradlew --write-verification-metadata sha256`) and keep it current when Dependabot bumps.
- Optional: run `PRAGMA integrity_check` on the extracted `library.db` before importing it.

### S6. Store cover file names, not absolute paths

`FileCoverStore.store` returns `finished.absolutePath` and the database keeps it as `coverPath`.
`RoomLibraryRestorer` has to rewrite these paths on restore, which shows the fragility: any move
of `filesDir` breaks every cover.

Change: store the file name, resolve it against `coversDirectory(context)` at read time (in the
mapper or a small resolver), and add a migration that strips the directory from existing rows.
Do this together with A3 so it ships in the first schema.

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

### S8. Delete cover photos left in the camera cache

`ui/add/confirm/CoverSection.kt` has the camera write `cache/camera/cover-<nanos>.jpg`. When the
book is saved the cover store copies the photo, and when the user backs out it is not used, but
no code was found that deletes the file in `cache/camera/`, so it stays until Android trims the
cache. These are the user's own photos.

Change: delete the camera file once the cover store has copied it or the user has cancelled, and
empty `cache/camera/` when the app starts, for files left by a process that died.

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

## 7. Applying these changes

- Follow the [style guide](../STYLE_GUIDE.md): run `./gradlew spotlessApply detekt` before each
  commit, and `./gradlew lint` when touching resources or the manifest.
- Commit one finding per commit with a Conventional Commits message, for example
  `fix(backup): check the upload session address`.
- A change that alters behavior updates its document in the same commit: the design doc for S4,
  A1 and A3, the roadmap for S4, and [SECURITY.md](../../SECURITY.md) for S3.
- Mark a finding done in the table in section 2 as it lands, and remove this document or mark it
  superseded once the table is clear.
