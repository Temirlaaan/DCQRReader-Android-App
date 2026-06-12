package kz.tcloud.dcinv.data.repository

import kz.tcloud.dcinv.data.device.TabletIdProvider
import kz.tcloud.dcinv.data.network.ApiCaller
import kz.tcloud.dcinv.data.network.api.SessionApi
import kz.tcloud.dcinv.data.network.dto.SessionEndRequest
import kz.tcloud.dcinv.data.network.dto.SessionInfo
import kz.tcloud.dcinv.data.network.dto.SessionStartRequest
import kz.tcloud.dcinv.data.network.idempotency.IdempotencyStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionApi: SessionApi,
    private val apiCaller: ApiCaller,
    private val tabletIdProvider: TabletIdProvider,
    private val idempotency: IdempotencyStore,
) {
    /** Current active shift, or null. */
    suspend fun activeSession(): SessionInfo? =
        apiCaller.call { sessionApi.active() }.session

    suspend fun startShift(): SessionInfo? {
        val tabletId = tabletIdProvider.tabletId()
        return idempotency.withKey("session:start:$tabletId") { key ->
            apiCaller.call {
                sessionApi.start(SessionStartRequest(tabletId = tabletId), key).session
            }
        }
    }

    suspend fun endShift(reason: String = "manual"): SessionInfo? =
        idempotency.withKey("session:end") { key ->
            apiCaller.call {
                sessionApi.end(SessionEndRequest(endReason = reason), key).session
            }
        }
}
