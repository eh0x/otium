#!/bin/sh
# Scans the shipped dependencies for known vulnerabilities with OSV-Scanner, using the CycloneDX
# SBOM that `./gradlew cyclonedxBom` builds from the release runtime classpath of each module.
# Run `./gradlew scanDependencies` to build the SBOM and scan it in one step.
#
# usage: scripts/scan-dependencies.sh [--sarif <file>]
#   no arguments    scan build/reports/cyclonedx/bom.json and print a table
#   --sarif <file>  also write the findings as a SARIF report (CI artifact, code scanning)
#
# A finding is fixed by upgrading the dependency, or its parent when it is transitive. When no fix
# exists yet, or the vulnerable code is unreachable, add an entry to osv-scanner.toml with a reason
# and an `ignoreUntil` date, so the decision is reviewed again. Needs osv-scanner
# (`brew install osv-scanner`). Without it the script skips locally with a notice, and fails when
# CI is set, so CI stays the gate.

# Keep in step with OSV_SCANNER_VERSION in .github/workflows/ci.yml and osv-scanner.yml.
OSV_SCANNER_VERSION="2.6.0"
SBOM="build/reports/cyclonedx/bom.json"

cd "$(dirname "$0")/.." || exit 1

# Hooks and IDE terminals often start without the shell profile that puts Homebrew on PATH.
ensure_osv_scanner() {
    command -v osv-scanner >/dev/null 2>&1 && return 0
    for dir in /opt/homebrew/bin /usr/local/bin "$HOME/go/bin"; do
        if [ -x "$dir/osv-scanner" ]; then
            PATH="$dir:$PATH"
            export PATH
            return 0
        fi
    done
    return 1
}

sarif=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        --sarif)
            [ -n "${2:-}" ] || { echo "scan-dependencies: --sarif needs a file path." >&2; exit 2; }
            sarif="$2"
            shift
            ;;
        *) echo "scan-dependencies: unknown argument: $1" >&2; exit 2 ;;
    esac
    shift
done

if ! ensure_osv_scanner; then
    if [ -n "${CI:-}" ]; then
        echo "scan-dependencies: osv-scanner is required but was not found." >&2
        exit 1
    fi
    echo "scan-dependencies: osv-scanner not found, skipping local scan (CI will run it)." >&2
    echo "scan-dependencies: install it with: brew install osv-scanner" >&2
    exit 0
fi

if [ ! -f "$SBOM" ]; then
    echo "scan-dependencies: $SBOM not found. Run ./gradlew cyclonedxBom first." >&2
    exit 1
fi

# The vulnerability database changes daily, so only a different scanner version is worth a note.
installed=$(osv-scanner --version 2>/dev/null | sed -n 's/^osv-scanner version: //p')
if [ "$installed" != "$OSV_SCANNER_VERSION" ]; then
    echo "scan-dependencies: osv-scanner $installed found, CI uses $OSV_SCANNER_VERSION." >&2
fi

osv-scanner scan --config osv-scanner.toml --lockfile "$SBOM"
status=$?

# The readable table above is the log. The SARIF report is written on every run, clean ones too:
# code scanning closes an alert only when a later report no longer lists it.
if [ -n "$sarif" ]; then
    osv-scanner scan --config osv-scanner.toml --lockfile "$SBOM" \
        --format sarif --output-file "$sarif" >/dev/null 2>&1
fi
exit "$status"
