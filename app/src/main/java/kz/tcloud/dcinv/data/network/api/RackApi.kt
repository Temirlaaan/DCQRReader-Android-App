package kz.tcloud.dcinv.data.network.api

import kz.tcloud.dcinv.data.network.dto.RackElevationResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface RackApi {
    @GET("api/v1/racks/{rackId}/elevation")
    suspend fun elevation(@Path("rackId") rackId: Int): RackElevationResponse
}
