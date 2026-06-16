package kz.tcloud.dcinv.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide "is the backend reachable" flag, driven by real request outcomes
 * rather than the system network state — the common failure here is "Wi-Fi up
 * but VPN down", which ConnectivityManager reports as online. [ApiCaller] marks
 * it offline on a NETWORK_ERROR and online again on the next success, so the
 * UI can show one persistent banner instead of a red snackbar per action.
 */
@Singleton
class ConnectivityState @Inject constructor() {
    private val _reachable = MutableStateFlow(true)
    val reachable: StateFlow<Boolean> = _reachable.asStateFlow()

    fun markReachable() {
        if (!_reachable.value) _reachable.value = true
    }

    fun markUnreachable() {
        if (_reachable.value) _reachable.value = false
    }
}
