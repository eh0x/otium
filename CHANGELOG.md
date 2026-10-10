# Changelog

All notable changes to Otium are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), with the Security group first, and
versions follow [Semantic Versioning](https://semver.org/) as described in
[docs/RELEASING.md](docs/RELEASING.md).

## [Unreleased]

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

[Unreleased]: https://github.com/eh0x/otium/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/eh0x/otium/releases/tag/v1.0.0
