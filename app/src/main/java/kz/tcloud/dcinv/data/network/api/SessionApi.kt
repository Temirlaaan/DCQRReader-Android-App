package kz.tcloud.dcinv.data.network.api

import kz.tcloud.dcinv.data.network.dto.SessionEndRequest
import kz.tcloud.dcinv.data.network.dto.SessionResponse
import kz.tcloud.dcinv.data.network.dto.SessionStartRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface SessionApi {
    @GET("api/v1/sessions/active")
    suspend fun active(): SessionResponse

    @POST("api/v1/sessions/start")
    suspend fun start(
        @Body body: SessionStartRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): SessionResponse

    @POST("api/v1/sessions/end")
    suspend fun end(
        @Body body: SessionEndRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): SessionResponse
}
