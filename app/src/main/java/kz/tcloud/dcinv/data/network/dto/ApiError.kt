package kz.tcloud.dcinv.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Structured error envelope returned by the backend, e.g.:
 * `{"error": {"code": "VERSION_CONFLICT", "message": "...", ...}}`.
 * See docs/mobile-api-guide.md §4.
 */
@Serializable
data class ApiErrorEnvelope(
    val error: ApiError,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String? = null,
    val userMessage: String? = null,
    val requestId: String? = null,
    val retryAfterSeconds: Int? = null,
    val currentVersion: String? = null,
    val currentStatus: String? = null,
)
