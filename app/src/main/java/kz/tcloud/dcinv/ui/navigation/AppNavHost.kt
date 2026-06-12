package kz.tcloud.dcinv.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kz.tcloud.dcinv.ui.bind.BindDeviceScreen
import kz.tcloud.dcinv.ui.create.CreateDeviceScreen
import kz.tcloud.dcinv.ui.edit.EditDeviceScreen
import kz.tcloud.dcinv.ui.home.HomeScreen
import kz.tcloud.dcinv.ui.login.LoginScreen
import kz.tcloud.dcinv.ui.qr.QrResultScreen
import kz.tcloud.dcinv.ui.scan.ScannerScreen

/**
 * Top-level navigation graph. Login -> Home -> Scanner -> QR result.
 * PIN lock, shift gating and the device edit/bind flows plug in here as each
 * vertical is implemented.
 */
@Composable
fun AppNavHost(appViewModel: AppViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Inactivity auto-logout: tokens are already cleared, return to login.
    LaunchedEffect(Unit) {
        appViewModel.lockEvents.collect {
            Toast.makeText(context, "Выход из-за неактивности", Toast.LENGTH_LONG).show()
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onScan = { navController.navigate(Routes.SCANNER) },
                onSignedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOpenScan = { qrId -> navController.navigate(Routes.qrResult(qrId)) },
            )
        }
        composable(Routes.SCANNER) {
            ScannerScreen(
                onQrScanned = { qrId ->
                    navController.navigate(Routes.qrResult(qrId)) {
                        // Don't keep the camera in the back stack behind the result.
                        popUpTo(Routes.SCANNER) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.QR_RESULT,
            arguments = listOf(navArgument(Routes.QR_ARG) { type = NavType.StringType }),
        ) { entry ->
            val reload by entry.savedStateHandle
                .getStateFlow(Routes.RESULT_RELOAD, false)
                .collectAsStateWithLifecycle()
            QrResultScreen(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                },
                onScanAgain = {
                    navController.navigate(Routes.SCANNER) {
                        // Replace the result screen so back from scanner returns Home.
                        popUpTo(Routes.QR_RESULT) { inclusive = true }
                    }
                },
                onBind = { qrId -> navController.navigate(Routes.bind(qrId)) },
                onCreate = { qrId -> navController.navigate(Routes.createDevice(qrId)) },
                onEdit = { deviceId -> navController.navigate(Routes.editDevice(deviceId)) },
                reloadSignal = reload,
                onReloadHandled = { entry.savedStateHandle[Routes.RESULT_RELOAD] = false },
            )
        }
        composable(
            route = Routes.BIND,
            arguments = listOf(navArgument(Routes.QR_ARG) { type = NavType.StringType }),
        ) { entry ->
            val qrId = entry.arguments?.getString(Routes.QR_ARG).orEmpty()
            BindDeviceScreen(
                onBack = { navController.popBackStack() },
                onBound = {
                    // Replace bind + the stale (free) result with a fresh lookup
                    // that now shows the bound device.
                    navController.navigate(Routes.qrResult(qrId)) {
                        popUpTo(Routes.QR_RESULT) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.CREATE_DEVICE,
            arguments = listOf(navArgument(Routes.QR_ARG) { type = NavType.StringType }),
        ) { entry ->
            val qrId = entry.arguments?.getString(Routes.QR_ARG).orEmpty()
            CreateDeviceScreen(
                onBack = { navController.popBackStack() },
                onDone = {
                    // Replace create + the stale (free) result with a fresh lookup
                    // that now shows the new bound device.
                    navController.navigate(Routes.qrResult(qrId)) {
                        popUpTo(Routes.QR_RESULT) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.EDIT_DEVICE,
            arguments = listOf(navArgument(Routes.DEVICE_ARG) { type = NavType.IntType }),
        ) {
            EditDeviceScreen(
                onBack = { navController.popBackStack() },
                onSaved = {
                    // Signal the QR result screen to refresh the (now stale) device.
                    navController.previousBackStackEntry
                        ?.savedStateHandle?.set(Routes.RESULT_RELOAD, true)
                    navController.popBackStack()
                },
            )
        }
    }
}
