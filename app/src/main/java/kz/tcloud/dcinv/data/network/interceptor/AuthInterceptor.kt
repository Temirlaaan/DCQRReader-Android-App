package kz.tcloud.dcinv.data.network.interceptor

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kz.tcloud.dcinv.data.auth.AuthManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches a fresh `Authorization: Bearer <jwt>` to every request. [AuthManager]
 * transparently refreshes the access token when it has expired.
 *
 * TODO(auth): add an OkHttp Authenticator to force-refresh + retry once on 401.
 */
class AuthInterceptor @Inject constructor(
    private val authManager: AuthManager,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // Bounded so a stuck token refresh can't pin this OkHttp worker forever;
        // on timeout we proceed without a token (→ 401, surfaced normally).
        val token = runBlocking { withTimeoutOrNull(TOKEN_TIMEOUT_MS) { authManager.freshAccessToken() } }
        val request = chain.request().newBuilder().apply {
            if (token != null) header("Authorization", "Bearer $token")
        }.build()
        return chain.proceed(request)
    }

    private companion object {
        const val TOKEN_TIMEOUT_MS = 15_000L
    }
}
