package kz.tcloud.dcinv.ui.qr

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
import kz.tcloud.dcinv.data.network.dto.QrLookupResponse
import kz.tcloud.dcinv.data.repository.DeviceRepository
import kz.tcloud.dcinv.data.repository.QrRepository
import kz.tcloud.dcinv.data.repository.SessionRepository
import kz.tcloud.dcinv.data.scan.ScanHistory
import kz.tcloud.dcinv.ui.common.ShiftGate
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

sealed interface QrUiState {
    data object Loading : QrUiState

    /**
     * [fallbackDeviceName] is resolved from the QR's bound device id when the
     * lookup couldn't embed the device itself, so the UI can name it instead of
     * showing a bare numeric id.
     */
    data class Success(val result: QrLookupResponse, val fallbackDeviceName: String? = null) : QrUiState

    /** 404 — QR not in the registry. */
    data class NotRegistered(val qrId: String) : QrUiState
    data class Error(val message: String) : QrUiState
}

@HiltViewModel
class QrViewModel @Inject constructor(
    private val repository: QrRepository,
    private val deviceRepository: DeviceRepository,
    private val scanHistory: ScanHistory,
    sessionRepository: SessionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val qrId: String = checkNotNull(savedStateHandle["qrId"]) { "qrId nav arg missing" }

    private val _state = MutableStateFlow<QrUiState>(QrUiState.Loading)
    val state: StateFlow<QrUiState> = _state.asStateFlow()

    /** Transient one-shot UI events (snackbar text). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _commentSubmitting = MutableStateFlow(false)
    val commentSubmitting: StateFlow<Boolean> = _commentSubmitting.asStateFlow()

    private val _decommissioning = MutableStateFlow(false)
    val decommissioning: StateFlow<Boolean> = _decommissioning.asStateFlow()

    private val _unbinding = MutableStateFlow(false)
    val unbinding: StateFlow<Boolean> = _unbinding.asStateFlow()

    val shiftGate = ShiftGate(sessionRepository, viewModelScope) { msg -> _message.value = msg }

    /** Device id of the currently bound device, if any. */
    val boundDeviceId: Int?
        get() = (_state.value as? QrUiState.Success)?.result?.device?.id

    init {
        load()
    }

    fun submitComment(text: String, onDone: () -> Unit) {
        val deviceId = boundDeviceId ?: return
        viewModelScope.launch {
            _commentSubmitting.value = true
            try {
                deviceRepository.addComment(deviceId, text)
                _message.value = "Комментарий добавлен"
                onDone()
            } catch (e: ApiException) {
                if (!shiftGate.trip(e) { submitComment(text, onDone) }) {
                    _message.value = e.apiError?.userMessage ?: e.apiError?.message ?: e.message
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: "Не удалось добавить комментарий"
            } finally {
                _commentSubmitting.value = false
            }
        }
    }

    /**
     * Decommission the bound device (admin-only on the backend): re-fetches the
     * device for a fresh version, posts the decommission, then reloads the QR —
     * its status flips to retired. Plain engineers get the backend's 403 message.
     */
    fun decommission(reason: String, onDone: () -> Unit) {
        val deviceId = boundDeviceId ?: return
        viewModelScope.launch {
            _decommissioning.value = true
            try {
                val fresh = deviceRepository.get(deviceId)
                deviceRepository.decommission(deviceId, fresh.version, reason)
                _message.value = "Устройство списано"
                onDone()
                load()
            } catch (e: ApiException) {
                if (!shiftGate.trip(e) { decommission(reason, onDone) }) {
                    _message.value = when (e.httpCode) {
                        403 -> "Списание доступно только администраторам"
                        else -> e.apiError?.userMessage ?: e.apiError?.message ?: e.message
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: "Не удалось списать устройство"
            } finally {
                _decommissioning.value = false
            }
        }
    }

    /**
     * Release the metka back to FREE (BOUND → FREE) with a mandatory reason.
     * Uses the current device's last_updated for optimistic concurrency; a
     * DEVICE_CONFLICT means it changed underneath — reload so the user retries.
     */
    fun unbind(reason: String, onDone: () -> Unit) {
        val device = (_state.value as? QrUiState.Success)?.result?.device ?: return
        val version = device.lastUpdated.orEmpty()
        viewModelScope.launch {
            _unbinding.value = true
            try {
                repository.unbind(qrId, version, reason)
                _message.value = "Метка отвязана"
                onDone()
                load()
            } catch (e: ApiException) {
                if (!shiftGate.trip(e) { unbind(reason, onDone) }) {
                    _message.value = when (e.code) {
                        "DEVICE_CONFLICT" -> "Устройство изменилось — данные обновлены, повторите"
                        "QR_UNBIND_ROLLED_BACK" -> "Не удалось отвязать, попробуйте ещё раз"
                        "QR_NOT_BOUND" -> "Метка уже не привязана"
                        else -> e.apiError?.userMessage ?: e.apiError?.message ?: e.message
                    }
                    if (e.code == "DEVICE_CONFLICT") load()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: "Не удалось отвязать метку"
            } finally {
                _unbinding.value = false
            }
        }
    }

    fun consumeMessage() = _message.update { null }

    fun load() {
        viewModelScope.launch {
            _state.value = QrUiState.Loading
            _state.value = try {
                val result = repository.lookup(qrId)
                scanHistory.record(qrId, result.device?.name)
                // Bound but the device wasn't embedded: resolve its name by id so
                // the screen shows a name, not a number.
                val fallbackName = if (result.device == null) {
                    result.qr.boundToDeviceId?.let { id ->
                        runCatching { deviceRepository.get(id).data.name }.getOrNull()
                    }
                } else {
                    null
                }
                QrUiState.Success(result, fallbackName)
            } catch (e: ApiException) {
                if (e.httpCode == 404) {
                    QrUiState.NotRegistered(qrId)
                } else {
                    QrUiState.Error(
                        e.apiError?.userMessage
                            ?: e.apiError?.message
                            ?: e.message
                            ?: "Не удалось получить данные QR",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                QrUiState.Error(e.message ?: "Не удалось получить данные QR")
            }
        }
    }
}
