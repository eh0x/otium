#!/bin/sh
# Lints the GitHub Actions workflows: actionlint for syntax, expressions and the shell in `run:`
# steps (through shellcheck when it is installed), and zizmor for security problems such as
# unpinned actions, template injection, broad permissions and cache poisoning.
#
# usage: scripts/lint-workflows.sh [--staged] [--sarif <file>]
#   no arguments    lint every workflow (`lintWorkflows`, CI)
#   --staged        lint only the workflows staged for commit (pre-commit hook); does nothing
#                   when none is staged
#   --sarif <file>  also write zizmor's findings as a SARIF report (CI, for code scanning)
#
# zizmor runs with the pedantic persona, which also asks for a comment next to every permission.
# With GH_TOKEN set it runs online and also checks for actions with known vulnerabilities and pins
# that do not belong to the action; otherwise it runs offline. A finding that is a false positive
# is suppressed on its line with `# zizmor: ignore[<audit>]` and a reason.
#
# Needs actionlint and zizmor (`brew install actionlint zizmor`). Without them the script skips
# locally with a notice, and fails when CI is set, so CI stays the gate.

# Keep in step with ACTIONLINT_VERSION and ZIZMOR_VERSION in .github/workflows/ci.yml.
ACTIONLINT_VERSION="1.7.12"
ZIZMOR_VERSION="1.30.1"

cd "$(dirname "$0")/.." || exit 1

staged=0
sarif=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        --staged) staged=1 ;;
        --sarif)
            [ -n "${2:-}" ] || { echo "lint-workflows: --sarif needs a file path." >&2; exit 2; }
            sarif="$2"
            shift
            ;;
        *) echo "lint-workflows: unknown argument: $1" >&2; exit 2 ;;
    esac
    shift
done

if [ "$staged" -eq 1 ]; then
    files=$(git diff --cached --name-only --diff-filter=ACMR -- \
        '.github/workflows/*.yml' '.github/workflows/*.yaml')
else
    files=$(git ls-files -- '.github/workflows/*.yml' '.github/workflows/*.yaml')
fi
[ -z "$files" ] && exit 0

# Hooks and IDE terminals often start without the shell profile that puts Homebrew on PATH.
for dir in /opt/homebrew/bin /usr/local/bin; do
    case ":$PATH:" in
        *":$dir:"*) ;;
        *) [ -d "$dir" ] && PATH="$PATH:$dir" ;;
    esac
done
export PATH

missing=""
command -v actionlint >/dev/null 2>&1 || missing="$missing actionlint"
command -v zizmor >/dev/null 2>&1 || missing="$missing zizmor"
if [ -n "$missing" ]; then
    if [ -n "${CI:-}" ]; then
        echo "lint-workflows:$missing required but not found." >&2
        exit 1
    fi
    echo "lint-workflows:$missing not found, skipping the workflow lint (CI will run it)." >&2
    echo "lint-workflows: install with: brew install actionlint zizmor" >&2
    exit 0
fi

# Rules change between releases, so a different version can disagree with CI. Warn, never block.
installed=$(actionlint -version 2>/dev/null | head -n 1)
if [ "$installed" != "$ACTIONLINT_VERSION" ]; then
    echo "lint-workflows: actionlint $installed found, CI uses $ACTIONLINT_VERSION; results may differ." >&2
fi
installed=$(zizmor --version 2>/dev/null | sed 's/^zizmor //')
if [ "$installed" != "$ZIZMOR_VERSION" ]; then
    echo "lint-workflows: zizmor $installed found, CI uses $ZIZMOR_VERSION; results may differ." >&2
fi

mode="--offline"
[ -n "${GH_TOKEN:-}" ] && mode=""

# Workflow paths come from git, one per line, with no spaces, so word splitting is safe here.
status=0
# shellcheck disable=SC2086
actionlint $files || status=1
# shellcheck disable=SC2086
zizmor $mode --persona=pedantic --quiet $files || status=1
if [ -n "$sarif" ]; then
    # shellcheck disable=SC2086
    zizmor $mode --persona=pedantic --quiet --format=sarif $files > "$sarif" || true
fi

if [ "$status" -ne 0 ]; then
    echo >&2
    echo "lint-workflows: fix the findings above, or suppress a false positive on its line" >&2
    echo "lint-workflows: with '# zizmor: ignore[<audit>]' and a reason." >&2
fi
exit "$status"
