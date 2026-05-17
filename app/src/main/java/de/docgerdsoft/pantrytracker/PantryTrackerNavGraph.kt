package de.docgerdsoft.pantrytracker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.docgerdsoft.pantrytracker.di.AppContainer
import de.docgerdsoft.pantrytracker.ui.home.HomeScreen
import de.docgerdsoft.pantrytracker.ui.home.HomeViewModel
import de.docgerdsoft.pantrytracker.ui.scan.ScanScreen
import de.docgerdsoft.pantrytracker.ui.scan.ScanViewModel

object Routes {
    const val HOME = "home"
    const val SCAN_ADD = "scan/add"
}

@Composable
fun PantryTrackerNavGraph(container: AppContainer) {
    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val factory = remember(container) {
                viewModelFactory {
                    initializer { HomeViewModel(container.productRepository) }
                }
            }
            val vm: HomeViewModel = viewModel(factory = factory)
            HomeScreen(
                viewModel = vm,
                onScanAddClick = { navController.navigate(Routes.SCAN_ADD) },
                onScanRemoveClick = { /* TODO: wire to scan-remove flow */ },
            )
        }
        composable(Routes.SCAN_ADD) {
            val factory = remember(container) {
                viewModelFactory {
                    initializer { ScanViewModel(container.productRepository) }
                }
            }
            val vm: ScanViewModel = viewModel(factory = factory)
            ScanScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
