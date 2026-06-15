package kz.tcloud.dcinv.ui.racks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kz.tcloud.dcinv.data.network.ApiException
import kz.tcloud.dcinv.data.network.dto.MetaRack
import kz.tcloud.dcinv.data.network.dto.MetaSite
import kz.tcloud.dcinv.data.repository.MetaRepository
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

data class RacksUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val sites: List<MetaSite> = emptyList(),
    val selectedSiteId: Int? = null,
    val racks: List<MetaRack> = emptyList(),
)

@HiltViewModel
class RacksViewModel @Inject constructor(
    private val metaRepository: MetaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RacksUiState())
    val state: StateFlow<RacksUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val sites = metaRepository.sites()
                val siteId = _state.value.selectedSiteId ?: sites.firstOrNull()?.id
                val racks = siteId?.let { metaRepository.racks(it).forSite(it) }.orEmpty()
                _state.update {
                    it.copy(loading = false, sites = sites, selectedSiteId = siteId, racks = racks)
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

    fun selectSite(siteId: Int) {
        if (siteId == _state.value.selectedSiteId) return
        _state.update { it.copy(selectedSiteId = siteId) }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val racks = metaRepository.racks(siteId).forSite(siteId)
                _state.update { it.copy(loading = false, racks = racks) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    private fun ApiException.userMessage(): String =
        apiError?.userMessage ?: apiError?.message ?: message ?: "Ошибка"

    /**
     * Defensive site filter: the backend's `/meta/racks?site_id=` returned racks
     * from other sites, so we also filter client-side by [MetaRack.siteId].
     */
    private fun List<MetaRack>.forSite(siteId: Int) = filter { it.siteId == siteId }
}
