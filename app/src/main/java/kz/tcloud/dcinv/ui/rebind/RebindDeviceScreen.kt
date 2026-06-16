package kz.tcloud.dcinv.ui.rebind

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.tcloud.dcinv.data.network.dto.DeviceResponse
import kz.tcloud.dcinv.ui.common.DevicePicker
import kz.tcloud.dcinv.ui.common.ShiftRequiredDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RebindDeviceScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: RebindViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickerState by viewModel.picker.state.collectAsStateWithLifecycle()
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
                "Выберите устройство, на которое нужно перенести метку. Текущая привязка будет снята.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            DevicePicker(
                state = pickerState,
                onQueryChange = viewModel.picker::onQueryChange,
                onSearch = viewModel.picker::search,
                onSelectSite = viewModel.picker::selectSite,
                onSelectRack = viewModel.picker::selectRack,
                onLoadMore = viewModel.picker::loadMore,
                onPick = { confirmTarget = it },
            )
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
