import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

android {
    namespace = "de.docgerdsoft.pantrytracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.docgerdsoft.pantrytracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // Release signing config. Keystore lives OUTSIDE the repo; the four
    // PANTRY_TRACKER_RELEASE_* values are read from ~/.gradle/gradle.properties
    // (or env vars in CI). Three discrete configurations are supported:
    //
    //   * All four properties present → release signing wired; assembleRelease
    //     produces a signed app-release.apk that installs on a device.
    //   * None of the four present → release signing left unconfigured;
    //     assembleDebug works normally; assembleRelease produces
    //     app-release-UNSIGNED.apk (note the suffix — that file cannot be
    //     installed; useful only for build-size checks).
    //   * Some-but-not-all present → fail fast at configuration time with the
    //     specific missing-property names, so a typo in one property doesn't
    //     surface as a generic Gradle MissingValueException that breaks even
    //     assembleDebug.
    //
    // Paths must be ABSOLUTE — Gradle's file() resolves relative to the :app
    // module dir (not project root), and does NOT expand ~. The path is also
    // checked for existence at configuration time so a typo'd path fails with
    // a clear message instead of surfacing late during validateSigningRelease.
    //
    // Full setup (one-time keystore + populating gradle.properties) is
    // documented in docs/release/SHIPPING.md §B.
    signingConfigs {
        create("release") {
            val propNames = listOf(
                "PANTRY_TRACKER_RELEASE_STORE_FILE",
                "PANTRY_TRACKER_RELEASE_STORE_PASSWORD",
                "PANTRY_TRACKER_RELEASE_KEY_ALIAS",
                "PANTRY_TRACKER_RELEASE_KEY_PASSWORD",
            )
            val present = propNames.filter { providers.gradleProperty(it).isPresent }
            when (present.size) {
                0 -> Unit // dev-default: leave signingConfig empty
                propNames.size -> {
                    val storeFilePath = providers.gradleProperty(propNames[0]).get()
                    require(!storeFilePath.startsWith("~")) {
                        "PANTRY_TRACKER_RELEASE_STORE_FILE must be an absolute path " +
                            "(no ~ expansion — Gradle's file() does not expand it). " +
                            "Got: $storeFilePath"
                    }
                    val resolved = file(storeFilePath)
                    require(resolved.exists()) {
                        "PANTRY_TRACKER_RELEASE_STORE_FILE points at $resolved which does " +
                            "not exist. Check the path in ~/.gradle/gradle.properties."
                    }
                    storeFile = resolved
                    storePassword = providers.gradleProperty(propNames[1]).get()
                    keyAlias = providers.gradleProperty(propNames[2]).get()
                    keyPassword = providers.gradleProperty(propNames[3]).get()
                }
                else -> {
                    val missing = propNames - present.toSet()
                    throw GradleException(
                        "Incomplete release-signing config. Missing: " +
                            "${missing.joinToString()}. Either set all four properties in " +
                            "~/.gradle/gradle.properties (see docs/release/SHIPPING.md §B), " +
                            "or unset PANTRY_TRACKER_RELEASE_STORE_FILE to fall back to " +
                            "unsigned release builds.",
                    )
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // SR-19: explicit declarations even though AGP defaults match.
            // Defends against a future merge accident or plugin that silently
            // flips either flag. Two lines, zero behaviour change today.
            isDebuggable = false
            isJniDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only wire the release signingConfig when storeFile was actually
            // populated above. Unconditional `signingConfig = ...` would make
            // assembleRelease throw NPE at packageRelease ("SigningConfig
            // 'release' is missing required property 'storeFile'") when the
            // dev hasn't set up the keystore yet — the unsigned-fallback path
            // would never produce an APK. Leaving signingConfig null is what
            // makes AGP emit app-release-unsigned.apk for build-size checks.
            signingConfigs.findByName("release")
                ?.takeIf { it.storeFile != null }
                ?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        // BuildConfig is enabled so OffApiClient can derive its User-Agent
        // from BuildConfig.VERSION_NAME, keeping the OFF-side identifier
        // in lockstep with future version bumps.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // SR-17: ship the exported Room schema JSON into the androidTest APK so
    // MigrationTestHelper can resolve them as assets at runtime. The helper
    // reads "<dbClass>/<version>.json" from the test APK's assets — not from
    // $projectDir/schemas on the host filesystem — so without this wiring,
    // createDatabase(TEST_DB, 1) throws FileNotFoundException ("Cannot find
    // the schema file in the assets folder"). The srcDir path matches the
    // room.schemaLocation value passed to KSP above.
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
}

// SR-22: assert the merged release manifest does not declare
// android:debuggable="true". buildTypes.release.isDebuggable=false above
// only controls AGP's *own* emission of the attribute — if a developer
// hardcodes android:debuggable="true" in app/src/main/AndroidManifest.xml
// (e.g. to flip a transient flag for debugging and forgets to remove it),
// the manifest-merge precedence for explicit attributes can leave the
// hardcoded value in the final APK manifest. This task reads the merged
// manifest after processReleaseManifest and fails the release build if
// debuggable=true survives anywhere in it.
androidComponents {
    onVariants(selector().withName("release")) { variant ->
        val verifyTask = project.tasks.register(
            "verify${variant.name.replaceFirstChar { it.uppercase() }}ManifestNotDebuggable",
        ) {
            val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
            inputs.file(manifest)
            // No artifact is produced — declare always-out-of-date so the
            // guard re-runs every assembleRelease / bundleRelease. The cost
            // is one file read + one regex match, which beats relying on
            // Gradle's implicit "no outputs = always rerun" semantics.
            outputs.upToDateWhen { false }
            doLast {
                val text = manifest.get().asFile.readText()
                val hasDebuggableTrue = Regex("""android:debuggable\s*=\s*"true"""")
                    .containsMatchIn(text)
                if (hasDebuggableTrue) {
                    throw GradleException(
                        "SR-22: merged release manifest declares android:debuggable=\"true\". " +
                            "Remove the hardcoded attribute from a source AndroidManifest.xml " +
                            "(this app's or a library's) before shipping a release build.",
                    )
                }
            }
        }
        // Lazy hook: tasks.named("assembleRelease") evaluates eagerly during
        // onVariants and throws because AGP registers per-variant assemble
        // tasks later in its own lifecycle. tasks.matching { ... }.configureEach
        // wires the dependency only when the task actually exists.
        //
        // Cover both assemble* (APK) and bundle* (AAB) — the Play Store path
        // documented in SHIPPING.md §C ships via bundleRelease, and the guard
        // claim is about *shipping*, not *assembling*.
        val capitalizedVariant = variant.name.replaceFirstChar { it.uppercase() }
        project.tasks.matching {
            it.name == "assemble$capitalizedVariant" || it.name == "bundle$capitalizedVariant"
        }.configureEach { dependsOn(verifyTask) }
    }
}

// Lock resolved dependency versions so OSV-Scanner has a manifest to scan.
// OSV-Scanner for Gradle only understands gradle.lockfile / gradle-verification-metadata.xml /
// pom.xml — NOT version catalogs. Without locking, the Security workflow scans zero packages.
//
// Scope: only RUNTIME classpaths (what ships in the APK). Build-tool classpaths
// (KSP processors, AGP plugin transitives, Detekt rule jars, Robolectric/JUnit
// scaffolding) pull in 400+ packages — including netty, bouncycastle, and Apache
// commons — that never reach users' devices. Their CVEs are real but they're the
// AGP/KSP maintainers' to fix, not ours, and adding them to our gate just teaches
// us to ignore the gate.
//
// Regenerate via `./gradlew :app:dependencies --write-locks` (also run by Security CI).
configurations.matching {
    it.name == "releaseRuntimeClasspath" || it.name == "debugRuntimeClasspath"
}.configureEach {
    resolutionStrategy.activateDependencyLocking()
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    autoCorrect = false
    ignoreFailures = false
    config.setFrom(files("$rootDir/detekt-config.yml"))
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.mlkit.barcode.scanning)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.ktor.client.mock)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    detektPlugins(libs.detekt.formatting)
}
