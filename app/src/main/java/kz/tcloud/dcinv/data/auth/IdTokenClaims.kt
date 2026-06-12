package kz.tcloud.dcinv.data.auth

import android.util.Base64
import org.json.JSONObject

/** Display-only user identity pulled from the Keycloak ID token. */
data class IdTokenClaims(
    val name: String?,
    val username: String?,
    val email: String?,
)

/**
 * Decodes the JWT payload without signature verification — fine here because
 * the token came straight from Keycloak over the code-exchange channel and is
 * used only to render the profile screen, never for authorization decisions.
 */
fun parseIdTokenClaims(idToken: String?): IdTokenClaims? {
    val payload = idToken?.split(".")?.getOrNull(1) ?: return null
    return runCatching {
        val json = JSONObject(
            String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)),
        )
        IdTokenClaims(
            name = json.optString("name").ifBlank { null },
            username = json.optString("preferred_username").ifBlank { null },
            email = json.optString("email").ifBlank { null },
        )
    }.getOrNull()
}
