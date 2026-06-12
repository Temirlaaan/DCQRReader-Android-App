package kz.tcloud.dcinv.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MetaSite(val id: Int, val name: String)

@Serializable
data class MetaRack(
    val id: Int,
    val name: String,
    val siteId: Int,
    val uHeight: Int,
)

@Serializable
data class MetaStatus(val value: String, val label: String)

/** NetBox device type for the create form. `uHeight` = how many U it occupies. */
@Serializable
data class MetaDeviceType(
    val id: Int,
    val model: String,
    val manufacturerName: String,
    val uHeight: Int,
)

/** NetBox device role for the create form. */
@Serializable
data class MetaRole(val id: Int, val name: String)

@Serializable
enum class FieldType {
    @SerialName("choice")
    CHOICE,

    @SerialName("reference")
    REFERENCE,

    @SerialName("integer")
    INTEGER,

    @SerialName("text")
    TEXT,

    @SerialName("multiline_text")
    MULTILINE_TEXT,

    @SerialName("boolean")
    BOOLEAN,
}

/**
 * One editable field. The backend sends a generic skeleton plus extra keys
 * (choices_endpoint, min, max_length, …) which we ignore here — field-specific
 * data is resolved by [key] against the meta endpoints.
 */
@Serializable
data class FormField(
    val key: String,
    val label: String,
    val type: FieldType,
    val required: Boolean = false,
)

@Serializable
data class DeviceFormConfig(
    val version: String,
    val fields: List<FormField>,
)
