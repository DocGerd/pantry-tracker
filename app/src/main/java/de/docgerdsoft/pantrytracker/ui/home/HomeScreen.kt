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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.ui.common.SnackbarEvent
import de.docgerdsoft.pantrytracker.ui.theme.AddGreen
import de.docgerdsoft.pantrytracker.ui.theme.RemoveRed

private const val OUT_OF_STOCK_ROW_ALPHA = 0.45f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onScanAddClick: () -> Unit,
    onScanRemoveClick: () -> Unit,
    onProductClick: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    SnackbarEventCollector(viewModel, snackbarHostState)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pantry Tracker") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                onAddClick = onScanAddClick,
                onRemoveClick = onScanRemoveClick,
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
            Spacer(Modifier.width(8.dp))
            Text("Scan to Add")
        }
        Button(
            onClick = onRemoveClick,
            modifier = Modifier.weight(1f).height(80.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RemoveRed),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Scan to Remove")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductRow(
    product: Product,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics(mergeDescendants = true) {}
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = product.name,
            modifier = Modifier
                .weight(1f)
                .alpha(if (product.quantity == 0) OUT_OF_STOCK_ROW_ALPHA else 1f),
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "No matches for \"$query\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    product: Product,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Copy intentionally lean: the UNDO snackbar that fires on confirm
    // carries the reversibility message, so the dialog no longer needs to
    // apologise for an irreversible delete (see arc42 §11 TD-7). Verb is
    // "Delete" everywhere — title, body, button, and the post-action
    // snackbar — to avoid the "Remove" / "Delete" mixed signal flagged in
    // the multi-agent review.
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${product.name}?") },
        text = { Text("Delete ${product.name} from your pantry?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Extracted from [HomeScreen] to keep that composable under detekt's
 * `LongMethod` threshold. Collects [HomeViewModel.snackbarEvents] for the
 * lifetime of the host composable and shows a Material 3 snackbar per
 * event — single-collector semantics via `LaunchedEffect(viewModel)` plus
 * the channel-backed flow (see [SnackbarEvent] KDoc).
 *
 * `showSnackbar` suspends until dismiss/action, which serialises back-to-back
 * delete events naturally — a second delete during the snackbar window
 * dismisses the previous snackbar (see arc42 §11 TD-7 "Edge case — second
 * delete during snackbar window"); the first UNDO closure is then unreachable
 * and the first deletion stays final, matching Gmail / Drive UX.
 *
 * The `when` is exhaustive over three variants: [SnackbarEvent.Deleted] is
 * the success path that offers UNDO; [SnackbarEvent.DeleteFailed] and
 * [SnackbarEvent.RestoreFailed] are the explicit failure paths so the UI
 * never silently lies that a deletion or restore succeeded.
 */
@Composable
private fun SnackbarEventCollector(
    viewModel: HomeViewModel,
    snackbarHostState: SnackbarHostState,
) {
    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { event ->
            when (event) {
                is SnackbarEvent.Deleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Deleted ${event.product.name}",
                        actionLabel = "UNDO",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete(event.product)
                    }
                }
                is SnackbarEvent.DeleteFailed -> {
                    snackbarHostState.showSnackbar(
                        message = "Could not delete ${event.name}",
                        duration = SnackbarDuration.Short,
                    )
                }
                is SnackbarEvent.RestoreFailed -> {
                    snackbarHostState.showSnackbar(
                        message = "Could not undo delete of ${event.name}",
                        duration = SnackbarDuration.Short,
                    )
                }
            }
        }
    }
}
