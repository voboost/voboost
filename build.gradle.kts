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
        versionCode = 1
        versionName = "1.0.0-alpha1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        // OTA configuration
        // TODO: Set production OTA base URL before release
        buildConfigField("String", "OTA_BASE_URL", "\"TODO_SET_PRODUCTION_URL\"")
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
            isMinifyEnabled = false
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

// Task to copy Frida scripts from voboost-script build directory to assets
// NOTE: These are carrier assets only. In daemon-contract architecture, the app does
// not use these scripts directly. They are provided for the daemon/harness.
tasks.register<Copy>("copyFridaScripts") {
    group = "build"
    description = "Copy Frida scripts from voboost-script/build to assets"

    val scriptSourceDir = file("../voboost-script/build")
    val scriptDestDir = file("src/main/assets/scripts")

    // Ensure destination directory exists
    doFirst {
        scriptDestDir.mkdirs()
    }

    // Copy all *_3debug.js files
    from(scriptSourceDir) {
        include("*_3debug.js")
    }
    into(scriptDestDir)
}

// Ensure scripts are prepared before Android asset merging
// NOTE: downloadFridaInject removed in daemon-contract architecture
tasks.named("preBuild") {
    dependsOn("copyFridaScripts")
}

dependencies {
    // Main dependency on the voboost-config library
    implementation(project(":voboost-config"))

    // Add dependency on voboost-components library
    implementation(project(":voboost-components"))

    // Android Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

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
