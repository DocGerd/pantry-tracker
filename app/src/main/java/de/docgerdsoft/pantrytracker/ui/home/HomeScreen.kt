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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.docgerdsoft.pantrytracker.data.local.Product
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
            if (state.products.isEmpty()) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
