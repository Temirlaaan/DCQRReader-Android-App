package kz.tcloud.dcinv.ui.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kz.tcloud.dcinv.data.network.ApiException
import kz.tcloud.dcinv.data.network.dto.DeviceData
import kz.tcloud.dcinv.data.network.dto.DeviceResponse
import kz.tcloud.dcinv.data.repository.DeviceRepository
import kz.tcloud.dcinv.data.repository.MetaRepository
import kz.tcloud.dcinv.ui.common.Option
import kz.tcloud.dcinv.ui.theme.DcInvTheme
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

data class DevicesUiState(
    val query: String = "",
    val sites: List<Option> = emptyList(),
    val racks: List<Option> = emptyList(),
    val selectedSiteId: String = "",
    val selectedRackId: String = "",
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val results: List<DeviceResponse> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val error: String? = null,
    val searched: Boolean = false,
)

/**
 * Read-only device browser (the "Устройства" tab). Server-side paginated: the
 * first page loads on search, more pages stream in as the list nears its end —
 * never the whole DC at once.
 */
@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val metaRepository: MetaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DevicesUiState())
    val state: StateFlow<DevicesUiState> = _state.asStateFlow()

    init {
        loadSites()
    }

    private fun loadSites() {
        viewModelScope.launch {
            runCatching { metaRepository.sites() }.onSuccess { sites ->
                _state.update {
                    it.copy(sites = listOf(ALL_SITES) + sites.map { s -> Option(s.id.toString(), s.name) })
                }
            }
        }
    }

    fun onQueryChange(q: String) = _state.update { it.copy(query = q) }

    fun selectSite(siteId: String) {
        _state.update { it.copy(selectedSiteId = siteId, selectedRackId = "", racks = emptyList()) }
        val id = siteId.toIntOrNull()
        if (id != null) {
            viewModelScope.launch {
                runCatching { metaRepository.racks(id) }.onSuccess { racks ->
                    _state.update {
                        it.copy(racks = listOf(ALL_RACKS) + racks.filter { r -> r.siteId == id }
                            .map { r -> Option(r.id.toString(), r.name) })
                    }
                }
            }
            search()
        }
    }

    fun selectRack(rackId: String) {
        _state.update { it.copy(selectedRackId = rackId) }
        search()
    }

    fun search() {
        val s = _state.value
        val q = s.query.trim()
        // Don't list the whole DC: require a query or at least a site.
        if (q.isBlank() && s.selectedSiteId.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, searched = true) }
            try {
                val resp = deviceRepository.search(
                    query = q,
                    siteId = s.selectedSiteId.toIntOrNull(),
                    rackId = s.selectedRackId.toIntOrNull(),
                    page = 1,
                )
                _state.update {
                    it.copy(loading = false, results = resp.results, page = 1, hasMore = resp.hasMore)
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

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.loading || !s.hasMore) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val next = s.page + 1
                val resp = deviceRepository.search(
                    query = s.query.trim(),
                    siteId = s.selectedSiteId.toIntOrNull(),
                    rackId = s.selectedRackId.toIntOrNull(),
                    page = next,
                )
                _state.update {
                    it.copy(
                        loadingMore = false,
                        results = it.results + resp.results,
                        page = next,
                        hasMore = resp.hasMore,
                    )
                }
            } catch (e: ApiException) {
                _state.update { it.copy(loadingMore = false, error = e.userMessage()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false, error = e.message) }
            }
        }
    }

    private fun ApiException.userMessage(): String =
        apiError?.userMessage ?: apiError?.message ?: message ?: "Ошибка"

    private companion object {
        val ALL_SITES = Option("", "Все площадки")
        val ALL_RACKS = Option("", "Все стойки")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onOpenDevice: (deviceId: Int) -> Unit,
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Infinite scroll: prefetch the next page when within 5 items of the end.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.results.size - 5
        }
    }
    LaunchedEffect(shouldLoadMore, state.results.size) {
        if (shouldLoadMore && state.hasMore) viewModel.loadMore()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Устройства", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                FilterChipDropdown(
                    placeholder = "Площадка",
                    selectedId = state.selectedSiteId,
                    options = state.sites,
                    onSelect = viewModel::selectSite,
                    modifier = Modifier.weight(1f),
                )
                FilterChipDropdown(
                    placeholder = "Стойка",
                    selectedId = state.selectedRackId,
                    options = state.racks,
                    enabled = state.selectedSiteId.isNotBlank(),
                    onSelect = viewModel::selectRack,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Имя, серийный или инв. номер") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = viewModel::search) {
                        Icon(Icons.Filled.Search, contentDescription = "Искать")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    !state.searched -> Hint("Выберите площадку/стойку или введите запрос")
                    state.results.isEmpty() -> Hint("Ничего не найдено")
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.results, key = { it.data.id }) { dev ->
                            DeviceRow(device = dev.data, onClick = { onOpenDevice(dev.data.id) })
                        }
                        if (state.loadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact filter as a chip that shows the selection (or a placeholder) and opens
 * a dropdown — lighter than a full text field, and highlights when active.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipDropdown(
    placeholder: String,
    selectedId: String,
    options: List<Option>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId && it.id.isNotBlank() }
    val active = selected != null

    Box(modifier = modifier) {
        FilterChip(
            selected = active,
            enabled = enabled,
            onClick = { expanded = true },
            label = {
                Text(
                    selected?.label ?: placeholder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedLabelColor = MaterialTheme.colorScheme.primary,
                selectedTrailingIconColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.label) },
                    onClick = {
                        expanded = false
                        onSelect(opt.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = DcInvTheme.extra.secondaryText, modifier = Modifier.padding(24.dp))
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
            Icon(
                Icons.Filled.Dns,
                contentDescription = null,
                tint = DcInvTheme.extra.accent,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    device.name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(device.site.name)
                        device.rack?.let { append(" · ${it.name}") }
                        device.position?.let { append(" · U$it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = DcInvTheme.extra.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                device.qrId?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = DcInvTheme.extra.secondaryText,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = DcInvTheme.extra.secondaryText,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
