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
        versionCode = 2
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // Release signing config. Keystore lives OUTSIDE the repo; the four
    // PANTRY_TRACKER_RELEASE_* values are read from ~/.gradle/gradle.properties
    // (or env vars in CI). If any of them is absent — typical on a dev machine
    // that has only ever built debug APKs — the signingConfig is left unconfigured,
    // so `./gradlew :app:assembleDebug` still works; only `assembleRelease`
    // produces an unsigned (and therefore un-installable) APK in that case.
    //
    // Full setup (one-time keystore + populating gradle.properties) is documented
    // in docs/release/SHIPPING.md §B.
    signingConfigs {
        create("release") {
            val storeFilePath = providers.gradleProperty("PANTRY_TRACKER_RELEASE_STORE_FILE")
            if (storeFilePath.isPresent) {
                storeFile = file(storeFilePath.get())
                storePassword = providers.gradleProperty("PANTRY_TRACKER_RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("PANTRY_TRACKER_RELEASE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("PANTRY_TRACKER_RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
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

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    detektPlugins(libs.detekt.formatting)
}
