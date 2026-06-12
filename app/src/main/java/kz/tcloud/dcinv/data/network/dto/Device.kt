package kz.tcloud.dcinv.data.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** NetBox reference object: `{id, name}`. */
@Serializable
data class ObjectRef(
    val id: Int,
    val name: String,
)

/** NetBox status: `{value, label}`. */
@Serializable
data class StatusRef(
    val value: String,
    val label: String,
)

/** Device payload (DeviceData in the OpenAPI). Field names map from snake_case. */
@Serializable
data class DeviceData(
    val id: Int,
    val name: String,
    val status: StatusRef,
    val site: ObjectRef,
    val rack: ObjectRef? = null,
    val position: Int? = null,
    val serial: String,
    val assetTag: String? = null,
    val comments: String,
    val deviceType: ObjectRef? = null,
    val manufacturer: ObjectRef? = null,
    val deviceRole: ObjectRef? = null,
    val uHeight: Int? = null,
    val primaryIp4: String? = null,
    val primaryIp6: String? = null,
    val lastUpdated: String? = null,
    val qrId: String? = null,
    val customFields: JsonObject? = null,
)

/** Read/update response wrapper: `{data, version}`. `version` → If-Unmodified-Since. */
@Serializable
data class DeviceResponse(
    val data: DeviceData,
    val version: String,
)
