## 🎫 Linear Issue
<!-- Link the Linear issue(s) associated with this PR -->
Fixes / Relates to: [Issue-XXX](https://linear.app/your-org/issue/XXX)

## 📝 Description
<!--
Provide a clear and concise summary of your changes.
Explain **WHAT** was changed and **WHY**.
Include any relevant technical context or design decisions.
-->

## 🚀 Type of Change
<!-- Check all that apply -->
- [ ] 🐛 Bug fix (non-breaking change which fixes an issue)
- [ ] ✨ New feature (non-breaking change which adds functionality)
- [ ] 💥 Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] ♻️ Code Refactoring (no new functionality or bug fixes)
- [ ] 📝 Documentation update
- [ ] 🔧 Build / CI / Tooling change

## 🧪 Testing & Verification
<!--
Describe the testing performed to verify your changes.
Include instructions for reviewers to reproduce the changes locally if necessary.
-->
- [ ] Tested locally
- [ ] Added / updated automated tests
- [ ] Tested in staging (if applicable)

## ✅ Checklist
<!-- Make sure all the following steps are completed before requesting a review -->
- [ ] I have performed a self-review of my code
- [ ] `./gradlew spotlessCheck detekt lint testDebugUnitTest :domain:test checkDocs lintMarkdown` passes
- [ ] Commits follow [Conventional Commits](https://www.conventionalcommits.org/) (use a `Docs: n/a - <reason>` trailer when docs are skipped on purpose)
- [ ] Documentation updated in this PR, or not needed because: <!-- reason -->
  - [ ] `README.md` (requirements, setup, commands, module layout)
  - [ ] `docs/STYLE_GUIDE.md` (rules or tooling changed)
  - [ ] Design doc or plan (decisions or phase status changed)
- [ ] No sensitive information (secrets, passwords, API keys) is exposed
