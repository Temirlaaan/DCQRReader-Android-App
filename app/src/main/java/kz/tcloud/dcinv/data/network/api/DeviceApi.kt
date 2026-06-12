package kz.tcloud.dcinv.data.network.api

import kotlinx.serialization.json.JsonObject
import kz.tcloud.dcinv.data.network.dto.AddCommentRequest
import kz.tcloud.dcinv.data.network.dto.AddCommentResponse
import kz.tcloud.dcinv.data.network.dto.DeviceCreateRequest
import kz.tcloud.dcinv.data.network.dto.DeviceDecommissionRequest
import kz.tcloud.dcinv.data.network.dto.DeviceResponse
import kz.tcloud.dcinv.data.network.dto.DeviceSearchResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface DeviceApi {
    @GET("api/v1/devices/{id}")
    suspend fun getDevice(@Path("id") id: Int): DeviceResponse

    @GET("api/v1/devices/search")
    suspend fun search(
        @Query("name") name: String? = null,
        @Query("asset_tag") assetTag: String? = null,
        @Query("serial") serial: String? = null,
        @Query("site") site: Int? = null,
        @Query("rack") rack: Int? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
    ): DeviceSearchResponse

    @PATCH("api/v1/devices/{id}")
    suspend fun update(
        @Path("id") id: Int,
        @Body body: JsonObject,
        @Header("If-Unmodified-Since") version: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): DeviceResponse

    @POST("api/v1/devices/{id}/comments")
    suspend fun addComment(
        @Path("id") id: Int,
        @Body body: AddCommentRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): AddCommentResponse

    @POST("api/v1/devices/")
    suspend fun create(
        @Body body: DeviceCreateRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): DeviceResponse

    /** Admin-only: retires the bound QR, sets NetBox status to Decommissioning. */
    @POST("api/v1/devices/{id}/decommission")
    suspend fun decommission(
        @Path("id") id: Int,
        @Body body: DeviceDecommissionRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): DeviceResponse
}
