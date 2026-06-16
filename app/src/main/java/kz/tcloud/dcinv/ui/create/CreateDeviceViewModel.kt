package kz.tcloud.dcinv.ui.create

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
import kz.tcloud.dcinv.data.network.dto.DeviceCreateRequest
import kz.tcloud.dcinv.data.network.dto.DeviceResponse
import kz.tcloud.dcinv.data.repository.DeviceRepository
import kz.tcloud.dcinv.data.repository.MetaRepository
import kz.tcloud.dcinv.data.repository.QrRepository
import kz.tcloud.dcinv.data.repository.SessionRepository
import kz.tcloud.dcinv.ui.common.Option
import kz.tcloud.dcinv.ui.common.ShiftGate
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

data class CreateUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val doneOk: Boolean = false,
    /** Device already created, only the QR bind failed — retry skips the create. */
    val bindPending: Boolean = false,
    val values: Map<String, String> = emptyMap(),
    val deviceTypeOptions: List<Option> = emptyList(),
    val roleOptions: List<Option> = emptyList(),
    val siteOptions: List<Option> = emptyList(),
    val statusOptions: List<Option> = emptyList(),
    val rackOptions: List<Option> = emptyList(),
)

/**
 * Create a new device for a free QR: fill the form, `POST /devices/`, then bind
 * the QR to the new device. The field set mirrors the backend's device-create
 * form (edit fields + required device_type/role, immutable after creation);
 * it's kept local because the generic FormField skeleton doesn't carry the
 * reference-resolution info these two extra fields need.
 */
@HiltViewModel
class CreateDeviceViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val qrRepository: QrRepository,
    private val metaRepository: MetaRepository,
    sessionRepository: SessionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val qrId: String = checkNotNull(savedStateHandle["qrId"]) { "qrId nav arg missing" }

    private val _state = MutableStateFlow(CreateUiState())
    val state: StateFlow<CreateUiState> = _state.asStateFlow()

    val shiftGate = ShiftGate(sessionRepository, viewModelScope) { msg ->
        _state.update { it.copy(error = msg) }
    }

    /** Set once `POST /devices/` succeeds so retries only redo the bind. */
    private var createdDevice: DeviceResponse? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null) }
            try {
                val deviceTypes = metaRepository.deviceTypes()
                    .map { Option(it.id.toString(), "${it.manufacturerName} ${it.model}") }
                val roles = metaRepository.roles().map { Option(it.id.toString(), it.name) }
                val sites = metaRepository.sites().map { Option(it.id.toString(), it.name) }
                val statuses = metaRepository.statuses().map { Option(it.value, it.label) }
                _state.update {
                    it.copy(
                        loading = false,
                        deviceTypeOptions = deviceTypes,
                        roleOptions = roles,
                        siteOptions = sites,
                        statusOptions = statuses,
                        values = it.values.ifEmpty {
                            // Sensible default: "active" when the backend offers it.
                            val active = statuses.firstOrNull { s -> s.id == "active" }
                            if (active != null) mapOf("status" to active.id) else emptyMap()
                        },
                    )
                }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, loadError = e.userMessage() ?: OFFLINE) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadError = e.message) }
            }
        }
    }

    fun onValueChange(key: String, value: String) {
        _state.update { it.copy(values = it.values + (key to value)) }
        if (key == "site") reloadRacks(value.toIntOrNull())
    }

    private fun reloadRacks(siteId: Int?) {
        viewModelScope.launch {
            val racks = runCatching {
                metaRepository.racks(siteId).map { Option(it.id.toString(), it.name) }
            }.getOrDefault(emptyList())
            _state.update {
                val rackStillValid = racks.any { r -> r.id == it.values["rack"] }
                it.copy(
                    rackOptions = racks,
                    values = if (rackStillValid) it.values else it.values + ("rack" to ""),
                )
            }
        }
    }

    fun submit() {
        val existing = createdDevice
        val request = if (existing == null) buildRequest() ?: return else null
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val device = existing
                    ?: deviceRepository.create(checkNotNull(request)).also { createdDevice = it }
                qrRepository.bind(qrId, device.data.id, device.version)
                _state.update { it.copy(saving = false, doneOk = true) }
            } catch (e: ApiException) {
                if (shiftGate.trip(e) { submit() }) {
                    _state.update { it.copy(saving = false, bindPending = createdDevice != null) }
                } else {
                    // Network errors → no snackbar (banner covers it); bindPending UI
                    // already conveys "created, bind didn't go through".
                    val prefix = if (createdDevice != null) "Устройство создано, но привязка не удалась: " else ""
                    val msg = e.userMessage()?.let { prefix + it }
                    _state.update {
                        it.copy(saving = false, bindPending = createdDevice != null, error = msg)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val prefix = if (createdDevice != null) "Устройство создано, но привязка не удалась: " else ""
                _state.update {
                    it.copy(saving = false, bindPending = createdDevice != null, error = prefix + (e.message ?: "Ошибка"))
                }
            }
        }
    }

    /** Validates required fields; null (and an error message) when incomplete. */
    private fun buildRequest(): DeviceCreateRequest? {
        val v = _state.value.values
        val name = v["name"]?.trim().orEmpty()
        val deviceTypeId = v["device_type"]?.toIntOrNull()
        val roleId = v["role"]?.toIntOrNull()
        val siteId = v["site"]?.toIntOrNull()
        val status = v["status"].orEmpty()
        if (name.isBlank() || deviceTypeId == null || roleId == null || siteId == null || status.isBlank()) {
            _state.update { it.copy(error = "Заполните обязательные поля: название, тип, роль, площадка, статус") }
            return null
        }
        return DeviceCreateRequest(
            deviceTypeId = deviceTypeId,
            roleId = roleId,
            siteId = siteId,
            status = status,
            name = name,
            rackId = v["rack"]?.toIntOrNull(),
            position = v["position"]?.toIntOrNull(),
            serial = v["serial"]?.trim()?.ifBlank { null },
            assetTag = v["asset_tag"]?.trim()?.ifBlank { null },
            comments = v["comments"]?.trim()?.ifBlank { null },
        )
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    private fun ApiException.userMessage(): String? = userMessageOrNull()

    private companion object {
        const val OFFLINE = "Нет связи с сервером. Проверьте VPN"
    }
}
