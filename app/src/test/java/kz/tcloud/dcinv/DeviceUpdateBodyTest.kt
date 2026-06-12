package kz.tcloud.dcinv

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kz.tcloud.dcinv.ui.edit.buildDeviceUpdateBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceUpdateBodyTest {

    @Test
    fun `maps form keys to backend snake_case keys`() {
        val body = buildDeviceUpdateBody(
            mapOf(
                "name" to "core-sw-01",
                "site" to "5",
                "position" to "12",
                "status" to "active",
            ),
        )
        assertEquals(JsonPrimitive("core-sw-01"), body["name"])
        assertEquals(JsonPrimitive(5), body["site_id"])
        assertEquals(JsonPrimitive(12), body["position"])
        assertEquals(JsonPrimitive("active"), body["status"])
    }

    @Test
    fun `blank rack clears it with explicit null`() {
        val body = buildDeviceUpdateBody(mapOf("rack" to ""))
        assertTrue(body.containsKey("rack_id"))
        assertEquals(JsonNull, body["rack_id"])
    }

    @Test
    fun `blank position clears it with explicit null`() {
        val body = buildDeviceUpdateBody(mapOf("position" to ""))
        assertEquals(JsonNull, body["position"])
    }

    @Test
    fun `non-blank rack is sent as id`() {
        val body = buildDeviceUpdateBody(mapOf("rack" to "7"))
        assertEquals(JsonPrimitive(7), body["rack_id"])
    }

    @Test
    fun `unchanged keys are absent`() {
        val body = buildDeviceUpdateBody(mapOf("name" to "x"))
        assertFalse(body.containsKey("serial"))
        assertFalse(body.containsKey("rack_id"))
        assertFalse(body.containsKey("position"))
    }
}
