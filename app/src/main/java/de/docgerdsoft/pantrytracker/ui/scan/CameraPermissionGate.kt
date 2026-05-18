package de.docgerdsoft.pantrytracker.ui.scan

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.logging.Level
import java.util.logging.Logger

// java.util.logging works in both Android (forwarded to logcat) and plain JVM
// unit tests. Avoids android.util.Log throwing "Method X not mocked" in
// non-Robolectric tests.
private val logger: Logger = Logger.getLogger("CameraPermissionGate")

/** State machine for the camera-permission rationale gate.
 *  See M6 spec — Camera-permission gate. */
sealed interface CameraPermissionPhase {
    /** Initial state when permission is not yet granted at composition time —
     *  show the rationale dialog so the user can opt in. The launcher result
     *  then transitions us to Granted / SoftDenied / HardDenied. */
    data object Unknown : CameraPermissionPhase

    /** Permission granted — render the wrapped scan content. */
    data object Granted : CameraPermissionPhase

    /** Denied, but the OS will still surface the system prompt on retry
     *  (`shouldShowRequestPermissionRationale == true`). Recoverable by
     *  re-launching the request. */
    data object SoftDenied : CameraPermissionPhase

    /** Denied + "don't ask again" — only recoverable via system settings. */
    data object HardDenied : CameraPermissionPhase
}

/** Stateful wrapper: checks permission, drives the launcher, computes phase.
 *  Re-checks permission on every `ON_RESUME` so the "Open settings → grant →
 *  return" recovery path flips the gate to Granted without requiring a restart. */
@Composable
fun CameraPermissionGate(
    onNavigateBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    var phase: CameraPermissionPhase by remember {
        mutableStateOf(initialPhase(context, activity))
    }

    // Re-check permission on ON_RESUME so the Settings round-trip recovers.
    // Promotes to Granted if the user just granted permission, and demotes
    // a previously-Granted phase back to Unknown if the user revoked it while
    // the app was backgrounded (which would otherwise crash CameraX on resume).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                phase = when {
                    nowGranted -> CameraPermissionPhase.Granted
                    phase == CameraPermissionPhase.Granted -> CameraPermissionPhase.Unknown
                    else -> phase
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
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

// Compute the gate's starting phase on (re-)entry. Three cases:
//   1. Permission granted  → Granted (content renders, no dialog).
//   2. Permission not granted but the OS reports we should explain why
//      (i.e. the user previously denied without "Don't ask again", so
//      shouldShowRequestPermissionRationale==true) → SoftDenied, so the
//      "Camera access needed" recovery screen with a Try again button is
//      shown directly — re-entering Scan to Add must NOT re-show the
//      one-time rationale dialog after the user has already been asked.
//   3. Otherwise (first-time entry, or hard-denied with "Don't ask again")
//      → Unknown, which shows the rationale dialog. The launcher callback
//      still routes a subsequent system-denied result into HardDenied if
//      shouldShowRationale is false at that point.
private fun initialPhase(context: Context, activity: Activity?): CameraPermissionPhase = when {
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED -> CameraPermissionPhase.Granted
    activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true ->
        CameraPermissionPhase.SoftDenied
    else -> CameraPermissionPhase.Unknown
}

// Walks ContextWrapper.baseContext so we find the Activity even when the
// LocalContext is wrapped (ContextThemeWrapper, etc.). A plain `context as?
// Activity` returns null in that case, which would collapse SoftDenied into
// HardDenied silently in the launcher callback.
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Some stripped AOSP, MDM-locked, or kiosk devices have the Settings
        // activity disabled or filtered. Surface a Toast so the user isn't
        // stranded on a dead button, and log the failure for diagnosis.
        @Suppress("SwallowedException")
        logger.log(Level.WARNING, "Couldn't open settings: no Settings activity on device", e)
        Toast.makeText(
            context,
            "Couldn't open settings on this device",
            Toast.LENGTH_LONG,
        ).show()
    }
}
