plugins {
    id("com.android.application") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "1.9.25"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("checkstyle")
    id("com.diffplug.spotless") version "6.25.0"
}

// Apply Voboost code style configuration
apply(from = "../voboost-codestyle/codestyle.gradle")

android {
    namespace = "ru.voboost"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.voboost"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0.2025012501"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
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

    // Activity and Fragment KTX
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Lifecycle components
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // JSON handling
    implementation("org.json:json:20231013")

    // Jetpack Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Testing dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.test:runner:1.5.2")
    testImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Desktop entry point task
tasks.register<JavaExec>("runDesktop") {
    group = "application"
    description = "Run Voboost desktop entry point"
    mainClass.set("ru.voboost.MainDesktopKt")

    // Build classpath from compiled outputs and manually selected dependencies
    val configProject = project(":voboost-config")

    // Get the Gradle user home to locate cached dependencies
    val gradleUserHome = gradle.gradleUserHomeDir
    val cacheDir = File(gradleUserHome, "caches/modules-2/files-2.1")

    classpath(
        // Main project compiled Kotlin classes
        layout.buildDirectory.dir("tmp/kotlin-classes/debug"),
        // Main project compiled Java classes
        layout.buildDirectory.dir("intermediates/javac/debug/classes"),
        // voboost-config compiled Kotlin classes
        configProject.layout.buildDirectory.dir("tmp/kotlin-classes/debug"),
        // voboost-config compiled Java classes
        configProject.layout.buildDirectory.dir("intermediates/javac/debug/classes"),
        // Add essential runtime dependencies from Gradle cache
        fileTree(cacheDir) {
            include("**/kotlin-stdlib-*.jar")
            include("**/kotlin-stdlib-common-*.jar")
            include("**/kotlinx-coroutines-core-jvm-*.jar")
            include("**/hoplite-core-*.jar")
            include("**/hoplite-yaml-*.jar")
            include("**/hoplite-watch-*.jar")
            include("**/snakeyaml-*.jar")
            include("**/annotations-*.jar")
            // Exclude Android-specific variants
            exclude("**/*-android*.jar")
        },
    )

    standardInput = System.`in`
    workingDir = projectDir

    // Ensure compilation happens before running
    dependsOn("compileDebugKotlin", "compileDebugJavaWithJavac")
    dependsOn(":voboost-config:compileDebugKotlin", ":voboost-config:compileDebugJavaWithJavac")
}
