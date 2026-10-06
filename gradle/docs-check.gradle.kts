// Documentation drift check. Applied from the root build.gradle.kts.
//
// Fails when the live docs (README.md, CLAUDE.md, docs/STYLE_GUIDE.md) disagree with the
// repository: broken relative links, Gradle tasks that do not exist, file paths that do not
// exist, or key facts (application id, minSdk, JDK, modules) that differ from the build files.
// Design and plan documents describe the future as well as the present, so they only get the
// link check. See docs/STYLE_GUIDE.md, "Documentation".

val liveDocs = listOf("README.md", "CLAUDE.md", "docs/STYLE_GUIDE.md")

// Markdown that git tracks or has staged. Untracked scratch files are not part of the docs.
fun versionedMarkdown(root: File): List<String> = runCatching {
    val builder = ProcessBuilder(
        "git",
        "ls-files",
        "--cached",
        "--",
        "README.md",
        "CLAUDE.md",
        "docs",
    )
        .directory(root)
        .redirectErrorStream(true)
    // A Gradle daemon started from a git hook keeps GIT_INDEX_FILE and friends from that moment
    // (a temporary index that is gone later), which makes git see an empty repository.
    builder.environment().keys.removeAll { it.startsWith("GIT_") }
    val process = builder.start()
    val files = process.inputStream.bufferedReader().readLines()
    check(process.waitFor() == 0)
    files.filter { it.endsWith(".md") }
}.getOrElse { error ->
    logger.warn("git ls-files failed ($error); falling back to scanning docs/")
    liveDocs + fileTree("docs") { include("**/*.md") }.files.map { it.relativeTo(root).path }
}

tasks.register("checkDocs") {
    group = "verification"
    description = "Fails when the documentation has drifted from the repository."

    val root = rootDir
    val gradleTaskNames = provider {
        allprojects.flatMap { project -> project.tasks.names }.toSet()
    }
    val moduleNames = provider { subprojects.map { it.name } }

    doLast {
        val problems = mutableListOf<String>()
        val known = gradleTaskNames.get()
        val linkCheckedDocs = (liveDocs + versionedMarkdown(root)).distinct()

        fun read(path: String) = root.resolve(path).takeIf { it.isFile }?.readText()

        // 1. Relative markdown links must resolve. Links shown as examples inside code blocks
        //    or code spans are not links, so they are skipped.
        val link = Regex("""\[[^\]]*]\(([^)\s]+)\)""")
        val codeBlock = Regex("```.*?```", RegexOption.DOT_MATCHES_ALL)
        val codeSpan = Regex("`[^`\\n]*`")
        for (doc in linkCheckedDocs) {
            val file = root.resolve(doc)
            val text = file.takeIf { it.isFile }?.readText()
                ?.replace(codeBlock, "")
                ?.replace(codeSpan, "")
                ?: continue
            for (match in link.findAll(text)) {
                val target = match.groupValues[1].substringBefore('#')
                val external = target.startsWith("http") || target.startsWith("mailto:")
                if (target.isEmpty() || external) continue
                if (!file.parentFile.resolve(target).exists()) {
                    problems += "$doc: broken link to '$target'"
                }
            }
        }

        // 2. Gradle tasks mentioned as commands must exist.
        val gradlew = Regex("""\./gradlew\s+([^\n`]+)""")
        for (doc in liveDocs) {
            val text = read(doc) ?: continue
            for (match in gradlew.findAll(text)) {
                val command = match.groupValues[1].substringBefore('#')
                command.trim().split(Regex("\\s+"))
                    .filter { it.isNotEmpty() && !it.startsWith("-") && !it.startsWith("<") }
                    .forEach { token ->
                        if (token.substringAfterLast(':') !in known) {
                            problems += "$doc: './gradlew $token' is not a Gradle task"
                        }
                    }
            }
        }

        // 3. Repository paths in backticks must exist (live docs only, globs and placeholders skipped).
        val path = Regex("""`([A-Za-z0-9_.@/-]+/[A-Za-z0-9_.@/-]*)`""")
        for (doc in liveDocs) {
            val text = read(doc) ?: continue
            for (match in path.findAll(text)) {
                val candidate = match.groupValues[1].trimEnd('/')
                val looksLikePath = candidate.contains('/') && !candidate.startsWith("http") &&
                    !candidate.startsWith("app.") && !candidate.contains("...")
                val rooted = root.resolve(candidate)
                val underModule = listOf("app", "domain").any {
                    root.resolve("$it/$candidate").exists()
                }
                if (looksLikePath && !rooted.exists() && !underModule &&
                    !candidate.startsWith(".")
                ) {
                    problems += "$doc: path '$candidate' does not exist"
                }
            }
        }

        // 4. Key facts must match the build files.
        val readme = read("README.md").orEmpty()
        val appBuild = read("app/build.gradle.kts").orEmpty()

        fun fact(label: String, actual: String?, mustAppearInReadme: (String) -> String) {
            if (actual == null) {
                problems += "could not read $label from the build files"
            } else if (mustAppearInReadme(actual) !in readme) {
                problems += "README.md does not state $label '$actual' (build files say so)"
            }
        }
        fact(
            "application id",
            Regex("""applicationId\s*=\s*"([^"]+)"""").find(appBuild)?.groupValues?.get(1),
        ) {
            "`$it`"
        }
        fact("minSdk", Regex("""minSdk\s*=\s*(\d+)""").find(appBuild)?.groupValues?.get(1)) {
            "minSdk $it"
        }
        fact(
            "JDK version",
            Regex("""jvmToolchain\((\d+)\)""").find(appBuild)?.groupValues?.get(1),
        ) {
            "JDK $it"
        }
        fact(
            "compile SDK",
            Regex("""compileSdk\s*=\s*(\d+)""").find(appBuild)?.groupValues?.get(1),
        ) {
            "platform $it"
        }
        moduleNames.get().forEach { module ->
            if ("`:$module`" !in readme) problems += "README.md does not describe module ':$module'"
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "Documentation is out of date:\n" + problems.joinToString("\n") { "  - $it" } +
                    "\nUpdate the docs (see docs/STYLE_GUIDE.md, \"Documentation\").",
            )
        }
        logger.info("Checked: ${linkCheckedDocs.joinToString()}")
        logger.lifecycle("Documentation check passed (${linkCheckedDocs.size} files).")
    }
}
