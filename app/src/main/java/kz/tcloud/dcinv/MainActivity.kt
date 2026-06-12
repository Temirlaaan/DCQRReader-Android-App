package kz.tcloud.dcinv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kz.tcloud.dcinv.data.auth.InactivityLockManager
import kz.tcloud.dcinv.ui.navigation.AppNavHost
import kz.tcloud.dcinv.ui.theme.DcInvTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var lockManager: InactivityLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent screenshots / screen recording of inventory data (ToR §5.5).
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        enableEdgeToEdge()

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
            DcInvTheme {
                AppNavHost()
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        lockManager.onUserInteraction()
    }
}
