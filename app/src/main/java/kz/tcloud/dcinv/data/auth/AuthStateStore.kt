package kz.tcloud.dcinv.data.auth

import android.content.SharedPreferences
import androidx.core.content.edit
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationServiceConfiguration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the AppAuth [AuthState] (access/refresh tokens, expiry) into
 * EncryptedSharedPreferences. AuthState serializes to JSON, so we just store
 * that string.
 */
@Singleton
class AuthStateStore @Inject constructor(
    private val prefs: SharedPreferences,
    private val defaultConfig: AuthorizationServiceConfiguration,
) {
    fun read(): AuthState {
        val json = prefs.getString(KEY_STATE, null) ?: return AuthState(defaultConfig)
        return runCatching { AuthState.jsonDeserialize(json) }
            .getOrElse { AuthState(defaultConfig) }
    }

    fun write(state: AuthState) {
        prefs.edit { putString(KEY_STATE, state.jsonSerializeString()) }
    }

    fun clear() {
        prefs.edit { remove(KEY_STATE) }
    }

    private companion object {
        const val KEY_STATE = "auth_state"
    }
}
