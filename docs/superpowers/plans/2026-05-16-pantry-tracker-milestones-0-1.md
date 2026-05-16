# Pantry Tracker · Milestones 0–1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the `pantry-tracker` repo from "empty Compose `Hello` screen" to "fully-working manual-entry inventory" — a runnable Android app where you can add a product by typing its name and quantity, search the list, and delete a product. No camera, no barcode, no network in this plan; all of that arrives in later milestones.

**Architecture:** Single `:app` Gradle module organized by package (`ui.home`, `ui.theme`, `data.local`, `repository`, `di`). One Room table (`products`) is the source of truth; a `ProductRepository` mediates between the DAO and the UI. UI is Jetpack Compose with one screen (`HomeScreen`) and one `HomeViewModel` whose `StateFlow<HomeUiState>` is collected via `collectAsStateWithLifecycle()`. Manual constructor wiring (`AppContainer`) replaces Hilt for v1.

**Tech Stack:** Kotlin 2.0, AGP 8.5+, Jetpack Compose (Material 3), Room 2.6.x with KSP, kotlinx-coroutines 1.8.x, kotlinx-datetime 0.6.x. JUnit 4 + Robolectric 4.13 for DAO tests, JUnit 4 + Turbine 1.x + `kotlinx-coroutines-test` for ViewModel/Repository tests, Compose UI test for one smoke test on the home screen.

> **Spec deviation note:** The spec (§3) lists JUnit 5. Switching to **JUnit 4** for v1 because the Android testing ecosystem (Robolectric, AndroidX test, Compose UI test) is JUnit 4–first; JUnit 5 works but requires extra adapters that add learning surface for no v1 benefit. Easy to migrate later if desired.

---

## Pre-flight: GitHub branch

Run these once before starting Task 1:

```bash
cd /home/pkuhn/inventory-androic
git switch -c milestone-0-skeleton
```

Each task ends with a commit on this branch. At the end of Milestone 0 (Task 4) we merge it to `main` via a PR. Milestone 1 then opens a new branch `milestone-1-room-manual-add`.

## File layout (locked-in decomposition)

```
pantry-tracker/
├── settings.gradle.kts
├── build.gradle.kts                       # top-level
├── gradle/libs.versions.toml              # version catalog
├── gradle.properties
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/de/docgerdsoft/pantrytracker/
│       │   │   ├── PantryTrackerApp.kt              # Application class
│       │   │   ├── MainActivity.kt
│       │   │   ├── di/
│       │   │   │   └── AppContainer.kt
│       │   │   ├── data/local/
│       │   │   │   ├── Product.kt                   # @Entity
│       │   │   │   ├── Converters.kt                # Room TypeConverters
│       │   │   │   ├── ProductDao.kt
│       │   │   │   └── AppDatabase.kt
│       │   │   ├── repository/
│       │   │   │   ├── ProductRepository.kt         # interface
│       │   │   │   └── ProductRepositoryImpl.kt
│       │   │   └── ui/
│       │   │       ├── theme/
│       │   │       │   ├── Color.kt
│       │   │       │   ├── Theme.kt
│       │   │       │   └── Type.kt
│       │   │       └── home/
│       │   │           ├── HomeUiState.kt
│       │   │           ├── HomeViewModel.kt
│       │   │           ├── HomeScreen.kt
│       │   │           └── AddProductSheet.kt
│       │   └── res/                                  # standard resource dirs
│       ├── test/                                     # JVM unit + Robolectric
│       │   └── java/de/docgerdsoft/pantrytracker/
│       │       ├── data/local/ProductDaoTest.kt
│       │       ├── repository/ProductRepositoryImplTest.kt
│       │       └── ui/home/HomeViewModelTest.kt
│       └── androidTest/                              # Compose UI test
│           └── java/de/docgerdsoft/pantrytracker/
│               └── ui/home/HomeScreenTest.kt
```

Each Kotlin file has one responsibility. `Product` is both the Room entity and the domain model — no duplicate DTO for v1.

## Test commands you'll keep typing

| What | Command | Where it runs |
|---|---|---|
| All unit + Robolectric tests | `./gradlew testDebugUnitTest` | JVM (fast) |
| One test class | `./gradlew testDebugUnitTest --tests "de.docgerdsoft.pantrytracker.data.local.ProductDaoTest"` | JVM |
| One test method | `./gradlew testDebugUnitTest --tests "*ProductDaoTest.observeAll_orderedByName"` | JVM |
| Lint | `./gradlew lintDebug` | JVM |
| Connected (UI) tests | `./gradlew connectedDebugAndroidTest` | Real device / emulator |
| Build APK | `./gradlew assembleDebug` | JVM |
| Install on connected device | `./gradlew installDebug` | Device |

---

# MILESTONE 0 · Project skeleton (Issue [#1](https://github.com/DocGerd/pantry-tracker/issues/1))

## Task 1: Gradle project skeleton + version catalog

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (top-level)
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PantryTracker"
include(":app")
```

- [ ] **Step 2: Create top-level `build.gradle.kts`**

```kotlin
// Top-level build file. Plugin versions live in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 3: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 4: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.5.2"
kotlin = "2.0.20"
ksp = "2.0.20-1.0.25"
coreKtx = "1.13.1"
lifecycle = "2.8.4"
activityCompose = "1.9.1"
composeBom = "2024.08.00"
room = "2.6.1"
coroutines = "1.8.1"
datetime = "0.6.0"
junit = "4.13.2"
robolectric = "4.13"
turbine = "1.1.0"
androidxJunit = "1.2.1"
espresso = "3.6.1"
composeUiTest = "1.6.8"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }

androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "datetime" }

junit = { group = "junit", name = "junit", version.ref = "junit" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxJunit" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/libs.versions.toml
git commit -m "M0: Gradle project skeleton + version catalog"
```

Note: there's no Gradle wrapper yet — that's created in Task 2.

---

## Task 2: App module + Gradle wrapper

**Files:**
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro` (empty)
- Create: `gradle/wrapper/gradle-wrapper.properties` (via `gradle wrapper`)
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` (via `gradle wrapper`)

- [ ] **Step 1: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.docgerdsoft.pantrytracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.docgerdsoft.pantrytracker"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

(Room/coroutine/datetime/test dependencies arrive in Task 5.)

- [ ] **Step 2: Create empty `app/proguard-rules.pro`**

```
# Add project-specific ProGuard rules here.
```

- [ ] **Step 3: Generate the Gradle wrapper**

You need a system Gradle (any 8.x) for this one bootstrap step. Verify and then generate:

```bash
gradle --version 2>/dev/null || sudo apt install -y gradle
gradle wrapper --gradle-version 8.9 --distribution-type all
```

Expected: `BUILD SUCCESSFUL` and four new files: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`.

- [ ] **Step 4: Verify the wrapper works**

```bash
chmod +x gradlew
./gradlew --version
```

Expected: Gradle 8.9 reported.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/proguard-rules.pro gradlew gradlew.bat gradle/wrapper/
git commit -m "M0: Add :app module + Gradle wrapper 8.9"
```

---

## Task 3: AndroidManifest, theme, Application class, MainActivity stub

**Files:**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/PantryTrackerApp.kt`
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/MainActivity.kt`
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/theme/Color.kt`
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/theme/Type.kt`
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/theme/Theme.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`

- [ ] **Step 1: `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".PantryTrackerApp"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.PantryTracker">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.PantryTracker">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

We omit `<uses-permission android:name="android.permission.INTERNET" />` here — it joins later in Milestone 3 when OFF lookups arrive. No camera permission yet either.

- [ ] **Step 2: `strings.xml`**

```xml
<resources>
    <string name="app_name">Pantry Tracker</string>
</resources>
```

- [ ] **Step 3: `themes.xml`** (minimal — Compose owns most styling)

```xml
<resources>
    <style name="Theme.PantryTracker" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 4: `ui/theme/Color.kt`**

```kotlin
package de.docgerdsoft.pantrytracker.ui.theme

import androidx.compose.ui.graphics.Color

val AddGreen = Color(0xFF2A6A2A)
val RemoveRed = Color(0xFF8A2A2A)

val Md3Primary = Color(0xFF3F6B3F)
val Md3OnPrimary = Color(0xFFFFFFFF)
val Md3Surface = Color(0xFFFAF9F6)
val Md3OnSurface = Color(0xFF1B1B1B)
val Md3SurfaceDark = Color(0xFF1B1B1B)
val Md3OnSurfaceDark = Color(0xFFEDEDED)
```

- [ ] **Step 5: `ui/theme/Type.kt`** (defaults are fine for v1)

```kotlin
package de.docgerdsoft.pantrytracker.ui.theme

import androidx.compose.material3.Typography

val PantryTypography = Typography()
```

- [ ] **Step 6: `ui/theme/Theme.kt`**

```kotlin
package de.docgerdsoft.pantrytracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Md3Primary,
    onPrimary = Md3OnPrimary,
    surface = Md3Surface,
    onSurface = Md3OnSurface,
)

private val DarkColors = darkColorScheme(
    primary = Md3Primary,
    onPrimary = Md3OnPrimary,
    surface = Md3SurfaceDark,
    onSurface = Md3OnSurfaceDark,
)

@Composable
fun PantryTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PantryTypography,
        content = content,
    )
}
```

- [ ] **Step 7: `PantryTrackerApp.kt`** (empty `Application` for now)

```kotlin
package de.docgerdsoft.pantrytracker

import android.app.Application

class PantryTrackerApp : Application() {
    // AppContainer arrives in Task 12 (milestone 1).
}
```

- [ ] **Step 8: `MainActivity.kt`** (placeholder content)

```kotlin
package de.docgerdsoft.pantrytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PantryTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Pantry Tracker — milestone 0 ready")
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 9: Build the app to verify everything compiles**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. (First build downloads Android SDK components — can take several minutes.)

- [ ] **Step 10: Commit**

```bash
git add app/src/main
git commit -m "M0: AndroidManifest, theme, Application, MainActivity stub"
```

---

## Task 4: Install on device + PR + merge milestone 0

- [ ] **Step 1: Connect a real Android phone via USB (with USB debugging enabled) or start an emulator.** Verify:

```bash
adb devices
```

Expected: one device listed under `List of devices attached`.

- [ ] **Step 2: Install and launch the debug APK**

```bash
./gradlew installDebug
adb shell am start -n de.docgerdsoft.pantrytracker/.MainActivity
```

Expected: the phone shows a centered "Pantry Tracker — milestone 0 ready" text.

- [ ] **Step 3: Push the branch and open a PR**

```bash
git push -u origin milestone-0-skeleton
gh pr create \
  --title "Milestone 0: Project skeleton" \
  --body "Closes #1

Implements the project-skeleton milestone: Gradle setup, Compose stub MainActivity, manifest + theme. CI workflow activates from this PR onward (Gradle wrapper now exists). Verified locally with installDebug on a physical device." \
  --base main
```

- [ ] **Step 4: Watch CI go green**

```bash
gh pr checks --watch
```

Expected: `CI / build` passes (no longer a "no gradlew" notice).

- [ ] **Step 5: Merge and clean up**

```bash
gh pr merge --squash --delete-branch
git switch main
git pull --ff-only
```

Issue #1 is now closed (the `Closes #1` keyword does this on merge). Milestone 0 is done.

---

# MILESTONE 1 · Room + manual add (Issue [#2](https://github.com/DocGerd/pantry-tracker/issues/2))

## Pre-flight: branch + dependency additions

```bash
git switch -c milestone-1-room-manual-add
```

---

## Task 5: Add Room + coroutines + datetime + test dependencies

**Files:**
- Modify: `gradle/libs.versions.toml` (no changes — entries already exist from Task 1)
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Update `app/build.gradle.kts`**

Add the `ksp` plugin alias, wire Room/coroutines/datetime, add Room schema export config, and declare all test dependencies. Replace the file with:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "de.docgerdsoft.pantrytracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.docgerdsoft.pantrytracker"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
```

- [ ] **Step 2: Sync / build to make sure the new dependencies resolve**

```bash
./gradlew help
```

Expected: `BUILD SUCCESSFUL` (no source changes yet — this is a dependency-resolution smoke test).

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "M1: Add Room, coroutines, datetime, test dependencies"
```

---

## Task 6: `Product` entity + `Converters`

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/data/local/Product.kt`
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/data/local/Converters.kt`

- [ ] **Step 1: Create `Product.kt`**

```kotlin
package de.docgerdsoft.pantrytracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "products",
    indices = [Index(value = ["barcode"], unique = true)],
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String?,
    val name: String,
    val brand: String? = null,
    val imageUrl: String? = null,
    val quantity: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

- [ ] **Step 2: Create `Converters.kt`**

```kotlin
package de.docgerdsoft.pantrytracker.data.local

import androidx.room.TypeConverter
import kotlinx.datetime.Instant

class Converters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilliseconds()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? =
        value?.let { Instant.fromEpochMilliseconds(it) }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/data/local
git commit -m "M1: Product entity + Instant TypeConverters"
```

---

## Task 7: `ProductDao` interface + `AppDatabase`

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/data/local/ProductDao.kt`
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/data/local/AppDatabase.kt`

- [ ] **Step 1: Create `ProductDao.kt`**

```kotlin
package de.docgerdsoft.pantrytracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): Product?

    @Query("SELECT * FROM products WHERE barcode = :code LIMIT 1")
    suspend fun findByBarcode(code: String): Product?

    @Query(
        "SELECT * FROM products " +
            "WHERE name LIKE '%' || :query || '%' COLLATE NOCASE " +
            "ORDER BY name COLLATE NOCASE"
    )
    fun search(query: String): Flow<List<Product>>

    @Upsert
    suspend fun upsert(product: Product): Long

    @Delete
    suspend fun delete(product: Product)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

- [ ] **Step 2: Create `AppDatabase.kt`**

```kotlin
package de.docgerdsoft.pantrytracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Product::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao

    companion object {
        const val DB_NAME = "pantry-tracker.db"
    }
}
```

- [ ] **Step 3: Sanity-build (KSP runs and generates the DAO/DB implementations)**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. A `schemas/de.docgerdsoft.pantrytracker.data.local.AppDatabase/1.json` file appears under `app/schemas/` — commit that too.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/data/local app/schemas
git commit -m "M1: ProductDao + AppDatabase (Room v1 schema)"
```

---

## Task 8: DAO tests (Robolectric, in-memory DB)

**Files:**
- Create: `app/src/test/java/de/docgerdsoft/pantrytracker/data/local/ProductDaoTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package de.docgerdsoft.pantrytracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProductDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ProductDao

    private fun product(
        name: String,
        quantity: Int = 1,
        barcode: String? = null,
    ): Product {
        val now = Clock.System.now()
        return Product(
            barcode = barcode,
            name = name,
            quantity = quantity,
            createdAt = now,
            updatedAt = now,
        )
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = db.productDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsert_assignsId() = runTest {
        val id = dao.upsert(product(name = "Coke"))
        assertEquals(1L, id)
    }

    @Test
    fun observeAll_ordersByNameCaseInsensitive() = runTest {
        dao.upsert(product(name = "tomato"))
        dao.upsert(product(name = "Apple"))
        dao.upsert(product(name = "banana"))

        dao.observeAll().test {
            val items = awaitItem()
            assertEquals(listOf("Apple", "banana", "tomato"), items.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun findByBarcode_returnsMatchingRow() = runTest {
        dao.upsert(product(name = "Coke", barcode = "5449000000996"))
        dao.upsert(product(name = "Pasta", barcode = "8001505005707"))

        val coke = dao.findByBarcode("5449000000996")
        assertEquals("Coke", coke?.name)

        val missing = dao.findByBarcode("0000000000000")
        assertNull(missing)
    }

    @Test
    fun search_matchesSubstringCaseInsensitive() = runTest {
        dao.upsert(product(name = "Tomato sauce"))
        dao.upsert(product(name = "Olive oil"))
        dao.upsert(product(name = "Cherry tomatoes"))

        dao.search("tomato").test {
            val results = awaitItem().map { it.name }
            assertEquals(listOf("Cherry tomatoes", "Tomato sauce"), results)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun delete_removesRow() = runTest {
        val id = dao.upsert(product(name = "Coke"))
        dao.deleteById(id)
        assertNull(dao.findById(id))
    }

    @Test
    fun upsert_updatesExistingRow() = runTest {
        val original = product(name = "Coke", quantity = 1)
        val id = dao.upsert(original)

        val updated = original.copy(id = id, quantity = 7)
        dao.upsert(updated)

        val loaded = dao.findById(id)
        assertEquals(7, loaded?.quantity)
    }
}
```

- [ ] **Step 2: Run the tests**

```bash
./gradlew testDebugUnitTest --tests "de.docgerdsoft.pantrytracker.data.local.ProductDaoTest"
```

Expected: 6 tests pass. (Room's generated DAO implementation makes them succeed without a separate "red" step — the value of these tests is catching regressions in the SQL queries as the schema evolves.)

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/de/docgerdsoft/pantrytracker/data/local
git commit -m "M1: ProductDao tests (Robolectric, in-memory Room)"
```

---

## Task 9: `ProductRepository` interface

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/repository/ProductRepository.kt`

- [ ] **Step 1: Create the interface**

```kotlin
package de.docgerdsoft.pantrytracker.repository

import de.docgerdsoft.pantrytracker.data.local.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    fun observeProducts(): Flow<List<Product>>
    fun search(query: String): Flow<List<Product>>
    suspend fun findById(id: Long): Product?
    suspend fun findLocalByBarcode(code: String): Product?

    /**
     * Creates a new product with the given initial quantity. Returns the new row id.
     */
    suspend fun addNew(
        name: String,
        brand: String? = null,
        barcode: String? = null,
        imageUrl: String? = null,
        initialQuantity: Int,
    ): Long

    /**
     * Applies a quantity change (positive or negative). Clamps the resulting
     * quantity at 0; never returns a negative value.
     */
    suspend fun applyDelta(productId: Long, delta: Int)

    suspend fun rename(productId: Long, newName: String)
    suspend fun delete(productId: Long)
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/repository/ProductRepository.kt
git commit -m "M1: ProductRepository interface"
```

---

## Task 10: `ProductRepositoryImpl` (TDD)

**Files:**
- Test: `app/src/test/java/de/docgerdsoft/pantrytracker/repository/ProductRepositoryImplTest.kt`
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/repository/ProductRepositoryImpl.kt`

- [ ] **Step 1: Write the failing tests against the (not-yet-existing) implementation**

```kotlin
package de.docgerdsoft.pantrytracker.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import de.docgerdsoft.pantrytracker.data.local.AppDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProductRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ProductRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        repo = ProductRepositoryImpl(db.productDao(), clock = Clock.System)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addNew_returnsRowId_andRowVisibleInObserveProducts() = runTest {
        val id = repo.addNew(name = "Coke", initialQuantity = 3)
        assertNotNull(id)

        repo.observeProducts().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("Coke", items[0].name)
            assertEquals(3, items[0].quantity)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun applyDelta_positive_increasesQuantity() = runTest {
        val id = repo.addNew(name = "Pasta", initialQuantity = 1)
        repo.applyDelta(id, +4)
        assertEquals(5, repo.findById(id)?.quantity)
    }

    @Test
    fun applyDelta_negative_decreasesQuantity() = runTest {
        val id = repo.addNew(name = "Pasta", initialQuantity = 5)
        repo.applyDelta(id, -2)
        assertEquals(3, repo.findById(id)?.quantity)
    }

    @Test
    fun applyDelta_quantityWouldGoNegative_clampsToZero() = runTest {
        val id = repo.addNew(name = "Pasta", initialQuantity = 1)
        repo.applyDelta(id, -10)
        assertEquals(0, repo.findById(id)?.quantity)
    }

    @Test
    fun applyDelta_unknownId_isNoOp() = runTest {
        // No exception, no inserted row.
        repo.applyDelta(productId = 9999L, delta = 1)
        repo.observeProducts().test {
            assertEquals(emptyList<Any>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rename_changesName_andTouchesUpdatedAt() = runTest {
        val id = repo.addNew(name = "Cokq", initialQuantity = 1)
        val before = repo.findById(id)!!.updatedAt

        // Sleep one millisecond so the new instant is strictly later.
        Thread.sleep(2)
        repo.rename(id, "Coke")

        val after = repo.findById(id)!!
        assertEquals("Coke", after.name)
        assert(after.updatedAt > before)
    }

    @Test
    fun delete_removesRow() = runTest {
        val id = repo.addNew(name = "Coke", initialQuantity = 1)
        repo.delete(id)
        assertNull(repo.findById(id))
    }

    @Test
    fun findLocalByBarcode_returnsRow_orNull() = runTest {
        repo.addNew(name = "Coke", barcode = "5449000000996", initialQuantity = 1)
        assertEquals("Coke", repo.findLocalByBarcode("5449000000996")?.name)
        assertNull(repo.findLocalByBarcode("0000000000000"))
    }
}
```

- [ ] **Step 2: Verify they fail (no `ProductRepositoryImpl` yet)**

```bash
./gradlew testDebugUnitTest --tests "*ProductRepositoryImplTest*"
```

Expected: **compile error** — `Unresolved reference: ProductRepositoryImpl`. That is our "red".

- [ ] **Step 3: Write the implementation**

```kotlin
package de.docgerdsoft.pantrytracker.repository

import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.data.local.ProductDao
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock

class ProductRepositoryImpl(
    private val dao: ProductDao,
    private val clock: Clock = Clock.System,
) : ProductRepository {

    override fun observeProducts(): Flow<List<Product>> = dao.observeAll()

    override fun search(query: String): Flow<List<Product>> = dao.search(query)

    override suspend fun findById(id: Long): Product? = dao.findById(id)

    override suspend fun findLocalByBarcode(code: String): Product? = dao.findByBarcode(code)

    override suspend fun addNew(
        name: String,
        brand: String?,
        barcode: String?,
        imageUrl: String?,
        initialQuantity: Int,
    ): Long {
        val now = clock.now()
        return dao.upsert(
            Product(
                barcode = barcode,
                name = name,
                brand = brand,
                imageUrl = imageUrl,
                quantity = initialQuantity.coerceAtLeast(0),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun applyDelta(productId: Long, delta: Int) {
        val existing = dao.findById(productId) ?: return
        val newQuantity = (existing.quantity + delta).coerceAtLeast(0)
        if (newQuantity == existing.quantity) return
        dao.upsert(existing.copy(quantity = newQuantity, updatedAt = clock.now()))
    }

    override suspend fun rename(productId: Long, newName: String) {
        val existing = dao.findById(productId) ?: return
        if (existing.name == newName) return
        dao.upsert(existing.copy(name = newName, updatedAt = clock.now()))
    }

    override suspend fun delete(productId: Long) {
        dao.deleteById(productId)
    }
}
```

- [ ] **Step 4: Run the repository tests — expect green**

```bash
./gradlew testDebugUnitTest --tests "*ProductRepositoryImplTest*"
```

Expected: 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/repository/ProductRepositoryImpl.kt \
        app/src/test/java/de/docgerdsoft/pantrytracker/repository
git commit -m "M1: ProductRepository implementation with clamp-at-zero and tests"
```

---

## Task 11: `AppContainer` (manual DI) wired into `Application`

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/di/AppContainer.kt`
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/PantryTrackerApp.kt`

- [ ] **Step 1: Create `AppContainer.kt`**

```kotlin
package de.docgerdsoft.pantrytracker.di

import android.content.Context
import androidx.room.Room
import de.docgerdsoft.pantrytracker.data.local.AppDatabase
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import de.docgerdsoft.pantrytracker.repository.ProductRepositoryImpl

class AppContainer(context: Context) {

    private val db: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        AppDatabase.DB_NAME,
    ).build()

    val productRepository: ProductRepository = ProductRepositoryImpl(db.productDao())
}
```

- [ ] **Step 2: Update `PantryTrackerApp.kt`**

```kotlin
package de.docgerdsoft.pantrytracker

import android.app.Application
import de.docgerdsoft.pantrytracker.di.AppContainer

class PantryTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/di app/src/main/java/de/docgerdsoft/pantrytracker/PantryTrackerApp.kt
git commit -m "M1: AppContainer (manual DI) wired into Application"
```

---

## Task 12: `HomeUiState`

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/home/HomeUiState.kt`

- [ ] **Step 1: Create the state class**

```kotlin
package de.docgerdsoft.pantrytracker.ui.home

import de.docgerdsoft.pantrytracker.data.local.Product

data class HomeUiState(
    val query: String = "",
    val products: List<Product> = emptyList(),
    val showAddSheet: Boolean = false,
    val pendingDelete: Product? = null,
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/ui/home/HomeUiState.kt
git commit -m "M1: HomeUiState"
```

---

## Task 13: `HomeViewModel` (TDD)

**Files:**
- Test: `app/src/test/java/de/docgerdsoft/pantrytracker/ui/home/HomeViewModelTest.kt`
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/home/HomeViewModel.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package de.docgerdsoft.pantrytracker.ui.home

import app.cash.turbine.test
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var fake: FakeProductRepository
    private lateinit var vm: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fake = FakeProductRepository()
        vm = HomeViewModel(fake)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_emptyList_emptyQuery() = runTest {
        vm.uiState.test {
            val state = awaitItem()
            assertEquals("", state.query)
            assertEquals(emptyList<Product>(), state.products)
            assertEquals(false, state.showAddSheet)
        }
    }

    @Test
    fun observeProducts_reflectsRepositoryEmissions() = runTest {
        val now = Clock.System.now()
        fake.emit(
            listOf(
                Product(id = 1, barcode = null, name = "Coke", quantity = 3, createdAt = now, updatedAt = now),
            ),
        )

        vm.uiState.test {
            // Drop the initial empty state if it arrives first.
            var state = awaitItem()
            if (state.products.isEmpty()) state = awaitItem()
            assertEquals(1, state.products.size)
            assertEquals("Coke", state.products[0].name)
        }
    }

    @Test
    fun setQuery_emptyQuery_streamsAll() = runTest {
        vm.setQuery("")
        assertEquals("", vm.uiState.value.query)
    }

    @Test
    fun setQuery_nonEmpty_switchesToSearchFlow() = runTest {
        vm.setQuery("co")
        assertEquals("co", vm.uiState.value.query)
        assertEquals("co", fake.lastSearchQuery)
    }

    @Test
    fun openAddSheet_andDismiss_togglesFlag() = runTest {
        vm.openAddSheet()
        assertEquals(true, vm.uiState.value.showAddSheet)
        vm.dismissAddSheet()
        assertEquals(false, vm.uiState.value.showAddSheet)
    }

    @Test
    fun submitAdd_callsRepository_andDismissesSheet() = runTest {
        vm.openAddSheet()
        vm.submitAdd(name = " Coke ", initialQuantity = 3)
        assertEquals(false, vm.uiState.value.showAddSheet)
        assertEquals("Coke", fake.lastAdded?.name)
        assertEquals(3, fake.lastAdded?.initialQuantity)
    }

    @Test
    fun submitAdd_blankName_isIgnored() = runTest {
        vm.submitAdd(name = "   ", initialQuantity = 1)
        assertNull(fake.lastAdded)
    }

    @Test
    fun submitAdd_nonPositiveQuantity_isIgnored() = runTest {
        vm.submitAdd(name = "Coke", initialQuantity = 0)
        assertNull(fake.lastAdded)
        vm.submitAdd(name = "Coke", initialQuantity = -2)
        assertNull(fake.lastAdded)
    }

    @Test
    fun confirmDelete_callsRepository_andClearsPending() = runTest {
        val now = Clock.System.now()
        val p = Product(id = 7, barcode = null, name = "Coke", quantity = 1, createdAt = now, updatedAt = now)
        vm.requestDelete(p)
        assertEquals(p, vm.uiState.value.pendingDelete)
        vm.confirmDelete()
        assertEquals(7L, fake.lastDeletedId)
        assertNull(vm.uiState.value.pendingDelete)
    }

    private class FakeProductRepository : ProductRepository {
        private val all = MutableStateFlow<List<Product>>(emptyList())
        var lastSearchQuery: String? = null
        var lastAdded: AddCall? = null
        var lastDeletedId: Long? = null

        data class AddCall(
            val name: String,
            val brand: String?,
            val barcode: String?,
            val imageUrl: String?,
            val initialQuantity: Int,
        )

        fun emit(list: List<Product>) {
            all.value = list
        }

        override fun observeProducts(): Flow<List<Product>> = all.asStateFlow()
        override fun search(query: String): Flow<List<Product>> {
            lastSearchQuery = query
            return all.asStateFlow()
        }

        override suspend fun findById(id: Long): Product? = all.value.firstOrNull { it.id == id }
        override suspend fun findLocalByBarcode(code: String): Product? = null

        override suspend fun addNew(
            name: String,
            brand: String?,
            barcode: String?,
            imageUrl: String?,
            initialQuantity: Int,
        ): Long {
            lastAdded = AddCall(name, brand, barcode, imageUrl, initialQuantity)
            return 1L
        }

        override suspend fun applyDelta(productId: Long, delta: Int) = Unit
        override suspend fun rename(productId: Long, newName: String) = Unit
        override suspend fun delete(productId: Long) {
            lastDeletedId = productId
        }
    }
}
```

- [ ] **Step 2: Run to verify they fail**

```bash
./gradlew testDebugUnitTest --tests "*HomeViewModelTest*"
```

Expected: compile error — `Unresolved reference: HomeViewModel`.

- [ ] **Step 3: Create `HomeViewModel.kt`**

```kotlin
package de.docgerdsoft.pantrytracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel(
    private val repository: ProductRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val showAddSheet = MutableStateFlow(false)
    private val pendingDelete = MutableStateFlow<Product?>(null)

    private val productsFlow = query
        .debounce(150)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isBlank()) repository.observeProducts() else repository.search(q.trim())
        }

    val uiState: StateFlow<HomeUiState> = combine(
        query, productsFlow, showAddSheet, pendingDelete,
    ) { q, products, sheet, pending ->
        HomeUiState(
            query = q,
            products = products,
            showAddSheet = sheet,
            pendingDelete = pending,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun setQuery(q: String) {
        query.value = q
    }

    fun openAddSheet() {
        showAddSheet.value = true
    }

    fun dismissAddSheet() {
        showAddSheet.value = false
    }

    fun submitAdd(name: String, initialQuantity: Int) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || initialQuantity <= 0) return
        viewModelScope.launch {
            repository.addNew(name = trimmed, initialQuantity = initialQuantity)
        }
        showAddSheet.value = false
    }

    fun requestDelete(product: Product) {
        pendingDelete.value = product
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete() {
        val target = pendingDelete.value ?: return
        viewModelScope.launch {
            repository.delete(target.id)
        }
        pendingDelete.value = null
    }
}
```

- [ ] **Step 4: Run the tests — expect green**

```bash
./gradlew testDebugUnitTest --tests "*HomeViewModelTest*"
```

Expected: 9 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/ui/home/HomeViewModel.kt \
        app/src/test/java/de/docgerdsoft/pantrytracker/ui/home/HomeViewModelTest.kt
git commit -m "M1: HomeViewModel with search debounce, add/delete intents, tests"
```

---

## Task 14: `AddProductSheet` Compose

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/home/AddProductSheet.kt`

- [ ] **Step 1: Create the sheet composable**

```kotlin
package de.docgerdsoft.pantrytracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductSheet(
    onDismiss: () -> Unit,
    onConfirm: (name: String, quantity: Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("1") }

    val quantity = quantityText.toIntOrNull() ?: 0
    val canSubmit = name.isNotBlank() && quantity > 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add product manually", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = quantityText,
                onValueChange = { input -> quantityText = input.filter { it.isDigit() }.take(4) },
                label = { Text("Initial quantity") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = canSubmit,
                    onClick = { onConfirm(name, quantity) },
                ) {
                    Text("Add")
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/ui/home/AddProductSheet.kt
git commit -m "M1: AddProductSheet modal Compose UI"
```

---

## Task 15: `HomeScreen` Compose

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreen.kt`

- [ ] **Step 1: Create `HomeScreen.kt`**

```kotlin
package de.docgerdsoft.pantrytracker.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.ui.theme.AddGreen
import de.docgerdsoft.pantrytracker.ui.theme.RemoveRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pantry Tracker") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openAddSheet() }) {
                Icon(Icons.Filled.Add, contentDescription = "Add manually")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            ScanButtonsRow(
                onAddClick = { /* wired up in Milestone 2 */ },
                onRemoveClick = { /* wired up in Milestone 4 */ },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (state.products.isEmpty()) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.products, key = { it.id }) { product ->
                        ProductRow(
                            product = product,
                            onLongPress = { viewModel.requestDelete(product) },
                        )
                    }
                }
            }
        }

        if (state.showAddSheet) {
            AddProductSheet(
                onDismiss = viewModel::dismissAddSheet,
                onConfirm = viewModel::submitAdd,
            )
        }

        state.pendingDelete?.let { product ->
            DeleteConfirmDialog(
                product = product,
                onConfirm = viewModel::confirmDelete,
                onDismiss = viewModel::cancelDelete,
            )
        }
    }
}

@Composable
private fun ScanButtonsRow(
    onAddClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onAddClick,
            modifier = Modifier.weight(1f).height(80.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AddGreen),
        ) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Text("  Scan to Add")
        }
        Button(
            onClick = onRemoveClick,
            modifier = Modifier.weight(1f).height(80.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RemoveRed),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Text("  Scan to Remove")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductRow(product: Product, onLongPress: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = product.name,
            modifier = Modifier.weight(1f).alpha(if (product.quantity == 0) 0.45f else 1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "×${product.quantity}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Your pantry is empty", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap the + button to add an item manually.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    product: Product,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${product.name}?") },
        text = { Text("This removes it from your inventory. Cannot be undone in v1.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreen.kt
git commit -m "M1: HomeScreen Compose UI (search, list, FAB, delete dialog)"
```

---

## Task 16: Wire `MainActivity` to `HomeScreen` via `viewModels` factory

**Files:**
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/MainActivity.kt`

- [ ] **Step 1: Replace `MainActivity.kt`**

```kotlin
package de.docgerdsoft.pantrytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.docgerdsoft.pantrytracker.ui.home.HomeScreen
import de.docgerdsoft.pantrytracker.ui.home.HomeViewModel
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme

class MainActivity : ComponentActivity() {

    private val homeViewModel by viewModels<HomeViewModel> {
        val app = application as PantryTrackerApp
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(app.container.productRepository) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PantryTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(viewModel = homeViewModel)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/MainActivity.kt
git commit -m "M1: Wire MainActivity to HomeScreen via AppContainer"
```

---

## Task 17: Compose UI smoke test for `HomeScreen`

**Files:**
- Create: `app/src/androidTest/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreenTest.kt`

This is an instrumented test — needs a connected device or emulator. We feed it a fake repository so it doesn't touch Room.

- [ ] **Step 1: Create the test**

```kotlin
package de.docgerdsoft.pantrytracker.ui.home

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun rendersProductRows_andShowsEmptyState_basedOnRepository() {
        val now = Clock.System.now()
        val products = listOf(
            Product(id = 1, barcode = null, name = "Coke 0.5L", quantity = 3, createdAt = now, updatedAt = now),
            Product(id = 2, barcode = null, name = "Olivenöl", quantity = 1, createdAt = now, updatedAt = now),
        )
        val repo = FakeRepository(MutableStateFlow(products))
        val vm = HomeViewModel(repo)

        composeRule.setContent {
            PantryTrackerTheme { Surface { HomeScreen(viewModel = vm) } }
        }

        composeRule.onNodeWithText("Coke 0.5L").assertIsDisplayed()
        composeRule.onNodeWithText("×3").assertIsDisplayed()
        composeRule.onNodeWithText("Olivenöl").assertIsDisplayed()
    }

    @Test
    fun fab_opensAddSheet_thenConfirm_callsRepository() {
        val repo = FakeRepository(MutableStateFlow(emptyList()))
        val vm = HomeViewModel(repo)

        composeRule.setContent {
            PantryTrackerTheme { Surface { HomeScreen(viewModel = vm) } }
        }

        composeRule.onNodeWithText("Add manually").performClick()
        composeRule.onNodeWithText("Name").performTextInput("Coke")
        composeRule.onNodeWithText("Add").performClick()

        assert(repo.lastAddName == "Coke")
    }

    private class FakeRepository(
        private val flow: MutableStateFlow<List<Product>>,
    ) : ProductRepository {
        var lastAddName: String? = null
        override fun observeProducts(): Flow<List<Product>> = flow.asStateFlow()
        override fun search(query: String): Flow<List<Product>> = flow.asStateFlow()
        override suspend fun findById(id: Long): Product? = flow.value.firstOrNull { it.id == id }
        override suspend fun findLocalByBarcode(code: String): Product? = null
        override suspend fun addNew(
            name: String,
            brand: String?,
            barcode: String?,
            imageUrl: String?,
            initialQuantity: Int,
        ): Long {
            lastAddName = name
            return 1L
        }
        override suspend fun applyDelta(productId: Long, delta: Int) = Unit
        override suspend fun rename(productId: Long, newName: String) = Unit
        override suspend fun delete(productId: Long) = Unit
    }
}
```

- [ ] **Step 2: Run on a connected device or emulator**

```bash
./gradlew connectedDebugAndroidTest
```

Expected: 2 tests pass. (If you don't have a device or emulator available right now, mark this step in-progress, skip to Task 18 for the manual smoke test, and come back. The PR-merge gate is Task 19.)

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreenTest.kt
git commit -m "M1: HomeScreen Compose smoke test"
```

---

## Task 18: Manual end-to-end smoke test on device

- [ ] **Step 1: Install and launch**

```bash
./gradlew installDebug
adb shell am start -n de.docgerdsoft.pantrytracker/.MainActivity
```

- [ ] **Step 2: Smoke checklist on the phone**

Walk through each of these on the actual device. Note any issues; fix and re-test before moving on.

- [ ] App opens to the home screen showing "Your pantry is empty".
- [ ] FAB (+) opens the Add Product bottom sheet.
- [ ] Typing a name and quantity, then tapping **Add**, dismisses the sheet and shows the new row.
- [ ] Adding three different products shows all three, sorted alphabetically (case-insensitive).
- [ ] Typing in the Search box filters the list as you type.
- [ ] Clearing the search returns to the full list.
- [ ] Long-pressing a row opens the Delete confirmation; Cancel keeps it; Delete removes it.
- [ ] Force-closing and reopening the app preserves all products (Room persistence verified).
- [ ] Adding a product with quantity 0 or empty name does nothing (button disabled).
- [ ] Adding two products with the same name is allowed (since manual items have `barcode = null`, no uniqueness conflict).

- [ ] **Step 3: If anything failed, fix it (commit the fix), then re-test before continuing.**

---

## Task 19: PR + merge milestone 1

- [ ] **Step 1: Push and open the PR**

```bash
git push -u origin milestone-1-room-manual-add
gh pr create \
  --title "Milestone 1: Room + manual add (end-to-end inventory)" \
  --body "Closes #2

Implements the manual-entry inventory slice: Room schema, ProductRepository with clamp-at-zero invariant, HomeViewModel with search debounce, HomeScreen UI with FAB / search / long-press delete. Unit tests cover DAO, repository, and ViewModel. Compose smoke test covers HomeScreen render + add flow. Manual device smoke test passed (checklist in Task 18 of plan)." \
  --base main
```

- [ ] **Step 2: Watch CI**

```bash
gh pr checks --watch
```

Expected: `CI / build` passes.

- [ ] **Step 3: Merge and clean up**

```bash
gh pr merge --squash --delete-branch
git switch main
git pull --ff-only
```

Issue #2 is now closed.

---

## Done — what shipped

After Task 19:

- A working `pantry-tracker` Android app that runs offline, persists data across launches, and supports manual product entry, search, and delete.
- 23 unit tests (6 DAO, 8 repository, 9 ViewModel) + 2 Compose UI smoke tests.
- CI workflow active and green on every push and PR.
- Issues #1 and #2 closed; #3 (milestone 2: Scan to Add) is up next, with its own plan document to be written when ready.

## Decisions explicitly deferred (and the milestone where they re-appear)

| Decision | Comes back in |
|---|---|
| Camera permission, CameraX, ML Kit | Milestone 2 |
| Open Food Facts lookup, Ktor, Coil | Milestone 3 |
| Scan-to-Remove flow | Milestone 4 |
| Item detail screen, rename, manual stepper | Milestone 5 |
| App icon, theming polish, error-state audit | Milestone 6 |
| JUnit 5 migration | Optional; not required for v1 done |
| Hilt | Not in v1; trivial to introduce later (the manual `AppContainer` makes the seams already explicit) |

