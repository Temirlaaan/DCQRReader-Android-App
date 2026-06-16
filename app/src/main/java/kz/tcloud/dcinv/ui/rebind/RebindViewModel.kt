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
import kz.tcloud.dcinv.data.prefs.PickerPreferences
import kz.tcloud.dcinv.data.repository.DeviceRepository
import kz.tcloud.dcinv.data.repository.MetaRepository
import kz.tcloud.dcinv.data.repository.QrRepository
import kz.tcloud.dcinv.data.repository.SessionRepository
import kz.tcloud.dcinv.ui.common.DevicePickerEngine
import kz.tcloud.dcinv.ui.common.ShiftGate
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

data class RebindUiState(
    val rebinding: Boolean = false,
    val error: String? = null,
    val doneOk: Boolean = false,
)

/**
 * Move an already-bound QR to a different device. Reuses the shared device
 * picker; the backend requires a mandatory reason (audited), enforced in the
 * confirm dialog.
 */
@HiltViewModel
class RebindViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val qrRepository: QrRepository,
    metaRepository: MetaRepository,
    sessionRepository: SessionRepository,
    pickerPreferences: PickerPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val qrId: String = checkNotNull(savedStateHandle["qrId"]) { "qrId nav arg missing" }

    private val _state = MutableStateFlow(RebindUiState())
    val state: StateFlow<RebindUiState> = _state.asStateFlow()

    val picker = DevicePickerEngine(metaRepository, deviceRepository, viewModelScope, pickerPreferences) { msg ->
        _state.update { it.copy(error = msg) }
    }

    val shiftGate = ShiftGate(sessionRepository, viewModelScope) { msg ->
        _state.update { it.copy(error = msg) }
    }

    init {
        picker.loadSites()
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
