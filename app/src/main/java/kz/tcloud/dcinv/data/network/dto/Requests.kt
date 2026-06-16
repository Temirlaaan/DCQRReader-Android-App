package kz.tcloud.dcinv.data.network.dto

import kotlinx.serialization.Serializable

/** Bind a free QR to an existing device. `version` = the device's last_updated. */
@Serializable
data class QrBindRequest(
    val deviceId: Int,
    val version: String,
)

/** Move a bound QR to another device. `reason` is mandatory (audited). */
@Serializable
data class QrRebindRequest(
    val deviceId: Int,
    val version: String,
    val reason: String,
)

/** Release a bound QR back to FREE. `reason` is mandatory (audited). */
@Serializable
data class QrUnbindRequest(
    val version: String,
    val reason: String,
)

@Serializable
data class AddCommentRequest(val comment: String)

@Serializable
data class AddCommentResponse(val id: Int)

// Device PATCH bodies are built as a raw JsonObject (see ui/edit/DeviceUpdateBody)
// so explicit nulls survive serialization and can clear nullable fields.

/**
 * `POST /devices/` payload. `device_type_id` and `role_id` are required at
 * creation but immutable afterwards. Optional nulls are omitted on the wire
 * (explicitNulls=false), which is what the backend expects for "not set".
 */
@Serializable
data class DeviceCreateRequest(
    val deviceTypeId: Int,
    val roleId: Int,
    val siteId: Int,
    val status: String,
    val name: String,
    val rackId: Int? = null,
    val position: Int? = null,
    val serial: String? = null,
    val assetTag: String? = null,
    val comments: String? = null,
)

/** `POST /devices/{id}/decommission` payload. `version` = device's last_updated. */
@Serializable
data class DeviceDecommissionRequest(
    val version: String,
    val reason: String? = null,
)

@Serializable
data class DeviceSearchResponse(
    val results: List<DeviceResponse>,
    val page: Int,
    val pageSize: Int,
    val hasMore: Boolean,
)
