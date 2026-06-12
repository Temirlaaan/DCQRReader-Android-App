package kz.tcloud.dcinv.ui.edit

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds the PATCH body for a device update from the set of changed fields,
 * keyed by form-field key. Uses a [JsonObject] (not a @Serializable DTO) so that
 * **explicit nulls survive serialization** — the global Json config omits nulls,
 * which would otherwise make it impossible to clear `rack`/`position`.
 *
 * Backend field names (snake_case): status, name, serial, asset_tag, comments,
 * site_id, rack_id, position.
 */
fun buildDeviceUpdateBody(changed: Map<String, String>): JsonObject = buildJsonObject {
    changed["status"]?.let { put("status", it) }
    changed["name"]?.let { put("name", it) }
    changed["serial"]?.let { put("serial", it) }
    changed["asset_tag"]?.let { put("asset_tag", it) }
    changed["comments"]?.let { put("comments", it) }
    changed["site"]?.toIntOrNull()?.let { put("site_id", it) }

    // rack / position are nullable on the backend — an empty value means "clear",
    // sent as an explicit JSON null.
    if ("rack" in changed) {
        val v = changed.getValue("rack")
        if (v.isBlank()) put("rack_id", JsonNull) else v.toIntOrNull()?.let { put("rack_id", it) }
    }
    if ("position" in changed) {
        val v = changed.getValue("position")
        if (v.isBlank()) put("position", JsonNull) else v.toIntOrNull()?.let { put("position", it) }
    }
}
