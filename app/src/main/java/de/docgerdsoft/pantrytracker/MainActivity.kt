package de.docgerdsoft.pantrytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        enableEdgeToEdge()
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
