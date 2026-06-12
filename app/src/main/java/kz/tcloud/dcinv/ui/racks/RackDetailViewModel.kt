package kz.tcloud.dcinv.ui.racks

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
import kz.tcloud.dcinv.data.network.dto.DeviceData
import kz.tcloud.dcinv.data.repository.DeviceRepository
import kz.tcloud.dcinv.data.repository.MetaRepository
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

/** One visual row of the elevation, top-to-bottom. */
sealed interface RackRowItem {
    /** A device block spanning [span] units, [topUnit] is its highest U. */
    data class DeviceBlock(val device: DeviceData, val topUnit: Int, val span: Int) : RackRowItem

    data class FreeUnit(val unit: Int) : RackRowItem
}

data class RackDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val rackName: String = "",
    val uHeight: Int = 0,
    val occupiedUnits: Int = 0,
    val rows: List<RackRowItem> = emptyList(),
    /** Devices assigned to the rack but without a mounted position. */
    val unplaced: List<DeviceData> = emptyList(),
)

/**
 * Phase-1 rack elevation built from `GET /devices/search?rack=N` (no dedicated
 * backend endpoint yet, so faces/reservations are not represented; a device
 * with unknown height renders as 1U).
 */
@HiltViewModel
class RackDetailViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val metaRepository: MetaRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val rackId: Int = checkNotNull(savedStateHandle["rackId"]) { "rackId nav arg missing" }

    private val _state = MutableStateFlow(RackDetailUiState())
    val state: StateFlow<RackDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val rack = metaRepository.racks(null).firstOrNull { it.id == rackId }
                val devices = deviceRepository.listByRack(rackId).map { it.data }
                val uHeight = rack?.uHeight ?: 42

                val placed = devices.filter { it.position != null }
                val unplaced = devices.filter { it.position == null }

                // Unit → device occupying it. NetBox: position = lowest unit,
                // the device grows upward by its u_height.
                val byUnit = HashMap<Int, DeviceData>()
                placed.forEach { d ->
                    val bottom = d.position!!
                    val span = (d.uHeight ?: 1).coerceAtLeast(1)
                    (bottom until bottom + span).forEach { u -> byUnit.putIfAbsent(u, d) }
                }

                val rows = mutableListOf<RackRowItem>()
                val rendered = HashSet<Int>()
                var u = uHeight
                while (u >= 1) {
                    val d = byUnit[u]
                    when {
                        d == null -> {
                            rows += RackRowItem.FreeUnit(u)
                            u--
                        }
                        d.id in rendered -> u--
                        else -> {
                            val bottom = d.position!!.coerceAtLeast(1)
                            rows += RackRowItem.DeviceBlock(d, topUnit = u, span = (u - bottom + 1).coerceAtLeast(1))
                            rendered += d.id
                            u = bottom - 1
                        }
                    }
                }

                _state.update {
                    it.copy(
                        loading = false,
                        rackName = rack?.name ?: "Стойка $rackId",
                        uHeight = uHeight,
                        occupiedUnits = byUnit.keys.count { unit -> unit in 1..uHeight },
                        rows = rows,
                        unplaced = unplaced,
                    )
                }
            } catch (e: ApiException) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = e.apiError?.userMessage ?: e.apiError?.message ?: e.message ?: "Ошибка",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }
}
