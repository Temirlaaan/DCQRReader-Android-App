package kz.tcloud.dcinv.ui.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kz.tcloud.dcinv.data.auth.InactivityLockManager
import javax.inject.Inject

/** Bridges app-wide auth events (inactivity logout) into the nav graph. */
@HiltViewModel
class AppViewModel @Inject constructor(
    lockManager: InactivityLockManager,
) : ViewModel() {
    val lockEvents: SharedFlow<Unit> = lockManager.lockEvents
}
