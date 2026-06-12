package kz.tcloud.dcinv.data.network

import kz.tcloud.dcinv.data.network.dto.ApiError

/**
 * Thrown for any non-2xx backend response. Carries the HTTP status and, when the
 * body matched the structured error envelope, the parsed [ApiError].
 */
class ApiException(
    val httpCode: Int,
    val apiError: ApiError? = null,
    cause: Throwable? = null,
) : Exception(apiError?.message ?: "HTTP $httpCode", cause) {

    /** Machine-readable backend code (e.g. VERSION_CONFLICT), if present. */
    val code: String? get() = apiError?.code
}
