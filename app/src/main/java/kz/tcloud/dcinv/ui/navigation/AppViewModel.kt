package kz.tcloud.dcinv.ui.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kz.tcloud.dcinv.data.auth.AuthManager
import kz.tcloud.dcinv.data.auth.InactivityLockManager
import kz.tcloud.dcinv.data.network.ConnectivityState
import javax.inject.Inject

/** Bridges app-wide auth + connectivity events into the nav graph. */
@HiltViewModel
class AppViewModel @Inject constructor(
    lockManager: InactivityLockManager,
    authManager: AuthManager,
    connectivity: ConnectivityState,
) : ViewModel() {
    val lockEvents: SharedFlow<Unit> = lockManager.lockEvents
    val sessionExpiredEvents: SharedFlow<Unit> = authManager.sessionExpired
    val reachable: StateFlow<Boolean> = connectivity.reachable
}
