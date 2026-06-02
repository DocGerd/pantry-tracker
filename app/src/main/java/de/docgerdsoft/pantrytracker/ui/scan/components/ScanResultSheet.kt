package de.docgerdsoft.pantrytracker.ui.scan.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.docgerdsoft.pantrytracker.R
import de.docgerdsoft.pantrytracker.repository.ScanCandidate
import de.docgerdsoft.pantrytracker.ui.common.sanitizeQuantityInput
import de.docgerdsoft.pantrytracker.ui.scan.ScanMode
import de.docgerdsoft.pantrytracker.ui.theme.AddGreen
import de.docgerdsoft.pantrytracker.ui.theme.OnVerb
import de.docgerdsoft.pantrytracker.ui.theme.RemoveRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadingSheet(barcode: String, onCancel: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(
                stringResource(R.string.scan_looking_up, barcode),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPreviewSheet(
    candidate: ScanCandidate,
    pendingQuantity: Int,
    mode: ScanMode,
    onQuantityChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            candidate.imageUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = stringResource(R.string.cd_product_photo),
                    modifier = Modifier.size(120.dp).align(Alignment.CenterHorizontally),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(candidate.name, style = MaterialTheme.typography.titleLarge)
            candidate.brand?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onQuantityChange(pendingQuantity - 1) }) {
                    Icon(
                        Icons.Filled.Remove,
                        contentDescription = stringResource(R.string.cd_decrement_quantity),
                    )
                }
                Text(
                    text = pendingQuantity.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                IconButton(onClick = { onQuantityChange(pendingQuantity + 1) }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.cd_increment_quantity),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mode == ScanMode.Add) AddGreen else RemoveRed,
                        contentColor = OnVerb,
                    ),
                ) {
                    Text(stringResource(if (mode == ScanMode.Add) R.string.scan_confirm_add else R.string.scan_confirm_remove))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntrySheet(
    barcode: String,
    pendingQuantity: Int,
    onQuantityChange: (Int) -> Unit,
    onSubmit: (name: String, quantity: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf("") }
    var quantityText by rememberSaveable(pendingQuantity) {
        mutableStateOf(pendingQuantity.toString())
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.scan_manual_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.scan_manual_body, barcode),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = quantityText,
                onValueChange = { input ->
                    val sanitized = sanitizeQuantityInput(input)
                    quantityText = sanitized
                    sanitized.toIntOrNull()?.let(onQuantityChange)
                },
                label = { Text(stringResource(R.string.field_initial_quantity)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    onClick = {
                        val q = quantityText.toIntOrNull() ?: 1
                        onSubmit(name, q)
                    },
                    enabled = name.isNotBlank() && (quantityText.toIntOrNull() ?: 0) > 0,
                ) { Text(stringResource(R.string.scan_add_to_inventory)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotInInventorySheet(
    barcode: String,
    onSwitchToAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                stringResource(R.string.scan_not_in_inventory_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.scan_not_in_inventory_body, barcode),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSwitchToAdd,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AddGreen,
                    contentColor = OnVerb,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.scan_switch_to_add))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorSheet(
    message: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.scan_error_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        }
    }
}
