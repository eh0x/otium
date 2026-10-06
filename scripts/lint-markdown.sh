#!/bin/sh
# Lints Markdown with markdownlint-cli2 using .markdownlint-cli2.jsonc (rules: docs/MARKDOWN_GUIDE.md).
#
# usage: scripts/lint-markdown.sh [--staged] [file ...]
#   no arguments  lint every Markdown file in the repository
#   --staged      lint only the Markdown files staged for commit (used by the pre-commit hook)
#   file ...      lint just these files
#
# The tool only checks; it never fixes. Fix findings in the editor and review them like any
# other change (see the guide, "Exceptions"). Needs Node.js. Without it the script skips locally
# with a notice, and fails when CI is set, so CI stays the gate.

MARKDOWNLINT_VERSION="0.23.3"

cd "$(dirname "$0")/.." || exit 1

# Hooks and IDE terminals often start without the shell profile that puts nvm's Node on PATH.
ensure_node() {
    command -v npx >/dev/null 2>&1 && return 0
    for dir in $(ls -d "$HOME"/.nvm/versions/node/*/bin 2>/dev/null | sort -V -r) \
        /opt/homebrew/bin /usr/local/bin; do
        if [ -x "$dir/npx" ]; then
            PATH="$dir:$PATH"
            export PATH
            return 0
        fi
    done
    return 1
}

if ! ensure_node; then
    if [ -n "${CI:-}" ]; then
        echo "lint-markdown: Node.js is required but was not found." >&2
        exit 1
    fi
    echo "lint-markdown: Node.js not found, skipping local Markdown lint (CI will run it)." >&2
    exit 0
fi

if [ "${1:-}" = "--staged" ]; then
    shift
    staged=$(git diff --cached --name-only --diff-filter=ACM -- '*.md')
    [ -z "$staged" ] && exit 0
    # shellcheck disable=SC2086
    set -- $staged
fi

[ "$#" -eq 0 ] && set -- "**/*.md"

exec npx --yes "markdownlint-cli2@$MARKDOWNLINT_VERSION" "$@"
