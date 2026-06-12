package kz.tcloud.dcinv.data.network.interceptor

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kz.tcloud.dcinv.data.auth.AuthManager
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retries a request once after a 401 with a force-refreshed token. Covers the
 * race where the access token expires (or is revoked server-side) between
 * [AuthInterceptor] attaching it and the backend validating it.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val authManager: AuthManager,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // One retry only: a second 401 with a fresh token isn't expiry
        // (revoked session, missing role) — let the error surface.
        if (response.priorResponse != null) return null
        val sent = response.request.header("Authorization") ?: return null
        val stale = sent.removePrefix("Bearer ")

        val fresh = runBlocking {
            withTimeoutOrNull(REFRESH_TIMEOUT_MS) { authManager.refreshedAccessToken(stale) }
        } ?: return null
        if (fresh == stale) return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $fresh")
            .build()
    }

    private companion object {
        const val REFRESH_TIMEOUT_MS = 15_000L
    }
}
