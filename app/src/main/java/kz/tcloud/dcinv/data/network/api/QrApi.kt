package kz.tcloud.dcinv.data.network.api

import kz.tcloud.dcinv.data.network.dto.QrBindRequest
import kz.tcloud.dcinv.data.network.dto.QrLookupResponse
import kz.tcloud.dcinv.data.network.dto.QrRebindRequest
import kz.tcloud.dcinv.data.network.dto.QrUnbindRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface QrApi {
    @GET("api/v1/qr/{qrId}")
    suspend fun lookup(@Path("qrId") qrId: String): QrLookupResponse

    @POST("api/v1/qr/{qrId}/bind")
    suspend fun bind(
        @Path("qrId") qrId: String,
        @Body body: QrBindRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): QrLookupResponse

    /** Move a bound metka to a different device. `version` = new device's last_updated. */
    @POST("api/v1/qr/{qrId}/rebind")
    suspend fun rebind(
        @Path("qrId") qrId: String,
        @Body body: QrRebindRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): QrLookupResponse

    /** Release a bound metka back to FREE. `version` = current device's last_updated. */
    @POST("api/v1/qr/{qrId}/unbind")
    suspend fun unbind(
        @Path("qrId") qrId: String,
        @Body body: QrUnbindRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): QrLookupResponse
}
