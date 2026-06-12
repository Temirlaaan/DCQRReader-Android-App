package kz.tcloud.dcinv.data.auth

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inactivity auto-logout (ToR §4.1): after [TIMEOUT_MS] without any user
 * interaction — including time spent in the background — tokens are cleared
 * and [lockEvents] fires so navigation can return to the login screen.
 *
 * MainActivity feeds [onUserInteraction] from every touch and runs a
 * foreground ticker that calls [lockIfExpired]; the first tick on returning
 * from the background covers the "left the app open overnight" case.
 */
@Singleton
class InactivityLockManager @Inject constructor(
    private val authManager: AuthManager,
) {
    private val _lockEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val lockEvents: SharedFlow<Unit> = _lockEvents.asSharedFlow()

    // elapsedRealtime is monotonic and keeps counting in deep sleep,
    // unlike uptimeMillis; wall clock would break on manual time changes.
    @Volatile
    private var lastActiveAt = SystemClock.elapsedRealtime()

    fun onUserInteraction() {
        lastActiveAt = SystemClock.elapsedRealtime()
    }

    /** Signs out and emits a lock event when the idle timeout has elapsed. */
    fun lockIfExpired() {
        if (!authManager.isAuthorized) return
        if (SystemClock.elapsedRealtime() - lastActiveAt < TIMEOUT_MS) return
        authManager.signOut()
        _lockEvents.tryEmit(Unit)
    }

    private companion object {
        /** ToR §4.1: 10 minutes of inactivity → full logout. */
        const val TIMEOUT_MS = 10L * 60 * 1000
    }
}
