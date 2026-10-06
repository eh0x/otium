# Otium Style Guide

This is the single reference for how code is written in this repository, and how those rules are
enforced.

**Baseline:** the [Android Kotlin style guide](https://developer.android.com/kotlin/style-guide),
which builds on the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
Everything in those two documents applies unless this file says otherwise. This file lists the
tightenings, project decisions and the tooling that checks them.

The aim is that style is never debated in review: the tools decide, and this guide explains the
rules the tools cannot see.

## 1. Enforcement at a glance

| What | Tool | Config | Runs |
|------|------|--------|------|
| Kotlin and Kotlin DSL formatting | Spotless + ktlint (`android_studio` code style) | `.editorconfig`, root `build.gradle.kts` | `pre-commit` hook, CI |
| XML, Markdown, YAML, TOML, properties, ProGuard whitespace | Spotless | root `build.gradle.kts`, `.editorconfig` | `pre-commit` hook, CI |
| Design, complexity, bug-prone Kotlin | detekt | `config/detekt/detekt.yml` | `pre-push` hook, CI |
| Compiler warnings | Kotlin `allWarningsAsErrors` | module `build.gradle.kts` | every build |
| Android correctness, security, resources | Android Lint with `warningsAsErrors` | `app/build.gradle.kts` | CI (`lint`) |
| Layering (`:domain` stays pure Kotlin) | `DomainPurityTest` | `domain/src/test` | every `:domain:test` |
| Commit messages | `commit-msg` hook | `.githooks/commit-msg` | every commit |
| Everything else in this guide | Code review | this file | pull requests |

### Daily commands

```bash
./gradlew spotlessApply      # fix formatting in place (run before committing)
./gradlew spotlessCheck      # verify formatting
./gradlew detekt             # static analysis
./gradlew lint               # Android Lint
./gradlew check              # all verification tasks
```

### One-time setup

```bash
./gradlew installGitHooks    # points git at .githooks (pre-commit, commit-msg, pre-push)
```

The hooks look for a JDK in the usual places. If none is found they skip the local run and CI
becomes the gate. Bypassing a hook with `--no-verify` is for emergencies only; CI will still fail.

### Suppression policy

- No baseline files. A finding is fixed, or suppressed in code with a reason.
- Suppress at the smallest scope (a function, not a file) and add a comment saying why:
  `@Suppress("SpreadOperator") // Room only offers varargs; spread once at startup.`
- Never disable a rule globally to make one finding disappear. Change `detekt.yml` only when the
  rule is wrong for the whole project, and say why in the config.

## 2. Kotlin

### Source files

- File name: `PascalCase.kt`. A file with one top-level class is named after it. A file with
  several related declarations gets a descriptive PascalCase name (`CrossRefs.kt`, `BookMappers.kt`).
- UTF-8, LF line endings, one final newline, no trailing whitespace, no tabs.
- Order: file-level annotations, `package`, imports, declarations. `package` and `import` lines
  are never wrapped.
- Imports: one sorted block, no wildcards, no unused imports. (ktlint)

### Formatting (ktlint, `android_studio` style)

- 4 space indent, continuation indent of at least 4. Column limit **100**. (ktlint, detekt)
- Braces on the same line as the construct, `else` on the same line as the closing brace.
- One statement per line. Wrap at the highest syntactic level available.
- **Trailing commas** on multiline parameter lists, argument lists and collection literals
  (Kotlin conventions; a project choice on top of the Android guide).
- Long numeric literals use underscores: `5_000`, `1_000L`.
- Expression bodies for single-expression functions that fit on the line or wrap cleanly.
- Run `./gradlew spotlessApply` and accept its output. Do not hand-format against it.

### Naming

| Element | Rule | Example |
|---------|------|---------|
| Package | lowercase, no underscores | `app.otium.data.local` |
| Class, interface, object, enum | `UpperCamelCase` | `BookRepositoryImpl` |
| Function, property, variable | `lowerCamelCase` | `observeLibrary` |
| `@Composable` function | `UpperCamelCase` (it is a UI node) | `ProgressCard` |
| `const val`, top-level or `object` immutable value | `SCREAMING_SNAKE_CASE` | `AUTHOR_SEPARATOR` |
| Backing property | `_name` for the private mutable twin | `_state` / `state` |
| Test function | sentence in backticks | `` `page above total is clamped` `` |
| Acronyms | treated as words | `isbn13`, `Isbn13`, `HttpClient` |

Interfaces are not prefixed (`BookRepository`, not `IBookRepository`). Implementations add the
suffix `Impl` only when there is a single obvious one (`BookRepositoryImpl`); prefer a
descriptive name (`MlKitBarcodeScanner`, `DriveBackupClientImpl`).

### Documentation

- KDoc (`/** ... */`) on every public type and on any public member whose behavior is not obvious
  from its name and signature. Start with a one-sentence summary fragment.
- Comments explain **why**, never what the next line obviously does. Delete commented-out code.
- `TODO:` is allowed, with a reason. `FIXME:` and `STOPSHIP:` fail the build. (detekt)

### Language use

- Prefer `val` and immutable collections. Prefer `data class` for plain data, `sealed interface`
  for closed hierarchies, `object` for stateless rules (`DeriveStatus`).
- No `!!` in production code. Use a safe call, `requireNotNull` or `checkNotNull` with a message.
  (detekt, tests excluded)
- No wildcard or unused imports, no unused private members, no magic numbers outside constants,
  tests and debug code. (detekt)
- Functions return at most 3 times (guard clauses at the top are free). Split instead of nesting.
- Public API has explicit types. Local variables may rely on inference.
- Keep functions short and files focused. detekt flags long methods, long parameter lists
  (data classes and defaults are exempt) and high complexity.

### Coroutines and Flow

- Structured concurrency only. No `GlobalScope`. (detekt)
- Never hard-code a dispatcher. Inject `DispatcherProvider`. (detekt `InjectDispatcher`)
- Never swallow `CancellationException`: rethrow it before catching broader exceptions. Do not
  use `runCatching` around suspending calls.
- ViewModels expose `StateFlow` (UI state) and consume `Flow` from repositories. Collect with
  lifecycle awareness (`collectAsStateWithLifecycle`).

### Error handling

- Expected failures are values: functions return `Try<T>` with an `AppError`. See design doc
  section 4.1, "Error model".
- Catch framework exceptions (`SQLiteException`, `IOException`, `HttpException`) only in `data/`
  and `work/`, convert them to `AppError`, and let nothing else escape.
- Exceptions are for programmer errors (`check`, `require`), not for control flow.

## 3. Architecture rules

These come from the design doc, section 4.1 "Layer rules", and are reviewed rather than linted
(except where noted).

- `:domain` is pure Kotlin/JVM: no Android, Room, Retrofit or Hilt imports. (`DomainPurityTest`)
- Interfaces belong to the consumer: repository and port interfaces live in `:domain`,
  implementations in `:app/data`, bindings in `:app/di`.
- Room entities, row projections and network DTOs never leave `data/`. Repositories map them to
  domain models with extension-function mappers.
- Use cases exist only where there is logic. Plain reads go from ViewModel to repository.
- ViewModels hold no business rules: they map domain results and `AppError` to UI state.
- Workers (`work/`) are thin: call a use case, translate the result.

## 4. Jetpack Compose (from Phase 1 on)

- Follow the [Compose API guidelines](https://developer.android.com/develop/ui/compose/api-guidelines).
- Screens are stateless composables fed by a UI state and callbacks (state hoisting). Only
  screen-level entry points touch a ViewModel.
- Every composable that emits UI takes a `modifier: Modifier = Modifier` as its first optional
  parameter and applies it to the root node.
- No hard-coded colors, text styles, shapes or user-visible strings in screens. Use `OtiumTheme`
  tokens and string resources. Raw hex values appear only in `Color.kt`.
- Meaningful content descriptions on images and icons; decorative ones use `null`.
- Provide `@Preview` for reusable components, in both light and dark.
- Status is never conveyed by color alone (label or icon as well).

## 5. Tests

- Write the failing test first for domain logic, queries and parsers.
- Names are sentences in backticks that state the behavior: `` `duplicate isbn reports the existing book` ``.
- Prefer fakes over mocks for owned interfaces (`FakeBookRepository`). Use MockK only at
  boundaries you do not own.
- Coroutines: `runTest`. Flows: Turbine, with an explicit wait for the item you expect.
- Database tests use in-memory Room under Robolectric (`app/src/test`). Keep them
  deterministic: no sleeps, no wall clock, no network (MockWebServer for HTTP).
- One behavior per test. Arrange, act, assert, with a blank line between the phases.
- Test-only code lives in test source sets. Debug-only code lives in `src/debug`.

## 6. Other file types

### Gradle (Kotlin DSL)

- Formatted by ktlint like Kotlin sources.
- Versions live only in `gradle/libs.versions.toml`. No version strings in build files.
- Plugins are applied through catalog aliases. Dependencies use catalog accessors
  (`libs.androidx.core.ktx`).
- Group dependencies by purpose, `implementation` before `debugImplementation` before tests.
- Secrets come from `local.properties` or the environment, never from the repository.

### Android XML resources

- 4 space indent, one attribute per line for tags with more than two attributes.
- Resource names are `snake_case` and prefixed by type or screen: `ic_launcher_foreground`,
  `library_empty_title`.
- User-visible text goes in `strings.xml`. Colors and dimensions come from the theme, not
  literals in layouts.
- Android Lint (warnings are errors) covers resource correctness, accessibility and security.

### TOML, YAML, JSON

- UTF-8, LF, final newline. 2 spaces in YAML and JSON, 4 elsewhere.
- Keep the version catalog sections ordered by purpose with a comment per group.

### Markdown

- One sentence per line is fine; wrap at about 100 columns where it reads better. No trailing
  spaces (use a blank line, not a double space, for a break). Fenced code blocks name their
  language.
- Documents under `docs/` are dated (`YYYY-MM-DD-name.md`) except this guide.

### Shell (git hooks)

- POSIX `sh`, `set -e` style failure handling, no bashisms. Shared helpers in `.githooks/lib.sh`.

### Java

The project has no Java sources. If one becomes unavoidable, add `googleJavaFormat().aosp()` to
Spotless and note it here.

## 7. Git

- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/):
  `type(scope): subject`, types `feat fix docs style refactor perf test build ci chore revert`.
  Subject in the imperative, 72 characters at most, no trailing period. (`commit-msg` hook)
- Body (optional) explains why, not what.
- One logical change per commit. Formatting-only changes are their own `style:` commit.
- Branches: work on `develop` or a short-lived `type/short-description` branch; merge to `main`
  at phase boundaries.

## 8. Changing this guide

Rules change by pull request that updates this file and the tool configuration together, so the
document and the enforcement never drift apart. If a tool finding looks wrong, fix the config,
not the symptom, and record the reason in the config file.
