package kz.tcloud.dcinv.ui.rebind

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.tcloud.dcinv.data.network.dto.DeviceData
import kz.tcloud.dcinv.data.network.dto.DeviceResponse
import kz.tcloud.dcinv.ui.common.ShiftRequiredDialog
import kz.tcloud.dcinv.ui.theme.DcInvTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RebindDeviceScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: RebindViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmTarget by remember { mutableStateOf<DeviceResponse?>(null) }

    LaunchedEffect(state.doneOk) { if (state.doneOk) onDone() }
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
                title = { Text("Перепривязка метки", fontWeight = FontWeight.SemiBold) },
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
            Text(
                "Выберите устройство, на которое нужно перенести метку. " +
                    "Текущая привязка будет снята.",
                style = MaterialTheme.typography.bodyMedium,
                color = DcInvTheme.extra.secondaryText,
                modifier = Modifier.padding(bottom = 12.dp),
            )
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
                                OutlinedButton(
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
        RebindConfirmDialog(
            deviceName = target.data.name,
            rebinding = state.rebinding,
            onDismiss = { if (!state.rebinding) confirmTarget = null },
            onConfirm = { reason -> viewModel.rebind(target, reason) },
        )
    }

    ShiftRequiredDialog(viewModel.shiftGate)
}

@Composable
private fun RebindConfirmDialog(
    deviceName: String,
    rebinding: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Перепривязать метку?") },
        text = {
            Column {
                Text("Метка будет перенесена на устройство «$deviceName». Укажите причину — она попадёт в журнал.")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Причина") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.trim()) },
                enabled = reason.isNotBlank() && !rebinding,
            ) { Text(if (rebinding) "Перепривязка…" else "Перепривязать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !rebinding) { Text("Отмена") }
        },
    )
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
