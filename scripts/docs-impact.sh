#!/bin/sh
# Warns when a change touches setup, build, tooling or app configuration but no documentation.
#
# usage: scripts/docs-impact.sh [<base-ref> [<head-ref>]] [--strict]
#   base-ref  default: the upstream branch, else origin/develop, else HEAD~1
#   head-ref  default: HEAD
#   --strict  exit 1 instead of warning
#
# Acknowledge a deliberate skip with a commit trailer in any commit of the range:
#   Docs: n/a - <reason>
# Output uses GitHub annotation syntax when running in GitHub Actions.

strict=0
refs=""
for arg in "$@"; do
    case "$arg" in
        --strict) strict=1 ;;
        *) refs="$refs $arg" ;;
    esac
done
# shellcheck disable=SC2086
set -- $refs

base="${1:-}"
head="${2:-HEAD}"
if [ -z "$base" ]; then
    base=$(git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' 2>/dev/null) ||
        base=$(git rev-parse --verify --quiet origin/develop) ||
        base=$(git rev-parse --verify --quiet HEAD~1) || exit 0
fi
# A push to a new branch reports an all-zero base; compare with the parent instead.
case "$base" in
    0000000000000000000000000000000000000000) base=$(git rev-parse --verify --quiet "$head~1") || exit 0 ;;
esac

changed=$(git diff --name-only "$base...$head" 2>/dev/null) || exit 0
[ -z "$changed" ] && exit 0

# Case sensitive on purpose: "Docs:" is the trailer, "docs:" is a Conventional Commits type.
if git log --format=%B "$base..$head" 2>/dev/null | grep -qE '^Docs:[[:space:]]*\S'; then
    echo "docs-impact: acknowledged by a Docs: trailer."
    exit 0
fi

docs_changed=$(printf '%s\n' "$changed" | grep -E '^(README\.md|CLAUDE\.md|docs/)' || true)
[ -n "$docs_changed" ] && exit 0

report=""
add() { report="$report\n  - $1 ($2)"; }

for file in $changed; do
    case "$file" in
        settings.gradle.kts | build.gradle.kts | */build.gradle.kts | gradle.properties | gradle/*)
            add "$file" "build or setup: README requirements, commands, layout" ;;
        app/src/main/AndroidManifest.xml)
            add "$file" "permissions or app config: README, design doc" ;;
        .githooks/* | config/* | .editorconfig | .markdownlint-cli2.jsonc | scripts/* | .claude/*)
            add "$file" "tooling: docs/STYLE_GUIDE.md, README" ;;
        .github/*)
            add "$file" "CI: README, docs/STYLE_GUIDE.md" ;;
    esac
done

[ -z "$report" ] && exit 0

message="This change touches files that usually need a documentation update, but no docs changed:$(printf '%b' "$report")
Update README.md or docs/, or add a commit trailer 'Docs: n/a - <reason>'."

if [ -n "${GITHUB_ACTIONS:-}" ]; then
    printf '::warning title=Docs may be out of date::%s\n' "$(printf '%s' "$message" | tr '\n' ' ')"
else
    printf 'docs-impact: %s\n' "$message" >&2
fi

[ "$strict" -eq 1 ] && exit 1
exit 0
