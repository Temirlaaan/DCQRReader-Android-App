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
import kz.tcloud.dcinv.data.prefs.PickerPreferences
import kz.tcloud.dcinv.data.repository.DeviceRepository
import kz.tcloud.dcinv.data.repository.MetaRepository
import kz.tcloud.dcinv.data.repository.QrRepository
import kz.tcloud.dcinv.data.repository.SessionRepository
import kz.tcloud.dcinv.ui.common.DevicePickerEngine
import kz.tcloud.dcinv.ui.common.ShiftGate
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

data class BindUiState(
    val binding: Boolean = false,
    val error: String? = null,
    val boundOk: Boolean = false,
)

@HiltViewModel
class BindViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val qrRepository: QrRepository,
    metaRepository: MetaRepository,
    sessionRepository: SessionRepository,
    pickerPreferences: PickerPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val qrId: String = checkNotNull(savedStateHandle["qrId"]) { "qrId nav arg missing" }

    private val _state = MutableStateFlow(BindUiState())
    val state: StateFlow<BindUiState> = _state.asStateFlow()

    val picker = DevicePickerEngine(metaRepository, deviceRepository, viewModelScope, pickerPreferences) { msg ->
        _state.update { it.copy(error = msg) }
    }

    val shiftGate = ShiftGate(sessionRepository, viewModelScope) { msg ->
        _state.update { it.copy(error = msg) }
    }

    init {
        picker.loadSites()
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
                    val msg = if (e.code == "DEVICE_ALREADY_BOUND") {
                        "У устройства «${device.data.name}» уже есть метка"
                    } else {
                        e.userMessage()
                    }
                    _state.update { it.copy(binding = false, error = msg) }
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
