package de.docgerdsoft.pantrytracker.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.ui.common.RelativeTime
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Auto-pop when the product is deleted or the nav arg is stale.
    // Order matters: pop FIRST so an intervening recomposition can't observe
    // the still-true flag and re-fire popBackStack on the next launch.
    LaunchedEffect(state.shouldNavigateBack) {
        if (state.shouldNavigateBack) {
            onNavigateBack()
            viewModel.onNavigatedBack()
        }
    }

    // Surface repository-operation failures (rename / stepperDelta / delete /
    // observe) as a Snackbar — matches ScanViewModel's Phase.Error UX per spec §7.
    LaunchedEffect(state.error) {
        val message = state.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        onClick = {
                            // Force the inline-edit field to lose focus so its
                            // commit-on-focus-loss callback fires before we
                            // destroy the row. Without this, a name typed but
                            // not committed would be lost on Delete.
                            focusManager.clearFocus()
                            viewModel.requestDelete()
                        },
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
            ProductBody(
                product = product,
                padding = padding,
                onRename = viewModel::rename,
                onStepperDelta = viewModel::stepperDelta,
                onSaveRestock = viewModel::saveRestockSettings,
            )
            if (state.showDeleteConfirm) {
                DeleteConfirmDialog(
                    productName = product.name,
                    onConfirm = viewModel::confirmDelete,
                    onCancel = viewModel::cancelDelete,
                )
            }
        }
    }
}

@Composable
private fun ProductBody(
    product: Product,
    padding: PaddingValues,
    onRename: (String) -> Unit,
    onStepperDelta: (Int) -> Unit,
    onSaveRestock: (Int?, Int) -> Unit,
) {
    // remember(product.name) resets the local edit state when the row is
    // renamed externally (e.g. another scan, another window). Trade-off:
    // an in-flight uncommitted edit is discarded in that case — acceptable
    // because external renames are rare and the data-correctness win is
    // worth more than preserving a half-typed string.
    var localName by remember(product.name) { mutableStateOf(product.name) }

    fun commitName() {
        if (localName.isNotBlank() && localName != product.name) {
            onRename(localName)
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
        product.brand?.let { brand ->
            Text(text = "Brand: $brand", style = MaterialTheme.typography.bodyMedium)
        }
        product.barcode?.let { barcode ->
            Text(text = "Barcode: $barcode", style = MaterialTheme.typography.bodyMedium)
        }
        StepperRow(quantity = product.quantity, onDelta = onStepperDelta)
        RestockSettings(product = product, onSave = onSaveRestock)
        Text(
            text = "Last updated ${RelativeTime.format(product.updatedAt, Clock.System.now())}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * #191: opt-in low-stock limit + default buy amount editing. Commits on
 * focus-loss (mirroring the name field). An empty low-limit field clears
 * tracking (`null`); buy-amount parses via `toIntOrNull() ?: 1`. The repository
 * clamps lowLimit >= 0 and defaultBuyAmount >= 1 — this UI just collects intent.
 */
@Composable
private fun RestockSettings(product: Product, onSave: (Int?, Int) -> Unit) {
    // Seed from the row; reset when the persisted values change externally.
    var lowLimitText by remember(product.lowLimit) {
        mutableStateOf(product.lowLimit?.toString() ?: "")
    }
    var buyAmountText by remember(product.defaultBuyAmount) {
        mutableStateOf(product.defaultBuyAmount.toString())
    }

    fun commit() {
        val newLimit = lowLimitText.trim().toIntOrNull() // blank or unparsable ⇒ null ⇒ clears tracking
        val newBuy = buyAmountText.trim().toIntOrNull() ?: 1
        if (newLimit != product.lowLimit || newBuy != product.defaultBuyAmount) {
            onSave(newLimit, newBuy)
        }
    }

    Text(text = "Restock", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = lowLimitText,
        onValueChange = { lowLimitText = it },
        label = { Text("Low limit") },
        supportingText = { Text("Leave blank to stop tracking") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) commit() },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
    )
    OutlinedTextField(
        value = buyAmountText,
        onValueChange = { buyAmountText = it },
        label = { Text("Buy amount") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) commit() },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { commit() }),
    )
}

@Composable
private fun StepperRow(quantity: Int, onDelta: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onDelta(-1) }, enabled = quantity > 0) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity")
        }
        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        IconButton(onClick = { onDelta(1) }) {
            Icon(Icons.Filled.Add, contentDescription = "Increase quantity")
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    productName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Delete this product?") },
        text = {
            Text("\"$productName\" will be removed from your inventory. This can't be undone.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}
