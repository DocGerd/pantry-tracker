package de.docgerdsoft.pantrytracker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.docgerdsoft.pantrytracker.di.AppContainer
import de.docgerdsoft.pantrytracker.ui.detail.DetailScreen
import de.docgerdsoft.pantrytracker.ui.detail.DetailViewModel
import de.docgerdsoft.pantrytracker.ui.home.HomeScreen
import de.docgerdsoft.pantrytracker.ui.home.HomeViewModel
import de.docgerdsoft.pantrytracker.ui.scan.CameraPermissionGate
import de.docgerdsoft.pantrytracker.ui.scan.ScanMode
import de.docgerdsoft.pantrytracker.ui.scan.ScanScreen
import de.docgerdsoft.pantrytracker.ui.scan.ScanViewModel

object Routes {
    const val HOME = "home"
    const val SCAN_ADD = "scan/add"
    const val SCAN_REMOVE = "scan/remove"
    const val DETAIL_ARG_PRODUCT_ID = "productId"
    const val DETAIL = "detail/{$DETAIL_ARG_PRODUCT_ID}"

    fun detail(id: Long) = "detail/$id"
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
                onScanRemoveClick = { navController.navigate(Routes.SCAN_REMOVE) },
                onProductClick = { id -> navController.navigate(Routes.detail(id)) },
            )
        }
        composable(Routes.SCAN_ADD) {
            val factory = remember(container) {
                viewModelFactory {
                    initializer { ScanViewModel(container.productRepository, initialMode = ScanMode.Add) }
                }
            }
            val vm: ScanViewModel = viewModel(factory = factory)
            CameraPermissionGate(onNavigateBack = { navController.popBackStack() }) {
                ScanScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() },
                    cameraSource = container.cameraSource,
                )
            }
        }
        composable(Routes.SCAN_REMOVE) {
            val factory = remember(container) {
                viewModelFactory {
                    initializer { ScanViewModel(container.productRepository, initialMode = ScanMode.Remove) }
                }
            }
            val vm: ScanViewModel = viewModel(factory = factory)
            CameraPermissionGate(onNavigateBack = { navController.popBackStack() }) {
                ScanScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() },
                    cameraSource = container.cameraSource,
                )
            }
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument(Routes.DETAIL_ARG_PRODUCT_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getLong(Routes.DETAIL_ARG_PRODUCT_ID) ?: return@composable
            val factory = remember(container, productId) {
                viewModelFactory {
                    initializer { DetailViewModel(container.productRepository, productId) }
                }
            }
            val vm: DetailViewModel = viewModel(factory = factory)
            DetailScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
