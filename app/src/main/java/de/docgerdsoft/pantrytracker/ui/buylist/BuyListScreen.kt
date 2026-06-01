package de.docgerdsoft.pantrytracker.ui.buylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.docgerdsoft.pantrytracker.R
import de.docgerdsoft.pantrytracker.data.local.Product

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyListScreen(
    viewModel: BuyListViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface restock / load failures as a Snackbar — mirrors DetailScreen.
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
                title = { Text(stringResource(R.string.buylist_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        BuyListContent(
            items = state.items,
            onBought = viewModel::onBought,
            onBack = onNavigateBack,
            padding = padding,
        )
    }
}

@Composable
internal fun BuyListContent(
    items: List<Product>,
    onBought: (Product) -> Unit,
    onBack: () -> Unit,
    padding: PaddingValues = PaddingValues(),
) {
    if (items.isEmpty()) {
        EmptyState(padding = padding, onBack = onBack)
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items, key = { it.id }) { product ->
                BuyListRow(product = product, onBought = { onBought(product) })
            }
        }
    }
}

@Composable
private fun BuyListRow(product: Product, onBought: () -> Unit) {
    val boughtCd = stringResource(R.string.cd_bought, product.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = product.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.quantity_count, product.quantity),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                // Out-of-stock rows get an urgent error-colour count.
                color = if (product.quantity == 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Button(
            onClick = onBought,
            modifier = Modifier.semantics { contentDescription = boughtCd },
        ) {
            Text(stringResource(R.string.action_bought))
        }
    }
}

@Composable
private fun EmptyState(padding: PaddingValues, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.buylist_empty_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.buylist_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onBack) { Text(stringResource(R.string.buylist_back_to_pantry)) }
        }
    }
}
