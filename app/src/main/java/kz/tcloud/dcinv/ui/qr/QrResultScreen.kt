package kz.tcloud.dcinv.ui.qr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.tcloud.dcinv.data.network.dto.QrLookupResponse
import kz.tcloud.dcinv.data.network.dto.QrStatus
import kz.tcloud.dcinv.ui.common.ShiftRequiredDialog
import kz.tcloud.dcinv.ui.device.DeviceProfile
import kz.tcloud.dcinv.ui.device.SectionCard
import kz.tcloud.dcinv.ui.theme.DcInvTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrResultScreen(
    onBack: () -> Unit,
    onScanAgain: () -> Unit,
    onBind: (qrId: String) -> Unit,
    onCreate: (qrId: String) -> Unit,
    onRebind: (qrId: String) -> Unit,
    onEdit: (deviceId: Int) -> Unit,
    reloadSignal: Boolean = false,
    onReloadHandled: () -> Unit = {},
    viewModel: QrViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val commentSubmitting by viewModel.commentSubmitting.collectAsStateWithLifecycle()
    val decommissioning by viewModel.decommissioning.collectAsStateWithLifecycle()
    val unbinding by viewModel.unbinding.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var commentOpen by remember { mutableStateOf(false) }
    var decommissionOpen by remember { mutableStateOf(false) }
    var unbindOpen by remember { mutableStateOf(false) }
    val isBound = (state as? QrUiState.Success)?.result?.qr?.status == QrStatus.BOUND

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // Reload only when an edit reported a save (nav result), not on every resume.
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
                title = { Text(viewModel.qrId, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onScanAgain) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Сканировать")
                    }
                    // Overflow only carries bound-device actions; hide it otherwise.
                    if (isBound) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Ещё")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Перепривязать метку") },
                                    leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
                                    onClick = { menuOpen = false; onRebind(viewModel.qrId) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Отвязать метку") },
                                    leadingIcon = { Icon(Icons.Filled.LinkOff, contentDescription = null) },
                                    onClick = { menuOpen = false; unbindOpen = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Списать устройство", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = { menuOpen = false; decommissionOpen = true },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isBound) {
                BottomActionBar(
                    onComment = { commentOpen = true },
                    onEdit = { viewModel.boundDeviceId?.let(onEdit) },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                QrUiState.Loading -> CenteredProgress()
                is QrUiState.NotRegistered -> CenteredInfo(
                    title = "QR не зарегистрирован",
                    body = "${s.qrId}\nВозможно, он создан другой системой или поддельный.",
                )
                is QrUiState.Error -> CenteredInfo(
                    title = "Ошибка",
                    body = s.message,
                    onRetry = viewModel::load,
                )
                is QrUiState.Success -> QrContent(
                    result = s.result,
                    onBind = { onBind(viewModel.qrId) },
                    onCreate = { onCreate(viewModel.qrId) },
                )
            }
        }
    }

    if (commentOpen) {
        CommentDialog(
            submitting = commentSubmitting,
            onDismiss = { commentOpen = false },
            onSubmit = { text -> viewModel.submitComment(text) { commentOpen = false } },
        )
    }

    if (decommissionOpen) {
        DecommissionDialog(
            submitting = decommissioning,
            onDismiss = { decommissionOpen = false },
            onConfirm = { reason -> viewModel.decommission(reason) { decommissionOpen = false } },
        )
    }

    if (unbindOpen) {
        ReasonDialog(
            title = "Отвязать метку?",
            body = "Метка станет свободной и её можно будет привязать к другому устройству. " +
                "Укажите причину — она попадёт в журнал.",
            confirmText = "Отвязать",
            submittingText = "Отвязка…",
            submitting = unbinding,
            onDismiss = { unbindOpen = false },
            onConfirm = { reason -> viewModel.unbind(reason) { unbindOpen = false } },
        )
    }

    ShiftRequiredDialog(viewModel.shiftGate)
}

/** Generic confirm dialog requiring a non-blank reason (audited actions). */
@Composable
private fun ReasonDialog(
    title: String,
    body: String,
    confirmText: String,
    submittingText: String,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                Text(body)
                androidx.compose.material3.OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Причина") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(reason.trim()) },
                enabled = reason.isNotBlank() && !submitting,
            ) { Text(if (submitting) submittingText else confirmText) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss, enabled = !submitting) { Text("Отмена") }
        },
    )
}

@Composable
private fun DecommissionDialog(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Списать устройство?") },
        text = {
            Column {
                Text(
                    "QR-метка будет погашена, статус устройства в NetBox изменится " +
                        "на Decommissioning. Действие доступно только администраторам.",
                )
                androidx.compose.material3.OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Причина (необязательно)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(reason.trim()) },
                enabled = !submitting,
            ) {
                Text(
                    if (submitting) "Списание…" else "Списать",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss, enabled = !submitting) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun CommentDialog(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Добавить комментарий") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Текст") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onSubmit(text.trim()) },
                enabled = text.isNotBlank() && !submitting,
            ) { Text(if (submitting) "Отправка…" else "Добавить") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss, enabled = !submitting) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun QrContent(result: QrLookupResponse, onBind: () -> Unit, onCreate: () -> Unit) {
    when (result.qr.status) {
        QrStatus.FREE -> FreeQr(onBind = onBind, onCreate = onCreate)
        QrStatus.BOUND -> DeviceProfile(result.device)
        QrStatus.RETIRED -> CenteredInfo(
            title = "QR списан",
            body = buildString {
                append("Списан: ${result.qr.retiredAt ?: "—"}")
                result.qr.retiredReason?.let { append("\nПричина: $it") }
            },
        )
    }
}

@Composable
private fun FreeQr(onBind: () -> Unit, onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        SectionCard(title = "Свободный QR", icon = Icons.Filled.QrCodeScanner) {
            Text(
                "Метка не привязана. Привяжите её к существующему устройству " +
                    "или создайте новое устройство.",
                style = MaterialTheme.typography.bodyMedium,
                color = DcInvTheme.extra.secondaryText,
            )
        }
        Button(
            onClick = onBind,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) { Text("Привязать к устройству") }
        OutlinedButton(
            onClick = onCreate,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            Icon(Icons.Filled.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Создать новое устройство", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun BottomActionBar(onComment: () -> Unit, onEdit: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Icon-only: the label never fit on narrow screens.
            OutlinedButton(
                onClick = onComment,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(50.dp),
            ) {
                Icon(Icons.Filled.Comment, contentDescription = "Комментарий", modifier = Modifier.size(20.dp))
            }
            Button(
                onClick = onEdit,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Редактировать", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun CenteredInfo(title: String, body: String, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = DcInvTheme.extra.secondaryText,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("Повторить") }
        }
    }
}

@Composable
private fun CenteredProgress() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
