package kz.tcloud.dcinv.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class QrStatus {
    @SerialName("free")
    FREE,

    @SerialName("bound")
    BOUND,

    @SerialName("retired")
    RETIRED,
}

/** Intended placement carried by the QR's batch (for the Free QR screen). */
@Serializable
data class QrBatchInfo(
    val intendedSiteId: Int? = null,
    val intendedLocationId: Int? = null,
    val intendedRackId: Int? = null,
)

@Serializable
data class QrInfo(
    val id: String,
    val status: QrStatus,
    val batch: QrBatchInfo,
    val boundToDeviceId: Int? = null,
    val boundAt: String? = null,
    val retiredAt: String? = null,
    val retiredReason: String? = null,
)

/** Response of `GET /api/v1/qr/{id}`. `device` is populated only when bound. */
@Serializable
data class QrLookupResponse(
    val qr: QrInfo,
    val device: DeviceData? = null,
    val deviceError: String? = null,
)
