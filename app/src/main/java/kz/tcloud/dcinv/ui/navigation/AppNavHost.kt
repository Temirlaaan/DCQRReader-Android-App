package kz.tcloud.dcinv.ui.navigation

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kz.tcloud.dcinv.ui.bind.BindDeviceScreen
import kz.tcloud.dcinv.ui.components.BottomNavIsland
import kz.tcloud.dcinv.ui.create.CreateDeviceScreen
import kz.tcloud.dcinv.ui.device.DeviceDetailScreen
import kz.tcloud.dcinv.ui.edit.EditDeviceScreen
import kz.tcloud.dcinv.ui.home.HomeScreen
import kz.tcloud.dcinv.ui.login.LoginScreen
import kz.tcloud.dcinv.ui.profile.ProfileScreen
import kz.tcloud.dcinv.ui.qr.QrResultScreen
import kz.tcloud.dcinv.ui.racks.RackDetailScreen
import kz.tcloud.dcinv.ui.racks.RacksScreen
import kz.tcloud.dcinv.ui.scan.ScannerScreen
import kz.tcloud.dcinv.ui.settings.SettingsScreen

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

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN,
            // No default crossfade between screens — it reads as a flash,
            // especially with the ripple-free, motion-only feedback language.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
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
                onOpenScan = { qrId -> navController.navigate(Routes.qrResult(qrId)) },
            )
        }
        composable(Routes.RACKS) {
            RacksScreen(
                onOpenRack = { rackId -> navController.navigate(Routes.rackDetail(rackId)) },
            )
        }
        composable(
            route = Routes.RACK_DETAIL,
            arguments = listOf(navArgument(Routes.RACK_ARG) { type = NavType.IntType }),
        ) {
            RackDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenDevice = { deviceId -> navController.navigate(Routes.deviceDetail(deviceId)) },
            )
        }
        composable(
            route = Routes.DEVICE_DETAIL,
            arguments = listOf(navArgument(Routes.DEVICE_ARG) { type = NavType.IntType }),
        ) { entry ->
            val reload by entry.savedStateHandle
                .getStateFlow(Routes.RESULT_RELOAD, false)
                .collectAsStateWithLifecycle()
            DeviceDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { deviceId -> navController.navigate(Routes.editDevice(deviceId)) },
                reloadSignal = reload,
                onReloadHandled = { entry.savedStateHandle[Routes.RESULT_RELOAD] = false },
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                onSignedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen()
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

        AnimatedVisibility(
            visible = currentRoute in Routes.TOP_LEVEL,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomNavIsland(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        // Standard tab behavior: single copy of each tab, state kept.
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onScan = { navController.navigate(Routes.SCANNER) },
            )
        }
    }
}
