# Milestone 6 — Polish Pass — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the v1-feature-complete app feel finished — fern-seeded Material 3 theme, custom three-jars launcher icon, typographic home empty state with CTAs, in-context camera-permission rationale gate, and a sweep over every `catch (Exception)` for tone + visible-surface compliance.

**Architecture:** Five orthogonal slices, each touching a different part of the codebase. Follow existing M5 patterns: typed UiState fields, Compose tests in `app/src/androidTest/` via `createComposeRule()`, `java.util.logging.Logger` (not `android.util.Log`) for unit-test JVM compatibility, manual DI via `AppContainer`.

**Tech Stack:** Kotlin 2.3.21, Compose, Material 3 (Compose BOM 2026.05.00), Android 8+ adaptive icons (`<adaptive-icon>` from `mipmap-anydpi-v26`). No new dependencies.

**Tracking issue:** TBD — create at handoff time.
**Spec:** `docs/superpowers/specs/2026-05-17-milestone-6-polish-pass-design.md`

---

### Task 1: Theme — replace hand-tuned palette with `Fern` seed

**Why:** Today `Theme.kt` is hand-tuned — `Md3Primary` is set on light + dark, surface colours are hard-coded. The spec calls for one seed colour with `lightColorScheme(...)` / `darkColorScheme(...)` generating the rest. The current `Md3Primary = 0xFF3F6B3F` is close to fern but slightly cooler — replace with the spec's `0xFF4F7942`.

**Files:**
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/theme/Color.kt`
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/theme/Theme.kt`

**No test** — theme is visual. Verified by manual smoke after the milestone lands.

- [ ] **Step 1: Replace `Color.kt`**

```kotlin
package de.docgerdsoft.pantrytracker.ui.theme

import androidx.compose.ui.graphics.Color

/** Pantry/produce-evocative seed colour. Material 3 derives the rest of the
 *  scheme (secondary, tertiary, surface, error, …) from this one anchor. */
val Fern: Color = Color(0xFF4F7942)

// Used by ScanButtonsRow in HomeScreen for the two big primary actions.
// These are intentionally outside the M3-derived scheme so the "add" and
// "remove" verbs stay distinguishable across light and dark.
val AddGreen: Color = Color(0xFF2A6A2A)
val RemoveRed: Color = Color(0xFF8A2A2A)
```

(Drops `Md3Primary`, `Md3OnPrimary`, `Md3Surface`, `Md3OnSurface`, `Md3SurfaceDark`, `Md3OnSurfaceDark` — they're now seed-derived.)

- [ ] **Step 2: Replace `Theme.kt`**

```kotlin
package de.docgerdsoft.pantrytracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(primary = Fern)
private val DarkColors = darkColorScheme(primary = Fern)

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

- [ ] **Step 3: Verify all call sites still compile**

Run: `grep -rn "Md3Primary\|Md3OnPrimary\|Md3Surface\|Md3OnSurface" app/src/`
Expected: zero matches. If anything still references those names, update the call site to use `MaterialTheme.colorScheme.<field>` instead.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/ui/theme/Color.kt \
        app/src/main/java/de/docgerdsoft/pantrytracker/ui/theme/Theme.kt
git commit -m "feat(m6): fern-seeded Material 3 theme — drop hand-tuned palette"
```

---

### Task 2: App icon — three jars on a shelf, adaptive

**Why:** No launcher icon exists; the app shows the default Android-bot. Adaptive icon is required for Android 8+ (minSdk = 26).

**Files:**
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/AndroidManifest.xml` (add `android:icon` + `android:roundIcon` to `<application>`)

**No test** — icon rendering is verified by launching on a device.

- [ ] **Step 1: Create `ic_launcher_foreground.xml`**

Three jars sitting on a horizontal shelf, white on transparent, sized to the 108dp adaptive canvas with all art inside the 66dp safe zone (centred — so x ∈ [21, 87], y ∈ [21, 87]).

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Shelf line: y=66, spans x=26..82 (safe-zone aware). -->
    <path
        android:pathData="M26,66 L82,66"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="3"
        android:strokeLineCap="round"/>

    <!-- Jar 1 (left): body 30..42 × 46..64, cap 32..40 × 42..46 -->
    <path
        android:pathData="M30,46 L30,64 L42,64 L42,46 Z M32,42 L40,42 L40,46 L32,46 Z"
        android:fillColor="#FFFFFF"/>

    <!-- Jar 2 (center): body 48..60 × 46..64, cap 50..58 × 42..46 -->
    <path
        android:pathData="M48,46 L48,64 L60,64 L60,46 Z M50,42 L58,42 L58,46 L50,46 Z"
        android:fillColor="#FFFFFF"/>

    <!-- Jar 3 (right): body 66..78 × 46..64, cap 68..76 × 42..46 -->
    <path
        android:pathData="M66,46 L66,64 L78,64 L78,46 Z M68,42 L76,42 L76,46 L68,46 Z"
        android:fillColor="#FFFFFF"/>
</vector>
```

- [ ] **Step 2: Create `values/colors.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Adaptive-icon background. Matches Fern (0xFF4F7942) from Color.kt. -->
    <color name="ic_launcher_background">#4F7942</color>
</resources>
```

- [ ] **Step 3: Create `mipmap-anydpi-v26/ic_launcher.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

- [ ] **Step 4: Create `mipmap-anydpi-v26/ic_launcher_round.xml`**

Identical content — the launcher mask handles the round vs square shape.

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

- [ ] **Step 5: Wire into manifest**

Locate the `<application>` opening tag in `app/src/main/AndroidManifest.xml` and add the two icon attributes (preserve all existing attributes). Example shape:

```xml
<application
    android:allowBackup="true"
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:label="@string/app_name"
    android:theme="@style/Theme.PantryTracker">
```

- [ ] **Step 6: Confirm no legacy PNG fallbacks exist**

Run: `find app/src/main/res -name "ic_launcher*.png" -o -name "ic_launcher*.webp"`
Expected: empty output. (We don't have them; this confirms.)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml \
        app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml \
        app/src/main/res/drawable/ic_launcher_foreground.xml \
        app/src/main/res/values/colors.xml \
        app/src/main/AndroidManifest.xml
git commit -m "feat(m6): adaptive launcher icon — three jars on a shelf, white on fern"
```

---

### Task 3: Home empty state — CTAs + NoMatchesHint

**Why:** Today `HomeScreen.EmptyState` (lines 183–194) shows whenever `state.products.isEmpty()` — including when search returns zero rows. The spec splits these: a big two-button CTA for the truly-empty pantry, and a small "no matches" hint for empty search results.

**Files:**
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreen.kt` (lines ~88-100 — list-body branch; ~183-194 — existing EmptyState)
- Create: `app/src/androidTest/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreenEmptyStateTest.kt`

- [ ] **Step 1: Write the failing Compose test**

Create `app/src/androidTest/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreenEmptyStateTest.kt`:

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
import de.docgerdsoft.pantrytracker.repository.ScanCandidate
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock

class HomeScreenEmptyStateTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun emptyPantry_emptyQuery_showsCTAs_andHidesNoMatches() {
        val repo = FakeRepository(MutableStateFlow(emptyList()))
        composeRule.setContent {
            PantryTrackerTheme {
                Surface { HomeScreen(viewModel = HomeViewModel(repo),
                    onScanAddClick = {}, onScanRemoveClick = {}, onProductClick = {}) }
            }
        }
        composeRule.onNodeWithText("Your pantry is empty").assertIsDisplayed()
        composeRule.onNodeWithText("Scan to Add").assertIsDisplayed()
        composeRule.onNodeWithText("Add manually").assertIsDisplayed()
    }

    @Test
    fun emptyPantry_nonEmptyQuery_showsNoMatchesHint_notCTAs() {
        val repo = FakeRepository(MutableStateFlow(emptyList()))
        composeRule.setContent {
            PantryTrackerTheme {
                Surface { HomeScreen(viewModel = HomeViewModel(repo),
                    onScanAddClick = {}, onScanRemoveClick = {}, onProductClick = {}) }
            }
        }
        composeRule.onNodeWithText("Search").performClick()
        composeRule.onNodeWithText("Search").performTextInput("zzz")
        composeRule.onNodeWithText("No matches for \"zzz\"").assertIsDisplayed()
    }

    @Test
    fun nonEmptyPantry_showsList_hidesBothEmptyStates() {
        val now = Clock.System.now()
        val repo = FakeRepository(MutableStateFlow(listOf(
            Product(id = 1, barcode = null, name = "Coke", quantity = 3,
                createdAt = now, updatedAt = now),
        )))
        composeRule.setContent {
            PantryTrackerTheme {
                Surface { HomeScreen(viewModel = HomeViewModel(repo),
                    onScanAddClick = {}, onScanRemoveClick = {}, onProductClick = {}) }
            }
        }
        composeRule.onNodeWithText("Coke").assertIsDisplayed()
    }

    private class FakeRepository(
        private val flow: MutableStateFlow<List<Product>>,
    ) : ProductRepository {
        override fun observeProducts(): Flow<List<Product>> = flow.asStateFlow()
        override fun search(query: String): Flow<List<Product>> =
            MutableStateFlow(flow.value.filter { it.name.contains(query, true) }).asStateFlow()
        override fun observeById(id: Long): Flow<Product?> =
            MutableStateFlow(flow.value.firstOrNull { it.id == id }).asStateFlow()
        override suspend fun findById(id: Long): Product? = flow.value.firstOrNull { it.id == id }
        override suspend fun findLocalByBarcode(code: String): Product? = null
        override suspend fun lookupForPreview(code: String): ScanCandidate? = null
        override suspend fun addNew(name: String, brand: String?, barcode: String?,
            imageUrl: String?, initialQuantity: Int): Long = 0L
        override suspend fun applyDelta(productId: Long, delta: Int) = Unit
        override suspend fun rename(productId: Long, newName: String) = Unit
        override suspend fun delete(productId: Long) = Unit
    }
}
```

- [ ] **Step 2: Refactor `HomeScreen.kt` body branch**

Replace the existing `if (state.products.isEmpty()) { … } else { LazyColumn … }` block (current lines ~88-100) with three explicit branches:

```kotlin
when {
    state.products.isEmpty() && state.query.isBlank() -> EmptyState(
        modifier = Modifier.fillMaxSize(),
        onScanAdd = onScanAddClick,
        onAddManual = viewModel::openAddSheet,
    )
    state.products.isEmpty() -> NoMatchesHint(query = state.query)
    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(state.products, key = { it.id }) { product ->
            ProductRow(
                product = product,
                onClick = { onProductClick(product.id) },
                onLongPress = { viewModel.requestDelete(product) },
            )
        }
    }
}
```

- [ ] **Step 3: Replace `EmptyState` + add `NoMatchesHint`**

Replace the existing `EmptyState` (current lines ~183-194) with the expanded CTA version and a new sibling for empty-search-results:

```kotlin
@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    onScanAdd: () -> Unit,
    onAddManual: () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Your pantry is empty", style = MaterialTheme.typography.titleLarge)
            Text(
                "Tap Scan to Add or + to start tracking",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onScanAdd) { Text("Scan to Add") }
                OutlinedButton(onClick = onAddManual) { Text("Add manually") }
            }
        }
    }
}

@Composable
private fun NoMatchesHint(query: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "No matches for \"$query\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

(Add any missing imports: `Arrangement`, `OutlinedButton`, `Spacer`, `height`, `padding`, `fillMaxWidth`.)

- [ ] **Step 4: Run the tests**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*.HomeScreenEmptyStateTest"`
Expected: 3 passing.

(If running without an emulator, defer this to manual verification during the milestone PR; the unit tests stay green via `./gradlew :app:testDebugUnitTest`.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreen.kt \
        app/src/androidTest/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreenEmptyStateTest.kt
git commit -m "feat(m6): home empty state — CTAs + NoMatchesHint for empty search"
```

---

### Task 4: Camera-permission gate

**Why:** Today both Scan flows go straight to the system permission prompt. The spec calls for an in-context rationale dialog explaining *why* we need camera, plus a recoverable path for "denied + don't ask again".

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/CameraPermissionGate.kt`
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/PantryTrackerNavGraph.kt` (wrap both `scan/add` and `scan/remove` composables with the gate)
- Create: `app/src/androidTest/java/de/docgerdsoft/pantrytracker/ui/scan/CameraPermissionGateTest.kt`

**Approach:** Split the gate into a stateful `CameraPermissionGate` (does `rememberLauncherForActivityResult`, `shouldShowRequestPermissionRationale`, settings intent) and a pure `CameraPermissionGateContent(phase, onContinue, onOpenSettings, onNavigateBack, content)` that the test drives directly with each phase. Keeps the test off any emulator-specific permission path.

- [ ] **Step 1: Create the gate file (state machine + composables)**

```kotlin
package de.docgerdsoft.pantrytracker.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** State machine for the camera-permission rationale gate. See M6 spec §2.4. */
sealed interface CameraPermissionPhase {
    /** No decision yet — show the rationale dialog so the user can opt in. */
    data object Unknown : CameraPermissionPhase
    /** Permission granted — render the wrapped scan content. */
    data object Granted : CameraPermissionPhase
    /** Denied once, but `shouldShowRationale` is true — let user retry. */
    data object SoftDenied : CameraPermissionPhase
    /** Denied + "don't ask again" — only recoverable via system settings. */
    data object HardDenied : CameraPermissionPhase
}

/** Stateful wrapper: checks permission, drives the launcher, computes phase. */
@Composable
fun CameraPermissionGate(
    onNavigateBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var phase: CameraPermissionPhase by remember {
        mutableStateOf(initialPhase(context))
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        phase = when {
            granted -> CameraPermissionPhase.Granted
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true ->
                CameraPermissionPhase.SoftDenied
            else -> CameraPermissionPhase.HardDenied
        }
    }

    CameraPermissionGateContent(
        phase = phase,
        onContinue = { launcher.launch(Manifest.permission.CAMERA) },
        onOpenSettings = { openAppSettings(context) },
        onNavigateBack = onNavigateBack,
        content = content,
    )
}

/** Pure presentation — testable without an emulator. */
@Composable
fun CameraPermissionGateContent(
    phase: CameraPermissionPhase,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    when (phase) {
        CameraPermissionPhase.Granted -> content()
        CameraPermissionPhase.Unknown -> RationaleDialog(
            onContinue = onContinue,
            onCancel = onNavigateBack,
        )
        CameraPermissionPhase.SoftDenied -> DeniedScreen(
            headline = "Camera access needed",
            body = "Pantry Tracker uses the camera to scan barcodes. Nothing leaves your device.",
            primaryLabel = "Try again",
            onPrimary = onContinue,
            onBack = onNavigateBack,
        )
        CameraPermissionPhase.HardDenied -> DeniedScreen(
            headline = "Camera access blocked",
            body = "Open Settings and allow camera access for Pantry Tracker, then come back.",
            primaryLabel = "Open settings",
            onPrimary = onOpenSettings,
            onBack = onNavigateBack,
        )
    }
}

@Composable
private fun RationaleDialog(onContinue: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Camera access") },
        text = {
            Text("We scan barcodes to find products. Nothing leaves your device.")
        },
        confirmButton = { Button(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun DeniedScreen(
    headline: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(headline, style = MaterialTheme.typography.titleLarge)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onPrimary) { Text(primaryLabel) }
            OutlinedButton(onClick = onBack) { Text("Go back") }
        }
    }
}

private fun initialPhase(context: android.content.Context): CameraPermissionPhase =
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED) {
        CameraPermissionPhase.Granted
    } else {
        CameraPermissionPhase.Unknown
    }

private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
```

(Add missing import: `import androidx.compose.ui.unit.dp`.)

- [ ] **Step 2: Wire the gate into NavGraph**

In `app/src/main/java/de/docgerdsoft/pantrytracker/PantryTrackerNavGraph.kt`, wrap the contents of both `composable(Routes.SCAN_ADD) { … }` and `composable(Routes.SCAN_REMOVE) { … }` with `CameraPermissionGate`. Example for `SCAN_ADD`:

```kotlin
composable(Routes.SCAN_ADD) {
    val factory = remember(container) {
        viewModelFactory {
            initializer { ScanViewModel(container.productRepository, initialMode = ScanMode.Add) }
        }
    }
    val vm: ScanViewModel = viewModel(factory = factory)
    CameraPermissionGate(onNavigateBack = { navController.popBackStack() }) {
        ScanScreen(viewModel = vm, onNavigateBack = { navController.popBackStack() })
    }
}
```

(Add `import de.docgerdsoft.pantrytracker.ui.scan.CameraPermissionGate`. Apply the same shape to `SCAN_REMOVE`.)

- [ ] **Step 3: Write the Compose tests**

Create `app/src/androidTest/java/de/docgerdsoft/pantrytracker/ui/scan/CameraPermissionGateTest.kt`:

```kotlin
package de.docgerdsoft.pantrytracker.ui.scan

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import org.junit.Rule
import org.junit.Test

class CameraPermissionGateTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun granted_rendersContent() {
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGateContent(
                        phase = CameraPermissionPhase.Granted,
                        onContinue = {}, onOpenSettings = {}, onNavigateBack = {},
                    ) { Text("WRAPPED CONTENT") }
                }
            }
        }
        composeRule.onNodeWithText("WRAPPED CONTENT").assertIsDisplayed()
    }

    @Test
    fun unknown_showsRationaleDialog_andContinueInvokesCallback() {
        var continueCalled = false
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGateContent(
                        phase = CameraPermissionPhase.Unknown,
                        onContinue = { continueCalled = true },
                        onOpenSettings = {}, onNavigateBack = {},
                    ) { Text("WRAPPED") }
                }
            }
        }
        composeRule.onNodeWithText("Camera access").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").performClick()
        assert(continueCalled) { "Continue button must trigger onContinue" }
    }

    @Test
    fun softDenied_showsRetryAffordance_andNotContent() {
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGateContent(
                        phase = CameraPermissionPhase.SoftDenied,
                        onContinue = {}, onOpenSettings = {}, onNavigateBack = {},
                    ) { Text("WRAPPED") }
                }
            }
        }
        composeRule.onNodeWithText("Camera access needed").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun hardDenied_showsOpenSettings_andInvokesIntentCallback() {
        var openSettingsCalled = false
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGateContent(
                        phase = CameraPermissionPhase.HardDenied,
                        onContinue = {},
                        onOpenSettings = { openSettingsCalled = true },
                        onNavigateBack = {},
                    ) { Text("WRAPPED") }
                }
            }
        }
        composeRule.onNodeWithText("Camera access blocked").assertIsDisplayed()
        composeRule.onNodeWithText("Open settings").performClick()
        assert(openSettingsCalled) { "Open settings button must trigger onOpenSettings" }
    }
}
```

- [ ] **Step 4: Verify import order in the new files**

Run: `./gradlew :app:detekt`
Expected: pass (or specific ImportOrdering violations to fix by moving `kotlin.*` imports last per IDEA convention — see M5 fix commits for the pattern).

(If no JDK available locally, defer to CI — Detekt runs as part of `:app:build`.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/CameraPermissionGate.kt \
        app/src/main/java/de/docgerdsoft/pantrytracker/PantryTrackerNavGraph.kt \
        app/src/androidTest/java/de/docgerdsoft/pantrytracker/ui/scan/CameraPermissionGateTest.kt
git commit -m "feat(m6): in-context camera-permission rationale gate"
```

---

### Task 5: Error-state audit — sweep + tone normalization

**Why:** §2.5 of the spec requires every `catch (Exception)` in `app/src/main` to (a) rethrow CancellationException first, (b) log via `java.util.logging.Logger`, (c) surface a user-visible state field, (d) format messages as `"Couldn't <verb>: <reason>"`. DetailViewModel already follows the pattern; ScanViewModel + ProductRepositoryImpl + OffApiClient need a sweep.

**Files (audit targets — exact line numbers found at sweep time):**
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModel.kt`
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/repository/ProductRepositoryImpl.kt`
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/data/remote/OffApiClient.kt`
- May modify: `app/src/test/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModelTest.kt` (if message-string assertions need updating after tone normalization)

- [ ] **Step 1: Sweep — list every `catch (Exception)` in main**

Run: `grep -rn "catch (.*Exception" app/src/main --include="*.kt"`
Expected output: a numbered list of catch sites. Record each one.

- [ ] **Step 2: Per-site checklist**

For each catch site:

| Property | Required | DetailViewModel template |
|---|---|---|
| Rethrows `CancellationException` first | yes | `catch (e: CancellationException) { throw e }` |
| Logs via `java.util.logging.Logger` | yes | `logger.log(Level.WARNING, "<op> failed", e)` |
| Surfaces user-visible state | yes | `_uiState.update { it.copy(error = "Couldn't <verb>: ${e.message ?: "unknown error"}") }` |
| Suppresses Detekt warnings appropriately | yes | `@Suppress("TooGenericExceptionCaught")` on the enclosing fun, `@Suppress("SwallowedException")` on the log call |

Reference: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/detail/DetailViewModel.kt:89-93` (the `surfaceError` helper).

- [ ] **Step 3: For ScanViewModel — verify Phase.Error messages**

Open `app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModel.kt`. Find every transition to `Phase.Error`. Rewrite message strings to match the `"Couldn't <verb>: <reason>"` pattern. Example transformations:

| Before | After |
|---|---|
| `"Network timeout"` | `"Couldn't look up barcode: network timeout"` |
| `"Already in inventory"` | (unchanged — this is a business-logic message, not an error) |
| `"OFF unavailable"` | `"Couldn't look up barcode: ${e.message ?: "service unavailable"}"` |

(Leave business-logic transitions like "Already in inventory" alone — only normalize *failure* messages.)

- [ ] **Step 4: Update ScanViewModelTest assertions for the new message strings**

Find any `assertEquals(...Phase.Error...)` in `app/src/test/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModelTest.kt`. Update the expected strings to match the new tone. Keep the existing test structure; only the string literals change.

- [ ] **Step 5: For ProductRepositoryImpl — verify catches surface, don't swallow**

Open `app/src/main/java/de/docgerdsoft/pantrytracker/repository/ProductRepositoryImpl.kt`. Find any `catch (Exception)` that logs and continues. If the calling ViewModel doesn't observe a failure mode, **rethrow** instead — the ViewModel's own catch arm will surface it. (Don't double-surface; pick the layer closest to the user.)

- [ ] **Step 6: Run the unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: green. Any failure means an updated message string wasn't matched in tests — fix the test assertion.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModel.kt \
        app/src/main/java/de/docgerdsoft/pantrytracker/repository/ProductRepositoryImpl.kt \
        app/src/main/java/de/docgerdsoft/pantrytracker/data/remote/OffApiClient.kt \
        app/src/test/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModelTest.kt
git commit -m "fix(m6): error-state audit — normalize tone to \"Couldn't <verb>: <reason>\""
```

(Omit any of the three main-file paths if the sweep finds no changes needed for that file.)

---

### Task 6: Fix androidTest FakeRepository + extend CI to `assembleAndroidTest`

**Why:** `HomeScreenTest.kt` (androidTest) has a `FakeRepository` missing `observeById` (M5) and `lookupForPreview` (M3) overrides — see task #71. Doesn't break the current CI because the workflow only runs `assembleDebug testDebugUnitTest lintDebug`, not `assembleAndroidTest`. Extending CI to also compile (but not run) androidTest closes the gap so future interface evolution can't drift.

**Files:**
- Modify: `app/src/androidTest/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreenTest.kt` (lines 58-79 — the `FakeRepository` class)
- Modify: `.github/workflows/ci.yml` (add `:app:assembleAndroidTest` to the build step)

- [ ] **Step 1: Add the missing overrides to `HomeScreenTest.FakeRepository`**

Open `app/src/androidTest/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreenTest.kt`. Inside `class FakeRepository(…) : ProductRepository`, after the existing `findById` override, add:

```kotlin
override fun observeById(id: Long): Flow<Product?> =
    MutableStateFlow(flow.value.firstOrNull { it.id == id }).asStateFlow()
override suspend fun lookupForPreview(code: String): ScanCandidate? = null
```

(Add missing import: `import de.docgerdsoft.pantrytracker.repository.ScanCandidate`.)

- [ ] **Step 2: Extend CI to compile androidTest**

Open `.github/workflows/ci.yml`. Locate the gradle invocation in the build job (typically a line like `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`). Add `:app:assembleAndroidTest`:

```yaml
- name: Build + test + lint
  run: ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleAndroidTest --stacktrace
```

(Compiles the Compose test sources without running them; surfaces interface-evolution drift at PR time.)

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreenTest.kt \
        .github/workflows/ci.yml
git commit -m "fix(m6): close interface-evolution gap — androidTest FakeRepository + CI compile"
```

---

### Task 7: Push, open PR, multi-agent review, fix findings, hand off

**Why:** Standing rule — every PR opened in this workflow gets the multi-agent review treatment, inline threads, and GraphQL-resolve-on-fix.

**No new files** — orchestration only.

- [ ] **Step 1: Push the branch**

```bash
git push -u origin m6-polish-pass
```

- [ ] **Step 2: Create tracking issue**

```bash
gh issue create --title "Milestone 6: Polish pass" --body "$(cat <<'EOF'
Five touch-points for the v1 finish line:
- Fern-seeded Material 3 theme
- Three-jars adaptive launcher icon
- Home empty-state CTAs + NoMatchesHint
- In-context camera-permission rationale gate
- Error-state audit + tone normalization (folds in task #71)

Spec: docs/superpowers/specs/2026-05-17-milestone-6-polish-pass-design.md
Plan: docs/superpowers/plans/2026-05-17-milestone-6-polish-pass.md
EOF
)"
```

Record the issue number (e.g. `#NN`).

- [ ] **Step 3: Open the PR**

```bash
gh pr create --title "M6: polish pass — theme, icon, empty state, permission gate, error audit" \
  --body "$(cat <<'EOF'
## Summary
- Replace hand-tuned palette with fern-seeded Material 3 theme (light + dark)
- Add adaptive launcher icon (three jars on a shelf, white-on-fern)
- Refactor Home empty state: two-CTA empty pantry + small NoMatchesHint for empty search
- Add in-context camera-permission rationale gate (rationale dialog → system prompt → granted/soft-denied/hard-denied paths with settings deep-link)
- Sweep every catch (Exception) in app/src/main for tone + visible-surface compliance
- Fix latent androidTest FakeRepository overrides and extend CI to compile androidTest

Closes #NN

## Test plan
- [ ] Unit tests pass (`./gradlew :app:testDebugUnitTest`)
- [ ] Compose Android tests render correctly (manual via emulator or device)
- [ ] Manual smoke: light + dark mode visual sanity
- [ ] Manual smoke: launcher icon renders correctly
- [ ] Manual smoke: first-launch empty state → tap "Add manually" → opens add sheet
- [ ] Manual smoke: tap Scan to Add → rationale dialog → continue → system prompt
- [ ] Manual smoke: deny + "don't ask again" → "Open settings" deep-links to app permission screen

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 4: Run multi-agent review (5-7 specialised agents in parallel)**

Invoke the toolkit reviewers in parallel via the Agent tool — each gets the branch + a focused remit (general code review, comment audit, error handling/silent failures, type design, test coverage, code simplifier, Kotlin coroutines specialist if relevant). For Compose-heavy changes also dispatch a Compose-specific reviewer if available.

- [ ] **Step 5: Post findings as inline review threads**

For each non-noise finding the reviewers surface, post a thread via:

```bash
gh api repos/<owner>/<repo>/pulls/<PR>/comments -X POST \
  -f body="<finding>" -f commit_id="<SHA>" -f path="<file>" -F line=<N> -f side=RIGHT
```

- [ ] **Step 6: Fix all findings; resolve threads via GraphQL after each fix lands**

Per fix commit:

```bash
gh api graphql -f query='mutation { resolveReviewThread(input: {threadId: "<THREAD_ID>"}) { thread { isResolved } } }'
```

- [ ] **Step 7: Verify CI green; post summary comment; hand off for merge**

```bash
gh pr checks <PR>          # all green
gh pr comment <PR> --body-file /tmp/pr-summary.md
```

---

## Self-review notes (writer's checklist)

- **Spec coverage:** five touch-points → six implementation tasks (theme, icon, empty state, permission gate, error audit, androidTest+CI cleanup) + one orchestration task. All five spec acceptance criteria map to a task.
- **Type consistency:** `CameraPermissionPhase` (Unknown/Granted/SoftDenied/HardDenied) used identically across the gate's three composables and the test. `EmptyState` signature `(modifier, onScanAdd, onAddManual)` matches between the new caller in HomeScreen and the new definition. `NoMatchesHint` is a single-arg composable, used the same way at both definition and call site.
- **No placeholders:** every step has either the full code, the full command, or a specific procedural list (the audit checklist in Task 5). Task 5 cannot have full code without the sweep results, but it has the canonical pattern + table of transformations so the implementer has zero ambiguity once they have the grep output.
