# Team session: Otium v1 status, release readiness and v2

Date: 2026-10-10
Format: four-role review (product, architecture, development, QA), each written independently
from the same brief, followed by a synthesis.
Related documents: [project context](../../PROJECT-CONTEXT.md),
[implementation plan](../plans/2026-10-06-otium-implementation-plan.md),
[security policy](../../SECURITY.md), [style guide](../STYLE_GUIDE.md)

## Contents

- [Brief](#brief)
- [State at the time of the session](#state-at-the-time-of-the-session)
- [Corrections to the role reviews](#corrections-to-the-role-reviews)
- [Product](#product)
- [Architecture](#architecture)
- [Development](#development)
- [QA](#qa)
- [Synthesis](#synthesis)
- [Proposed next steps](#proposed-next-steps)
- [Open decisions](#open-decisions)

## Brief

Review the status of Otium v1 and the next steps: open-source readiness, security, the CI/CD
setup, missing checks, release guidelines, how the app, its dependencies and its tools stay up to
date, and which v2 ideas come first.

## State at the time of the session

- **Features:** phases 0 to 6 are built and tested on the JVM (581 unit tests). Phase 7, polish
  and Play readiness, is planned. Device checks for phases 3 to 6 and the Google Cloud and OAuth
  setup for Drive are still open. A release build passed a smoke test on one real device (a Xiaomi
  phone on Android 16, 2026-10-09) after a keep rule fixed an R8 crash in ML Kit scanning.
- **Release:** no release, no tags, no `CHANGELOG.md`. `versionName` 0.1.0 and `versionCode` 1 are
  fixed in `app/build.gradle.kts`. Release signing reads a local `keystore.properties`. There is
  no release workflow.
- **CI:** six required jobs on GitHub Actions with pinned action SHAs and read-only permissions:
  formatting, detekt (findings also sent to code scanning), docs and Markdown checks; gitleaks over
  the history; OSV-Scanner on a CycloneDX SBOM; Android Lint; unit tests with a Kover floor;
  assemble debug and release. CodeQL runs on pushes, pull requests and weekly, with the
  `security-extended` queries, and covers all 206 shipped Kotlin files. The dependency graph is
  submitted to GitHub. On commit `4bbf275`, CI, CodeQL and the dependency submission all passed,
  with no open code-scanning alerts.
- **Updates:** Dependabot opens grouped weekly pull requests into `develop` for Gradle and GitHub
  Actions, with a cooldown. Major versions of Kotlin, KSP, AGP and Gradle are upgraded by hand.
- **Repository:** public, Apache 2.0. README, CONTRIBUTING, SECURITY (private vulnerability
  reporting), issue forms, a pull request template and Discussions are in place. A ruleset on
  `main` requires a pull request and the six CI checks, with no bypass; local hooks refuse commits
  and pushes to `main`. `develop` is not protected. Secret scanning is on; push protection is off.
  There is no code of conduct, no CODEOWNERS file, no milestone and no triage or priority labels.
- **v2 ideas** (from the removed roadmap): import and export (Goodreads, StoryGraph, JSON, CSV),
  batch scanning, e-book and audiobook progress, notes and quotes ("Marginalia"), lending, quiet
  retrospective statistics ("Almanac"), app lock, possibly SQLCipher.

## Corrections to the role reviews

Two claims in the reviews were checked against the repository:

- The development review says no release build has run on a real device. One has: see the Phase 7
  acceptance notes in the implementation plan. Its point stands that one device is not enough.
- The architecture review asks to confirm that Android Auto Backup and device transfer exclude the
  database. They do: `android:allowBackup="false"`, and `res/xml/data_extraction_rules.xml`
  excludes every domain from cloud backup and device transfer. The open question that remains is
  whether the database itself should be encrypted.

## Product

**First reaction.** v1 is feature-complete but has never shipped: there is no release process,
tag or changelog. The real blocker is the Drive OAuth and Cloud project setup. Backup is the
headline privacy feature, it cannot be verified in a real Play build without that setup, and
Google's OAuth verification can take weeks.

### Key concerns

- **Drive OAuth and the consent screen.** This has the longest lead time, so start it now. Scope
  review, a privacy policy URL and app verification all gate the data safety form and the Play
  listing. If verification slips, ship v1 with backup behind a flag.
- **Device checks for phases 3 to 6.** One smoke-tested device cannot sign off scanning, lookup,
  restore and passphrase encryption. A broken restore loses user data, the one failure a private
  catalogue cannot afford.
- **No repeatable release path.** Document SemVer and the version code scheme. Add a tag-triggered
  release workflow that builds a signed AAB (Play App Signing, keystore in secrets) and uploads it
  to the internal track. Generate a changelog from the Conventional Commits.

**First action.** Create a v1.0 milestone. File every Phase 7 item, the open device checks and the
OAuth setup as issues under it, with priority and triage labels. Start the Google Cloud and OAuth
verification request the same day.

**Question for the team.** If OAuth verification is not done when Phase 7 polish is, does v1.0
ship without Drive backup, or does backup define v1?

### v2 priority

1. **Import and export** (Goodreads, StoryGraph, JSON, CSV). Highest value: it brings existing
   libraries in and lets people leave freely, which fits "your data is yours".
2. **Batch scanning.** Cataloguing a shelf one book at a time is the biggest onboarding friction,
   and it reuses the existing scan pipeline.
3. **Marginalia (notes and quotes).** Deepens use quietly and stays on the device. Almanac comes
   next, only if it stays retrospective with no streaks.

A code of conduct and CODEOWNERS are cheap and belong before launch; a project board can wait.

## Architecture

**First reaction.** The architecture is ready for release; release engineering is not. CI builds a
release APK, but nothing produces a versioned, signed, attested artifact traceable to a commit.
Backups are encrypted while the Room database on the device is plaintext; that should be a written
decision, not an accident.

### Key concerns

- **No release path.** The version is fixed, signing depends on a local file, and there are no
  tags. Enrol in Play App Signing, keep the upload key in a protected GitHub Environment, and
  derive `versionCode` from the tag.
- **Schemas and formats become contracts once v1 ships.** v2's Marginalia table needs Room
  migrations. That calls for exported schemas, `MigrationTestHelper` tests, and a versioned backup
  header covering the Argon2id parameters and the schema version, plus a test that restores an
  older backup. CI has no instrumented test job to run them.
- **Plaintext database.** Write a decision record on SQLCipher. Adopting it in v2 means migrating
  existing plaintext databases, which is cheaper to plan now.

**First action.** A tag-triggered release workflow: build the AAB, sign it from secrets in the
protected Environment, attach the CycloneDX SBOM, run build provenance attestation, and upload to
the Play internal track.

**Question for the team.** Is the plaintext database an accepted risk for v1, given Android's
file-based encryption?

### Missing checks, by value

1. Gradle wrapper validation, plus Gradle dependency verification (`verification-metadata.xml`).
2. A release workflow that publishes a provenance attestation and the SBOM as release assets.
3. An instrumented test job on Gradle Managed Devices for Room migrations and backup round trips.
4. `dependency-review-action` as a required check on pull requests.
5. Secret-scanning push protection, and a weekly OpenSSF Scorecard run.

## Development

**First reaction.** The JVM suite is strong, but the minified release build is the riskiest
artifact: R8 already broke ML Kit scanning once.

### Key concerns

- **Release build on devices.** Run the minified, signed build through every phase 3 to 6 flow
  (scan, Drive, Tink, WorkManager). Move the debug seeder and theme catalog into the `debug`
  source set rather than relying on R8, and add a check that fails if they reach the release
  build.
- **Drive OAuth depends on Play App Signing.** The OAuth client needs the SHA-1 of the Play
  app-signing certificate, not the upload key's. Create the Play app, enrol in App Signing, then
  register the client; otherwise sign-in fails only in production.
- **Hardcoded version.** Fix it before the first upload: Play never accepts a `versionCode` equal
  to or lower than one it already has.

**First action.** Build `bundleRelease`, install it through bundletool, and run the full manual
checklist on a low-end API 26 device and a current one. Every crash becomes a keep rule plus a
regression test.

**Question for the team.** "No backend" still leaves a Google Cloud project to maintain for OAuth.
Does Drive ship in v1, or does v1 ship with local export and Drive later?

### Release mechanics

- **Versioning:** SemVer, with the tag (`v1.0.0`) as the single source of truth, read by Gradle
  through `git describe`.
- **Version code:** `major * 10000 + minor * 100 + patch`. Deterministic and always increasing.
  Keep room for a hotfix build number.
- **Changelog:** git-cliff over the Conventional Commits writes `CHANGELOG.md` and the release
  notes; trim the notes to 500 characters for Play's "What's new".
- **Tagging:** merge `develop` into `main` by pull request, then push an annotated tag from
  `main`. Confirm that the pre-push hook lets tag pushes through.
- **Workflow:** on `v*`, decode the upload keystore from secrets, build the signed AAB and a
  universal APK, attach `SHA256SUMS`, the CycloneDX SBOM and `mapping.txt`, and create the GitHub
  release. Keep the Play upload manual for the first two releases.
- **v2:** import and export first; it doubles as a backup. Moving progress from pages to a progress
  type and value (for e-books and audiobooks) needs a tested Room migration. Statistics and app
  lock are the cheapest of the rest.

## QA

**First reaction.** The JVM suite is strong, but release confidence rests on one phone. The single
run there found an R8 crash no JVM test could catch, so the minified build on real devices is the
biggest blind spot.

### Key concerns

- **Old and small devices.** Nothing has run on API 26 to 29, low-memory devices or small screens.
  CameraX, ML Kit, Tink keysets and WorkManager each behave differently across that range.
- **Backup and restore.** A backup has never been restored onto a fresh install. A backup that has
  never been restored is a data-loss risk, and the data is the product.
- **No beta channel or release record.** There is no closed testing track, changelog or checklist,
  so dependency updates (AGP, R8, Compose, ML Kit) can reach users without a device check.

**First action.** A CI job that builds the release variant and runs the `:benchmark` smoke test on
an emulator at API 26 and at the latest API, triggered by tags and by pull requests into `main`.

**Question for the team.** What is the oldest device or API level actually tested by hand before
each release? If none, should minSdk go up?

### Release gate

- [ ] All required checks on `main` are green, with no new high or critical findings.
- [ ] The minified release smoke test passes on emulators at API 26 and the latest API.
- [ ] One physical phone passes barcode scanning, metadata lookup and library timings.
- [ ] Drive backup, uninstall, reinstall and restore passes, with matching book counts.
- [ ] The migration test is green, and the schema JSON is committed for any database version bump.
- [ ] TalkBack and 200% text pass on the main flows.
- [ ] `CHANGELOG.md` and `versionCode` are updated, and `mapping.txt` is archived.
- [ ] The build spends at least 48 hours on the closed testing track with no crashes before
  promotion.

## Synthesis

- **Blocking, raised by all four roles: there is no repeatable release path.** The version should
  come from the tag; a tag-triggered workflow should build and sign the AAB and attach checksums,
  the SBOM and provenance; `CHANGELOG.md` should come from the Conventional Commits.
- **Blocking, raised by product, development and QA: Drive backup cannot be verified.** The order
  is fixed: Play app, App Signing, OAuth client with the Play certificate's SHA-1, then the
  verification request. Google's review takes weeks, so it starts first.
- **Blocking, raised by architecture, development and QA: device coverage is thin.** An emulator
  job for the release build at API 26 and the latest API, plus a real restore round trip, protects
  user data more than any other check.
- **Tension, product against development: does Drive backup define v1?** Product treats backup as
  the headline feature; development would ship v1 with local export and add Drive later. This is a
  product decision and is listed under [open decisions](#open-decisions).
- **Tension, QA against the others: the weight of the release gate.** A 48-hour closed track and a
  physical-device pass for every release is heavy for one maintainer. A middle path: the full gate
  for minor and major releases; the automated gate and the closed track for patch releases.
- **Agreed small fixes:** secret-scanning push protection, a code of conduct and CODEOWNERS,
  priority and triage labels with a v1.0 milestone, `dependency-review-action`, Gradle wrapper
  validation, and a lighter ruleset for `develop`.

## Proposed next steps

Ordered by lead time first, then by risk reduced.

1. **Start the Drive OAuth path.** Create the Play app, enrol in Play App Signing, register the
   OAuth client with the Play certificate's SHA-1, and submit the consent screen for verification.
2. **Quick wins in the repository.** Turn on push protection; add `CODE_OF_CONDUCT.md` and
   CODEOWNERS; create priority and triage labels and a v1.0 milestone, and file Phase 7, the device
   checks and the OAuth setup as issues; add `dependency-review-action` and Gradle wrapper
   validation; protect `develop`.
3. **Release track.** Derive the version from the tag; adopt the version code scheme; add git-cliff
   and `CHANGELOG.md`; add the tag-triggered release workflow with signed AAB, universal APK,
   checksums, SBOM, provenance attestation and `mapping.txt`; document the release process and the
   release gate in `CONTRIBUTING.md` or a release guide.
4. **Device coverage.** Add an emulator job (API 26 and the latest API) for the release smoke test
   and Room migration tests; run the restore round trip on a fresh install.
5. **Phase 7.** Accessibility pass on device, licences screen and privacy policy page, Play listing
   and data safety form, baseline profile, and moving debug-only code into the `debug` source set.
6. **Before v2.** Write decision records on SQLCipher and on the backup format version; plan the
   progress-model migration.
7. **v2, in order.** Import and export, batch scanning, Marginalia, then Almanac.

## Open decisions

| # | Decision | Options | Raised by |
|---|----------|---------|-----------|
| 1 | Does v1 ship with Drive backup? | Wait for OAuth verification; ship v1 with local export and add Drive in 1.1; ship with backup behind a flag | Product, development |
| 2 | Is the plaintext Room database accepted for v1? | Accept with file-based encryption and record it; adopt SQLCipher before v1; adopt it in v2 with a migration | Architecture |
| 3 | What is the release gate for each kind of release? | Full gate every time; full gate for minor and major, lighter for patches | QA |
| 4 | What is the oldest device tested before release? | Emulator at API 26 only; a physical old device; raise minSdk | QA |
| 5 | When does the Play upload become automated? | From the first release; after two manual releases | Development, architecture |
