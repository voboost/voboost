import org.gradle.api.tasks.options.Option
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.util.zip.CRC32
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.time.Duration
import java.time.Instant

plugins { java }

repositories { mavenCentral() }

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
    testRuntimeOnly("com.twelvemonkeys.imageio:imageio-webp:3.15.0")
}

java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }

sourceSets {
    main { java.setSrcDirs(listOf("tests/common", "tests/emulator/driver")) }
    test { java.setSrcDirs(listOf("tests/unit")) }
    create("integration") {
        java.setSrcDirs(listOf("tests/integration"))
        compileClasspath += sourceSets.main.get().output + configurations.testCompileClasspath.get()
        runtimeClasspath += output + sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    }
    create("emulator") {
        java.setSrcDirs(listOf("tests/emulator"))
        java.exclude("driver/**", "agents/agent-launcher/app/**")
        compileClasspath += sourceSets.main.get().output + configurations.testCompileClasspath.get()
        runtimeClasspath += output + sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    }
}

tasks.withType<Test>().configureEach {
    val tempRoot = layout.buildDirectory.dir("test-tmp").get().asFile
    systemProperty("java.io.tmpdir", tempRoot.absolutePath)
    providers.gradleProperty("checkRunId").orNull?.let { systemProperty("checkRunId", it) }
    doFirst { tempRoot.mkdirs() }
}

val unitTest = tasks.register<Test>("unitTest") {
    group = "verification"
    description = "Run root and component unit suites through their owning build tools."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
}
tasks.test { useJUnitPlatform() }
// This observer is a root test tool; it is never embedded in a product package.
tasks.register<Exec>("compileGuiBridge") {
    group = "verification"
    description = "Compile the scoped macOS native GUI test observer without launching GUI."
    val source = layout.projectDirectory.file("tests/emulator/driver/macos/Observe.swift")
    val output = layout.buildDirectory.file("gui-tools/observe").get().asFile
    val temporary = layout.buildDirectory.dir("gui-tools/tmp").get().asFile
    val modules = layout.projectDirectory.dir("../.build-cache/gui-swift-modules").asFile
    inputs.file(source)
    outputs.file(output)
    commandLine("/usr/bin/xcrun", "--sdk", "macosx", "swiftc", "-O", "-module-cache-path", modules.absolutePath,
        source.asFile.absolutePath, "-o", output.absolutePath)
    environment("TMPDIR", temporary.absolutePath)
    timeout.set(Duration.ofMinutes(2))
    doFirst {
        check(System.getProperty("os.name") == "Mac OS X") { "The native GUI test bridge requires macOS" }
        output.parentFile.mkdirs()
        temporary.mkdirs()
        modules.mkdirs()
    }
}


// Compiles only; execution requires a separately coordinated candidate runtime.
tasks.register<Exec>("compileGuiLauncher") {
    group = "verification"
    val source = layout.projectDirectory.file("tests/emulator/driver/macos/Launch.swift")
    val output = layout.buildDirectory.file("gui-tools/launch").get().asFile
    val temporary = layout.buildDirectory.dir("gui-tools/tmp").get().asFile
    val modules = layout.projectDirectory.dir("../.build-cache/gui-swift-modules").asFile
    inputs.file(source); outputs.file(output)
    commandLine("/usr/bin/xcrun", "--sdk", "macosx", "swiftc", "-O", "-module-cache-path", modules.absolutePath,
        source.asFile.absolutePath, "-o", output.absolutePath)
    environment("TMPDIR", temporary.absolutePath)
    timeout.set(Duration.ofMinutes(2))
    doFirst {
        check(System.getProperty("os.name") == "Mac OS X") { "The native GUI launcher requires macOS" }
        output.parentFile.mkdirs(); temporary.mkdirs(); modules.mkdirs()
    }
}

// Component builds retain ownership of their test source sets and toolchains.
val unitCommands = linkedMapOf(
    "unitComponents" to ("voboost-components" to listOf("./gradlew", "testUnit")),
    "unitAgents" to ("voboost-agents" to listOf("./gradlew", "testUnit", "generateNativeRegistry")),
    "unitApp" to ("voboost-app" to listOf("./gradlew", "testUnit")),
    "unitInject" to ("voboost-inject" to listOf("cargo", "test", "--workspace", "--locked")),
    "unitInjectNative" to ("voboost-inject" to listOf("make", "test-injector", "test-implant", "test-cluster-locale-observer", "test-sign-elf")),
    "unitInstaller" to ("voboost-installer" to listOf("npm", "test", "--", "--run")),
    "unitInstallerNode" to ("voboost-installer" to listOf("npm", "run", "test:node")),
    "unitInstallerRust" to ("voboost-installer" to listOf("npm", "run", "test:rust", "--")),
    "unitRelease" to ("voboost" to listOf("cargo", "test", "--manifest-path", "release/Cargo.toml", "--locked", "--bins")),
    "unitProductTools" to ("voboost" to listOf("npm", "run", "check")),
)
var previousUnit: TaskProvider<Task>? = null
unitCommands.forEach { (name, configuration) ->
    val (repository, arguments) = configuration
    val previous = previousUnit
    val component = tasks.register(name) {
        group = "verification"
        description = "Run the owning $repository unit suite and retain its independent outcome."
        if (previous != null) dependsOn(previous)
        doLast {
            val temporary = layout.buildDirectory.dir("unit-tmp/$name").get().asFile
            temporary.mkdirs()
            val reports = layout.buildDirectory.dir("reports/unit-components").get().asFile.apply { mkdirs() }
            val log = reports.resolve("$name.log")
            val command = arguments.toMutableList()
            if (gradle.startParameter.isOffline && (command[0] == "./gradlew" || command[0] == "cargo" || name == "unitInstallerRust")) command.add("--offline")
            if (providers.gradleProperty("checkRunId").isPresent && command[0] == "./gradlew") command.add("--rerun-tasks")
            providers.gradleProperty("cargo").orNull?.let { cargo ->
                require(File(cargo).isAbsolute && File(cargo).canExecute()) { "absolute executable cargo required" }
                if (command[0] == "cargo") command[0] = cargo
            }
            if (name == "unitInject") command.addAll(listOf("--", "--test-threads=4"))
            val directory = if (repository == "voboost") rootDir else rootDir.parentFile.resolve(repository)
            val invocations = if (name == "unitProductTools") listOf(
                listOf("node", "tools/validate-openspec-evidence.ts"),
                listOf("node", "tools/check-target-docs.ts"),
                listOf("node", "--test") + fileTree("tools") {
                    include("**/*.test.ts")
                    exclude("tests/emulator-gate.test.ts")
                }.files.map { it.relativeTo(rootDir).invariantSeparatorsPath }.sorted(),
                listOf("node", "node_modules/.bin/openspec", "validate", "--all", "--strict"),
            ) else listOf(command)
            val started = Instant.now().toString()
            var code: Int? = null
            var problem: String? = null
            log.outputStream().use { output ->
                for (invocation in invocations) {
                code = null
                try {
                    code = exec {
                        workingDir(directory)
                        environment("TMPDIR", temporary.absolutePath)
                        val sdk = System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME")
                        if (sdk != null) { environment("ANDROID_SDK_ROOT", sdk); environment("ANDROID_HOME", sdk) }
                        if (name == "unitInject" || name == "unitInjectNative" || name == "unitRelease") environment("CARGO_TARGET_DIR", rootDir.parentFile.resolve(".build-cache/inject-host").absolutePath)
                        providers.gradleProperty("cargo").orNull?.let { cargo ->
                            environment("PATH", File(cargo).parent + File.pathSeparator + System.getenv("PATH"))
                        }
                        providers.gradleProperty("rustc").orNull?.let { rustc ->
                            require(File(rustc).isAbsolute && File(rustc).canExecute()) { "absolute executable rustc required" }
                            environment("RUSTC", rustc)
                        }
                        if (name == "unitInject") {
                            val registry = rootDir.parentFile.resolve("voboost-agents/build/native/registry.rs")
                            if (!Files.isRegularFile(registry.toPath(), LinkOption.NOFOLLOW_LINKS) || registry.length() == 0L) {
                                throw GradleException("BLOCKED: generated native registry required; run voboost-agents ./gradlew generateNativeRegistry with its existing toolchain/cache")
                            }
                            environment("VOBOOST_NATIVE_REGISTRY", registry.absolutePath)
                        }
                        commandLine(invocation)
                        val retained = object : java.io.FilterOutputStream(output) {
                            override fun close() { flush() }
                        }
                        standardOutput = retained
                        errorOutput = retained
                        isIgnoreExitValue = true
                    }.exitValue
                } catch (error: Exception) { problem = if (name == "unitInject") error.message else error.javaClass.simpleName }
                if (code != 0) break
                }
            }
            val result = linkedMapOf<String, Any?>(
                "schemaVersion" to 1, "task" to name, "runId" to providers.gradleProperty("checkRunId").orNull, "repository" to directory.absolutePath,
                "commands" to invocations, "startedAt" to started, "completedAt" to Instant.now().toString(),
                "status" to if (code == 0) "PASS" else if (code == null) "BLOCKED" else "FAIL",
                "exitCode" to code, "problem" to problem, "log" to log.absolutePath,
            )
            reports.resolve("$name.json").writeText(groovy.json.JsonOutput.toJson(result) + "\n")
        }
    }
    previousUnit = component
    unitTest.configure { dependsOn(component) }
}
unitTest.configure {
    doLast {
        val slurper = groovy.json.JsonSlurper()
        val failures = unitCommands.keys.filter { name ->
            val report = layout.buildDirectory.file("reports/unit-components/$name.json").get().asFile
            !report.isFile || (slurper.parse(report) as Map<*, *>)["status"] != "PASS"
        }
        if (failures.isNotEmpty()) throw GradleException("Component unit verification failed or is blocked: ${failures.joinToString()}; see build/reports/unit-components")
    }
}

tasks.register<Exec>("buildProduct") {
    group = "build"
    description = "Run the existing product closure builder."
    doFirst {
        val config = providers.gradleProperty("config").orNull
            ?: throw GradleException("buildProduct requires -Pconfig=/absolute/build.json")
        if (!File(config).isAbsolute) {
            throw GradleException("buildProduct config must be an absolute path")
        }
        commandLine("node", "tools/release/build-closure.ts", config)
    }
}

val verifierDirectory = arrayOfNulls<File>(1)
val cleanFocusedVerifier = tasks.register("cleanFocusedVerifier") {
    doLast { verifierDirectory[0]?.deleteRecursively() }
}

fun registerIntegration(name: String, full: Boolean) = tasks.register<Test>(name) {
    environment("VOBOOST_NATIVE_REGISTRY", rootDir.parentFile.resolve("voboost-agents/build/native/registry.rs").absolutePath)
    finalizedBy(cleanFocusedVerifier)
    group = "verification"
    description = "Run selected implemented host integration cases; not full acceptance."
    val suite = sourceSets["integration"]
    testClassesDirs = suite.output.classesDirs
    classpath = suite.runtimeClasspath
    useJUnitPlatform()
    listOf("candidateDmg", "productPublicRoot", "trustScope", "installerCli", "cargo", "rustc").forEach { key ->
        providers.gradleProperty(key).orNull?.let { systemProperty(key, it) }
    }
    systemProperty("testWorkDir", providers.gradleProperty("checkWorkDir").orNull ?: layout.buildDirectory.dir("p03-work").get().asFile.absolutePath)
    if (full) exclude("**/InstallerCliHostTest.class")
    exclude("**/CanTxPolicyTest.class")
    outputs.upToDateWhen { false }
    // Single authority gate for this task: trust scope, then either the CLI-only
    // diagnostic early return (real packaged binary, no verifier provisioning;
    // the delivery case stays BLOCKED by its own input checks) or verifier
    // provisioning for runs that need a delivery candidate.
    doFirst {
        if (providers.gradleProperty("trustScope").orNull != "local-input") {
            throw GradleException("BLOCKED: select -PtrustScope=local-input for a diagnostic run using the explicitly trusted local public root; candidate provenance remains unverified")
        }
        val cli = providers.gradleProperty("installerCli").orNull
        if (cli != null && providers.gradleProperty("candidateDmg").orNull == null) {
            // CLI-only diagnostic run: the installer CLI host cases execute the
            // real packaged binary and need no delivery candidate or verifier
            // trust roots. Nothing below (verifier provisioning) may run.
            if (!File(cli).isAbsolute) throw GradleException("BLOCKED: installerCli must be an absolute path")
            return@doFirst
        }
        val cargo = providers.gradleProperty("cargo").orNull ?: throw GradleException("BLOCKED: set -Pcargo to the absolute path of trusted cargo")
        val rustc = providers.gradleProperty("rustc").orNull ?: throw GradleException("BLOCKED: set -Prustc to the absolute path of trusted rustc")
        if (!File(cargo).isAbsolute || !File(rustc).isAbsolute) throw GradleException("BLOCKED: tool paths must be absolute")
        val rustInfo = ByteArrayOutputStream()
        exec { commandLine(rustc, "-vV"); standardOutput = rustInfo }
        val host = rustInfo.toString().lineSequence().firstOrNull { it.startsWith("host: ") }?.substring(6)
            ?: throw GradleException("BLOCKED: rustc did not report a host target")
        val targetDir = rootDir.parentFile.resolve(".build-cache/inject-host").apply { mkdirs() }
        val toolDirectory = Files.createTempDirectory(layout.buildDirectory.dir("p03-verifier").get().asFile.apply { mkdirs() }.toPath(), "tool-").toFile()
        verifierDirectory[0] = toolDirectory
        val cargoOutput = ByteArrayOutputStream()
        exec {
            workingDir(projectDir)
            environment("RUSTC", rustc)
            environment.remove("RUSTC_WRAPPER")
            environment.remove("RUSTC_WORKSPACE_WRAPPER")
            commandLine(cargo, "build", "--offline", "--locked", "--manifest-path", "release/Cargo.toml", "--target", host, "--target-dir", targetDir, "--bin", "voboost-release", "--message-format=json")
            standardOutput = cargoOutput
        }
        val slurper = groovy.json.JsonSlurper()
        val executables = cargoOutput.toString().lineSequence().filter { it.startsWith("{") }.mapNotNull {
            val message = slurper.parseText(it) as? Map<*, *>
            val target = message?.get("target") as? Map<*, *>
            if (message?.get("reason") == "compiler-artifact" && target?.get("name") == "voboost-release" && target["src_path"] == file("release/main.rs").absolutePath) message["executable"] as? String else null
        }.toList()
        if (executables.size != 1 || !File(executables.single()).toPath().startsWith(targetDir.toPath())) throw GradleException("BLOCKED: cargo did not identify exactly one verifier in the shared target")
        val verifier = toolDirectory.resolve("voboost-release")
        Files.copy(File(executables.single()).toPath(), verifier.toPath())
        if (!verifier.setExecutable(true, true)) throw GradleException("BLOCKED: verifier executable permission unavailable")
        systemProperty("releaseVerifier", verifier.absolutePath)
    }
}

val testDesiredOwner = tasks.register<Test>("testDesiredOwner") {
    group = "verification"
    description = "Run root-owned desired authority scenarios against actual host owner RPC and durable storage."
    val suite = sourceSets["integration"]
    testClassesDirs = suite.output.classesDirs
    classpath = suite.runtimeClasspath
    useJUnitPlatform()
    include("**/DaemonDesiredOwnerTest.class")
    listOf("cargo", "rustc").forEach { key -> providers.gradleProperty(key).orNull?.let { systemProperty(key, it) } }
    systemProperty("testWorkDir", providers.gradleProperty("checkWorkDir").orNull
        ?: layout.buildDirectory.dir("desired-owner-work").get().asFile.absolutePath)
    outputs.upToDateWhen { false }
}

val testCanTxPolicy = tasks.register<Test>("testCanTxPolicy") {
    group = "verification"
    description = "Run only the owner CAN TX/PID metadata policy gate; no Cargo or native readiness claim."
    val suite = sourceSets["integration"]
    testClassesDirs = suite.output.classesDirs
    classpath = suite.runtimeClasspath
    useJUnitPlatform()
    include("**/CanTxPolicyTest.class")
    systemProperty("testWorkDir", providers.gradleProperty("checkWorkDir").orNull
        ?: layout.buildDirectory.dir("can-tx-policy-work").get().asFile.absolutePath)
    outputs.upToDateWhen { false }
}

val focusedIntegration = registerIntegration("integrationTestFocused", false)
val integrationTest = registerIntegration("integrationTest", true)


val emulatorDriverBinary = layout.projectDirectory.file("../.build-cache/installer-desktop-cargo/aarch64-apple-darwin/release/emulator-driver")
val compileEmulatorDriver = tasks.register<Exec>("compileEmulatorDriver") {
    group = "verification"
    description = "Build the host-only ADB test tool; never package it in the installer."
    val installer = layout.projectDirectory.dir("../voboost-installer")
    val temporary = layout.buildDirectory.dir("emulator-driver-tmp").get().asFile
    workingDir(installer)
    inputs.dir(installer.dir("src-tauri/src"))
    inputs.dir(installer.dir("observation"))
    inputs.dir(layout.projectDirectory.dir("../voboost-inject/shared"))
    inputs.dir(layout.projectDirectory.dir("../voboost-inject/contract"))
    inputs.dir(layout.projectDirectory.dir("../voboost-inject/common/contracts"))
    inputs.dir(layout.projectDirectory.dir("tests/emulator/driver/adb"))
    inputs.files(installer.file("src-tauri/Cargo.toml"), installer.file("src-tauri/Cargo.lock"),
        installer.file("scripts/cargo-test-with-ephemeral-role-roots.sh"))
    outputs.file(emulatorDriverBinary)
    environment("CARGO_TARGET_DIR", layout.projectDirectory.dir("../.build-cache/installer-desktop-cargo").asFile.absolutePath)
    environment("TMPDIR", temporary.absolutePath)
    commandLine("bash", "scripts/cargo-test-with-ephemeral-role-roots.sh", "--emulator-driver",
        "--locked", "--offline", "--target", "aarch64-apple-darwin", "--bin", "emulator-driver")
    doFirst {
        check(System.getProperty("os.name") == "Mac OS X") { "The current native ADB test driver targets macOS" }
        temporary.mkdirs()
        environment("JAVA_HOME", javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }.get().metadata.installationPath.asFile.absolutePath)
        check(System.getenv("ANDROID_SDK_ROOT") != null || System.getenv("ANDROID_HOME") != null) {
            "ANDROID_SDK_ROOT or ANDROID_HOME must select the existing Android SDK"
        }
    }
}

fun registerEmulator(name: String, full: Boolean) = tasks.register<Test>(name) {
    group = "verification"
    description = "Run the existing release acceptance or diagnostic scenario."
    dependsOn(compileEmulatorDriver)
    systemProperty("emulatorDriver", emulatorDriverBinary.asFile.absolutePath)
    val suite = sourceSets["emulator"]
    testClassesDirs = suite.output.classesDirs
    classpath = suite.runtimeClasspath
    useJUnitPlatform { excludeTags("emulator-workflow") }
    maxParallelForks = 1
    outputs.upToDateWhen { false }
    listOf("voyemu", "machine", "machineName", "scenario", "candidateDmg", "withCluster", "prepareExisting", "resumeRun", "observerDmg", "reviewedKeyboardMaps").forEach { key ->
        providers.gradleProperty(key).orNull?.let { systemProperty(key, it) }
    }
    systemProperty("testWorkDir", providers.gradleProperty("checkWorkDir").orNull ?: layout.buildDirectory.dir("emulator-work").get().asFile.absolutePath)
    exclude("**/AgentPedestrianWarning.class")
    if (full) {
        include("**/InstallerCliLifecycleTest.class")
        dependsOn("compileGuiBridge", "compileGuiLauncher", "buildLauncherFixture")
        systemProperty("guiObserver", layout.buildDirectory.file("gui-tools/observe").get().asFile.absolutePath)
        systemProperty("guiLauncher", layout.buildDirectory.file("gui-tools/launch").get().asFile.absolutePath)
    }
    systemProperty("voyemuConfig", file("config/config-voyemu.json").absolutePath)
    systemProperty("voyemuLocalConfig", file("config/config-voyemu-local.json").absolutePath)
}

val emulatorFocused = registerEmulator("emulatorFocused", false)
val emulatorAcceptance = registerEmulator("emulatorAcceptance", true)

abstract class EmulatorTask : JavaExec() {
    @Option(option = "tests", description = "Select a JUnit class or method. Supports *.")
    fun selectTests(pattern: String) {
        systemProperty("emulatorTests", pattern)
    }

    @Option(option = "test-dry-run", description = "List matching tests without opening a session.")
    fun listTests() {
        systemProperty("emulatorDryRun", "true")
    }
}

dependencies {
    add("emulatorImplementation", "org.junit.platform:junit-platform-launcher:1.10.1")
}

fun emulatorTask(name: String, stage: String, purpose: String) = tasks.register<EmulatorTask>(name) {
    group = "verification"
    description = purpose
    if (stage != "stop") dependsOn(compileEmulatorDriver)
    systemProperty("emulatorDriver", emulatorDriverBinary.asFile.absolutePath)
    val emulator = sourceSets["emulator"]
    classpath = emulator.runtimeClasspath
    mainClass.set("ru.voboost.voyemu.EmulatorRun")
    args(stage)
    val temporary = layout.buildDirectory.dir("emulator-tmp").get().asFile
    systemProperty("java.io.tmpdir", temporary.absolutePath)
    environment("TMPDIR", temporary.absolutePath)
    doFirst { temporary.mkdirs() }
    listOf("voyemu", "machine", "machineName", "candidateDmg", "previousDmg", "prepareExisting").forEach { key ->
        providers.gradleProperty(key).orNull?.let { systemProperty(key, it) }
    }
    providers.gradleProperty("suite").orNull?.let { systemProperty("emulatorSuite", it) }
    systemProperty("scenario", providers.gradleProperty("scenario").getOrElse("fresh"))
    systemProperty("voyemuConfig", file("config/config-voyemu.json").absolutePath)
    systemProperty("voyemuLocalConfig", file("config/config-voyemu-local.json").absolutePath)
    systemProperty("testWorkDir", providers.gradleProperty("checkWorkDir").orNull
        ?: layout.buildDirectory.dir("emulator-work").get().asFile.absolutePath)
    val selected = providers.gradleProperty("suite").orNull
    if (stage in setOf("check", "test") && selected in setOf(null, "all", "installer", "installer-gui")) {
        dependsOn("compileGuiBridge", "compileGuiLauncher")
        systemProperty("guiObserver", layout.buildDirectory.file("gui-tools/observe").get().asFile.absolutePath)
        systemProperty("guiLauncher", layout.buildDirectory.file("gui-tools/launch").get().asFile.absolutePath)
    }
}

val emulatorPrepare = emulatorTask("emulatorPrepare", "prepare", "Prepare persistent state on the selected machine.")
val emulatorStart = emulatorTask("emulatorStart", "start", "Start an owned session on the prepared machine.")
val emulatorTest = emulatorTask("emulatorTest", "test", "Run selected cases on the already started session.")
val emulatorReconcile = emulatorTask("emulatorReconcile", "reconcile", "Observe the retained operation without replaying its inputs.")
val emulatorStop = emulatorTask("emulatorStop", "stop", "Stop the owned test session and release its mounts.")
val emulatorCheck = emulatorTask("emulatorCheck", "check", "Prepare, start, test and stop the selected emulator lanes.")
emulatorStart.configure { mustRunAfter(emulatorPrepare) }
emulatorTest.configure { mustRunAfter(emulatorStart) }
emulatorStop.configure { mustRunAfter(emulatorTest, emulatorStart) }

val stockSettingsBaseline = registerEmulator("testStockSettingsBaseline", false).configure {
    description = "Capture pre-install stock OEM reference candidates; never installs or grants acceptance."
    filter { includeTestsMatching("ru.voboost.tests.installer.cli.InstallerCliLifecycleTest") }
    systemProperty("stockReferenceOnly", "true")
}

val vehicleOnly = registerEmulator("testVehicleOnly", false).configure {
    description = "Install the packaged candidate and run only PW/FEV scenarios; preserve the final vehicle-only configuration."
    include("**/InstallerCliLifecycleTest.class")
    systemProperty("vehicleOnly", "true")
}


val failedInstallRecovery = registerEmulator("testFailedInstallRecovery", false).configure {
    description = "Reconcile failed install acceptance and normally remove it; never product PASS."
    include("**/InstallerCliLifecycleTest.class")
    systemProperty("failedInstallCleanup", "true")
}

val failedFixtureRetirement = registerEmulator("retireFailedFixture", false).configure {
    description = "Register an existing stopped FAILED fixture; fresh baseline remains required."
    include("**/FailedFixtureRetirementTest.class")
    systemProperty("retireStoppedFailure", "true")
}

val guiLifecycle = registerEmulator("testGuiLifecycle", false).configure {
    description = "Explicit genuine packaged native GUI G01/G02/G06 subset; never full GUI acceptance."
    include("**/InstallerGuiLifecycleTest.class")
    dependsOn("compileGuiBridge", "compileGuiLauncher")
    systemProperty("nativeGuiLifecycle", "true")
    systemProperty("guiObserver", layout.buildDirectory.file("gui-tools/observe").get().asFile.absolutePath)
    systemProperty("guiLauncher", layout.buildDirectory.file("gui-tools/launch").get().asFile.absolutePath)
}

tasks.register("prepareCandidateCheck") {
    group = "verification"
    description = "Compile the candidate coordinator and expose its existing runtime classpath."
    dependsOn(tasks.classes)
    doLast {
        val target = layout.buildDirectory.file("candidate-runner.classpath").get().asFile
        target.writeText(sourceSets.main.get().runtimeClasspath.asPath)
    }
}

// This APK is a root-owned test input and is never a release component.
val buildLauncherFixture = tasks.register("buildLauncherFixture") {
    group = "verification"
    description = "Build the minimal first-party launcher fixture using the existing offline Android tools."
    val fixture = layout.projectDirectory.dir("tests/emulator/agents/agent-launcher/app")
    val out = layout.buildDirectory.dir("test-fixtures/launcher")
    inputs.dir(fixture)
    outputs.file(out.map { it.file("voboost-launcher-fixture.apk") })
    doLast {
        val local = groovy.json.JsonSlurper().parse(file("config/config-voyemu-local.json")) as Map<*, *>
        val binary = providers.gradleProperty("voyemu").orNull ?: (local["voyemu"] as? String ?: error("Missing local Voyemu executable mapping"))
        val root = file(binary).canonicalFile.parentFile
        val tools = root.resolve(".local/toolchains")
        val android = tools.resolve("java-sdk/platforms/android-34/android.jar")
        val buildTools = tools.resolve("java-sdk/build-tools/34.0.0/lib")
        val aapt = tools.resolve("native-sdk/build-tools/34.0.0/aapt")
        listOf(android, aapt, buildTools.resolve("d8.jar"), buildTools.resolve("apksigner.jar")).forEach { check(it.isFile) { "Existing Voyemu Android tool missing: $it" } }
        val dest = out.get().asFile; dest.mkdirs()
        val classes = dest.resolve("classes"); classes.mkdirs()
        val dex = dest.resolve("dex"); dex.mkdirs()
        val temp = dest.resolve("tmp"); temp.mkdirs()
        val javaHome = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }.get().metadata.installationPath.asFile
        fun run(vararg args: String) { exec { commandLine(*args); environment("TMPDIR", temp.absolutePath) }.assertNormalExitValue() }
        run(javaHome.resolve("bin/javac").absolutePath, "--release", "8", "-classpath", android.absolutePath, "-d", classes.absolutePath,
            fixture.file("src/ru/voboost/tests/launcherfixture/MainActivity.java").asFile.absolutePath)
        run(javaHome.resolve("bin/java").absolutePath, "-cp", buildTools.resolve("d8.jar").absolutePath, "com.android.tools.r8.D8", "--min-api", "28", "--lib", android.absolutePath,
            "--output", dex.absolutePath, classes.resolve("ru/voboost/tests/launcherfixture/MainActivity.class").absolutePath)
        val unsigned = dest.resolve("unsigned.apk")
        run(aapt.absolutePath, "package", "-f", "-0", "xml", "-M", fixture.file("AndroidManifest.xml").asFile.absolutePath, "-S", fixture.dir("res").asFile.absolutePath, "-I", android.absolutePath, "-F", unsigned.absolutePath)
        exec { workingDir(dex); commandLine(aapt.absolutePath, "add", unsigned.absolutePath, "classes.dex"); environment("TMPDIR", temp.absolutePath) }.assertNormalExitValue()
        val stored = dest.resolve("stored.apk")
        ZipFile(unsigned).use { input -> ZipOutputStream(stored.outputStream()).use { output ->
            input.entries().asSequence().forEach { original ->
                val bytes = input.getInputStream(original).use { it.readBytes() }
                val entry = ZipEntry(original.name)
                entry.method = ZipEntry.STORED; entry.size = bytes.size.toLong(); entry.compressedSize = entry.size
                entry.crc = CRC32().apply { update(bytes) }.value
                output.putNextEntry(entry); output.write(bytes); output.closeEntry()
            }
        } }
        val key = dest.resolve("test-only.p12")
        if (!key.exists()) run(javaHome.resolve("bin/keytool").absolutePath, "-genkeypair", "-keystore", key.absolutePath, "-storetype", "PKCS12", "-storepass", "fixture-only", "-keypass", "fixture-only", "-alias", "fixture", "-dname", "CN=Voboost test fixture only", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650", "-noprompt")
        run(javaHome.resolve("bin/java").absolutePath, "-jar", buildTools.resolve("apksigner.jar").absolutePath, "sign", "--v4-signing-enabled", "false", "--ks", key.absolutePath, "--ks-pass", "pass:fixture-only", "--key-pass", "pass:fixture-only", "--out", dest.resolve("voboost-launcher-fixture.apk").absolutePath, stored.absolutePath)
        run(javaHome.resolve("bin/java").absolutePath, "-jar", buildTools.resolve("apksigner.jar").absolutePath, "verify", dest.resolve("voboost-launcher-fixture.apk").absolutePath)
        run(aapt.absolutePath, "dump", "badging", dest.resolve("voboost-launcher-fixture.apk").absolutePath)
        delete(classes, dex, temp, unsigned, stored, key, dest.resolve("voboost-launcher-fixture.apk.idsig"))
    }
}

// Native PW composition needs neither a delivery package nor an emulator.
tasks.register<Test>("testPwNative") {
    group = "verification"
    description = "Check saved PW setting lifecycle and visual mask on the host."
    val suite = sourceSets["integration"]
    testClassesDirs = suite.output.classesDirs
    classpath = suite.runtimeClasspath
    useJUnitPlatform()
    include("**/AgentPedestrianWarningNativeTest.class")
    outputs.upToDateWhen { false }
}
