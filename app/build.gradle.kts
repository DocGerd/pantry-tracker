import com.android.build.api.artifact.SingleArtifact
import java.time.Duration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    jacoco
}

android {
    namespace = "de.docgerdsoft.pantrytracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.docgerdsoft.pantrytracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.2.0"
        // Custom runner (extends AndroidJUnitRunner) that forces the test
        // Application class via newApplication() — the reliable on-device
        // mechanism to swap PantryTrackerApp → TestPantryTrackerApp. The
        // androidTest-manifest `android:name` override is NOT reliable for this
        // because the runtime Application comes from the target (app) manifest.
        // See PantryTestRunner.kt for the full rationale.
        testInstrumentationRunner = "de.docgerdsoft.pantrytracker.testfixtures.PantryTestRunner"
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
            isMinifyEnabled = true
            isShrinkResources = true
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

        // SR-80: optional R8 keep-rule survival check, gated on -PverifyR8=true.
        // Shells out to scripts/uat/verify-r8-keep-rules.sh after the APK is built.
        // Kept opt-in so dev builds (assembleDebug) and regular assembleRelease are
        // not slowed down — the check is only needed before a release UAT pass.
        //
        // Usage:
        //   ./gradlew :app:assembleRelease -PverifyR8=true
        if (providers.gradleProperty("verifyR8").orNull == "true") {
            val verifyR8Task = project.tasks.register("verifyR8KeepRules") {
                // Depend on the APK being built — the script reads it.
                dependsOn("assemble$capitalizedVariant")
                outputs.upToDateWhen { false }
                doLast {
                    val scriptPath = rootProject.file("scripts/uat/verify-r8-keep-rules.sh").absolutePath
                    if (!file(scriptPath).exists()) {
                        throw GradleException(
                            "SR-80: verify-r8-keep-rules.sh not found at $scriptPath. " +
                                "Check that the scripts/uat/ directory is present.",
                        )
                    }
                    val process = ProcessBuilder("bash", scriptPath)
                        .inheritIO()
                        .start()
                    val exitCode = process.waitFor()
                    if (exitCode != 0) {
                        throw GradleException(
                            "SR-80: verifyR8KeepRules failed — one or more annotated classes " +
                                "are missing from the DEX. See the output above for remediation hints.",
                        )
                    }
                }
            }
            // Wire verifyR8KeepRules to run after assembleRelease completes.
            // We cannot use dependsOn here because assembleRelease already owns
            // the build graph; instead we register verifyR8KeepRules as a
            // finalizer so it runs immediately after the APK task finishes.
            project.tasks.matching {
                it.name == "assemble$capitalizedVariant"
            }.configureEach { finalizedBy(verifyR8Task) }
        }
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

// SR-78: wire the custom detekt rule set as a normal detektPlugins artifact.
// The rules live in the standalone :detekt-rules JVM module; detekt loads the
// produced jar (classes + META-INF/services RuleSetProvider) via ServiceLoader.
//
// History: the original SR-78 wiring compiled the rules into :app's unit-test
// source set and pointed detektPlugins(files(...)) at AGP's internal
// `intermediates/` dirs. That was both fragile (paths are AGP-version-internal)
// and INERT — detekt loaded nothing and the rule never fired. A real Gradle
// module avoids both problems; the :detekt-rules:test proof test guarantees it
// keeps firing.
dependencies {
    detektPlugins(project(":detekt-rules"))
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

// SR-169 — JaCoCo statement-coverage reporting (OpenSSF Silver
// `test_statement_coverage80`, phase 1 of #169).
//
// PHASE 1 (this PR) deliberately measures JVM unit-test coverage only
// (testDebugUnitTest) and sets the verification gate to 0.00 so it cannot
// fail. The 0% minimum is NOT the target — it is a MEASURED baseline that
// records today's real coverage in the PR body and the CI artifact without
// blocking merges while the test suite is grown.
//
// PHASE 2 (tracked follow-up) raises the minimum to 0.80, merges instrumented
// (connectedDebugAndroidTest, emulator-gated per SR-79) execution data into
// the same report, and adds :app:jacocoTestCoverageVerification to the
// required CI status checks — only then does the #140 silver
// test_statement_coverage80 row flip Unmet -> Met.
//
// The exclusions strip generated bytecode (Room *_Impl DAOs/DB, kotlinx
// serializers, Compose preview/singleton scaffolding, BuildConfig/R/Manifest)
// so the ratio reflects hand-written code, not codegen.
jacoco {
    toolVersion = "0.8.12"
}

val coverageExclusions = listOf(
    "**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*",
    "**/*Test*.*", "android/**/*.*",
    "**/*_Impl*.*", // Room-generated DAO/DB impls
    "**/*\$\$serializer*.*", // kotlinx.serialization generated serializers
    "**/ComposableSingletons*.*", "**/*Preview*.*", // Compose preview/generated
    "**/databinding/**", "**/BR.*",
)

// Resolve the debug Kotlin class output from the compileDebugKotlin task itself
// rather than hardcoding an AGP-internal path. Recent AGP/KGP moved the output
// from `build/tmp/kotlin-classes/debug` to
// `build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes`
// (built-in Kotlin support); keying off the task's destinationDirectory keeps
// the report correct across that move and future relocations. CLAUDE.md's
// `:app:fuzzTest` task uses the same "reuse task output, never hardcode
// intermediates/" discipline.
//
// The lookup is wrapped in `provider { }` so it stays lazy: AGP registers the
// per-variant compile task only later in its own lifecycle, so an eager
// `tasks.named("compileDebugKotlin")` at script-evaluation time throws
// "task not found" (same lazy-registration trap the SR-22 block above handles
// with tasks.matching{}.configureEach). The provider is resolved when the
// JaCoCo task graph is realised, by which point the compile task exists.
val debugKotlinClassesTree = provider {
    val classesDir = tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>(
        "compileDebugKotlin",
    ).flatMap { it.destinationDirectory }
    fileTree(classesDir) { exclude(coverageExclusions) }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    description = "Generates a JaCoCo coverage report from the debug JVM unit tests (SR-169)."
    group = "verification"
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(debugKotlinClassesTree)
    sourceDirectories.setFrom(files("$projectDir/src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory.get()) { include("**/*.exec", "**/*.ec") },
    )
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    description = "Verifies JaCoCo coverage against the phase-1 baseline gate (SR-169)."
    group = "verification"
    dependsOn("jacocoTestReport")
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                // Phase-1 baseline gate: 0.00 cannot fail. Raise to 0.80 in the
                // tracked follow-up once instrumented coverage is merged in.
                minimum = "0.00".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(debugKotlinClassesTree)
    sourceDirectories.setFrom(files("$projectDir/src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory.get()) { include("**/*.exec", "**/*.ec") },
    )
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
    // SR-144: Jazzer JUnit 5 driver — used ONLY by the :app:fuzzTest task
    // below (filtered to *FuzzTest classes); the regular :app:test task
    // still runs on JUnit 4 via Robolectric. jazzer-junit pulls in JUnit
    // Jupiter 5.x as a transitive runtime dep, which is fine because the
    // JUnit Platform happily hosts both engines in the same source set —
    // the per-task `useJUnitPlatform()` vs default JUnit 4 split keeps
    // the two from interfering.
    testImplementation(libs.jazzer.junit)
    // Compose UI test APIs (createComposeRule, captureToImage, onRoot) used by
    // RNG screenshot tests under src/test. The androidTestImplementation line
    // below is kept for instrumentation tests; this line enables the same APIs
    // for pure-JVM Robolectric tests (SR-74).
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    detektPlugins(libs.detekt.formatting)
}

// SR-144: Jazzer fuzz-test task. Runs ONLY classes matching `*FuzzTest` under
// the unit-test source set, using JUnit Platform (Jupiter) so the @FuzzTest
// annotation is picked up by jazzer-junit's TestEngine. The regular :app:test
// task is untouched and continues to use JUnit 4 — Jazzer is filtered out
// there by class-name pattern.
//
// Mode toggle: setting JAZZER_FUZZ=1 puts Jazzer into *fuzzing* mode (generate
// + mutate). Without it, the driver runs in *regression* mode and only
// replays the static seed corpus under src/test/resources/.../OffApiClientFuzzTestInputs/
// — which finishes in milliseconds and is useful for CI smoke-tests but is
// not actually fuzzing. The :app:fuzzTest task sets JAZZER_FUZZ=1 so a local
// `./gradlew :app:fuzzTest` invocation actually fuzzes.
//
// Time-cap: the @FuzzTest(maxDuration = "5m") annotation on the single fuzz
// method is Jazzer's own hard ceiling on a fuzzing run. We *also* apply
// Gradle's task-level `timeout` as a belt-and-braces — if a future fuzz
// method is added and forgets the annotation, the task still can't run
// longer than 6 minutes (1-minute slack covers Jazzer warmup + JVM start).
//
// Why a separate task instead of folding into :app:test: (1) JUnit 4 is the
// default for :app:test (Robolectric + Compose UI tests); switching the whole
// task to JUnit Platform would force a Jupiter migration on the existing
// ~200 tests, out of scope here. (2) Fuzz runs are slow (5 min) and gating
// every PR on them would balloon CI time. (3) The fuzz.yml workflow runs
// this task on a weekly schedule, decoupled from :app:test.
tasks.register<Test>("fuzzTest") {
    description = "Run Jazzer-driven fuzz tests against OffApiClient JSON decode (SR-144)."
    group = "verification"

    // Reuse the compiled unit-test outputs + classpath from the existing
    // testDebugUnitTest task so we stay in lockstep with whatever AGP
    // currently produces (no hardcoded `build/intermediates/...` paths,
    // which are AGP-version-internal). dependsOn the per-variant
    // *compileTask* (not the test task itself — we don't want to run
    // the JUnit 4 suite as a side effect of compiling).
    val testTaskProvider = tasks.named<Test>("testDebugUnitTest")
    testClassesDirs = testTaskProvider.get().testClassesDirs
    classpath = testTaskProvider.get().classpath
    dependsOn("compileDebugUnitTestKotlin", "compileDebugUnitTestJavaWithJavac")

    useJUnitPlatform()

    filter {
        // Only classes ending in `FuzzTest` (the project convention). The
        // unit-test sibling for the same target — OffApiClientTest — does
        // NOT match this pattern, so the regular JUnit 4 tests don't get
        // re-discovered as JUnit 5 zero-test classes here.
        includeTestsMatching("*FuzzTest")
    }

    // Belt-and-braces hard ceiling — see the comment block above.
    timeout.set(Duration.ofMinutes(6))

    // Switch Jazzer from regression-only mode into actual fuzzing.
    // The @FuzzTest(maxDuration = "5m") annotation on the fuzz method is
    // Jazzer's own hard ceiling per fuzz run.
    environment("JAZZER_FUZZ", "1")

    // Surface any crashing input that Jazzer discovers — Gradle's default
    // is to swallow stdout/stderr unless the test fails, which makes the
    // post-mortem on a finding harder than it needs to be.
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}
