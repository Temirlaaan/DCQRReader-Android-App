package kz.tcloud.dcinv.data.network.dto

import kotlinx.serialization.Serializable

/** Active or historical shift session (SessionInfo in OpenAPI). */
@Serializable
data class SessionInfo(
    val id: String,
    val userEmail: String,
    val userKeycloakId: String,
    val shiftStartAt: String,
    val shiftEndAt: String? = null,
    val tabletId: String,
    val endReason: String? = null,
)

/** Wrapper for active / start / end responses: `{session: ... | null}`. */
@Serializable
data class SessionResponse(
    val session: SessionInfo? = null,
)

@Serializable
data class SessionStartRequest(
    val tabletId: String,
)

@Serializable
data class SessionEndRequest(
    val endReason: String,
)
