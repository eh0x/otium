#!/bin/sh
# Claude Code PostToolUse hook: after Edit/Write on a documentation-sensitive file, remind the
# assistant to check whether README.md or docs/ need updating. Informational only (never blocks)
# and shown once per category per session to avoid noise.
# Input: hook JSON on stdin. Output: JSON with additionalContext, or nothing.

input=$(cat)

file=$(printf '%s' "$input" | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)
session=$(printf '%s' "$input" | sed -n 's/.*"session_id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)
[ -z "$file" ] && exit 0

case "$file" in
    */README.md | */CLAUDE.md | */docs/*) exit 0 ;;
    */settings.gradle.kts | */build.gradle.kts | */gradle.properties | */gradle/*)
        category="build"
        hint="Build or setup files changed. Check README.md (requirements, setup, commands, module table)." ;;
    */AndroidManifest.xml)
        category="manifest"
        hint="The manifest changed. Check README.md and the design doc for permissions or app config." ;;
    */.githooks/* | */config/* | */.editorconfig | */scripts/* | */.claude/*)
        category="tooling"
        hint="Tooling changed. Check docs/STYLE_GUIDE.md (enforcement table) and README.md." ;;
    */.github/*)
        category="ci"
        hint="CI changed. Check README.md and docs/STYLE_GUIDE.md." ;;
    *) exit 0 ;;
esac

marker="${TMPDIR:-/tmp}/otium-docs-reminder-${session:-nosession}-$category"
[ -e "$marker" ] && exit 0
: >"$marker"

message="Docs reminder: $hint Run ./gradlew checkDocs to verify, and update docs in the same change, or use a 'Docs: n/a - reason' commit trailer. Rules: docs/STYLE_GUIDE.md, section Documentation."
printf '{"hookSpecificOutput":{"hookEventName":"PostToolUse","additionalContext":"%s"}}\n' "$message"
