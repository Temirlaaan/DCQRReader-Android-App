package kz.tcloud.dcinv.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kz.tcloud.dcinv.data.network.ApiException
import kz.tcloud.dcinv.data.network.userMessageOrNull
import kz.tcloud.dcinv.data.network.dto.DeviceData
import kz.tcloud.dcinv.data.network.dto.DeviceResponse
import kz.tcloud.dcinv.data.prefs.PickerPreferences
import kz.tcloud.dcinv.data.repository.DeviceRepository
import kz.tcloud.dcinv.data.repository.MetaRepository
import kz.tcloud.dcinv.ui.theme.DcInvTheme
import kotlin.coroutines.cancellation.CancellationException

data class DevicePickerState(
    val query: String = "",
    val sites: List<Option> = emptyList(),
    val racks: List<Option> = emptyList(),
    val selectedSiteId: String = "",
    val selectedRackId: String = "",
    val searching: Boolean = false,
    val results: List<DeviceResponse> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val searched: Boolean = false,
)

/**
 * Shared device search/browse used by bind and rebind. The engineer is usually
 * standing at a rack and doesn't know the device name, so picking a site → rack
 * lists that rack's devices with no text query at all (browse mode); a name
 * query still works and narrows within the chosen filters.
 */
class DevicePickerEngine(
    private val metaRepository: MetaRepository,
    private val deviceRepository: DeviceRepository,
    private val scope: CoroutineScope,
    private val prefs: PickerPreferences,
    private val onError: (String?) -> Unit,
) {
    private val _state = MutableStateFlow(DevicePickerState())
    val state: StateFlow<DevicePickerState> = _state.asStateFlow()

    fun loadSites() {
        scope.launch {
            runCatching { metaRepository.sites() }.onSuccess { sites ->
                _state.update {
                    it.copy(sites = listOf(ALL_SITES) + sites.map { s -> Option(s.id.toString(), s.name) })
                }
                // Reopen on the rack the engineer last worked.
                val lastSite = prefs.lastSiteId
                if (lastSite.isNotBlank() && sites.any { s -> s.id.toString() == lastSite }) {
                    restoreSite(lastSite, prefs.lastRackId)
                }
            }
        }
    }

    private fun restoreSite(siteId: String, rackId: String) {
        val id = siteId.toIntOrNull() ?: return
        _state.update { it.copy(selectedSiteId = siteId) }
        scope.launch {
            runCatching { metaRepository.racks(id) }.onSuccess { racks ->
                val options = racks.filter { r -> r.siteId == id }.map { r -> Option(r.id.toString(), r.name) }
                val validRack = if (options.any { it.id == rackId }) rackId else ""
                _state.update { it.copy(racks = listOf(ALL_RACKS) + options, selectedRackId = validRack) }
                search()
            }
        }
    }

    fun onQueryChange(q: String) = _state.update { it.copy(query = q) }

    fun selectSite(siteId: String) {
        _state.update { it.copy(selectedSiteId = siteId, selectedRackId = "", racks = emptyList()) }
        prefs.lastSiteId = siteId
        prefs.lastRackId = ""
        val id = siteId.toIntOrNull()
        if (id != null) {
            scope.launch {
                runCatching { metaRepository.racks(id) }.onSuccess { racks ->
                    _state.update {
                        it.copy(racks = listOf(ALL_RACKS) + racks.filter { r -> r.siteId == id }
                            .map { r -> Option(r.id.toString(), r.name) })
                    }
                }
            }
        }
        if (id != null) search()
    }

    fun selectRack(rackId: String) {
        _state.update { it.copy(selectedRackId = rackId) }
        prefs.lastRackId = rackId
        search()
    }

    fun search() {
        val s = _state.value
        val q = s.query.trim()
        // Avoid listing the whole DC: require a query or at least a site filter.
        if (q.isBlank() && s.selectedSiteId.isBlank()) return
        scope.launch {
            _state.update { it.copy(searching = true, searched = true) }
            onError(null)
            try {
                val resp = deviceRepository.search(
                    query = q,
                    siteId = s.selectedSiteId.toIntOrNull(),
                    rackId = s.selectedRackId.toIntOrNull(),
                    page = 1,
                )
                _state.update { it.copy(searching = false, results = resp.results, page = 1, hasMore = resp.hasMore) }
            } catch (e: ApiException) {
                _state.update { it.copy(searching = false) }; onError(e.userMessage())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(searching = false) }; onError(e.message)
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || !s.hasMore) return
        scope.launch {
            _state.update { it.copy(loadingMore = true) }
            onError(null)
            try {
                val next = s.page + 1
                val resp = deviceRepository.search(
                    query = s.query.trim(),
                    siteId = s.selectedSiteId.toIntOrNull(),
                    rackId = s.selectedRackId.toIntOrNull(),
                    page = next,
                )
                _state.update {
                    it.copy(loadingMore = false, results = it.results + resp.results, page = next, hasMore = resp.hasMore)
                }
            } catch (e: ApiException) {
                _state.update { it.copy(loadingMore = false) }; onError(e.userMessage())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false) }; onError(e.message)
            }
        }
    }

    private fun ApiException.userMessage(): String? = userMessageOrNull()

    private companion object {
        val ALL_SITES = Option("", "Все площадки")
        val ALL_RACKS = Option("", "Все стойки")
    }
}

@Composable
fun DevicePicker(
    state: DevicePickerState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectSite: (String) -> Unit,
    onSelectRack: (String) -> Unit,
    onLoadMore: () -> Unit,
    onPick: (DeviceResponse) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) {
                DropdownField(
                    label = "Площадка",
                    value = state.selectedSiteId,
                    options = state.sites,
                    onChange = onSelectSite,
                )
            }
            Box(Modifier.weight(1f)) {
                DropdownField(
                    label = "Стойка",
                    value = state.selectedRackId,
                    options = state.racks,
                    onChange = onSelectRack,
                )
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text("Имя устройства (необязательно)") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "Искать") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.searching -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                !state.searched -> Text(
                    "Выберите площадку и стойку — устройства появятся списком, " +
                        "или введите имя для поиска.",
                    color = DcInvTheme.extra.secondaryText,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                state.results.isEmpty() -> Text(
                    "Ничего не найдено",
                    color = DcInvTheme.extra.secondaryText,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.results, key = { it.data.id }) { dev ->
                        DeviceRow(device = dev.data, onClick = { onPick(dev) })
                    }
                    if (state.hasMore) {
                        item {
                            OutlinedButton(
                                onClick = onLoadMore,
                                enabled = !state.loadingMore,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (state.loadingMore) "Загрузка…" else "Загрузить ещё") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: DeviceData, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, DcInvTheme.extra.border),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = buildString {
                        append(device.site.name)
                        device.rack?.let { append(" · ${it.name}") }
                        device.position?.let { append(" · U$it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = DcInvTheme.extra.secondaryText,
                )
                device.qrId?.let {
                    Text(
                        text = "Уже есть метка: $it",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(device.status.label, style = MaterialTheme.typography.labelMedium, color = DcInvTheme.extra.secondaryText)
        }
    }
}
