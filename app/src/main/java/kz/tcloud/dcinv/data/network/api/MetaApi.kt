package kz.tcloud.dcinv.data.network.api

import kz.tcloud.dcinv.data.network.dto.DeviceFormConfig
import kz.tcloud.dcinv.data.network.dto.MetaDeviceType
import kz.tcloud.dcinv.data.network.dto.MetaRack
import kz.tcloud.dcinv.data.network.dto.MetaRole
import kz.tcloud.dcinv.data.network.dto.MetaSite
import kz.tcloud.dcinv.data.network.dto.MetaStatus
import retrofit2.http.GET
import retrofit2.http.Query

interface MetaApi {
    @GET("api/v1/meta/sites")
    suspend fun sites(): List<MetaSite>

    @GET("api/v1/meta/racks")
    suspend fun racks(@Query("site_id") siteId: Int? = null): List<MetaRack>

    @GET("api/v1/meta/statuses")
    suspend fun statuses(): List<MetaStatus>

    @GET("api/v1/meta/device-types")
    suspend fun deviceTypes(): List<MetaDeviceType>

    @GET("api/v1/meta/roles")
    suspend fun roles(): List<MetaRole>

    @GET("api/v1/meta/device-form")
    suspend fun deviceForm(): DeviceFormConfig
}
