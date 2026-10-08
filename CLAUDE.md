## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
  A Stop hook (`.claude/hooks/graphify-refresh.sh`) does this at the end of every turn in which a
  source file changed, so it is normally automatic. Run it by hand after editing outside Claude
  Code.

## Code style

Rules and enforcement are in `docs/STYLE_GUIDE.md` (baseline: the Android Kotlin style guide).

- Before committing run `./gradlew spotlessApply detekt`, and `./gradlew lint` when touching
  resources or the manifest. Do not hand-format against ktlint; accept its output.
- Commit messages use Conventional Commits (`type(scope): subject`, 72 characters at most).
  The `commit-msg` hook rejects anything else.
- Fix findings; do not add baselines or global rule disables. Suppress at the smallest scope with a
  comment giving the reason.
- Markdown follows `docs/MARKDOWN_GUIDE.md` (100 columns, `-` bullets, fenced code with a
  language). Run `scripts/lint-markdown.sh` after editing docs; the commit hook runs it too.
- Keep `:domain` free of Android and framework imports. Follow the layer rules in the design doc.
- JDK for local Gradle runs: Temurin 17 (`brew install --cask temurin@17`), installed in
  `/Library/Java/JavaVirtualMachines`, where Gradle's toolchain detection finds it. Plain
  `./gradlew` works; no `JAVA_HOME` prefix needed. Do not use Homebrew's keg-only `openjdk@17`:
  it is ad-hoc signed, and macOS refuses it access to files in `~/.gradle` ("Operation not
  permitted" on `*.lck` and `*.lock` files).
