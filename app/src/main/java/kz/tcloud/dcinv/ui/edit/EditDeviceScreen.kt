package kz.tcloud.dcinv.ui.edit

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import kz.tcloud.dcinv.data.network.dto.FieldType
import kz.tcloud.dcinv.data.network.dto.FormField
import kz.tcloud.dcinv.ui.common.DropdownField
import kz.tcloud.dcinv.ui.common.Option
import kz.tcloud.dcinv.ui.common.ShiftRequiredDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDeviceScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditDeviceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.savedOk) { if (state.savedOk) onSaved() }
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
                title = { Text(state.deviceName.ifBlank { "Редактирование" }, fontWeight = FontWeight.SemiBold) },
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
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    state.fields.forEach { field ->
                        FieldEditor(
                            field = field,
                            value = state.values[field.key].orEmpty(),
                            statusOptions = state.statusOptions,
                            siteOptions = state.siteOptions,
                            rackOptions = state.rackOptions,
                            onChange = { viewModel.onValueChange(field.key, it) },
                        )
                    }
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.saving,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(if (state.saving) "Сохранение…" else "Сохранить")
                    }
                }
            }
        }
    }

    ShiftRequiredDialog(viewModel.shiftGate)

    if (state.conflict) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Устройство изменилось") },
            text = { Text("Устройство было изменено после того, как вы его открыли. Обновить данные и применить заново?") },
            confirmButton = {
                TextButton(onClick = viewModel::reloadAfterConflict) { Text("Обновить") }
            },
            dismissButton = {
                TextButton(onClick = onBack) { Text("Выйти") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldEditor(
    field: FormField,
    value: String,
    statusOptions: List<Option>,
    siteOptions: List<Option>,
    rackOptions: List<Option>,
    onChange: (String) -> Unit,
) {
    when (field.type) {
        FieldType.TEXT -> OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(field.label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FieldType.MULTILINE_TEXT -> OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(field.label) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        FieldType.INTEGER -> OutlinedTextField(
            value = value,
            onValueChange = { new -> onChange(new.filter(Char::isDigit)) },
            label = { Text(field.label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        FieldType.CHOICE, FieldType.REFERENCE -> {
            val options = when (field.key) {
                "status" -> statusOptions
                "site" -> siteOptions
                "rack" -> rackOptions
                else -> emptyList()
            }
            DropdownField(
                label = field.label,
                value = value,
                options = options,
                onChange = onChange,
            )
        }
        FieldType.BOOLEAN -> {
            // Not used by the MVP editable set, but handle generically.
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(field.label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
