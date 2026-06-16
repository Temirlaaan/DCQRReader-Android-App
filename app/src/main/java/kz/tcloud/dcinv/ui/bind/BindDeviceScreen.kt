package kz.tcloud.dcinv.ui.bind

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
fun BindDeviceScreen(
    onBack: () -> Unit,
    onBound: () -> Unit,
    viewModel: BindViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickerState by viewModel.picker.state.collectAsStateWithLifecycle()
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
        AlertDialog(
            onDismissRequest = { if (!state.binding) confirmTarget = null },
            title = { Text("Привязать QR?") },
            text = { Text("Метка будет привязана к устройству «${target.data.name}». Продолжить?") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.bind(target) },
                    enabled = !state.binding,
                ) { Text(if (state.binding) "Привязка…" else "Привязать") }
            },
            dismissButton = {
                TextButton(onClick = { confirmTarget = null }, enabled = !state.binding) { Text("Отмена") }
            },
        )
    }

    ShiftRequiredDialog(viewModel.shiftGate)
}
