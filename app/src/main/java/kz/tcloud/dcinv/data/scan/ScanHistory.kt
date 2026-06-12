package kz.tcloud.dcinv.data.scan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ScanEntry(
    val qrId: String,
    val deviceName: String?,
    val atEpochMs: Long,
)

/**
 * In-memory recent-scan list shown on Home. Not persisted (cleared on process
 * death) — it's a convenience surface, not a record of truth (audit lives on the
 * backend). Capped to the most recent [CAP] entries, de-duplicated by qrId.
 */
@Singleton
class ScanHistory @Inject constructor() {
    private val _items = MutableStateFlow<List<ScanEntry>>(emptyList())
    val items: StateFlow<List<ScanEntry>> = _items.asStateFlow()

    fun record(qrId: String, deviceName: String?) {
        _items.value = (
            listOf(ScanEntry(qrId, deviceName, System.currentTimeMillis())) +
                _items.value.filterNot { it.qrId == qrId }
            ).take(CAP)
    }

    fun clear() {
        _items.value = emptyList()
    }

    private companion object {
        const val CAP = 10
    }
}
