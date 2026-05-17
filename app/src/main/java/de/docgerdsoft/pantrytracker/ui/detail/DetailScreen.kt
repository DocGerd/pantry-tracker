package de.docgerdsoft.pantrytracker.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.docgerdsoft.pantrytracker.ui.common.RelativeTime
import kotlinx.datetime.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Auto-pop when the product is deleted (flag consumed once, then cleared).
    LaunchedEffect(state.shouldNavigateBack) {
        if (state.shouldNavigateBack) {
            viewModel.onNavigatedBack()
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::requestDelete,
                        enabled = state.product != null,
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete product")
                    }
                },
            )
        },
    ) { padding ->
        val product = state.product

        if (product == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Local name state — resets if the product name changes externally.
            var localName by remember(product.name) { mutableStateOf(product.name) }

            fun commitName() {
                if (localName.isNotBlank() && localName != product.name) {
                    viewModel.rename(localName)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                // Product image (only if we have a URL).
                product.imageUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = "Product photo",
                        modifier = Modifier
                            .size(160.dp)
                            .align(Alignment.CenterHorizontally),
                        contentScale = ContentScale.Fit,
                    )
                }

                // Inline-editable name field.
                OutlinedTextField(
                    value = localName,
                    onValueChange = { localName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) commitName()
                        },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitName() }),
                )

                // Read-only brand.
                product.brand?.let { brand ->
                    Text(
                        text = "Brand: $brand",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // Read-only barcode.
                product.barcode?.let { barcode ->
                    Text(
                        text = "Barcode: $barcode",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // ±1 quantity stepper.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { viewModel.stepperDelta(-1) },
                        enabled = product.quantity > 0,
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity")
                    }
                    Text(
                        text = product.quantity.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    IconButton(onClick = { viewModel.stepperDelta(1) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase quantity")
                    }
                }

                // Relative last-updated time.
                Text(
                    text = "Last updated ${RelativeTime.format(product.updatedAt, Clock.System.now())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Delete confirmation dialog.
        if (state.showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = viewModel::cancelDelete,
                title = { Text("Delete product?") },
                text = { Text("This removes it from your inventory. Cannot be undone in v1.") },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::confirmDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") }
                },
            )
        }
    }
}
