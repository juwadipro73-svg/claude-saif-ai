/*
 * SAIF AI - storage cleanup for the Linux workspace.
 * Copyright (C) 2026 SAIF AI contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.example.storage

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * One folder that can be removed without losing any work, because a tool downloads or rebuilds it again.
 *
 * @property path the folder itself
 * @property kind short machine name, for example "node_modules"
 * @property projectDir the project that owns the folder, or null for a cache shared by every project
 * @property restoreCommand what brings the folder back
 * @property deleteSelf false = only the children are removed and the folder stays
 * @property keepChildren names directly inside [path] that are never removed (for example "lock" in the apt cache)
 * @property childNamePrefixes when not empty, only children whose name starts with one of these are removed
 * @property minAgeMs only children older than this are removed (used for the shared temp folder); 0 = no age limit
 * @property onlyChildren when not empty, only sub-folders with exactly these names are removed; every file and
 * every other folder stays (used for Gradle's build/ folder, which also holds the finished products)
 */
data class CleanableItem(
    val path: File,
    val kind: String,
    val projectDir: File?,
    val restoreCommand: String,
    val deleteSelf: Boolean = true,
    val keepChildren: Set<String> = emptySet(),
    val childNamePrefixes: List<String> = emptyList(),
    val minAgeMs: Long = 0L,
    val onlyChildren: Set<String> = emptySet()
)

/** A project and everything removable inside it. */
data class ProjectScan(
    val projectDir: File,
    val items: List<CleanableItem>,
    /** Newest modification time of the project's own files (removable folders and .git are not counted). */
    val lastSourceChangeMs: Long
)

data class RemovedItem(val item: CleanableItem, val freedBytes: Long)

data class SkippedProject(val projectDir: File, val reason: String)

data class CleanupResult(
    val removed: List<RemovedItem>,
    val skipped: List<SkippedProject>,
    val errors: List<String>,
    val sharedCachesSkippedReason: String? = null,
    /** True when the clean-up ended early because the user started working again. */
    val stopped: Boolean = false
) {
    val freedBytes: Long get() = removed.sumOf { it.freedBytes }
}

data class CleanupOptions(
    /** A project is cleaned only when none of its own files changed during this time. */
    val minIdleMs: Long,
    /** The user pressed "clean now": the idle time is ignored, running programs are still respected. */
    val ignoreIdle: Boolean = false,
    val includeSharedCaches: Boolean = true
)

/** What is running in the Linux environment right now. A project with a running program is left alone. */
interface BusyProbe {
    /** Working directories of the live processes. */
    fun workingDirs(): List<File>

    /** True while a package manager or build tool is running (its caches must not be removed under it). */
    fun packageToolRunning(): Boolean
}

/** Reads /proc. Processes of other apps are not readable and are ignored, which is what is wanted. */
class ProcBusyProbe(private val procDir: File = File("/proc")) : BusyProbe {
    private fun pids(): List<File> =
        procDir.listFiles { f -> f.isDirectory && f.name.all(Char::isDigit) }?.toList().orEmpty()

    override fun workingDirs(): List<File> = pids().mapNotNull { pid ->
        try {
            Files.readSymbolicLink(File(pid, "cwd").toPath()).toFile()
        } catch (_: Exception) {
            null
        }
    }

    override fun packageToolRunning(): Boolean = pids().any { pid ->
        val commandLine = try {
            File(pid, "cmdline").readBytes()
        } catch (_: Exception) {
            return@any false
        }
        // Arguments are separated by NUL. Linux programs in the app are started through a dynamic loader
        // ("ld-linux.so --library-path <dir> /usr/bin/node /usr/bin/npm install"), so the tool is not always the
        // first word: the first few words are compared with the tool names. A wrong match only postpones the
        // clean-up of the shared caches; it never deletes anything.
        String(commandLine, Charsets.UTF_8).split('\u0000').take(COMMAND_WORDS_CHECKED).any { argument ->
            argument.substringAfterLast('/') in PACKAGE_TOOLS
        }
    }

    companion object {
        const val COMMAND_WORDS_CHECKED = 8

        val PACKAGE_TOOLS = setOf(
            "npm", "npx", "pnpm", "yarn", "bun", "pip", "pip3", "pipx", "poetry", "uv",
            "gradle", "gradlew", "java", "mvn", "cargo", "go", "apt", "apt-get", "dpkg", "composer", "flutter", "dart"
        )
    }
}

/**
 * Finds and removes re-downloadable folders in the Linux workspace: dependency folders inside projects
 * (node_modules, Python virtual environments, Gradle build intermediates ...) and package-manager caches.
 *
 * Safety rules, each covered by a unit test:
 *  - a folder is removed only when the file that can rebuild it is next to it (package.json, build.gradle ...);
 *  - symbolic links are never followed and never removed, so nothing outside the scanned folders is touched;
 *  - `.git` is never entered; source files, `.env` files and build outputs (build/outputs) are never removed;
 *  - every path is checked again against the allowed roots immediately before it is deleted;
 *  - a folder is first renamed out of the way in one step and deleted afterwards, so a tool never finds a
 *    half-removed `node_modules`: it is either complete or gone. A renamed folder that an interrupted run left
 *    behind is removed by the next run.
 */
class WorkspaceModuleCleaner(
    /** Folders whose direct sub-folders are projects (the workspace, the Linux home). */
    private val projectParents: List<File>,
    /** Home folders that hold package-manager caches (.npm, .cache, .gradle ...). */
    private val homeDirs: List<File> = emptyList(),
    /** Root of the Linux file system (for the apt caches), or null. */
    private val linuxRoot: File? = null,
    /** The shared temp folder, or null. */
    private val tempDir: File? = null,
    private val probe: BusyProbe = ProcBusyProbe(),
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private val allowedRoots: List<Path> = (projectParents + homeDirs + listOfNotNull(linuxRoot, tempDir))
        .mapNotNull { realPathOrNull(it.toPath()) }

    // ------------------------------------------------------------------------------------------------ scan

    /** Every project below the project parents that contains at least one removable folder. */
    fun scanProjects(): List<ProjectScan> = projectParents.flatMap { scanParent(it) }

    private fun scanParent(parent: File): List<ProjectScan> {
        val root = parent.toPath()
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return emptyList()
        val itemsByProject = LinkedHashMap<Path, MutableList<CleanableItem>>()
        val newestChange = HashMap<Path, Long>()
        var visited = 0

        /** A removable folder directly in the parent belongs to the parent; deeper ones to the first folder below it. */
        fun ownerOfItem(path: Path): Path {
            val relative = root.relativize(path)
            return if (relative.nameCount <= 1) root else root.resolve(relative.getName(0))
        }

        /** A plain folder directly in the parent is a project of its own; a loose file belongs to the parent. */
        fun ownerOfEntry(path: Path, isDirectory: Boolean): Path {
            val relative = root.relativize(path)
            return if (relative.nameCount >= 2 || (isDirectory && relative.nameCount == 1)) root.resolve(relative.getName(0)) else root
        }

        fun noteChange(path: Path, isDirectory: Boolean, attrs: BasicFileAttributes) {
            val owner = ownerOfEntry(path, isDirectory)
            val time = attrs.lastModifiedTime().toMillis()
            if (time > (newestChange[owner] ?: 0L)) newestChange[owner] = time
        }

        try {
            Files.walkFileTree(root, emptySet(), MAX_DEPTH, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (++visited > MAX_ENTRIES) return FileVisitResult.TERMINATE
                    if (dir == root) return FileVisitResult.CONTINUE
                    val name = dir.fileName.toString()
                    val directChild = dir.parent == root
                    if (name.startsWith(TOMBSTONE_PREFIX)) {
                        itemsByProject.getOrPut(ownerOfItem(dir)) { mutableListOf() }.add(leftover(dir.toFile(), ownerOfItem(dir).toFile()))
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    // Hidden folders directly in a home folder are caches and settings, never projects.
                    if (directChild && (name.startsWith(".") || name in NEVER_A_PROJECT)) return FileVisitResult.SKIP_SUBTREE
                    if (name == ".git") return FileVisitResult.SKIP_SUBTREE
                    val owner = ownerOfItem(dir)
                    val item = matchProjectFolder(dir, name, owner.toFile())
                    if (item != null) {
                        itemsByProject.getOrPut(owner) { mutableListOf() }.add(item)
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    // Never look inside package folders: their nested files only look like projects.
                    if (name in OPAQUE_FOLDERS) return FileVisitResult.SKIP_SUBTREE
                    noteChange(dir, true, attrs)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (++visited > MAX_ENTRIES) return FileVisitResult.TERMINATE
                    if (!attrs.isSymbolicLink) noteChange(file, false, attrs)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
            })
        } catch (_: IOException) {
            // An unreadable folder yields a partial scan; whatever was found is still valid.
        }
        return itemsByProject.map { (project, items) ->
            ProjectScan(project.toFile(), items, newestChange[project] ?: 0L)
        }
    }

    /** A folder that an earlier run renamed for removal and could not finish deleting. Nothing uses it. */
    private fun leftover(dir: File, project: File?) = CleanableItem(
        path = dir, kind = KIND_LEFTOVER, projectDir = project,
        restoreCommand = "not needed: left behind by an interrupted clean-up"
    )

    private fun hasFile(dir: Path, vararg names: String): Boolean =
        names.any { Files.isRegularFile(dir.resolve(it), LinkOption.NOFOLLOW_LINKS) }

    /** Returns the removal rule for [dir], or null when it must be kept. */
    private fun matchProjectFolder(dir: Path, name: String, project: File): CleanableItem? {
        val parent = dir.parent ?: return null
        fun item(kind: String, restore: String, only: Set<String> = emptySet()) = CleanableItem(
            path = dir.toFile(), kind = kind, projectDir = project, restoreCommand = restore,
            deleteSelf = only.isEmpty(), onlyChildren = only
        )
        return when (name) {
            "node_modules" -> if (hasFile(parent, "package.json")) {
                val install = when {
                    hasFile(parent, "pnpm-lock.yaml") -> "pnpm install"
                    hasFile(parent, "yarn.lock") -> "yarn install"
                    hasFile(parent, "bun.lockb", "bun.lock") -> "bun install"
                    else -> "npm install"
                }
                item("node_modules", install)
            } else null

            ".venv", "venv", "env" -> {
                // Only a real virtual environment, and only when its package list is written down in the project.
                val isVenv = Files.isRegularFile(dir.resolve("pyvenv.cfg"), LinkOption.NOFOLLOW_LINKS)
                val restore = when {
                    !isVenv -> null
                    hasFile(parent, "requirements.txt") -> "python3 -m venv $name && $name/bin/pip install -r requirements.txt"
                    hasFile(parent, "pyproject.toml", "setup.py") -> "python3 -m venv $name && $name/bin/pip install -e ."
                    hasFile(parent, "Pipfile") -> "pipenv install"
                    else -> null
                }
                restore?.let { item("python-venv", it) }
            }

            "__pycache__", ".pytest_cache", ".mypy_cache", ".ruff_cache" ->
                item("python-cache", "recreated automatically by Python")

            ".gradle" -> if (hasFile(parent, "settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts", "gradlew")) {
                item("gradle-project-cache", "recreated automatically by the next Gradle build")
            } else null

            // Gradle module: only the intermediates go. The finished products stay: build/outputs (APK, AAB),
            // build/libs (JAR), build/distributions, reports, and every file placed directly in build/ (the in-app
            // APK builder copies the signed APK there).
            "build" -> if (hasFile(parent, "build.gradle", "build.gradle.kts")) {
                item("gradle-build", "./gradlew assembleDebug", only = GRADLE_INTERMEDIATES)
            } else null

            // Next.js keeps its finished build in .next as well: only the compiler cache inside it goes.
            ".next" -> if (hasFile(parent, "package.json")) {
                item("js-build-cache", "recreated automatically by the next build or dev run", only = setOf("cache"))
            } else null

            ".nuxt", ".svelte-kit", ".turbo", ".parcel-cache", ".vite", ".angular", ".docusaurus" ->
                if (hasFile(parent, "package.json")) item("js-build-cache", "recreated automatically by the next build or dev run") else null

            ".dart_tool" -> if (hasFile(parent, "pubspec.yaml")) item("dart-tool", "flutter pub get") else null

            "vendor" -> if (hasFile(parent, "composer.json") && hasFile(dir, "autoload.php")) item("composer-vendor", "composer install") else null

            else -> null
        }
    }

    /** Package-manager caches that every project shares. All of them are downloaded again on demand. */
    fun scanSharedCaches(): List<CleanableItem> {
        val found = mutableListOf<CleanableItem>()
        val seenLeftovers = HashSet<File>()
        fun add(dir: File, kind: String, restore: String, deleteSelf: Boolean = true, keep: Set<String> = emptySet(),
                prefixes: List<String> = emptyList(), minAgeMs: Long = 0L) {
            // A cache folder that an interrupted run had already renamed lies next to where the cache was.
            if (deleteSelf) {
                dir.parentFile?.listFiles { f ->
                    f.name.startsWith(TOMBSTONE_PREFIX) && Files.isDirectory(f.toPath(), LinkOption.NOFOLLOW_LINKS)
                }?.forEach { if (seenLeftovers.add(it)) found += leftover(it, null) }
            }
            if (Files.isDirectory(dir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                found += CleanableItem(dir, kind, null, restore, deleteSelf, keep, prefixes, minAgeMs)
            }
        }
        for (home in homeDirs) {
            add(File(home, ".npm/_cacache"), "npm-cache", "downloaded again by npm when needed")
            add(File(home, ".npm/_npx"), "npm-cache", "downloaded again by npx when needed")
            add(File(home, ".npm/_logs"), "npm-cache", "not needed")
            add(File(home, ".cache"), "user-cache", "recreated by each tool when needed", deleteSelf = false)
            add(File(home, ".gradle/caches"), "gradle-cache", "downloaded again by the next Gradle build")
            add(File(home, ".gradle/daemon"), "gradle-cache", "recreated by Gradle")
            add(File(home, ".gradle/wrapper/dists"), "gradle-cache", "downloaded again by ./gradlew")
            add(File(home, ".m2/repository"), "maven-cache", "downloaded again by the next Maven or Gradle build")
            add(File(home, ".cargo/registry/cache"), "cargo-cache", "downloaded again by cargo")
            add(File(home, ".cargo/registry/src"), "cargo-cache", "unpacked again by cargo")
            add(File(home, "go/pkg/mod"), "go-module-cache", "downloaded again by go build")
            add(File(home, ".local/share/pnpm/store"), "pnpm-store", "downloaded again by pnpm install")
            add(File(home, ".yarn/cache"), "yarn-cache", "downloaded again by yarn install")
            add(File(home, ".bun/install/cache"), "bun-cache", "downloaded again by bun install")
            add(File(home, ".pub-cache"), "pub-cache", "downloaded again by flutter pub get")
            add(File(home, ".composer/cache"), "composer-cache", "downloaded again by composer install")
        }
        linuxRoot?.let { root ->
            // Downloaded .deb files of packages that are already installed, and the package index.
            add(File(root, "var/cache/apt/archives"), "apt-archives", "not needed: the packages stay installed",
                deleteSelf = false, keep = setOf("lock", "partial"))
            add(File(root, "var/cache/apt/archives/partial"), "apt-archives", "not needed", deleteSelf = false)
            add(File(root, "var/lib/apt/lists"), "apt-lists", "apt update",
                deleteSelf = false, keep = setOf("lock", "partial", "auxfiles"))
        }
        tempDir?.let { tmp ->
            // Only leftovers of package tools, and only old ones: sockets and live temp files of running programs stay.
            add(tmp, "temp-leftovers", "not needed", deleteSelf = false, prefixes = TEMP_PREFIXES, minAgeMs = TEMP_MIN_AGE_MS)
        }
        return found
    }

    // --------------------------------------------------------------------------------------------- cleanup

    /**
     * Scans and removes. Projects that were changed recently or have a running program are skipped.
     *
     * @param shouldStop asked before every project and every cache; when it answers true (the user started
     * working again) nothing further is removed and the result says [CleanupResult.stopped].
     */
    fun clean(options: CleanupOptions, shouldStop: () -> Boolean = { false }): CleanupResult {
        val removed = mutableListOf<RemovedItem>()
        val skipped = mutableListOf<SkippedProject>()
        val errors = mutableListOf<String>()
        val busyDirs = try { probe.workingDirs().mapNotNull { realPathOrNull(it.toPath()) } } catch (_: Exception) { emptyList() }
        val currentTime = now()
        fun remove(item: CleanableItem, countEmpty: Boolean) {
            val freed = delete(item, currentTime, errors)
            if (freed > 0L || (countEmpty && freed == 0L)) removed += RemovedItem(item, freed)
        }

        for (scan in scanProjects()) {
            if (shouldStop()) return CleanupResult(removed, skipped, errors, stopped = true)
            val projectPath = realPathOrNull(scan.projectDir.toPath()) ?: continue
            // What an interrupted run left behind is used by nothing, so it goes even from a busy project.
            val (leftovers, items) = scan.items.partition { it.kind == KIND_LEFTOVER }
            leftovers.forEach { remove(it, countEmpty = true) }
            if (items.isEmpty()) continue
            val idleFor = currentTime - scan.lastSourceChangeMs
            when {
                busyDirs.any { it.startsWith(projectPath) } ->
                    skipped += SkippedProject(scan.projectDir, "a program is running in this project")
                !options.ignoreIdle && idleFor < options.minIdleMs ->
                    skipped += SkippedProject(scan.projectDir, "changed recently")
                else -> items.forEach { remove(it, countEmpty = true) }
            }
        }

        var sharedSkipped: String? = null
        if (options.includeSharedCaches) {
            val toolRunning = try { probe.packageToolRunning() } catch (_: Exception) { false }
            if (toolRunning) {
                sharedSkipped = "a package manager or build is running"
            } else {
                for (item in scanSharedCaches()) {
                    if (shouldStop()) return CleanupResult(removed, skipped, errors, stopped = true)
                    remove(item, countEmpty = false)
                }
            }
        }
        return CleanupResult(removed, skipped, errors, sharedSkipped)
    }

    /** Removes one item. Returns the bytes freed, or -1 when the item was refused. */
    internal fun delete(item: CleanableItem, currentTime: Long, errors: MutableList<String>): Long {
        val path = item.path.toPath()
        if (!isDeletable(path)) {
            errors += "refused (outside the workspace): ${item.path}"
            return -1L
        }
        if (item.deleteSelf && item.keepChildren.isEmpty() && item.childNamePrefixes.isEmpty() && item.onlyChildren.isEmpty()) {
            return deleteTree(detach(path), errors)
        }
        var freed = 0L
        val children = try {
            Files.newDirectoryStream(path).use { stream -> stream.toList() }
        } catch (e: IOException) {
            errors += "cannot list ${item.path}: ${e.message}"
            return 0L
        }
        for (child in children) {
            val name = child.fileName.toString()
            if (name in item.keepChildren) continue
            if (item.childNamePrefixes.isNotEmpty() && item.childNamePrefixes.none { name.startsWith(it) }) continue
            if (item.onlyChildren.isNotEmpty() &&
                (name !in item.onlyChildren || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS))) continue
            if (item.minAgeMs > 0L) {
                val modified = try {
                    Files.getLastModifiedTime(child, LinkOption.NOFOLLOW_LINKS).toMillis()
                } catch (_: IOException) {
                    continue
                }
                if (currentTime - modified < item.minAgeMs) continue
            }
            freed += deleteTree(child, errors)
        }
        return freed
    }

    /**
     * Renames the folder out of the way in one atomic step and returns its new place, in the same parent folder.
     * From that moment the project has no `node_modules` at all, instead of a half-deleted one, and a new install
     * can start at once while the old files are still being deleted. When renaming is not possible the folder is
     * deleted where it is.
     */
    private fun detach(path: Path): Path {
        val name = path.fileName?.toString() ?: return path
        if (name.startsWith(TOMBSTONE_PREFIX)) return path
        val stamp = java.lang.Long.toString(now(), 36)
        for (attempt in 0 until 20) {
            val target = path.resolveSibling("$TOMBSTONE_PREFIX$name-$stamp-$attempt")
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) continue
            return try {
                Files.move(path, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: IOException) {
                path
            } catch (_: UnsupportedOperationException) {
                path
            }
        }
        return path
    }

    /** True when [path] is a real folder strictly inside one of the allowed roots. */
    private fun isDeletable(path: Path): Boolean {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) return false
        val real = realPathOrNull(path) ?: return false
        return allowedRoots.any { root -> real != root && real.startsWith(root) } || tempDirIs(real)
    }

    /** The temp folder itself is listed as an item (only matching old children are removed from it). */
    private fun tempDirIs(real: Path): Boolean = tempDir?.let { realPathOrNull(it.toPath()) == real } ?: false

    /** Deletes [start] and everything in it without following links. Returns the size of the removed files. */
    private fun deleteTree(start: Path, errors: MutableList<String>): Long {
        var freed = 0L
        if (!Files.exists(start, LinkOption.NOFOLLOW_LINKS)) return 0L
        try {
            Files.walkFileTree(start, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    // Go's module cache and some archives are read-only: make the folder writable so it can be emptied.
                    if (!Files.isWritable(dir)) dir.toFile().setWritable(true, true)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val size = if (attrs.isRegularFile) attrs.size() else 0L
                    if (tryDelete(file)) freed += size else errors += "cannot delete $file"
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    // An unreadable entry (or a folder that could not be opened): try to remove it directly.
                    if (!tryDelete(file)) errors += "cannot delete $file: ${exc.message}"
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (!tryDelete(dir)) errors += "cannot delete folder $dir"
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: IOException) {
            errors += "cleanup stopped in $start: ${e.message}"
        }
        return freed
    }

    private fun tryDelete(path: Path): Boolean = try {
        Files.deleteIfExists(path)
        true
    } catch (_: IOException) {
        try {
            path.parent?.toFile()?.setWritable(true, true)
            path.toFile().setWritable(true, true)
            Files.deleteIfExists(path)
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun realPathOrNull(path: Path): Path? = try {
        path.toRealPath()
    } catch (_: IOException) {
        null
    }

    companion object {
        /** Deep enough for monorepos (packages/app/android/app/build), shallow enough to stay fast. */
        const val MAX_DEPTH = 9

        /** Upper bound of entries looked at per parent folder, so a huge tree cannot stall the app. */
        const val MAX_ENTRIES = 400_000

        const val TEMP_MIN_AGE_MS = 60L * 60L * 1000L

        /**
         * The sub-folders of a Gradle build/ folder that only hold intermediate files. Everything else in build/
         * (outputs, libs, distributions, reports, loose files) is a finished product or a report and stays.
         */
        val GRADLE_INTERMEDIATES = setOf(
            "intermediates", "tmp", "generated", "kotlin", "classes", "resources", "snapshot", "kspCaches",
            "kotlinToolingMetadata", "compileSync", "processedResources"
        )

        /** Name prefix of a folder that is being removed (see [detach]). */
        const val TOMBSTONE_PREFIX = ".saif-removing-"

        const val KIND_LEFTOVER = "leftover"

        /** Folders directly inside a home folder that are not projects. */
        val NEVER_A_PROJECT = setOf("workspace", "uploads", "go", "snap", "Android")

        /** Never entered while looking for projects. */
        val OPAQUE_FOLDERS = setOf("node_modules", "site-packages", "__pycache__", ".venv", "venv", "vendor", "Pods")

        val TEMP_PREFIXES = listOf(
            "npm-", "pip-", "pip_", "yarn-", "pnpm-", "v8-compile-cache", "node-compile-cache", "go-build",
            "cargo-install", "pytest-of-", "gradle", "kotlin-daemon", "jiti", "metro-", "tsx-", "esbuild-"
        )

        /** "1.2 GB", "340 MB" ... for messages to the user. */
        fun formatBytes(bytes: Long): String {
            val value = bytes.coerceAtLeast(0L).toDouble()
            return when {
                value >= 1024.0 * 1024.0 * 1024.0 -> String.format(java.util.Locale.US, "%.2f GB", value / (1024.0 * 1024.0 * 1024.0))
                value >= 1024.0 * 1024.0 -> String.format(java.util.Locale.US, "%.1f MB", value / (1024.0 * 1024.0))
                value >= 1024.0 -> String.format(java.util.Locale.US, "%.0f KB", value / 1024.0)
                else -> "${bytes.coerceAtLeast(0L)} B"
            }
        }
    }
}
