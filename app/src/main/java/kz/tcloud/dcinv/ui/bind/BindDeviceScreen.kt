package kz.tcloud.dcinv.ui.bind

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.tcloud.dcinv.data.network.dto.DeviceData
import kz.tcloud.dcinv.data.network.dto.DeviceResponse
import kz.tcloud.dcinv.ui.common.ShiftRequiredDialog
import kz.tcloud.dcinv.ui.theme.DcInvTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BindDeviceScreen(
    onBack: () -> Unit,
    onBound: () -> Unit,
    viewModel: BindViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmTarget by remember { mutableStateOf<DeviceResponse?>(null) }

    LaunchedEffect(state.boundOk) { if (state.boundOk) onBound() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Выбор устройства", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Имя устройства") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = viewModel::search) {
                        Icon(Icons.Filled.Search, contentDescription = "Искать")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                modifier = Modifier.fillMaxWidth(),
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.searching -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    state.results.isEmpty() -> Text(
                        text = "Введите имя и нажмите поиск",
                        color = DcInvTheme.extra.secondaryText,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.results, key = { it.data.id }) { dev ->
                            DeviceRow(device = dev.data, onClick = { confirmTarget = dev })
                        }
                        if (state.hasMore) {
                            item {
                                androidx.compose.material3.OutlinedButton(
                                    onClick = viewModel::loadMore,
                                    enabled = !state.loadingMore,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (state.loadingMore) "Загрузка…" else "Загрузить ещё")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmTarget?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!state.binding) confirmTarget = null },
            title = { Text("Привязать QR?") },
            text = {
                Text("Метка будет привязана к устройству «${target.data.name}». Продолжить?")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.bind(target) },
                    enabled = !state.binding,
                ) { Text(if (state.binding) "Привязка…" else "Привязать") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { confirmTarget = null },
                    enabled = !state.binding,
                ) { Text("Отмена") }
            },
        )
    }

    ShiftRequiredDialog(viewModel.shiftGate)
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
                        text = "Уже привязан: $it",
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
