package kz.tcloud.dcinv.ui.device

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kz.tcloud.dcinv.data.network.ApiException
import kz.tcloud.dcinv.data.network.dto.DeviceData
import kz.tcloud.dcinv.data.repository.DeviceRepository
import kz.tcloud.dcinv.ui.theme.DcInvTheme
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

sealed interface DeviceDetailState {
    data object Loading : DeviceDetailState
    data class Loaded(val device: DeviceData) : DeviceDetailState
    data class Error(val message: String) : DeviceDetailState
}

@HiltViewModel
class DeviceDetailViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val deviceId: Int = checkNotNull(savedStateHandle["deviceId"]) { "deviceId nav arg missing" }

    private val _state = MutableStateFlow<DeviceDetailState>(DeviceDetailState.Loading)
    val state: StateFlow<DeviceDetailState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DeviceDetailState.Loading
            _state.value = try {
                DeviceDetailState.Loaded(deviceRepository.get(deviceId).data)
            } catch (e: ApiException) {
                DeviceDetailState.Error(
                    e.apiError?.userMessage ?: e.apiError?.message ?: e.message ?: "Ошибка",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DeviceDetailState.Error(e.message ?: "Ошибка")
            }
        }
    }
}

/** Standalone device profile, reached from the rack elevation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    onBack: () -> Unit,
    onEdit: (deviceId: Int) -> Unit,
    reloadSignal: Boolean = false,
    onReloadHandled: () -> Unit = {},
    viewModel: DeviceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Refresh after the edit screen reports a save.
    LaunchedEffect(reloadSignal) {
        if (reloadSignal) {
            viewModel.load()
            onReloadHandled()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (state as? DeviceDetailState.Loaded)?.device?.name ?: "Устройство",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(viewModel.deviceId) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Редактировать")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                DeviceDetailState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is DeviceDetailState.Error -> androidx.compose.foundation.layout.Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Ошибка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DcInvTheme.extra.secondaryText,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Button(onClick = viewModel::load, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Повторить")
                    }
                }
                is DeviceDetailState.Loaded -> DeviceProfile(s.device)
            }
        }
    }
}
