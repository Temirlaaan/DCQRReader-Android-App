package kz.tcloud.dcinv.data.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kz.tcloud.dcinv.data.network.dto.ApiError
import kz.tcloud.dcinv.data.network.dto.ApiErrorEnvelope
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Retrofit calls so repositories deal with [ApiException] instead of raw
 * [HttpException]. Parses the structured error envelope when present.
 *
 * Note: [SerializationException] is caught and mapped too — otherwise a response
 * whose JSON doesn't match our DTOs would crash the calling coroutine silently
 * (visible only in logcat) instead of surfacing on screen.
 *
 * CancellationException is intentionally NOT caught here (no broad `Exception`
 * catch) so coroutine cancellation keeps working.
 */
@Singleton
class ApiCaller @Inject constructor(
    private val json: Json,
) {
    suspend fun <T> call(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: HttpException) {
            val raw = e.response()?.errorBody()?.string()
            val apiError = raw
                ?.let { runCatching { json.decodeFromString<ApiErrorEnvelope>(it).error }.getOrNull() }
            throw ApiException(httpCode = e.code(), apiError = apiError, cause = e)
        } catch (e: SerializationException) {
            throw ApiException(
                httpCode = HTTP_PARSE_ERROR,
                apiError = ApiError(code = "RESPONSE_PARSE_ERROR", message = e.message),
                cause = e,
            )
        } catch (e: IOException) {
            // Connectivity / TLS / timeout — surface the concrete reason so it's
            // actionable (DNS vs SSL handshake vs timeout) instead of just "-1".
            val reason = e.message ?: e.javaClass.simpleName
            throw ApiException(
                httpCode = HTTP_NETWORK_ERROR,
                apiError = ApiError(
                    code = "NETWORK_ERROR",
                    message = "Сеть: $reason",
                ),
                cause = e,
            )
        }
    }

    companion object {
        /** Synthetic codes for non-HTTP failures. */
        const val HTTP_NETWORK_ERROR = -1
        const val HTTP_PARSE_ERROR = -2
    }
}
