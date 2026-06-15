package kz.tcloud.dcinv

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kz.tcloud.dcinv.data.auth.InactivityLockManager
import kz.tcloud.dcinv.data.prefs.ThemeMode
import kz.tcloud.dcinv.data.prefs.ThemePreferences
import kz.tcloud.dcinv.ui.navigation.AppNavHost
import kz.tcloud.dcinv.ui.theme.DcInvTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var lockManager: InactivityLockManager

    @Inject
    lateinit var themePreferences: ThemePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent screenshots / screen recording of inventory data (ToR §5.5).
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        // Inactivity auto-logout: tick while visible; the first check after
        // returning from the background catches long idle gaps immediately.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    lockManager.lockIfExpired()
                    delay(15_000)
                }
            }
        }

        setContent {
            val themeMode by themePreferences.mode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Drive edge-to-edge from the IN-APP theme, not the system one:
            // otherwise a light app under a dark system theme gets a dark
            // navigation-bar scrim that reads as black behind the nav island.
            // Transparent scrims = no overlay; detectDarkMode only sets icon color.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
                onDispose {}
            }

            DcInvTheme(darkTheme = darkTheme) {
                AppNavHost()
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        lockManager.onUserInteraction()
    }
}
