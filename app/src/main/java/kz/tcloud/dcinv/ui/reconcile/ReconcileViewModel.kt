package kz.tcloud.dcinv.ui.reconcile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kz.tcloud.dcinv.data.network.ApiException
import kz.tcloud.dcinv.data.network.userMessageOrNull
import kz.tcloud.dcinv.data.repository.QrRepository
import kz.tcloud.dcinv.data.repository.RackRepository
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

/** Per-expected-device state during a reconciliation walk. */
enum class CheckState { PENDING, CONFIRMED, WRONG_POSITION }

data class ExpectedItem(
    val deviceId: Int,
    val name: String,
    val position: Int?,
    val state: CheckState = CheckState.PENDING,
)

/** A scanned label that doesn't cleanly belong here. */
data class Anomaly(val qrId: String, val message: String)

enum class BannerKind { OK, WARN }

data class ScanBanner(val text: String, val kind: BannerKind)

data class ReconcileUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val rackName: String = "",
    val expected: List<ExpectedItem> = emptyList(),
    val anomalies: List<Anomaly> = emptyList(),
    val banner: ScanBanner? = null,
    val checking: Boolean = false,
) {
    val total: Int get() = expected.size
    val confirmed: Int get() = expected.count { it.state != CheckState.PENDING }
}

/**
 * Rack reconciliation: load the rack's expected devices from the elevation, then
 * scan labels one by one. Each scan is looked up and classified against what
 * NetBox says — confirming presence, flagging wrong positions, and surfacing
 * labels that belong to another rack or aren't bound. The live checklist IS the
 * discrepancy report.
 */
@HiltViewModel
class ReconcileViewModel @Inject constructor(
    private val rackRepository: RackRepository,
    private val qrRepository: QrRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val rackId: Int = checkNotNull(savedStateHandle["rackId"]) { "rackId nav arg missing" }

    private val _state = MutableStateFlow(ReconcileUiState())
    val state: StateFlow<ReconcileUiState> = _state.asStateFlow()

    /** Labels already resolved this session — the analyzer fires continuously. */
    private val processed = HashSet<String>()
    private val expectedPos = HashMap<Int, Int?>()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val resp = rackRepository.elevation(rackId)
                expectedPos.clear()
                val items = resp.devices.map {
                    expectedPos[it.id] = it.position
                    ExpectedItem(deviceId = it.id, name = it.name, position = it.position)
                }.sortedByDescending { it.position ?: -1 }
                _state.update { it.copy(loading = false, rackName = resp.rack.name, expected = items) }
            } catch (e: ApiException) {
                _state.update {
                    it.copy(loading = false, error = e.userMessageOrNull() ?: "Нет связи с сервером. Проверьте VPN")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    /**
     * Returns the feedback kind to play, or null if the code was already handled
     * (the camera analyzer re-fires the same code many times per second).
     */
    fun onScan(qrId: String, onFeedback: (BannerKind) -> Unit) {
        if (qrId in processed) return
        processed += qrId
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            try {
                val result = qrRepository.lookup(qrId)
                val device = result.device
                when {
                    device == null -> anomaly(qrId, "Метка не привязана к устройству", onFeedback)
                    device.rack?.id == rackId -> classifyInRack(qrId, device.id, device.name, device.position, onFeedback)
                    else -> {
                        val where = device.rack?.name ?: "другая стойка"
                        anomaly(qrId, "«${device.name}» числится в: $where", onFeedback)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Let the engineer retry this label: don't mark it processed.
                processed.remove(qrId)
                val msg = (e as? ApiException)?.let { it.apiError?.userMessage ?: it.message } ?: e.message
                _state.update { it.copy(banner = ScanBanner(msg ?: "Ошибка проверки", BannerKind.WARN)) }
                onFeedback(BannerKind.WARN)
            } finally {
                _state.update { it.copy(checking = false) }
            }
        }
    }

    private fun classifyInRack(
        qrId: String,
        deviceId: Int,
        name: String,
        position: Int?,
        onFeedback: (BannerKind) -> Unit,
    ) {
        val expected = _state.value.expected.any { it.deviceId == deviceId }
        if (!expected) {
            // In this rack per NetBox but not in the elevation (e.g. no position).
            anomaly(qrId, "«$name» в этой стойке, но без позиции в раскладке", onFeedback)
            return
        }
        val posMatch = expectedPos[deviceId] == position
        val newState = if (posMatch) CheckState.CONFIRMED else CheckState.WRONG_POSITION
        _state.update { s ->
            s.copy(
                expected = s.expected.map { if (it.deviceId == deviceId) it.copy(state = newState) else it },
                banner = ScanBanner(
                    if (posMatch) "✓ $name" else "$name — позиция не совпадает (U${position ?: "—"})",
                    if (posMatch) BannerKind.OK else BannerKind.WARN,
                ),
            )
        }
        onFeedback(if (posMatch) BannerKind.OK else BannerKind.WARN)
    }

    private fun anomaly(qrId: String, message: String, onFeedback: (BannerKind) -> Unit) {
        _state.update { s ->
            if (s.anomalies.any { it.qrId == qrId }) s
            else s.copy(
                anomalies = s.anomalies + Anomaly(qrId, message),
                banner = ScanBanner(message, BannerKind.WARN),
            )
        }
        onFeedback(BannerKind.WARN)
    }

    fun consumeBanner() = _state.update { it.copy(banner = null) }
}
