package kz.tcloud.dcinv.data.auth

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kz.tcloud.dcinv.BuildConfig
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import kz.tcloud.dcinv.ui.theme.BrandPrimaryArgb
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Owns the OIDC Authorization Code + PKCE flow against Keycloak.
 *
 * - [createAuthRequestIntent] starts the browser/Custom Tab login.
 * - [handleAuthResponse] exchanges the returned code for tokens.
 * - [freshAccessToken] returns a valid access token, refreshing if needed.
 *
 * Token state is persisted via [AuthStateStore] (EncryptedSharedPreferences).
 */
@Singleton
class AuthManager @Inject constructor(
    private val authService: AuthorizationService,
    private val serviceConfig: AuthorizationServiceConfiguration,
    private val stateStore: AuthStateStore,
) {
    private val _state = MutableStateFlow(stateStore.read())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /**
     * Fired when Keycloak rejects the refresh token (SSO session expired or
     * revoked) — the session is dead, tokens are cleared, UI must re-login.
     */
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    private val refreshMutex = Mutex()

    val isAuthorized: Boolean get() = _state.value.isAuthorized

    fun createAuthRequestIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            BuildConfig.OIDC_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(BuildConfig.OIDC_REDIRECT_URI),
        )
            .setScope("openid profile email offline_access")
            .build()

        // Theme the Custom Tab toolbar to match the app's brand color.
        val colorParams = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(BrandPrimaryArgb)
            .build()
        val customTabs = authService.createCustomTabsIntentBuilder(request.toUri())
            .setDefaultColorSchemeParams(colorParams)
            .build()

        return authService.getAuthorizationRequestIntent(request, customTabs)
    }

    /** Parses the redirect result and exchanges the auth code for tokens. */
    suspend fun handleAuthResponse(data: Intent): Result<Unit> {
        val response = AuthorizationResponse.fromIntent(data)
        val authException = AuthorizationException.fromIntent(data)
        val current = _state.value
        current.update(response, authException)

        if (response == null) {
            return Result.failure(authException ?: IllegalStateException("Empty authorization response"))
        }

        return suspendCancellableCoroutine { cont ->
            authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenException ->
                current.update(tokenResponse, tokenException)
                persist(current)
                if (tokenResponse != null) {
                    cont.resume(Result.success(Unit))
                } else {
                    cont.resume(
                        Result.failure(tokenException ?: IllegalStateException("Token exchange failed")),
                    )
                }
            }
        }
    }

    /**
     * Returns a fresh access token, transparently refreshing it if expired.
     * Serialized with a mutex so concurrent requests don't trigger overlapping
     * refreshes of the same refresh token.
     */
    suspend fun freshAccessToken(): String? = refreshMutex.withLock {
        suspendCancellableCoroutine { cont ->
            val state = _state.value
            state.performActionWithFreshTokens(authService) { accessToken, _, ex ->
                persist(state)
                handleRefreshFailure(ex)
                cont.resume(accessToken)
            }
        }
    }

    /**
     * Force-refresh after a 401: the server rejected a token that looked valid
     * locally (clock skew, revoked session). If a concurrent request already
     * refreshed — the stored token no longer matches [staleAccessToken] — the
     * stored one is returned without burning another refresh.
     */
    suspend fun refreshedAccessToken(staleAccessToken: String?): String? = refreshMutex.withLock {
        val state = _state.value
        val current = state.accessToken
        if (current != null && current != staleAccessToken) return@withLock current

        state.needsTokenRefresh = true
        suspendCancellableCoroutine<String?> { cont ->
            state.performActionWithFreshTokens(authService) { accessToken, _, ex ->
                persist(state)
                handleRefreshFailure(ex)
                cont.resume(accessToken)
            }
        }
    }

    /**
     * Only a server-side rejection of the refresh token kills the session;
     * transient failures (network down mid-refresh) must NOT log the user out.
     */
    private fun handleRefreshFailure(ex: AuthorizationException?) {
        if (ex != null && ex.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR) {
            signOut()
            _sessionExpired.tryEmit(Unit)
        }
    }

    fun signOut() {
        persist(AuthState(serviceConfig))
        stateStore.clear()
    }

    private fun persist(state: AuthState) {
        _state.value = state
        stateStore.write(state)
    }
}
