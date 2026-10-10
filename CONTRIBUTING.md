# Contributing to Otium

Thank you for your interest in Otium. This guide covers how to set up a development checkout, the
workflow for a change, and the checks a pull request must pass. For what Otium is and how to build
it, start with the [README](README.md).

## Contents

- [Before you start](#before-you-start)
- [Set up](#set-up)
- [Workflow](#workflow)
- [Everyday commands](#everyday-commands)
- [Testing](#testing)
- [Documentation](#documentation)
- [Troubleshooting](#troubleshooting)
- [License](#license)

## Before you start

- **Bugs and feature requests** go to the [issue tracker](https://github.com/eh0x/otium/issues),
  through its forms. Search first; a short report with steps to reproduce, the Android version and
  the device helps most.
- **Questions and rough ideas** go to [Discussions](https://github.com/eh0x/otium/discussions):
  Q&A for help, Ideas for anything not yet a feature request.
- **Larger changes** (a new feature, a new dependency, a change to the architecture) need an issue
  first, so the approach can be agreed before you write code. Small fixes can go straight to a
  pull request.
- **Vulnerabilities** are never reported in public. Follow [SECURITY.md](SECURITY.md).

Otium is maintained by one person in spare time, so reviews can take a few days.

## Set up

Follow [Getting started](README.md#getting-started) in the README first. A development checkout
also uses:

- gitleaks (`brew install gitleaks`), for the secret scan in the git hooks. Without it the hooks
  skip the scan with a notice; CI runs it either way.
- OSV-Scanner, optional (`brew install osv-scanner`). Only `./gradlew scanDependencies` needs it.
- Node.js, optional. Only the Markdown lint needs it, and it is skipped when Node.js is missing.

Then enable the git hooks:

```bash
./gradlew installGitHooks
```

This points git at the hooks in `.githooks`: a gitleaks secret scan, formatting and Markdown checks
on commit, a Conventional Commits check on the message, and a history secret scan and the test
suite on push. The hooks look for a JDK in the usual places and skip the local run when none is
found. They are a convenience; CI is the gate.

`main` changes only through a pull request. The hooks refuse a commit or merge on `main` and a
push to it, and the repository ruleset on GitHub refuses the push even when the hooks are skipped.

## Workflow

1. Branch from `develop` (never commit on `main`), named `<type>/<short-description>` with a
   Conventional Commits type.
2. Keep each commit to one logical change, with a
   [Conventional Commits](https://www.conventionalcommits.org/) subject of at most 72 characters:
   `type(scope): subject`, for example `fix(library): keep particles with surnames`.
3. Write the test first for logic, queries and parsers. Follow the
   [style guide](docs/STYLE_GUIDE.md), including the layer rules: `:domain` stays free of Android
   and framework imports.
4. Format with `./gradlew spotlessApply` and accept its output rather than formatting by hand.
5. Run `./gradlew check` and fix what it reports, including the docs. Fix findings instead of
   adding baselines or global rule disables; suppress at the smallest scope, with a comment giving
   the reason.
6. Open a pull request against `develop` and complete the checklist in the template. Link the
   issue it closes, and add screenshots for UI changes.

## Everyday commands

| Command | What it does |
|---------|--------------|
| `./gradlew assembleDebug` | Build the debug APK |
| `./gradlew installDebug` | Install the debug build on a device or emulator |
| `./gradlew assembleRelease` | Build the R8-shrunk release APK (needs `keystore.properties`, or `-Potium.allowDebugSigning=true`) |
| `./gradlew testDebugUnitTest :domain:test` | Run all unit tests |
| `./gradlew :benchmark:connectedBenchmarkAndroidTest` | Run the scroll and search benchmarks on a device or emulator (see [Testing](#testing)) |
| `./gradlew spotlessApply` | Fix formatting of Kotlin, Gradle, XML and text files |
| `./gradlew spotlessCheck detekt lint` | Check formatting, static analysis and Android Lint |
| `./gradlew checkDocs lintMarkdown` | Check that docs match the repository, and lint Markdown |
| `./gradlew scanSecrets` | Scan the git history for secrets with gitleaks |
| `./gradlew scanDependencies` | Scan the shipped dependencies for known vulnerabilities (OSV-Scanner) |
| `./gradlew check` | Run every verification task |

CI runs the same checks (see `.github/workflows/ci.yml`). To reproduce a CI run locally:

```bash
./gradlew spotlessCheck detekt checkDocs lintMarkdown scanSecrets scanDependencies lint testDebugUnitTest :domain:test koverVerifyCoverage assembleDebug assembleRelease -Potium.allowDebugSigning=true
```

`.github/workflows/dependency-submission.yml` also sends the resolved Gradle dependency tree to
GitHub on pushes to `main` and `develop` that change the build files, so Dependabot alerts cover
transitive dependencies.

## Testing

Almost everything runs on the JVM, so no device or emulator is needed:

- **Domain tests** cover the pure rules: status, page clamping, ISBN validation, metadata merge.
- **Data tests** run the real Room database under Robolectric: search, every filter and sort,
  constraints, change notification, and a 5,000-book latency guard.
- **UI tests** check the theme, the contrast of the palette in both themes (WCAG), component
  semantics and touch targets, and render the main screens headlessly, in both themes and at 200%
  text, to `app/build/screenshots`.
- **Architecture tests** fail the build when a layering rule breaks, for example when the UI layer
  imports the data layer or a ViewModel imports an Android type.
- **Manifest policy tests** fail when the app gains a permission or an exported component, or
  turns on Android backup or cleartext traffic.

Coverage is measured with Kover across both modules and checked against a floor in CI:

```bash
./gradlew koverHtmlReportCoverage   # report at build/reports/kover/htmlCoverage/index.html
./gradlew koverVerifyCoverage       # fails below the floor set in build.gradle.kts
```

One check needs a device or emulator: the release smoke test drives the shrunk (R8) build through
adding a book by hand, tracking progress, deleting it, and an online ISBN lookup. Drive backup and
restore stay a manual check, as they need a Google account.

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.otium.benchmark.ReleaseSmokeTest
```

The 5,000-book timings measured on the JVM are a regression guard, not device numbers. Device
numbers come from the `:benchmark` module, which seeds 5,000 books through the debug seeder and
records frame timing while scrolling the shelf and typing in search. It needs a device or emulator.
On an emulator add `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`;
its numbers only compare one run with another.

## Documentation

Docs live in the repository and change in the same commit as the code they describe. The
[style guide](docs/STYLE_GUIDE.md#8-documentation) says which document owns what. Markdown follows
the [Markdown guidelines](docs/MARKDOWN_GUIDE.md): 100 columns, `-` bullets, fenced code with a
language.

`./gradlew checkDocs` fails when the README or the guides drift from the build files, and CI warns
when setup or tooling changes come without a docs update. When a change needs no docs on purpose,
add a commit trailer such as `Docs: n/a - internal refactor`.

## Troubleshooting

**A commit or push is blocked.** Read the message: formatting is fixed with
`./gradlew spotlessApply`, Markdown findings are listed by file and line, and commit subjects must
follow Conventional Commits (`type(scope): subject`). A gitleaks finding means a possible secret:
remove it, and rotate it if it ever left your machine. False positives are handled as described in
`scripts/scan-secrets.sh`.

**`checkDocs` fails.** A doc disagrees with the repository, for example a command that no longer
exists or a stale fact. Update the doc in the same change.

For build problems (SDK, JDK, platform), see [Troubleshooting](README.md#troubleshooting) in the
README.

## License

By contributing, you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE), the license of the project.
