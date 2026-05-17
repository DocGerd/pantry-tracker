package de.docgerdsoft.pantrytracker.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Context
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

private fun initialPhase(context: Context): CameraPermissionPhase =
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED
    ) {
        CameraPermissionPhase.Granted
    } else {
        CameraPermissionPhase.Unknown
    }

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
