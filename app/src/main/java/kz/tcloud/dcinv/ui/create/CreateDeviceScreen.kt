package kz.tcloud.dcinv.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.tcloud.dcinv.ui.common.DropdownField
import kz.tcloud.dcinv.ui.common.ShiftRequiredDialog
import kz.tcloud.dcinv.ui.theme.DcInvTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDeviceScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: CreateDeviceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
                title = { Text("Новое устройство", fontWeight = FontWeight.SemiBold) },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.loadError != null -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Не удалось загрузить справочники", fontWeight = FontWeight.Bold)
                    Text(
                        state.loadError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DcInvTheme.extra.secondaryText,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Button(onClick = viewModel::load, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Повторить")
                    }
                }
                else -> CreateForm(state = state, viewModel = viewModel)
            }
        }
    }

    ShiftRequiredDialog(viewModel.shiftGate)
}

@Composable
private fun CreateForm(state: CreateUiState, viewModel: CreateDeviceViewModel) {
    val v = state.values
    // Once the device exists only the bind can be retried — freeze the form.
    val editable = !state.bindPending && !state.saving
    fun value(key: String) = v[key].orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "QR ${viewModel.qrId} будет привязан к созданному устройству.",
            style = MaterialTheme.typography.bodyMedium,
            color = DcInvTheme.extra.secondaryText,
        )

        OutlinedTextField(
            value = value("name"),
            onValueChange = { viewModel.onValueChange("name", it) },
            label = { Text("Название *") },
            singleLine = true,
            enabled = editable,
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownField(
            label = "Тип устройства *",
            value = value("device_type"),
            options = state.deviceTypeOptions,
            onChange = { viewModel.onValueChange("device_type", it) },
        )
        DropdownField(
            label = "Роль *",
            value = value("role"),
            options = state.roleOptions,
            onChange = { viewModel.onValueChange("role", it) },
        )
        DropdownField(
            label = "Площадка *",
            value = value("site"),
            options = state.siteOptions,
            onChange = { viewModel.onValueChange("site", it) },
        )
        DropdownField(
            label = "Статус *",
            value = value("status"),
            options = state.statusOptions,
            onChange = { viewModel.onValueChange("status", it) },
        )
        DropdownField(
            label = "Стойка",
            value = value("rack"),
            options = state.rackOptions,
            onChange = { viewModel.onValueChange("rack", it) },
        )
        OutlinedTextField(
            value = value("position"),
            onValueChange = { new -> viewModel.onValueChange("position", new.filter(Char::isDigit)) },
            label = { Text("Позиция (U)") },
            singleLine = true,
            enabled = editable,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = value("serial"),
            onValueChange = { viewModel.onValueChange("serial", it) },
            label = { Text("Серийный №") },
            singleLine = true,
            enabled = editable,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = value("asset_tag"),
            onValueChange = { viewModel.onValueChange("asset_tag", it) },
            label = { Text("Инв. номер") },
            singleLine = true,
            enabled = editable,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = value("comments"),
            onValueChange = { viewModel.onValueChange("comments", it) },
            label = { Text("Комментарии") },
            minLines = 3,
            enabled = editable,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = viewModel::submit,
            enabled = !state.saving,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(
                when {
                    state.saving -> "Сохранение…"
                    state.bindPending -> "Повторить привязку"
                    else -> "Создать и привязать"
                },
            )
        }
    }
}
