#!/bin/sh
# Claude Code Stop hook: keep the graphify code graph current without relying on anyone to
# remember. Runs `graphify update .` (AST only: no LLM, no API cost, about a second) once at the
# end of a turn, and only when a source file is newer than the graph. Never blocks and never
# prints. Edits made outside Claude Code are covered by the rule in CLAUDE.md.

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

# Only where graphify is set up for this project.
[ -f graphify-out/graph.json ] || exit 0

graphify_bin=$(command -v graphify || echo "$HOME/.local/bin/graphify")
[ -x "$graphify_bin" ] || exit 0

# Anything that graphify extracts (Kotlin and Kotlin DSL) changed since the last update?
changed=$(find . \
    \( -path ./graphify-out -o -path ./.git -o -path ./.gradle -o -name build -o -name node_modules \) -prune \
    -o -type f \( -name '*.kt' -o -name '*.kts' -o -name '*.java' \) \
    -newer graphify-out/graph.json -print -quit 2>/dev/null)
[ -z "$changed" ] && exit 0

"$graphify_bin" update . >/dev/null 2>&1
exit 0
