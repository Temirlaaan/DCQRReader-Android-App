package kz.tcloud.dcinv.data.repository

import kotlinx.serialization.json.JsonObject
import kz.tcloud.dcinv.data.network.ApiCaller
import kz.tcloud.dcinv.data.network.api.DeviceApi
import kz.tcloud.dcinv.data.network.dto.AddCommentRequest
import kz.tcloud.dcinv.data.network.dto.DeviceCreateRequest
import kz.tcloud.dcinv.data.network.dto.DeviceDecommissionRequest
import kz.tcloud.dcinv.data.network.dto.DeviceResponse
import kz.tcloud.dcinv.data.network.dto.DeviceSearchResponse
import kz.tcloud.dcinv.data.network.idempotency.IdempotencyStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val deviceApi: DeviceApi,
    private val apiCaller: ApiCaller,
    private val idempotency: IdempotencyStore,
) {
    suspend fun get(id: Int): DeviceResponse = apiCaller.call { deviceApi.getDevice(id) }

    suspend fun search(query: String, page: Int = 1): DeviceSearchResponse = apiCaller.call {
        // Free-text search by name; the backend also supports asset_tag/serial/site/rack.
        deviceApi.search(name = query.ifBlank { null }, page = page)
    }

    suspend fun update(
        id: Int,
        body: JsonObject,
        version: String,
    ): DeviceResponse {
        // Same device + base version + payload = the same logical edit → same key.
        val actionId = "update:$id:$version:${body.hashCode()}"
        return idempotency.withKey(actionId) { key ->
            apiCaller.call { deviceApi.update(id = id, body = body, version = version, idempotencyKey = key) }
        }
    }

    suspend fun addComment(id: Int, comment: String): Int {
        val actionId = "comment:$id:${comment.hashCode()}"
        return idempotency.withKey(actionId) { key ->
            apiCaller.call {
                deviceApi.addComment(id = id, body = AddCommentRequest(comment = comment), idempotencyKey = key).id
            }
        }
    }

    suspend fun create(body: DeviceCreateRequest): DeviceResponse {
        // Same payload = same logical create → same key, so a network retry
        // can't produce a second device.
        val actionId = "create:${body.hashCode()}"
        return idempotency.withKey(actionId) { key ->
            apiCaller.call { deviceApi.create(body = body, idempotencyKey = key) }
        }
    }

    suspend fun decommission(id: Int, version: String, reason: String?): DeviceResponse {
        val actionId = "decommission:$id:$version"
        return idempotency.withKey(actionId) { key ->
            apiCaller.call {
                deviceApi.decommission(
                    id = id,
                    body = DeviceDecommissionRequest(version = version, reason = reason?.ifBlank { null }),
                    idempotencyKey = key,
                )
            }
        }
    }
}
