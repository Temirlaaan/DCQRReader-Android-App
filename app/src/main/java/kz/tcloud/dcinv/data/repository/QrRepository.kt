package kz.tcloud.dcinv.data.repository

import kz.tcloud.dcinv.data.network.ApiCaller
import kz.tcloud.dcinv.data.network.api.QrApi
import kz.tcloud.dcinv.data.network.dto.QrBindRequest
import kz.tcloud.dcinv.data.network.dto.QrLookupResponse
import kz.tcloud.dcinv.data.network.idempotency.IdempotencyStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QrRepository @Inject constructor(
    private val qrApi: QrApi,
    private val apiCaller: ApiCaller,
    private val idempotency: IdempotencyStore,
) {
    suspend fun lookup(qrId: String): QrLookupResponse = apiCaller.call { qrApi.lookup(qrId) }

    /** Bind a free QR to [deviceId]; [version] is that device's last_updated. */
    suspend fun bind(qrId: String, deviceId: Int, version: String): QrLookupResponse =
        idempotency.withKey("bind:$qrId:$deviceId") { key ->
            apiCaller.call {
                qrApi.bind(
                    qrId = qrId,
                    body = QrBindRequest(deviceId = deviceId, version = version),
                    idempotencyKey = key,
                )
            }
        }
}
