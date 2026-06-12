package kz.tcloud.dcinv.data.repository

import kz.tcloud.dcinv.data.network.ApiCaller
import kz.tcloud.dcinv.data.network.api.MetaApi
import kz.tcloud.dcinv.data.network.dto.DeviceFormConfig
import kz.tcloud.dcinv.data.network.dto.MetaDeviceType
import kz.tcloud.dcinv.data.network.dto.MetaRack
import kz.tcloud.dcinv.data.network.dto.MetaRole
import kz.tcloud.dcinv.data.network.dto.MetaSite
import kz.tcloud.dcinv.data.network.dto.MetaStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaRepository @Inject constructor(
    private val metaApi: MetaApi,
    private val apiCaller: ApiCaller,
) {
    suspend fun sites(): List<MetaSite> = apiCaller.call { metaApi.sites() }

    suspend fun racks(siteId: Int?): List<MetaRack> = apiCaller.call { metaApi.racks(siteId) }

    suspend fun statuses(): List<MetaStatus> = apiCaller.call { metaApi.statuses() }

    suspend fun deviceTypes(): List<MetaDeviceType> = apiCaller.call { metaApi.deviceTypes() }

    suspend fun roles(): List<MetaRole> = apiCaller.call { metaApi.roles() }

    suspend fun deviceForm(): DeviceFormConfig = apiCaller.call { metaApi.deviceForm() }
}
