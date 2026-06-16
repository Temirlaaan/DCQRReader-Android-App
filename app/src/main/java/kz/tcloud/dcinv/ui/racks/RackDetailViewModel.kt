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
import kz.tcloud.dcinv.data.network.userMessageOrNull
import kz.tcloud.dcinv.data.network.dto.ElevationDevice
import kz.tcloud.dcinv.data.network.dto.RackElevationResponse
import kz.tcloud.dcinv.data.repository.RackRepository
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

enum class RackFace(val api: String, val label: String) {
    FRONT("front", "Перёд"),
    REAR("rear", "Зад"),
}

/** One visual row of the elevation, top-to-bottom. */
sealed interface RackRowItem {
    data class DeviceBlock(val device: ElevationDevice, val topUnit: Int, val span: Int) : RackRowItem
    data class ReservedUnit(val unit: Int, val description: String?) : RackRowItem
    data class FreeUnit(val unit: Int) : RackRowItem
}

data class RackDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val rackName: String = "",
    val uHeight: Int = 0,
    val occupiedUnits: Int = 0,
    val unpositionedCount: Int = 0,
    val face: RackFace = RackFace.FRONT,
    val hasRear: Boolean = false,
    val rows: List<RackRowItem> = emptyList(),
)

/**
 * Rack elevation from `GET /racks/{id}/elevation` (phase 2): faces, reservations
 * and device heights come straight from the backend. The face toggle re-projects
 * the cached response without a refetch.
 */
@HiltViewModel
class RackDetailViewModel @Inject constructor(
    private val rackRepository: RackRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val rackId: Int = checkNotNull(savedStateHandle["rackId"]) { "rackId nav arg missing" }

    private val _state = MutableStateFlow(RackDetailUiState())
    val state: StateFlow<RackDetailUiState> = _state.asStateFlow()

    private var cached: RackElevationResponse? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val resp = rackRepository.elevation(rackId)
                cached = resp
                _state.update {
                    it.copy(
                        loading = false,
                        rackName = resp.rack.name,
                        uHeight = resp.rack.uHeight,
                        occupiedUnits = resp.occupiedUnits,
                        unpositionedCount = resp.unpositionedCount,
                        hasRear = resp.devices.any { d -> d.face.equals("rear", ignoreCase = true) },
                        rows = buildRows(resp, it.face),
                    )
                }
            } catch (e: ApiException) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = e.userMessageOrNull() ?: "Нет связи с сервером. Проверьте VPN",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun selectFace(face: RackFace) {
        if (face == _state.value.face) return
        val resp = cached ?: return
        _state.update { it.copy(face = face, rows = buildRows(resp, face)) }
    }

    private fun buildRows(resp: RackElevationResponse, face: RackFace): List<RackRowItem> {
        val uHeight = resp.rack.uHeight.coerceAtLeast(1)

        // Devices on this face (face null → treated as front, the common case).
        val faceDevices = resp.devices.filter { d ->
            val f = d.face?.lowercase() ?: "front"
            f == face.api
        }
        val byUnit = HashMap<Int, ElevationDevice>()
        faceDevices.forEach { d ->
            val bottom = (d.position ?: return@forEach).coerceAtLeast(1)
            val span = d.uHeight.coerceAtLeast(1)
            (bottom until bottom + span).forEach { u -> byUnit.putIfAbsent(u, d) }
        }

        val reservedUnit = HashMap<Int, String?>()
        resp.reservations.forEach { r -> r.units.forEach { u -> reservedUnit.putIfAbsent(u, r.description) } }

        val rows = mutableListOf<RackRowItem>()
        val rendered = HashSet<Int>()
        var u = uHeight
        while (u >= 1) {
            val d = byUnit[u]
            when {
                d != null && d.id in rendered -> u--
                d != null -> {
                    val bottom = (d.position ?: u).coerceAtLeast(1)
                    rows += RackRowItem.DeviceBlock(d, topUnit = u, span = (u - bottom + 1).coerceAtLeast(1))
                    rendered += d.id
                    u = bottom - 1
                }
                reservedUnit.containsKey(u) -> {
                    rows += RackRowItem.ReservedUnit(u, reservedUnit[u]); u--
                }
                else -> {
                    rows += RackRowItem.FreeUnit(u); u--
                }
            }
        }
        return rows
    }
}
