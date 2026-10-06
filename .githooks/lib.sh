#!/bin/sh
# Shared helpers for the git hooks. Sourced, not executed.

# Makes sure a JDK is reachable. Git clients such as Android Studio often start hooks
# without the shell profile, so fall back to well known install locations.
ensure_java() {
    # Check that java actually runs: macOS ships a /usr/bin/java stub that exists on PATH
    # but fails with "Unable to locate a Java Runtime" when no JDK is installed.
    if [ -n "$JAVA_HOME" ] && "$JAVA_HOME/bin/java" -version >/dev/null 2>&1; then
        return 0
    fi
    if command -v java >/dev/null 2>&1 && java -version >/dev/null 2>&1; then
        return 0
    fi
    # Homebrew's keg root also has a working bin/java, but it is not a JDK home: the Kotlin
    # compiler fails there with "No class roots are found in the JDK path". Use libexec.
    for candidate in \
        /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
        /usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
        /opt/homebrew/opt/openjdk@17 \
        /usr/local/opt/openjdk@17 \
        /usr/lib/jvm/java-17-openjdk \
        "/Applications/Android Studio.app/Contents/jbr/Contents/Home"; do
        if [ -x "$candidate/bin/java" ] && "$candidate/bin/java" -version >/dev/null 2>&1; then
            JAVA_HOME="$candidate"
            export JAVA_HOME
            return 0
        fi
    done
    return 1
}

# Runs ./gradlew with the given tasks, or skips with a notice when no JDK is available.
# CI runs the same checks, so a skipped local run is never the last line of defence.
run_gradle() {
    if ! ensure_java; then
        echo "git hook: no JDK found, skipping local checks (CI will run them)." >&2
        return 0
    fi
    ./gradlew --quiet "$@"
}
