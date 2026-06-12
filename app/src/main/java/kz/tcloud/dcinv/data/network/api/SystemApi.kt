package kz.tcloud.dcinv.data.network.api

import retrofit2.Response
import retrofit2.http.GET

/**
 * Backend health probe (no auth). Used to validate connectivity / config.
 * Feature-specific APIs (sessions, qr, devices, meta) are added per vertical.
 */
interface SystemApi {
    @GET("health")
    suspend fun health(): Response<Unit>
}
