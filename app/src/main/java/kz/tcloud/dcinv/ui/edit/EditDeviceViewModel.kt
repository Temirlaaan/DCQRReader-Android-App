package kz.tcloud.dcinv.ui.edit

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
import kz.tcloud.dcinv.data.network.dto.FormField
import kz.tcloud.dcinv.data.repository.DeviceRepository
import kz.tcloud.dcinv.data.repository.MetaRepository
import kz.tcloud.dcinv.data.repository.SessionRepository
import kz.tcloud.dcinv.ui.common.Option
import kz.tcloud.dcinv.ui.common.ShiftGate
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

data class EditUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val savedOk: Boolean = false,
    val conflict: Boolean = false,
    val deviceName: String = "",
    val version: String = "",
    val fields: List<FormField> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    val statusOptions: List<Option> = emptyList(),
    val siteOptions: List<Option> = emptyList(),
    val rackOptions: List<Option> = emptyList(),
)

/**
 * Server-driven device edit. Fetches the editable field list from
 * `GET /meta/device-form` and the device's current values, renders fields by
 * type, then PATCHes only the changed ones with optimistic concurrency
 * (If-Unmodified-Since = version). Handles 409 VERSION_CONFLICT.
 */
@HiltViewModel
class EditDeviceViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val metaRepository: MetaRepository,
    sessionRepository: SessionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val deviceId: Int = checkNotNull(savedStateHandle["deviceId"]) { "deviceId nav arg missing" }

    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    val shiftGate = ShiftGate(sessionRepository, viewModelScope) { msg ->
        _state.update { it.copy(error = msg) }
    }

    // Snapshot of initial values to compute the diff at save time.
    private var initial: Map<String, String> = emptyMap()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, conflict = false) }
            try {
                val deviceResp = deviceRepository.get(deviceId)
                val form = metaRepository.deviceForm()
                val device = deviceResp.data
                val editable = form.fields.filter { it.key in SUPPORTED_KEYS }

                val values = buildMap {
                    put("name", device.name)
                    put("serial", device.serial)
                    put("asset_tag", device.assetTag ?: "")
                    put("comments", device.comments)
                    put("position", device.position?.toString() ?: "")
                    put("status", device.status.value)
                    put("site", device.site.id.toString())
                    put("rack", device.rack?.id?.toString() ?: "")
                }
                initial = values

                // Load choice/reference option sources only if such fields exist.
                val keys = editable.map { it.key }.toSet()
                val statusOptions = if ("status" in keys) {
                    metaRepository.statuses().map { Option(it.value, it.label) }
                } else emptyList()
                val siteOptions = if ("site" in keys) {
                    metaRepository.sites().map { Option(it.id.toString(), it.name) }
                } else emptyList()
                val rackOptions = if ("rack" in keys) {
                    metaRepository.racks(device.site.id).map { Option(it.id.toString(), it.name) }
                } else emptyList()

                _state.update {
                    it.copy(
                        loading = false,
                        deviceName = device.name,
                        version = deviceResp.version,
                        fields = editable,
                        values = values,
                        statusOptions = statusOptions,
                        siteOptions = siteOptions,
                        rackOptions = rackOptions,
                    )
                }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
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
                // Clear the rack selection if it's no longer valid for the new site.
                val rackStillValid = racks.any { r -> r.id == it.values["rack"] }
                it.copy(
                    rackOptions = racks,
                    values = if (rackStillValid) it.values else it.values + ("rack" to ""),
                )
            }
        }
    }

    fun save() {
        val s = _state.value
        val changed = s.values.filter { (k, v) -> initial[k] != v }
        if (changed.isEmpty()) {
            _state.update { it.copy(error = "Нет изменений") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val body = buildDeviceUpdateBody(changed)
                deviceRepository.update(deviceId, body, s.version)
                _state.update { it.copy(saving = false, savedOk = true) }
            } catch (e: ApiException) {
                when {
                    e.code == "VERSION_CONFLICT" ->
                        _state.update { it.copy(saving = false, conflict = true) }
                    shiftGate.trip(e) { save() } ->
                        _state.update { it.copy(saving = false) }
                    else ->
                        _state.update { it.copy(saving = false, error = e.userMessage()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message) }
            }
        }
    }

    /** Re-fetch fresh data after a conflict so the user can re-apply on top. */
    fun reloadAfterConflict() = load()

    fun consumeError() = _state.update { it.copy(error = null) }

    private fun ApiException.userMessage(): String =
        apiError?.userMessage ?: apiError?.message ?: message ?: "Ошибка"

    private companion object {
        val SUPPORTED_KEYS = setOf(
            "name", "serial", "asset_tag", "comments", "position", "status", "site", "rack",
        )
    }
}
