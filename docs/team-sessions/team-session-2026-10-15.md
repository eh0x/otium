# Team session: Marginalia, notes and scanned quotes

Date: 2026-10-15
Format: four-role review (product, architecture, development, QA), each written independently
from the same brief, followed by a synthesis.
Related documents: [project context](../../PROJECT-CONTEXT.md),
[earlier session](team-session-2026-10-10.md),
[design document](../design/2026-10-06-otium-library-app-design.md),
[style guide](../STYLE_GUIDE.md)

## Contents

- [Brief](#brief)
- [Product](#product)
- [Architecture](#architecture)
- [Development](#development)
- [QA](#qa)
- [Synthesis](#synthesis)
- [Open decisions](#open-decisions)
- [Decisions taken](#decisions-taken)

## Brief

Idea 6, "Marginalia": free-text notes and quotes on a book, each with a page number, and a "Scan
text" action that photographs a page, runs on-device text recognition and lets the owner tap the
lines to keep. It is ranked third for v2. Competing apps sell quote scanning.

Notes need a Room migration and bring back decision D29: notes are the trigger D29 named for
revisiting SQLCipher, to be decided with the app lock merged. A private 1 to 5 mark could ride
along as a note field, not as a public rating. The effort estimate is L. Quotes can be long
copyrighted text; they stay in the owner's private database and backup and are not shared. Text
recognition on curved pages is imperfect, so the owner always edits before saving.

State at the time: version 1.1.0 is released on GitHub and not yet on Play. The database is at
schema version 2 (series), and the Otium JSON export is at version 1.

## Product

First reaction: readers will feel this feature. It is also the first user-authored content in
Otium, and D29 is still open. Treat it as a staged bet, not one epic.

Concerns:

- D29 gates everything. Decide SQLCipher first, with the app lock merged. If SQLCipher is
  declined, record why and what users are told, because the backup passphrase is optional and
  notes could sit in Drive in plain form.
- The scope is two features. Typed notes and quotes with page numbers are the core value and
  cheap. Scan (camera, recognition, line selection, editing) is the expensive part. Ship notes
  first and scan second, so scan can slip without blocking.
- Done must include the backup round-trip (create notes, back up, restore on a clean install,
  see identical notes), edit and delete behaviour, searchable notes, and a scan flow that works
  at 200% text with no camera-only path.
- The private mark needs a clear "never shown or shared" rule, or it goes. It risks reading as a
  rating, which conflicts with the calm design. Cut it from v2.
- Copyright is a lesser concern while quotes stay private. Say so in the privacy copy and add no
  share or export-as-image feature.

First action: a one-page D29 decision memo (SQLCipher now, SQLCipher deferred with the risk
accepted, or app lock only), with a cost estimate and the backup impact. In parallel, write the
acceptance criteria for notes.

Question: if SQLCipher adds migration risk to existing users' libraries, do we hold notes until
after the Play launch, or ship them on D29's current terms?

## Architecture

First reaction: cheap in code, expensive in consequences. Notes turn the database from catalogue
metadata into private reading thoughts, which is what makes D29 real. Recognition is the easy
part.

Concerns:

- The encryption decision gates everything. SQLCipher would change the database open path, the
  Room factory, the migration to version 3 (a plaintext database has to be re-keyed), FTS4
  behaviour, APK size and the benchmark module. The key would sit in the Keystore, so a device
  without a screen lock gains little. The alternative is file-based encryption plus the app lock,
  documented as the choice. Record either one as an ADR before any schema work.
- The backup and export contract. Notes belong in the Drive backup, and the passphrase encryption
  is then the real protection for them. Adding notes means the JSON export moves to version 2
  with a defined reader for version 1 files. The restore round-trip in the release gate must
  cover notes, including long quotes, and the cascade delete needs a migration test.
- Scope and calm. Keep the private mark out of the first version, or prove it cannot reach any
  shared or sorting surface. The scan flow stores no image (process and discard the bitmap),
  requires an edit step and works offline. Index notes in FTS only if the encryption decision
  allows it. Tap-to-select needs large targets, a non-gesture alternative and 200% text.

First action: write the D29 ADR with a threat model (lost device, malicious backup reader, rooted
device), SQLCipher against file-based encryption plus the app lock, and the cost of each. Spike
a SQLCipher open and re-key on a copy of a real version 1.1 database.

Question: is a passphrase-encrypted backup enough of a promise for private notes, or must the app
say clearly that unencrypted Drive backups now contain quotes?

## Development

First reaction: two features under one name. Typed notes are a small table plus a screen. Scan is
a capture, a new text-recognition dependency, a line-selection screen and an edit step. The L
estimate comes mostly from scan and from D29.

Concerns:

- D29 is a gate, not a detail. SQLCipher adds native libraries (APK size, 16 KB page alignment)
  and a one-way migration from plaintext, where a failed re-key loses a whole library. FTS4 and
  the Tink/Argon2id backup layer must still work. The cheaper alternative, app-private storage
  plus the app lock without database encryption, is defensible: notes are only slightly more
  sensitive than reading status. Ask what threat SQLCipher covers that encrypted backups do not.
- Schema: a table keyed to the book with a cascading foreign key, a nullable page (roman
  numerals and ranges exist, so an integer may not do), a kind field (note or quote),
  timestamps, and the mark as its own nullable column. It needs CSV and JSON import and export,
  backup coverage, an FTS decision and a MigrationTestHelper test.
- Recognition quality. Curved pages, glare and low light give bad lines, so the output is a draft
  that always goes to an editable field. Latin script only in version 1. Handle camera
  permission denial. Line tapping must work with TalkBack and at 200% text.

First action: a time-boxed D29 record with a spike on APK size and migration risk. In parallel,
build notes only (entity, migration with test, DAO, book detail UI, export and import, backup
round-trip), then scan as a separate issue.

Question: is SQLCipher actually required, given that backups can be encrypted and the app lock is
merged? If it is only nice to have, defer it and avoid the biggest risk.

## QA

First reaction: the first long, sensitive, user-authored text. Recognition is the easy part to
test; the backup round-trip and the migration are where data gets lost.

Concerns:

- Decide D29 before any schema work. Moving to SQLCipher changes how every migration test runs,
  how FTS4 behaves and what the Drive backup contains, and it must work with passphrase-encrypted
  backups, where restore is all or nothing. It needs an acceptance test: an existing version 1.1
  plain database upgrades to the new format without losing data. Deciding on no encryption is also
  valid, if it is written down and the app lock is merged.
- Migration 2 to 3 and backup compatibility. Cover: a Room migration test with notes present,
  cascade delete of a book's notes, a version 2 backup restored into the version 3 app and the
  reverse (an older app reading a newer backup), whether the JSON export version bumps, the Kover
  floor, and the CSV formula-injection fix applied to free-text quotes.
- Failure modes of scan and editing: camera permission denied, an empty or garbage result,
  process death mid-edit, rotation, 200% text, TalkBack on tap-to-select (needs a non-visual
  alternative). Validate page numbers (blank, "xii", very large). Give quotes a length limit or
  tested paging.

First action: write the D29 record with the app lock dependency, then the failing acceptance tests
for the migration and the backup round-trip, and add them to the release gate before any UI work.

Question: if the mark ships as a note field, how do we guarantee it never reaches CSV or JSON
export or any share path? Do exports include notes by default?

## Synthesis

- **D29 comes first.** All four roles put the encryption decision before any schema work. Write
  the ADR with a threat model.
- **Tension on SQLCipher.** Architecture and QA lean toward a spike and a full record.
  Development questions whether it is needed, since the app lock exists and backups can be
  passphrase-encrypted. Product wants "decline and say why" kept as a real option. Nobody showed
  what threat SQLCipher covers that the app lock plus an encrypted backup does not; the ADR has
  to answer that.
- **Notes first, scan second.** Typed notes (M1) ship before scan (M2), and scan can slip without
  blocking.
- **The private mark.** Product, Architecture and QA want it out of v2 or a hard guarantee that
  it never reaches exports. If it stays, it is a separate nullable column.
- **Backup is the real risk.** Unencrypted Drive backups would now contain private quotes. The
  round-trip gate must cover notes in both directions across versions, and the JSON export
  version and whether exports include notes need a decision.
- **Factual conflict, unresolved.** Architecture says ML Kit text recognition is already in the
  stack; Development says it is a new dependency. The barcode scanner is a different ML Kit
  artifact, so Development is probably right. Check before costing it.

## Open decisions

1. D29: SQLCipher now, SQLCipher deferred, or app lock only, with the threat each covers.
2. Hold notes until after the Play launch, or ship them on D29's current terms.
3. Keep or cut the private 1 to 5 mark; if kept, how it is kept out of exports.
4. JSON export version 2, and whether exports and CSV include notes by default.
5. Whether text recognition is a new dependency, and what it adds to the APK.
6. Page number type: integer, or text for roman numerals and ranges.

## Decisions taken

Taken on 2026-10-15 after the plan for this session, recorded as D31 in the
[design document](../design/2026-10-06-otium-library-app-design.md):

1. D29 stands: notes live in the same unencrypted database, and SQLCipher is not added. The one
   new risk, an unencrypted Drive backup holding quotes, is met by suggesting a passphrase before
   the first backup.
2. The app lock is merged first, through its own pull requests, and notes are released with or
   after it.
3. The private 1 to 5 mark is not built.
4. A note's page is an optional integer, like reading progress.
5. Notes are searchable per book first; a library-wide note search can follow.
6. Notes ship in a normal minor release, not held for the Play launch. Typed notes come first and
   scan second, as separate pull requests.
7. Text recognition is a new dependency (`com.google.mlkit:text-recognition`); only barcode
   scanning is in the stack today.
