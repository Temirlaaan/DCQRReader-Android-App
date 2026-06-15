package kz.tcloud.dcinv.ui.rebind

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
import kz.tcloud.dcinv.data.network.dto.DeviceResponse
import kz.tcloud.dcinv.data.repository.DeviceRepository
import kz.tcloud.dcinv.data.repository.QrRepository
import kz.tcloud.dcinv.data.repository.SessionRepository
import kz.tcloud.dcinv.ui.common.ShiftGate
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

data class RebindUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<DeviceResponse> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val rebinding: Boolean = false,
    val error: String? = null,
    val doneOk: Boolean = false,
)

/**
 * Move an already-bound QR to a different device. Mirrors the bind search flow
 * but the QR is currently BOUND and the backend requires a mandatory reason
 * (audited): the confirm dialog enforces it.
 */
@HiltViewModel
class RebindViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val qrRepository: QrRepository,
    sessionRepository: SessionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val qrId: String = checkNotNull(savedStateHandle["qrId"]) { "qrId nav arg missing" }

    private val _state = MutableStateFlow(RebindUiState())
    val state: StateFlow<RebindUiState> = _state.asStateFlow()

    val shiftGate = ShiftGate(sessionRepository, viewModelScope) { msg ->
        _state.update { it.copy(error = msg) }
    }

    fun onQueryChange(q: String) = _state.update { it.copy(query = q) }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(searching = true, error = null) }
            try {
                val resp = deviceRepository.search(q, page = 1)
                _state.update {
                    it.copy(searching = false, results = resp.results, page = 1, hasMore = resp.hasMore)
                }
            } catch (e: ApiException) {
                _state.update { it.copy(searching = false, error = e.userMessage()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(searching = false, error = e.message) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || !s.hasMore) return
        val q = s.query.trim()
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true, error = null) }
            try {
                val next = s.page + 1
                val resp = deviceRepository.search(q, page = next)
                _state.update {
                    it.copy(
                        loadingMore = false,
                        results = it.results + resp.results,
                        page = next,
                        hasMore = resp.hasMore,
                    )
                }
            } catch (e: ApiException) {
                _state.update { it.copy(loadingMore = false, error = e.userMessage()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false, error = e.message) }
            }
        }
    }

    /** Rebind to [device] with a mandatory [reason], using its version for OCC. */
    fun rebind(device: DeviceResponse, reason: String) {
        viewModelScope.launch {
            _state.update { it.copy(rebinding = true, error = null) }
            try {
                qrRepository.rebind(qrId, device.data.id, device.version, reason)
                _state.update { it.copy(rebinding = false, doneOk = true) }
            } catch (e: ApiException) {
                if (shiftGate.trip(e) { rebind(device, reason) }) {
                    _state.update { it.copy(rebinding = false) }
                } else {
                    _state.update { it.copy(rebinding = false, error = e.userMessage()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(rebinding = false, error = e.message) }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    private fun ApiException.userMessage(): String =
        apiError?.userMessage ?: apiError?.message ?: message ?: "Ошибка"
}
