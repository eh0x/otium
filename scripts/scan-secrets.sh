#!/bin/sh
# Scans for committed secrets with gitleaks, using .gitleaks.toml (default rules plus project
# settings).
#
# usage: scripts/scan-secrets.sh [--staged] [--sarif <file>]
#   no arguments    scan every commit in the history (pre-push hook, `scanSecrets`, CI)
#   --staged        scan only the changes staged for commit (pre-commit hook)
#   --sarif <file>  also write the findings, redacted, as a SARIF report (CI artifact)
#
# Findings are printed with the secret redacted. A real secret is never allowlisted: remove it and
# rotate it. For a false positive, add a `gitleaks:allow` comment on the line, or add the finding's
# fingerprint to .gitleaksignore with a comment giving the reason. Needs gitleaks
# (`brew install gitleaks`). Without it the script skips locally with a notice, and fails when CI
# is set, so CI stays the gate.

# Keep in step with GITLEAKS_VERSION in .github/workflows/ci.yml.
GITLEAKS_VERSION="8.30.1"

cd "$(dirname "$0")/.." || exit 1

# Hooks and IDE terminals often start without the shell profile that puts Homebrew on PATH.
ensure_gitleaks() {
    command -v gitleaks >/dev/null 2>&1 && return 0
    for dir in /opt/homebrew/bin /usr/local/bin "$HOME/go/bin"; do
        if [ -x "$dir/gitleaks" ]; then
            PATH="$dir:$PATH"
            export PATH
            return 0
        fi
    done
    return 1
}

if ! ensure_gitleaks; then
    if [ -n "${CI:-}" ]; then
        echo "scan-secrets: gitleaks is required but was not found." >&2
        exit 1
    fi
    echo "scan-secrets: gitleaks not found, skipping local secret scan (CI will run it)." >&2
    echo "scan-secrets: install it with: brew install gitleaks" >&2
    exit 0
fi

# Rules change between releases, so a different version can disagree with CI. Warn, never block.
installed=$(gitleaks version 2>/dev/null | sed 's/^v//')
if [ "$installed" != "$GITLEAKS_VERSION" ]; then
    echo "scan-secrets: gitleaks $installed found, CI uses $GITLEAKS_VERSION; results may differ." >&2
fi

args=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        --staged) args="$args --pre-commit --staged" ;;
        --sarif)
            [ -n "${2:-}" ] || { echo "scan-secrets: --sarif needs a file path." >&2; exit 2; }
            args="$args --report-format sarif --report-path $2"
            shift
            ;;
        *) echo "scan-secrets: unknown argument: $1" >&2; exit 2 ;;
    esac
    shift
done
# shellcheck disable=SC2086
set -- $args

exec gitleaks git "$@" --config .gitleaks.toml --redact --no-banner --verbose --log-level warn .
