package kz.tcloud.dcinv.data.network.dto

import kotlinx.serialization.Serializable

/** Response of `GET /api/v1/racks/{id}/elevation` (rack visualization, phase 2). */
@Serializable
data class RackElevationResponse(
    val rack: RackElevationInfo,
    val devices: List<ElevationDevice> = emptyList(),
    val reservations: List<RackReservation> = emptyList(),
    /** Devices assigned to the rack but without a mounted position (count only). */
    val unpositionedCount: Int = 0,
    val occupiedUnits: Int = 0,
)

@Serializable
data class RackElevationInfo(
    val id: Int,
    val name: String,
    val siteId: Int = 0,
    val uHeight: Int,
)

/** A status `{value, label}`; tolerant of a missing label. */
@Serializable
data class ElevationStatus(val value: String = "", val label: String = "")

@Serializable
data class ElevationDevice(
    val id: Int,
    val name: String,
    val status: ElevationStatus = ElevationStatus(),
    val roleName: String? = null,
    val deviceTypeModel: String? = null,
    /** Lowest occupied unit. Positioned devices only — never null here. */
    val position: Int? = null,
    val uHeight: Int = 1,
    /** "front" | "rear". */
    val face: String? = null,
)

@Serializable
data class RackReservation(
    val units: List<Int> = emptyList(),
    val description: String? = null,
)
