package kz.tcloud.dcinv.data.network.idempotency

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kz.tcloud.dcinv.data.network.ApiException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent idempotency-key store (mobile-api-guide §2.5).
 *
 * A *logical action* (e.g. "bind QR X to device Y") maps to one stable UUID.
 * [keyFor] returns the same UUID for the same action id — including after the
 * app is killed and relaunched — so a retry of the same action carries the same
 * `Idempotency-Key`. [clear] is called once a terminal (2xx/4xx) response is
 * received, freeing the slot so a genuinely new action gets a fresh key.
 *
 * Keys are not secret, so plain SharedPreferences (not the encrypted store) is
 * sufficient and avoids coupling to the token storage.
 */
@Singleton
class IdempotencyStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("dcinv_idempotency", Context.MODE_PRIVATE)

    /** Stable key for [actionId], created and persisted on first request. */
    @Synchronized
    fun keyFor(actionId: String): String {
        prefs.getString(actionId, null)?.let { return it }
        val key = UUID.randomUUID().toString()
        prefs.edit { putString(actionId, key) }
        return key
    }

    @Synchronized
    fun clear(actionId: String) {
        prefs.edit { remove(actionId) }
    }

    /**
     * Runs [block] with the stable key for [actionId]. Frees the slot on success
     * and on terminal client errors (4xx — won't change on retry). Keeps the key
     * on network failures (-1) and 5xx so a same-key retry can reuse it.
     */
    suspend fun <T> withKey(actionId: String, block: suspend (String) -> T): T {
        val key = keyFor(actionId)
        try {
            val result = block(key)
            clear(actionId)
            return result
        } catch (e: ApiException) {
            if (e.httpCode in 400..499) clear(actionId)
            throw e
        }
    }
}
