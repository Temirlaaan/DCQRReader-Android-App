package kz.tcloud.dcinv.ui.bind

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

data class BindUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<DeviceResponse> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val binding: Boolean = false,
    val error: String? = null,
    val boundOk: Boolean = false,
)

@HiltViewModel
class BindViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val qrRepository: QrRepository,
    sessionRepository: SessionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val qrId: String = checkNotNull(savedStateHandle["qrId"]) { "qrId nav arg missing" }

    private val _state = MutableStateFlow(BindUiState())
    val state: StateFlow<BindUiState> = _state.asStateFlow()

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

    /** Bind the QR to [device], using its version for optimistic concurrency. */
    fun bind(device: DeviceResponse) {
        viewModelScope.launch {
            _state.update { it.copy(binding = true, error = null) }
            try {
                qrRepository.bind(qrId, device.data.id, device.version)
                _state.update { it.copy(binding = false, boundOk = true) }
            } catch (e: ApiException) {
                if (shiftGate.trip(e) { bind(device) }) {
                    _state.update { it.copy(binding = false) }
                } else {
                    _state.update { it.copy(binding = false, error = e.userMessage()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(binding = false, error = e.message) }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    private fun ApiException.userMessage(): String =
        apiError?.userMessage ?: apiError?.message ?: message ?: "Ошибка"
}
