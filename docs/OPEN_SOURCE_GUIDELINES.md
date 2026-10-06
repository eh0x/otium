# Open Source Project Management, Security, and Governance Guidelines

This document establishes comprehensive guidelines and standards for hosting, managing, and securing
open-source Android projects on GitHub. It synthesizes recommendations from the **Linux Foundation
Research**, **Open Source Guides (Security & Code of Conduct)**, and the **OpenSSF SCM Best
Practices**.

---

## 1. Issue Management and Triage Strategy

Effective issue management keeps the repository organized, lowers the barrier to entry for
contributors, and prevents maintainer burnout.

### Label Taxonomy

Adopt a consistent, color-coded labeling system to classify issues and pull requests at a glance:

| Category | Label | Color | Description |
| :--- | :--- | :--- | :--- |
| **Type** | `type: bug` | `#d73a4a` | Something isn't working as expected in the Android app. |
| | `type: feature` | `#a2eeef` | New feature or user-facing enhancement. |
| | `type: refactor` | `#cfd3d7` | Internal code cleanup or architectural adjustment without changing functionality. |
| | `type: docs` | `#0075ca` | Improvements or additions to documentation. |
| **Difficulty** | `good first issue` | `#7057ff` | Small, self-contained tasks suited for first-time contributors. |
| | `help wanted` | `#008672` | Tasks requiring additional maintainer or community expertise. |
| **Priority** | `priority: critical` | `#b60205` | Showstopper bugs, crashes, or security vulnerabilities. |
| | `priority: low` | `#fef2c0` | Minor bugs or polish items. |
| **Status** | `status: triage` | `#d4c5f9` | Newly opened issues awaiting maintainer review. |
| | `status: blocked` | `#000000` | Blocked by upstream dependencies, design decisions, or external APIs. |

> [!TIP]
> Reserve `good first issue` exclusively for tasks that have clear requirements, explicit code
> pointers, and require minimal environment setup. Avoid overloading new contributors with complex
> architectural changes under this label.

### Issue Templates

Implement YAML-based GitHub Issue Forms (`.github/ISSUE_TEMPLATE/`) to enforce structured
submissions:

- **`bug_report.yml`**: Captures Android OS version, device model, app version/build type, steps to
  reproduce, expected vs. actual behavior, and logcat logs.
- **`feature_request.yml`**: Captures user problem statement, proposed solution, alternative ideas
  considered, and mockups/UI designs.

### Milestones and Project Boards

- **Milestones**: Map issues and PRs to explicit target releases (e.g., `v1.0.0-alpha`, `v1.1.0`).
  Milestones track progress toward release goals and provide transparency on feature velocity.
- **Automated Stale Management**: Configure GitHub Actions (e.g., `actions/stale`) to label inactive
  issues as `stale` after 60 days of inactivity, and automatically close them after an additional 14
  days if no response is provided.

---

## 2. Contribution Lifecycle and Code Review

Establishing clear contribution pathways ensures consistent code quality and protects against
unintended regressions.

### `CONTRIBUTING.md` Architecture

Maintain a top-level `CONTRIBUTING.md` outlining the developer experience:

1. **Local Setup Instructions**: JDK version requirement (e.g., JDK 17/21), Android SDK/NDK
   requirements, Gradle configuration, and build commands (`./gradlew assembleDebug`).
2. **Branch Strategy**: Feature branches off `main` following `feature/short-description`,
   `fix/issue-number`, or `chore/task-name`.
3. **Commit Conventions**: Conventional Commits standard (e.g., `feat(ui): add dark mode toggle`,
   `fix(auth): handle expired token`).
4. **Testing Standards**: Every bug fix or feature must include corresponding Kotlin unit tests
   (`/src/test/`) or Compose UI tests (`/src/androidTest/`).

### Pull Request (PR) Workflow

- **PR Templates**: Enforce `.github/pull_request_template.md` asking contributors to link related
  issues (`Closes #123`), describe changes, list testing performed, and provide screenshots/screen
  recordings for UI changes.
- **`CODEOWNERS` File**: Define path-based ownership (`.github/CODEOWNERS`) to route core
  architecture changes (e.g., `:domain`, `:core:database`, Gradle build scripts) to specific senior
  maintainers.
- **Review Culture**:
  - Emphasize constructive, empathetic feedback.
  - Distinguish between **blocking requests** (correctness, performance, security) and
    **non-blocking suggestions** (style nitpicks, minor refactoring).
  - Encourage community PR reviews to distribute workload while requiring official maintainer
    approval for merge.

---

## 3. Documentation Standards and Maintenance

Clear, up-to-date documentation reduces support overhead and empowers contributors to work
independently.

### `README.md` Essentials

Your project's `README.md` is the front door. Ensure it contains:

- **Project Badges**: Build status (CI), license, latest release version, Kotlin version, and
  OpenSSF Scorecard.
- **Vision & Elevator Pitch**: High-level statement of what the app does, target audience, and key
  features.
- **Screenshots / GIF Demos**: Visual representations of key Android screens (Light/Dark mode,
  phone/tablet form factors).
- **Quickstart Guide**:

  ```bash
  git clone https://github.com/username/project.name.git
  cd project.name
  ./gradlew assembleDebug
  ```

- **Architecture Overview**: High-level explanation of the Android architecture (e.g., Clean
  Architecture, MVVM/MVI, Jetpack Compose, Room, Hilt).

### Extended Documentation

For complex architectures, publish full documentation using tools like **MkDocs** (with Material
theme) or **Docusaurus**, hosted via GitHub Pages:

- **Architecture Decision Records (ADRs)**: Document significant architectural choices (e.g.,
  choosing `Room` over `SQLDelight`, migrating from XML Views to Jetpack Compose).
- **API / Module Documentation**: Document core library modules and public interfaces using
  **Dokka** for Kotlin codebases.

> [!IMPORTANT]
> Adopt a **"Doc-as-Code"** rule: No PR modifying or adding public APIs or major feature behavior
> should be merged without corresponding updates to inline KDoc or project documentation files.

---

## 4. Community Engagement and Governance

Building an inclusive, welcoming community ensures long-term project health and sustainability.

### Code of Conduct (CoC)

Adopt the **Contributor Covenant (v2.1)** and commit to actively enforcing it:

- **File Location**: Place `CODE_OF_CONDUCT.md` in the repository root or `.github/` folder.
- **Clear Incident Reporting**: Provide a dedicated email address (e.g., `conduct@yourproject.org`)
  monitored by designated maintainers.
- **Alternate Contact**: Specify an alternative contact person in case a conflict involves a primary
  report handler.
- **Enforcement Scale**: Outline explicit responses for violations (Public Warning $\rightarrow$
  Private Reprimand $\rightarrow$ Temporary Suspension $\rightarrow$ Permanent Ban).

### Community Interaction Channels

- **GitHub Discussions**: Enable the Discussions tab categorized into **Q&A**, **Ideas/Proposals**,
  **Show and Tell**, and **Announcements**. Keep GitHub Issues reserved strictly for actionable bugs
  and feature work.
- **Social & Developer Reach**: Share milestones, release notes, and contributor shout-outs on
  platforms like Reddit (`/r/androiddev`), Kotlin Slack, Mastodon/Bluesky, and Android developer
  forums.

---

## 5. Automation and CI/CD for Android

Automate repetitive tasks to enforce quality gates without consuming maintainer time manually.

### Continuous Integration (GitHub Actions)

Set up workflows (`.github/workflows/ci.yml`) triggered on every pull request and push to `main`:

1. **Static Analysis & Formatting**: Run `spotlessCheck` / `ktlintCheck` and Android Lint
   (`./gradlew lintDebug`).
2. **Unit & Integration Testing**: Execute `./gradlew testDebugUnitTest`.
3. **Build Verification**: Run `./gradlew assembleDebug` to verify compilation.

```yaml
name: Android CI
on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build-and-test:
    runs-name: Build & Test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'gradle'
      - name: Run Code Style & Linter Check
        run: ./gradlew spotlessCheck lintDebug
      - name: Run Unit Tests
        run: ./gradlew testDebugUnitTest
      - name: Build Debug APK
        run: ./gradlew assembleDebug
```

### Dependency Automation

- **Dependabot Configuration**: Enable `.github/dependabot.yml` to track updates for Gradle
  dependencies, Gradle plugins, and GitHub Actions workflows. Group minor and patch updates into
  single weekly PRs to avoid PR noise.

---

## 6. Security and Vulnerability Management

Security best practices from **OpenSource.guide** and the **OpenSSF SCM guidelines** protect the
project supply chain and user base.

### Security Policy (`SECURITY.md`)

Maintain a `SECURITY.md` file specifying:

- Supported app versions receiving security backports.
- Coordinated Vulnerability Disclosure (CVD) policy.
- Private disclosure channel (e.g., GitHub Private Vulnerability Reporting or security email).
- Expected response timeframes (e.g., acknowledgement within 48 hours, triage within 7 days).

### Static Analysis & Secret Scanning

- **Static Application Security Testing (SAST)**: Enable **GitHub CodeQL** or **Semgrep** targeting
  Kotlin/Java to scan for common Android security risks (e.g., exported components, insecure
  storage, hardcoded keys).
- **Secret Scanning**: Enable GitHub Secret Scanning or Trufflehog to detect and block accidental
  commits containing API keys, private keys, or credentials.

### Supply Chain Security

- **Software Bill of Materials (SBOM)**: Generate SPDX SBOM artifacts during official releases
  (e.g., using `spdx-gradle-plugin`).
- **Dependency Review**: Use GitHub Dependency Review Action on pull requests to detect vulnerable
  or maliciously license-changed third-party libraries.

---

## 7. OpenSSF Repository & SCM Hardening Checklist

Implement strict repository settings based on OpenSSF SCM Best Practices to defend against account
compromise and malicious code injection.

```text
[Repository Hardening Checklist]
├── Authentication & Access Control
│   ├── [x] Mandatory 2FA enabled for all organization maintainers
│   ├── [x] Maintainer / Organization Admin accounts limited (< 3 admins)
│   └── [x] Restrict base permissions to Read-Only
├── Branch Protection Rules (main branch)
│   ├── [x] Require a minimum of 2 approving code reviews before merging
│   ├── [x] Require code review from CODEOWNERS
│   ├── [x] Require status checks to pass before merging (CI, Lint, Tests)
│   ├── [x] Dismiss stale pull request approvals when new commits are pushed
│   ├── [x] Require linear history (Squash or Rebase merging)
│   ├── [x] Require signed commits (GPG/SSH verification)
│   └── [x] Disallow force pushes and branch deletion
└── CI/CD Security (GitHub Actions)
    ├── [x] Default workflow permissions set to `read-all`
    ├── [x] Disable "Allow GitHub Actions to create and approve pull requests"
    └── [x] Restrict actions to verified or explicit third-party actions
```

> [!WARNING]
> Never grant elevated `write` or `admin` permissions to automated CI tokens or third-party GitHub
> Actions unless strictly required for release deployment workflows. Always scope job-level
> permissions explicitly in workflow files.

---

## 8. Summary Action Plan for Repository Setup

To apply these guidelines immediately to your Android repository:

1. **Root Files**: Ensure `README.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, and
   `LICENSE` exist in the root directory.
2. **GitHub Templates**: Create `.github/ISSUE_TEMPLATE/` (bug & feature forms) and
   `.github/pull_request_template.md`.
3. **Branch Protection**: Go to **Settings $\rightarrow$ Branches** and configure protection rules
   for `main`.
4. **CI/CD Pipeline**: Add `.github/workflows/ci.yml` with Gradle build, spotless/ktlint, and test
   checks.
5. **Dependabot**: Add `.github/dependabot.yml` for automated dependency management.
6. **Discussions & Security**: Enable GitHub Discussions and Private Vulnerability Reporting in
   repository settings.
