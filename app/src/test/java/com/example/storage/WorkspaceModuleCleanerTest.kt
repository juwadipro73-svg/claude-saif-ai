package com.example.storage

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The cleaner deletes files on the user's phone, so every safety rule is pinned here:
 * only re-downloadable folders go, project files never do.
 */
class WorkspaceModuleCleanerTest {
    private lateinit var base: File
    private lateinit var workspace: File
    private lateinit var linuxRoot: File
    private lateinit var home: File
    private lateinit var temp: File
    private lateinit var outside: File

    private var busyDirs: List<File> = emptyList()
    private var toolRunning = false
    private var clock: Long = 0L

    private val probe = object : BusyProbe {
        override fun workingDirs(): List<File> = busyDirs
        override fun packageToolRunning(): Boolean = toolRunning
    }

    @Before fun setUp() {
        base = Files.createTempDirectory("saif-cleaner-test").toFile()
        workspace = File(base, "files/home").apply { mkdirs() }
        linuxRoot = File(base, "files/ubuntu").apply { mkdirs() }
        home = File(linuxRoot, "root").apply { mkdirs() }
        temp = File(base, "files/tmp").apply { mkdirs() }
        outside = File(base, "outside").apply { mkdirs() }
        clock = System.currentTimeMillis()
    }

    @After fun tearDown() {
        // Read-only test folders must be writable again before they can be removed.
        base.walkBottomUp().forEach { it.setWritable(true) }
        base.deleteRecursively()
    }

    private fun cleaner() = WorkspaceModuleCleaner(
        projectParents = listOf(workspace, home),
        homeDirs = listOf(home, workspace),
        linuxRoot = linuxRoot,
        tempDir = temp,
        probe = probe,
        now = { clock }
    )

    private fun file(parent: File, path: String, text: String = "x"): File =
        File(parent, path).apply { parentFile!!.mkdirs(); writeText(text) }

    private val manual = CleanupOptions(minIdleMs = 30 * 60_000L, ignoreIdle = true)
    private val automatic = CleanupOptions(minIdleMs = 30 * 60_000L)

    @Test fun nodeModulesAreRemovedAndTheProjectStays() {
        val project = File(workspace, "shop")
        val pkg = file(project, "package.json", "{}")
        val source = file(project, "src/index.js", "console.log(1)")
        val env = file(project, ".env", "SECRET=1")
        file(project, "node_modules/react/index.js", "12345")
        file(project, "node_modules/react/node_modules/dep/index.js", "123")

        val result = cleaner().clean(manual)

        assertFalse(File(project, "node_modules").exists())
        assertTrue(pkg.exists() && source.exists() && env.exists())
        assertEquals(8L, result.freedBytes)
        assertEquals("npm install", result.removed.single().item.restoreCommand)
        assertTrue(result.errors.isEmpty())
    }

    @Test fun lockFileSelectsTheRestoreCommand() {
        val project = File(workspace, "web")
        file(project, "package.json", "{}")
        file(project, "pnpm-lock.yaml")
        file(project, "node_modules/a.js")
        assertEquals("pnpm install", cleaner().scanProjects().single().items.single().restoreCommand)
    }

    @Test fun aFolderWithoutItsManifestIsKept() {
        // No package.json: nothing could download these files again, so they are not "modules".
        val loose = file(File(workspace, "notes"), "node_modules/keep.txt")
        // A Python environment whose package list is written nowhere cannot be rebuilt either.
        val venv = file(File(workspace, "py"), ".venv/pyvenv.cfg")
        file(File(workspace, "py"), ".venv/lib/python3.11/site-packages/pkg/a.py")
        // A web build folder is the finished product, not a cache.
        val webBuild = file(File(workspace, "site"), "build/index.html")
        file(File(workspace, "site"), "package.json", "{}")
        val dist = file(File(workspace, "site"), "dist/app.js")

        val result = cleaner().clean(manual)

        assertTrue(loose.exists() && venv.exists() && webBuild.exists() && dist.exists())
        assertTrue(result.removed.isEmpty())
    }

    @Test fun pythonEnvironmentIsRemovedOnlyWhenRequirementsExist() {
        val project = File(workspace, "bot")
        val requirements = file(project, "requirements.txt", "requests")
        val script = file(project, "main.py")
        val secrets = file(project, ".env", "TOKEN=1")
        file(project, "venv/pyvenv.cfg")
        file(project, "venv/lib/python3.11/site-packages/requests/api.py")
        file(project, "__pycache__/main.cpython-311.pyc")
        // A plain folder called "env" that is not a virtual environment holds the user's own files.
        val plainEnv = file(project, "env/production.yaml")

        val result = cleaner().clean(manual)

        assertFalse(File(project, "venv").exists())
        assertFalse(File(project, "__pycache__").exists())
        assertTrue(requirements.exists() && script.exists() && secrets.exists() && plainEnv.exists())
        assertTrue(result.removed.any { it.item.restoreCommand.contains("pip install -r requirements.txt") })
    }

    @Test fun gradleIntermediatesGoButTheBuiltApkStays() {
        val project = File(workspace, "android-app")
        file(project, "settings.gradle.kts")
        file(project, "build.gradle.kts")
        val moduleScript = file(project, "app/build.gradle.kts")
        val kotlin = file(project, "app/src/main/java/com/demo/build/Main.kt")
        file(project, ".gradle/8.9/fileHashes.bin")
        file(project, "app/build/intermediates/dex/classes.dex", "0123456789")
        file(project, "app/build/tmp/kotlin-classes/Main.class")
        file(project, "app/build/generated/source/BuildConfig.java")
        file(project, "app/build/kotlin/compileDebugKotlin/cache.bin")
        val apk = file(project, "app/build/outputs/apk/debug/app-debug.apk", "APK")

        val result = cleaner().clean(manual)

        assertFalse(File(project, ".gradle").exists())
        listOf("intermediates", "tmp", "generated", "kotlin").forEach { name ->
            assertFalse(name, File(project, "app/build/$name").exists())
        }
        assertTrue("the finished APK must survive", apk.exists())
        assertTrue(moduleScript.exists() && kotlin.exists())
        assertEquals(13L, result.removed.single { it.item.kind == "gradle-build" }.freedBytes)
    }

    @Test fun everyFinishedProductInAGradleBuildFolderStays() {
        val project = File(workspace, "calculator")
        file(project, "build.gradle")
        // The in-app APK builder copies the signed APK straight into build/.
        val builtInApp = file(project, "build/calculator.apk", "APK")
        val jar = file(project, "build/libs/calculator-1.0.jar", "JAR")
        val distribution = file(project, "build/distributions/calculator.zip", "ZIP")
        val report = file(project, "build/reports/tests/index.html")
        val bundle = file(project, "build/outputs/bundle/release/app.aab", "AAB")
        val unknownFolder = file(project, "build/my-notes/todo.txt")
        // A file that merely has the name of an intermediate folder is not a folder of intermediates.
        val fileNamedTmp = file(project, "build/tmp", "not a folder")
        file(project, "build/classes/java/main/Main.class", "1234")
        file(project, "build/resources/main/app.properties", "12")

        val result = cleaner().clean(manual)

        assertTrue(builtInApp.exists() && jar.exists() && distribution.exists() && report.exists() && bundle.exists())
        assertTrue(unknownFolder.exists() && fileNamedTmp.exists())
        assertFalse(File(project, "build/classes").exists())
        assertFalse(File(project, "build/resources").exists())
        assertEquals(6L, result.freedBytes)
    }

    @Test fun nextJsKeepsItsBuildAndLosesOnlyTheCompilerCache() {
        val project = File(workspace, "site")
        file(project, "package.json", "{}")
        val page = file(project, ".next/server/app/page.js", "PAGE")
        val buildId = file(project, ".next/BUILD_ID", "abc")
        file(project, ".next/cache/webpack/client-production/0.pack", "0123456789")
        // Tool caches of other frameworks hold nothing finished and go completely.
        file(project, ".turbo/cache/a.tar.zst", "12")
        file(project, ".svelte-kit/generated/root.js", "1")
        // Expo's folder holds device settings, not a cache.
        val expo = file(project, ".expo/settings.json", "{}")

        val result = cleaner().clean(manual)

        assertTrue(page.exists() && buildId.exists() && expo.exists())
        assertFalse(File(project, ".next/cache").exists())
        assertFalse(File(project, ".turbo").exists())
        assertFalse(File(project, ".svelte-kit").exists())
        assertEquals(13L, result.freedBytes)
    }

    @Test fun gitHistoryIsNeverEntered() {
        val project = File(workspace, "repo")
        file(project, "package.json", "{}")
        // Even a path inside .git that looks exactly like a module folder stays.
        file(project, ".git/modules/sub/package.json", "{}")
        val inGit = file(project, ".git/modules/sub/node_modules/x.js")
        val pack = file(project, ".git/objects/pack/pack-1.pack")
        file(project, "node_modules/x.js")

        cleaner().clean(manual)

        assertTrue(inGit.exists() && pack.exists())
        assertFalse(File(project, "node_modules").exists())
    }

    @Test fun symbolicLinksAreNeitherFollowedNorRemoved() {
        val precious = file(outside, "other-project/src/main.kt", "precious")
        val project = File(workspace, "linked")
        file(project, "package.json", "{}")
        file(project, "node_modules/real/index.js")
        // npm link: a package inside node_modules that points at another project's source.
        Files.createSymbolicLink(File(project, "node_modules/linked-pkg").toPath(), File(outside, "other-project").toPath())

        // A project whose node_modules is itself a link to a shared folder elsewhere.
        val shared = file(outside, "shared-modules/lib/index.js", "shared")
        val second = File(workspace, "second")
        file(second, "package.json", "{}")
        Files.createSymbolicLink(File(second, "node_modules").toPath(), File(outside, "shared-modules").toPath())

        // A whole project folder that is a link out of the workspace.
        file(outside, "external/package.json", "{}")
        val externalModule = file(outside, "external/node_modules/a.js")
        Files.createSymbolicLink(File(workspace, "external-link").toPath(), File(outside, "external").toPath())

        val result = cleaner().clean(manual)

        // The scan itself must not look through links (the delete step would refuse them as a second guard).
        assertEquals(listOf("node_modules"), result.removed.map { it.item.path.name })
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertFalse(File(project, "node_modules").exists())
        assertTrue("a linked package's target must be untouched", precious.exists())
        assertTrue("a linked node_modules must be untouched", shared.exists())
        assertTrue(Files.isSymbolicLink(File(second, "node_modules").toPath()))
        assertTrue("a linked project must be untouched", externalModule.exists())
    }

    @Test fun aProjectChangedRecentlyWaitsUntilItIsIdle() {
        val project = File(workspace, "active")
        file(project, "package.json", "{}")
        val source = file(project, "index.js")
        file(project, "node_modules/a.js")

        clock = source.lastModified() + 5 * 60_000L
        val early = cleaner().clean(automatic)
        assertTrue(File(project, "node_modules").exists())
        assertEquals("changed recently", early.skipped.single().reason)

        clock = System.currentTimeMillis() + 31 * 60_000L
        val later = cleaner().clean(automatic)
        assertFalse(File(project, "node_modules").exists())
        assertTrue(later.skipped.isEmpty())
        assertTrue(source.exists())
    }

    @Test fun aProjectWithARunningProgramIsLeftAlone() {
        val project = File(workspace, "serving")
        file(project, "package.json", "{}")
        file(project, "src/server.js")
        file(project, "node_modules/a.js")
        val idle = File(workspace, "idle")
        file(idle, "package.json", "{}")
        file(idle, "node_modules/a.js")
        busyDirs = listOf(File(project, "src"), File("/"))

        val result = cleaner().clean(manual)

        assertTrue("a dev server may be using these modules", File(project, "node_modules").exists())
        assertFalse(File(idle, "node_modules").exists())
        assertEquals(project.canonicalFile, result.skipped.single().projectDir.canonicalFile)
    }

    @Test fun sharedCachesAreEmptiedButSettingsAndToolsStay() {
        file(home, ".npm/_cacache/index-v5/aa/bb", "1234567890")
        file(home, ".cache/pip/http/a", "12345")
        file(home, ".gradle/caches/modules-2/files-2.1/lib.jar", "123")
        file(home, ".m2/repository/org/x/x.jar", "12")
        val gradleProperties = file(home, ".gradle/gradle.properties")
        val npmrc = file(home, ".npmrc")
        val sshKey = file(home, ".ssh/id_ed25519")
        val token = file(home, ".gemini/jetski-standalone-oauth-token")
        val claude = file(home, ".local/share/claude/versions/2.0/claude")
        val bridge = file(home, ".config/saif-bridge.env")
        val history = file(home, ".bash_history")
        file(linuxRoot, "var/cache/apt/archives/nodejs_20_arm64.deb", "1234")
        val aptLock = file(linuxRoot, "var/cache/apt/archives/lock", "")
        file(linuxRoot, "var/cache/apt/archives/partial/half.deb", "12")
        file(linuxRoot, "var/lib/apt/lists/ports.ubuntu.com_dists_noble_InRelease", "123456")
        val listsLock = file(linuxRoot, "var/lib/apt/lists/lock", "")
        val installedTool = file(linuxRoot, "usr/lib/node_modules/npm/package.json", "{}")
        file(linuxRoot, "usr/lib/node_modules/npm/node_modules/semver/index.js")
        val dpkgStatus = file(linuxRoot, "var/lib/dpkg/status")

        val result = cleaner().clean(manual)

        assertFalse(File(home, ".npm/_cacache").exists())
        assertTrue("the cache folder itself stays", File(home, ".cache").isDirectory)
        assertEquals(0, File(home, ".cache").list()!!.size)
        assertFalse(File(home, ".gradle/caches").exists())
        assertFalse(File(home, ".m2/repository").exists())
        assertFalse(File(linuxRoot, "var/cache/apt/archives/nodejs_20_arm64.deb").exists())
        assertFalse(File(linuxRoot, "var/cache/apt/archives/partial/half.deb").exists())
        assertFalse(File(linuxRoot, "var/lib/apt/lists/ports.ubuntu.com_dists_noble_InRelease").exists())
        assertTrue(aptLock.exists() && listsLock.exists() && File(linuxRoot, "var/cache/apt/archives/partial").isDirectory)
        assertTrue(gradleProperties.exists() && npmrc.exists() && sshKey.exists() && token.exists())
        assertTrue(claude.exists() && bridge.exists() && history.exists() && dpkgStatus.exists())
        assertTrue("globally installed tools are not project modules", installedTool.exists())
        assertTrue(File(linuxRoot, "usr/lib/node_modules/npm/node_modules/semver/index.js").exists())
        assertEquals(10L + 5L + 3L + 2L + 4L + 2L + 6L, result.freedBytes)
    }

    @Test fun sharedCachesWaitWhileAPackageManagerRuns() {
        val cached = file(home, ".npm/_cacache/x")
        toolRunning = true
        val result = cleaner().clean(manual)
        assertTrue(cached.exists())
        assertEquals("a package manager or build is running", result.sharedCachesSkippedReason)
    }

    @Test fun onlyOldPackageToolLeftoversLeaveTheTempFolder() {
        val oldLeftover = file(temp, "npm-1234/package/a.js")
        File(temp, "npm-1234").setLastModified(clock - 3 * 60 * 60_000L)
        val freshLeftover = file(temp, "pip-install-9/setup.py")
        val socketFolder = file(temp, "tmux-0/default")
        File(temp, "tmux-0").setLastModified(clock - 5 * 60 * 60_000L)
        val unknown = file(temp, "my-export.zip")
        File(temp, "my-export.zip").setLastModified(clock - 5 * 60 * 60_000L)

        cleaner().clean(manual)

        assertFalse(oldLeftover.exists())
        assertTrue(freshLeftover.exists() && socketFolder.exists() && unknown.exists())
        assertTrue(temp.isDirectory)
    }

    @Test fun hiddenHomeFoldersAndUploadsAreNotProjects() {
        val toolModule = file(home, ".local/share/some-tool/node_modules/a.js")
        file(home, ".local/share/some-tool/package.json", "{}")
        val upload = file(home, "uploads/site/node_modules/a.js")
        file(home, "uploads/site/package.json", "{}")

        cleaner().clean(manual)

        assertTrue(toolModule.exists() && upload.exists())
    }

    @Test fun aProjectDirectlyInTheWorkspaceRootIsHandled() {
        file(workspace, "package.json", "{}")
        val page = file(workspace, "index.html")
        file(workspace, "node_modules/a/index.js")

        cleaner().clean(manual)

        assertFalse(File(workspace, "node_modules").exists())
        assertTrue(page.exists() && workspace.isDirectory)
    }

    @Test fun readOnlyCacheFoldersAreRemoved() {
        val module = file(home, "go/pkg/mod/github.com/x/y@v1/y.go")
        module.setWritable(false)
        module.parentFile!!.setWritable(false)
        File(home, "go/pkg/mod/github.com").setWritable(false)

        val result = cleaner().clean(manual)

        assertFalse(File(home, "go/pkg/mod").exists())
        assertTrue(result.errors.toString(), result.errors.isEmpty())
    }

    @Test fun nothingOutsideTheWorkspaceCanBeDeleted() {
        val victim = file(outside, "node_modules/a.js")
        file(outside, "package.json", "{}")
        val errors = mutableListOf<String>()
        val item = CleanableItem(File(outside, "node_modules"), "node_modules", outside, "npm install")

        val freed = cleaner().delete(item, clock, errors)

        assertEquals(-1L, freed)
        assertTrue(victim.exists())
        assertTrue(errors.single().startsWith("refused"))
        // The roots themselves are refused as well.
        assertEquals(-1L, cleaner().delete(CleanableItem(workspace, "x", null, ""), clock, mutableListOf()))
        assertTrue(workspace.isDirectory)
    }

    @Test fun procProbeSeesRunningProgramsOfThisUser() {
        if (!File("/proc/self/cwd").exists()) return // not Linux: the probe has nothing to read
        val project = File(workspace, "live").apply { mkdirs() }
        // A script called "npm" stands in for the real tool; only the program name is looked at.
        val fakeNpm = file(base, "bin/npm", "#!/bin/sh\nread line\n").apply { setExecutable(true) } // waits on its input pipe
        val server = ProcessBuilder("sleep", "30").directory(project).start()
        val install = ProcessBuilder("/bin/sh", fakeNpm.absolutePath, "install").directory(base).start()
        try {
            val real = ProcBusyProbe()
            var dirs = emptyList<File>()
            var toolSeen = false
            // The children need a moment to start.
            for (attempt in 1..50) {
                dirs = real.workingDirs().map { it.canonicalFile }
                toolSeen = real.packageToolRunning()
                if (project.canonicalFile in dirs && toolSeen) break
                Thread.sleep(40)
            }
            assertTrue("working directory of a running program", project.canonicalFile in dirs)
            assertTrue("a running package manager", toolSeen)
        } finally {
            server.destroyForcibly()
            install.destroyForcibly()
        }
    }

    @Test fun aRemovedFolderLeavesNoRenamedCopyBehind() {
        val project = File(workspace, "shop")
        file(project, "package.json", "{}")
        file(project, "node_modules/react/index.js", "12345")
        file(home, ".npm/_cacache/index-v5/aa", "1234")

        val result = cleaner().clean(manual)

        // The folder is renamed first and then deleted: afterwards neither it nor the renamed copy exists.
        assertEquals(listOf("package.json"), project.list()!!.toList())
        assertEquals(emptyList<String>(), File(home, ".npm").list()!!.toList())
        assertEquals(9L, result.freedBytes)
        // The result names the folder the user knows, not the temporary name.
        assertEquals(File(project, "node_modules"), result.removed.first { it.item.kind == "node_modules" }.item.path)
        assertTrue(result.errors.isEmpty())
    }

    @Test fun leftoversOfAnInterruptedRunAreRemovedEvenFromABusyProject() {
        val prefix = WorkspaceModuleCleaner.TOMBSTONE_PREFIX
        // The app was killed half-way through an earlier clean-up: a renamed folder is still lying in the project.
        val project = File(workspace, "shop")
        file(project, "package.json", "{}")
        val source = file(project, "src/index.js")
        file(project, "$prefix" + "node_modules-abc-0/react/index.js", "12345")
        // The user has come back and reinstalled: the new modules must stay, the project is in use.
        val fresh = file(project, "node_modules/react/index.js", "new")
        busyDirs = listOf(File(project, "src"))
        // Leftovers in the workspace root itself and next to a shared cache.
        file(workspace, "package.json", "{}")
        file(workspace, "$prefix" + "node_modules-abc-1/x.js", "12")
        file(home, ".npm/$prefix" + "_cacache-abc-0/blob", "123")
        // Not leftovers: a file with the prefix, and a folder that only contains the text somewhere in its name.
        val prefixedFile = file(project, "$prefix" + "notes.txt")
        val lookalike = file(project, "my$prefix" + "notes/keep.txt")

        val result = cleaner().clean(automatic)

        assertFalse(File(project, "$prefix" + "node_modules-abc-0").exists())
        assertFalse(File(workspace, "$prefix" + "node_modules-abc-1").exists())
        assertFalse(File(home, ".npm/$prefix" + "_cacache-abc-0").exists())
        assertTrue(fresh.exists() && source.exists() && prefixedFile.exists() && lookalike.exists())
        assertEquals(10L, result.freedBytes)
        assertEquals("a program is running in this project", result.skipped.single { it.projectDir == project }.reason)
        // Nothing has to be reinstalled because of a leftover, so the agent's log does not mention it.
        assertFalse(ModuleCleanupRules.renderLog(result, File(base, "files").absolutePath, "2026-11-12 10:00").contains(prefix))
    }

    @Test fun aProjectWithOnlyALeftoverIsNotReportedAsSkipped() {
        val project = File(workspace, "done")
        file(project, "package.json", "{}")
        file(project, WorkspaceModuleCleaner.TOMBSTONE_PREFIX + "node_modules-abc-0/a.js", "1")
        busyDirs = listOf(project)

        val result = cleaner().clean(automatic)

        assertEquals(listOf("package.json"), project.list()!!.toList())
        assertTrue(result.skipped.isEmpty())
    }

    @Test fun cleaningStopsWhenTheUserStartsWorkingAgain() {
        listOf("a", "b", "c").forEach { name ->
            file(File(workspace, name), "package.json", "{}")
            file(File(workspace, name), "node_modules/x.js", "1")
        }
        file(home, ".npm/_cacache/blob", "1")

        // Asked before anything is removed: nothing is touched.
        val stoppedAtOnce = cleaner().clean(manual, shouldStop = { true })
        assertTrue(stoppedAtOnce.stopped && stoppedAtOnce.removed.isEmpty())
        assertEquals(3, listOf("a", "b", "c").count { File(workspace, "$it/node_modules/x.js").exists() })

        // The user comes back after the first project was cleaned: the rest stays as it is.
        var asked = 0
        val stoppedLater = cleaner().clean(manual, shouldStop = { ++asked > 1 })
        assertTrue(stoppedLater.stopped)
        assertEquals(1, stoppedLater.removed.size)
        assertEquals(2, listOf("a", "b", "c").count { File(workspace, "$it/node_modules/x.js").exists() })
        assertTrue(File(home, ".npm/_cacache/blob").exists())

        // Without an interruption everything goes and the result is not marked as stopped.
        val finished = cleaner().clean(manual)
        assertFalse(finished.stopped)
        assertEquals(0, listOf("a", "b", "c").count { File(workspace, "$it/node_modules").exists() })
        assertFalse(File(home, ".npm/_cacache").exists())
    }

    @Test fun cleaningTheSharedCachesAlsoStopsWhenTheUserReturns() {
        file(home, ".npm/_cacache/blob", "1")
        file(home, ".m2/repository/lib.jar", "12")

        var asked = 0
        val result = cleaner().clean(manual, shouldStop = { ++asked > 1 })

        assertTrue(result.stopped)
        assertEquals(1, result.removed.size)
        // Exactly one of the two caches is gone; the other one was not started.
        assertEquals(1, listOf(".npm/_cacache", ".m2/repository").count { File(home, it).exists() })
    }

    @Test fun aPackageManagerStartedThroughALoaderIsRecognised() {
        val proc = File(base, "proc")
        fun process(pid: Int, vararg words: String) = file(proc, "$pid/cmdline", words.joinToString("\u0000") + "\u0000")
        // An editor with a file open, and a shell: no package manager is running.
        process(101, "/usr/bin/nano", "/root/workspace/shop/notes.txt")
        process(102, "/bin/bash", "-c", "cd /root/workspace && ls")
        file(proc, "self/cmdline", "npm") // not a process id: ignored
        assertFalse(ProcBusyProbe(proc).packageToolRunning())

        // Programs in the Linux environment run through a loader, so the tool is not the first word.
        process(
            103, "/data/files/glibc/lib/ld-linux-aarch64.so.1", "--library-path", "/data/files/glibc/lib",
            "/usr/bin/node", "/usr/bin/npm", "install"
        )
        assertTrue(ProcBusyProbe(proc).packageToolRunning())
    }

    @Test fun sizesAreReadable() {
        assertEquals("512 B", WorkspaceModuleCleaner.formatBytes(512))
        assertEquals("2 KB", WorkspaceModuleCleaner.formatBytes(2048))
        assertEquals("1.5 MB", WorkspaceModuleCleaner.formatBytes(1572864))
        assertEquals("2.00 GB", WorkspaceModuleCleaner.formatBytes(2L * 1024 * 1024 * 1024))
    }
}
