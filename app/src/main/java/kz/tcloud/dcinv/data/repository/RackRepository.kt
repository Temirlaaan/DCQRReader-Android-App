package kz.tcloud.dcinv.data.repository

import kz.tcloud.dcinv.data.network.ApiCaller
import kz.tcloud.dcinv.data.network.api.RackApi
import kz.tcloud.dcinv.data.network.dto.RackElevationResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RackRepository @Inject constructor(
    private val rackApi: RackApi,
    private val apiCaller: ApiCaller,
) {
    suspend fun elevation(rackId: Int): RackElevationResponse =
        apiCaller.call { rackApi.elevation(rackId) }
}
