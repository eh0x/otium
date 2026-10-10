<!--
Thanks for contributing to Otium. Before you open this pull request:
- Target the `develop` branch.
- Keep it to one change. Open it as a draft if it is not ready for review.
- For anything larger than a small fix, link the issue where the approach was agreed.
Delete any section that does not apply.
-->

## Summary

<!-- What does this change, and why? Two or three sentences; the commits hold the detail. -->

## Related issue

<!-- Write "Closes #123" to close the issue on merge, or "Part of #123". -->

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Refactoring (no change in behaviour)
- [ ] Documentation
- [ ] Build, CI or tooling
- [ ] Breaking change (data, backup format or behaviour users rely on)

## How it was tested

<!--
Tests added or changed, and anything checked by hand: device or emulator, Android version,
light and dark themes, 200% text, TalkBack.
-->

## Screenshots

<!-- For UI changes: before and after, in the light and dark themes. -->

| Before | After |
|--------|-------|
|        |       |

## Checklist

- [ ] I reviewed my own diff and the commits follow [Conventional Commits](https://www.conventionalcommits.org/).
- [ ] `./gradlew check` passes locally.
- [ ] New or changed logic, queries and parsers have tests.
- [ ] UI changes work in both themes, at 200% text, and with TalkBack (content descriptions, touch targets of at least 44 dp).
- [ ] No new permission, network call, tracking or data leaving the device, or it is explained above.
- [ ] Database changes bump the Room version, export the schema and add a migration test.
- [ ] Docs are updated (README, CONTRIBUTING, style guide, design doc or plan), or a commit has a `Docs: n/a - <reason>` trailer.
- [ ] No secrets, API keys or personal data are included.
