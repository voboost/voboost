import java.util.Properties

plugins {
    id("com.android.application") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("checkstyle")
    id("com.diffplug.spotless") version "6.25.0"
}

// Apply Voboost code style configuration
apply(from = "../voboost-codestyle/codestyle.gradle")

// Load signing config from local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

android {
    namespace = "ru.voboost"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.voboost"
        minSdk = 28
        targetSdk = 34
        versionCode = 3
        versionName = "1.0.0-beta5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        // OTA manifest URL. Read from local.properties key `ota.manifestUrl`
        // so the production GitHub raw URL (or a local file:// test URL) is
        // not baked into the release APK. The sentinel default is recognized
        // by VoboostService.initializeOtaClient() which skips OTA init when it
        // is unchanged, so a release build without the key does no OTA work.
        // Production: set `ota.manifestUrl=https://...` in local.properties
        // (or a CI secret) before building a release that should self-update.
        // The Gradle fallback (when the key is absent) is the GitHub raw URL;
        // the local.properties default stays the sentinel so an unset key
        // disables OTA (matching the prior ota.baseUrl behavior).
        val otaManifestUrl =
            localProperties.getProperty(
                "ota.manifestUrl",
                "TODO_SET_PRODUCTION_URL",
            )
        buildConfigField("String", "OTA_MANIFEST_URL", "\"$otaManifestUrl\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("voboost-release.jks")
            storePassword = localProperties.getProperty("KEYSTORE_PASSWORD", "")
            keyAlias = "voboost"
            keyPassword = localProperties.getProperty("KEYSTORE_PASSWORD", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Single release build; the debug variant is disabled below. Enable
            // JDWP for deep debugging with: ./gradlew build -Pdebuggable=true
            isDebuggable = (project.findProperty("debuggable")?.toString() == "true")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // Strip the build-type suffix from the output APK: the single release
    // variant ships as `voboost.apk`, not `voboost-release.apk`.
    applicationVariants.all {
        outputs.forEach { output ->
            val apk = output as com.android.build.gradle.api.ApkVariantOutput
            apk.outputFileName = apk.outputFileName.replace("-release", "")
        }
    }

    lint {
        // voboost is a system-level automotive app installed via ADB with
        // privileged permissions. These are legitimate for the use case.
        disable.add("ProtectedPermissions")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Test screenshots co-located with voboost-components sources (BEM
            // structure) leak in as Java classpath resources via that module's
            // resources.srcDir("src/main/java"). Drop them from the final APK.
            excludes += "**/*.screenshots/**"
            // Fonts ship via assets (Typeface.createFromAsset); the identical
            // copies bundled as classpath resources are pure duplicates.
            excludes += "**/*.ttf"
            // BouncyCastle Picnic/LowMC post-quantum data files. Only Ed25519 is
            // used for OTA signatures, so these ~1.2 MB resources are dead weight.
            excludes += "org/bouncycastle/pqc/**"
        }
    }
}

// Release-only: drop the debug variant entirely. `./gradlew build` produces the
// single release variant; `-Pdebuggable=true` flips release's isDebuggable for
// rare deep-debugging without reintroducing a debug build type.
androidComponents {
    beforeVariants { variant ->
        variant.enable = variant.buildType != "debug"
    }
}

// Carrier-assets task: embeds the Frida agents produced by the sibling
// voboost-script repo into this APK as `assets/agents/`. The app is the
// carrier; at runtime ScriptExtractor writes these into the staging dir,
// and the voboost-inject daemon picks them up from there (verified against
// its embedded key — see docs/17-ota-architecture.md).
//
// Source layout (voboost-script/build/):
//   <name>-mod.js                      built, minified agent (Rollup output)
//   manifest.json                      per-agent metadata + sha256
//   manifest.json.sig                  detached ed25519 over manifest.json
//
// Destination layout (src/main/assets/agents/):
//   <name>.js                          renamed from <name>-mod.js to match
//                                      the manifest's `file: "agents/<name>.js"`
//   agents-manifest.json               manifest.json (renamed for clarity)
//   agents-manifest.json.sig           manifest.json.sig
tasks.register<Copy>("copyAgents") {
    group = "build"
    description = "Copy Frida agents + signed manifest from voboost-script/build to assets/agents/"

    // The `rename {}` blocks below use closures that the Gradle configuration
    // cache cannot serialize. This task is a build-time side-effect (it copies
    // sibling-repo artifacts into assets); it does not benefit from caching, so
    // opt out explicitly instead of fighting the cache rules.
    notCompatibleWithConfigurationCache("uses rename closures over sibling-repo files")

    val agentsSourceDir = file("../voboost-script/build")
    val agentsDestDir = file("src/main/assets/agents")

    doFirst {
        if (!agentsSourceDir.isDirectory) {
            throw GradleException(
                "voboost-script build output not found at ${agentsSourceDir.path}. " +
                    "Run 'npm run build' in ../voboost-script first."
            )
        }
        if (!file("${agentsSourceDir.path}/manifest.json").isFile) {
            throw GradleException(
                "agents manifest not found at ${agentsSourceDir.path}/manifest.json. " +
                    "Run 'npm run build' in ../voboost-script first."
            )
        }
        agentsDestDir.mkdirs()
    }

    // Agents: <name>-mod.js -> <name>.js (drop the build-only "-mod" suffix
    // so the on-disk filename matches the manifest's `file` field).
    from(agentsSourceDir) {
        include("*-mod.js")
        rename { name -> name.removeSuffix("-mod.js") + ".js" }
    }

    // Signed manifest: manifest.json{,.sig} -> agents-manifest.json{,.sig}.
    // Renamed on arrival so the app assets dir makes the role explicit and
    // avoids confusion with other manifests (release-manifest, inject-manifest).
    // The .sig is optional at this stage (voboost-script does not yet sign);
    // if absent it is simply not copied. Once voboost-script starts emitting
    // manifest.json.sig in CI, it will flow through automatically.
    from(agentsSourceDir) {
        include("manifest.json", "manifest.json.sig")
        rename { name ->
            when (name) {
                "manifest.json" -> "agents-manifest.json"
                "manifest.json.sig" -> "agents-manifest.json.sig"
                else -> name
            }
        }
    }

    into(agentsDestDir)
}

// Ensure agents are staged before Android asset merging runs.
tasks.named("preBuild") {
    dependsOn("copyAgents")
}

dependencies {
    // Main dependency on the voboost-config library
    implementation(project(":voboost-config"))

    // Add dependency on voboost-components library
    implementation(project(":voboost-components"))

    // Android Core
    implementation("androidx.core:core-ktx:1.12.0")

    // Lifecycle components
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // JSON handling
    implementation("org.json:json:20231013")

    // HTTP client for APK downloads from Huawei AppGallery
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Ed25519 verification for OTA release-manifest signatures.
    // API 28 lacks java.security EdDSA (added in Android API 33), so verify via BouncyCastle.
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // Testing dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.test:runner:1.5.2")
    testImplementation("androidx.test:rules:1.5.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
}

// Desktop entry point task - REMOVED in daemon-contract architecture
// Desktop mode is no longer supported. Android-only architecture.
// If you need desktop support, use the Android emulator instead.
tasks.register<JavaExec>("runDesktop") {
    group = "application"
    description = "DEPRECATED: Desktop mode removed in daemon-contract architecture"

    doFirst {
        logger.error("Desktop mode removed. Use the Android emulator: ./gradlew build")
        throw GradleException("Desktop mode removed - use Android emulator")
    }
}

// Emulator E2E test: boots AVD 'free', provisions the daemon + agents, runs the
// contract scenarios. Requires a rooted arm64 API-28 AVD and assembled daemon
// artifacts (see tools/emulator/README.md). Silent on success; fails the build
// on any harness assertion failure.
tasks.register<Exec>("emulatorTest") {
    group = "verification"
    description = "Run the emulator E2E harness (tools/emulator/run-test.sh) against AVD 'free'"
    dependsOn("build")
    workingDir = rootDir
    commandLine(rootDir.resolve("tools/emulator/run-test.sh").absolutePath, "--keep-logs")
}

// Validate openspec changes before commit / in CI.
tasks.register<Exec>("openspecValidate") {
    group = "verification"
    description = "Validate openspec changes (npx @fission-ai/openspec validate --all --strict)"
    workingDir = rootDir
    commandLine("npx", "--yes", "@fission-ai/openspec", "validate", "--all", "--strict")
}

// Unit tests for the single (release) variant. AGP names the per-variant task
// `testReleaseUnitTest`; this alias keeps "Release"/"Debug" out of the command line.
tasks.register("testUnit") {
    group = "verification"
    description = "Run unit tests (single release variant)"
    dependsOn("testReleaseUnitTest")
}
