# Changelog

All notable changes to Otium are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), with the Security group first, and
versions follow [Semantic Versioning](https://semver.org/) as described in
[docs/RELEASING.md](docs/RELEASING.md).

## [Unreleased]

### Added

- Notes and quotes on a book, with an optional page number. Add, edit and delete them from the
  book's page; a book with six or more notes can be searched. Notes stay on the phone and are
  part of your Drive backup, so set a backup passphrase if you want them encrypted there.

## [1.1.0] - 2026-10-15

Known issues: Otium is not on Google Play yet, and backup to Google Drive works only once the
Drive OAuth client for the release signing key is set up. The Goodreads and StoryGraph import
follows the documented columns and has not been checked against fresh exports yet.

### Added

- Import a Goodreads or StoryGraph CSV export, or an Otium JSON file, from Settings. A preview
  shows what the file adds before anything is written. Books already in the library are skipped
  by ISBN, and books with an ISBN get their covers and details once they are looked up.
- Export the library as CSV in Goodreads' columns, or as Otium JSON that keeps every field.
  Export files are not encrypted.
- A book can belong to a series, with a number. Lookups suggest the series, and the confirm form
  lets you check and correct it. The detail screen shows it, for example "Dune Chronicles ·
  Book 1".
- Filter the library by series, or sort by series to group the shelves under one heading per
  series, in reading order, with how many of its books are read.

### Changed

- Series names are searchable.
- A lookup run that hits an offline, rate-limit or timeout failure now stops there and resumes
  later, and a finished book stays finished when its page count changes.

## [1.0.1] - 2026-10-11

Security fixes. Known issues are as in 1.0.0: not on Google Play yet, and Drive backup works only
once the Drive OAuth client for the release signing key is set up.

### Security

- Backups sent to and fetched from Drive are checked against SHA-256 instead of MD5.
- Restoring a backup checks once more that every file it unpacks stays inside the restore folder.
- The build uses patched versions of libraries that its tools bring in (Android Gradle Plugin,
  Android Lint, Kover, CycloneDX, androidx.benchmark). None of them ships in the app.

## [1.0.0] - 2026-10-10

The first release, with the version 1 features, published on GitHub only.

Known issues: Otium is not on Google Play yet, and backup to Google Drive works only once the
Drive OAuth client for the release signing key is set up.

### Security

- Backups can be encrypted with a passphrase: Argon2id key derivation and Tink's streaming
  AES-256-GCM, with the derived key wrapped by the Android Keystore.
- Cover images load over HTTPS only, and the Drive upload session address is checked.
- Restoring a backup refuses archives that unpack to more than 512 MB.
- Android backup and device transfer exclude the library database and the backup key.

### Added

- A library of physical books stored on the device, with full-text search, filters and sorting,
  filed by title or by the author's surname.
- Add books by scanning the barcode (ML Kit, bundled), by typing an ISBN, or by searching title
  and author.
- Book details from Google Books and Open Library, merged, with alternate covers to choose from
  and covers from the gallery or the camera.
- ISBNs saved while offline are looked up once the phone is back online.
- Reading progress by page, a Reading tab, and book details with progress and an About tab.
- Edit a book, including its shelf.
- Backup to and restore from the app's own folder in Google Drive, by hand or on a schedule,
  restoring all or nothing.
- The Classical theme in light and dark, with Cormorant and Lora, built for WCAG AA contrast and
  text at 200%.
- The Ribbon O launcher icon.

[Unreleased]: https://github.com/eh0x/otium/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/eh0x/otium/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/eh0x/otium/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/eh0x/otium/releases/tag/v1.0.0
