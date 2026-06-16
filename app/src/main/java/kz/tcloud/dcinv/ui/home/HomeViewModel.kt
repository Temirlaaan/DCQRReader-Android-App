package kz.tcloud.dcinv.ui.home

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
import kz.tcloud.dcinv.data.network.dto.SessionInfo
import kz.tcloud.dcinv.data.repository.SessionRepository
import kz.tcloud.dcinv.data.scan.ScanEntry
import kz.tcloud.dcinv.data.scan.ScanHistory
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val shift: SessionInfo? = null,
    val error: String? = null,
) {
    val hasActiveShift: Boolean get() = shift != null
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    scanHistory: ScanHistory,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val recentScans: StateFlow<List<ScanEntry>> = scanHistory.items

    init {
        refresh()
    }

    /** Re-check the active shift (call on app foreground per API guide §5). */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runAction { sessionRepository.activeSession() }
        }
    }

    fun startShift() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val shift = sessionRepository.startShift()
                _state.update { it.copy(loading = false, shift = shift) }
            } catch (e: ApiException) {
                // SESSION_ALREADY_ACTIVE → just reflect the existing shift.
                if (e.code == "SESSION_ALREADY_ACTIVE") {
                    runAction { sessionRepository.activeSession() }
                } else {
                    _state.update { it.copy(loading = false, error = e.userMessage()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun endShift() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                sessionRepository.endShift()
                _state.update { it.copy(loading = false, shift = null) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    private suspend fun runAction(block: suspend () -> SessionInfo?) {
        try {
            val shift = block()
            _state.update { it.copy(loading = false, shift = shift) }
        } catch (e: ApiException) {
            _state.update { it.copy(loading = false, error = e.userMessage()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.message) }
        }
    }

    private fun ApiException.userMessage(): String? = userMessageOrNull()
}
