package kz.tcloud.dcinv.ui.qr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.tcloud.dcinv.data.network.dto.DeviceData
import kz.tcloud.dcinv.data.network.dto.QrInfo
import kz.tcloud.dcinv.data.network.dto.QrLookupResponse
import kz.tcloud.dcinv.data.network.dto.QrStatus
import kz.tcloud.dcinv.ui.common.ShiftRequiredDialog
import kz.tcloud.dcinv.ui.theme.DcInvTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrResultScreen(
    onBack: () -> Unit,
    onScanAgain: () -> Unit,
    onBind: (qrId: String) -> Unit,
    onCreate: (qrId: String) -> Unit,
    onEdit: (deviceId: Int) -> Unit,
    reloadSignal: Boolean = false,
    onReloadHandled: () -> Unit = {},
    viewModel: QrViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val commentSubmitting by viewModel.commentSubmitting.collectAsStateWithLifecycle()
    val decommissioning by viewModel.decommissioning.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var commentOpen by remember { mutableStateOf(false) }
    var decommissionOpen by remember { mutableStateOf(false) }
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
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Ещё")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Сканировать ещё") },
                                leadingIcon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
                                onClick = { menuOpen = false; onScanAgain() },
                            )
                            if (isBound) {
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

    ShiftRequiredDialog(viewModel.shiftGate)
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
private fun DeviceProfile(device: DeviceData?) {
    if (device == null) {
        CenteredInfo(title = "Устройство недоступно", body = "QR привязан, но данные устройства не получены.")
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Data header: name + status chip.
        Column {
            Text(device.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            device.deviceType?.let {
                Text(it.name, style = MaterialTheme.typography.bodyMedium, color = DcInvTheme.extra.secondaryText)
            }
            Spacer(Modifier.height(8.dp))
            StatusChip(device.status.label)
        }

        SectionCard(title = "Location", icon = Icons.Filled.LocationOn) {
            KvGrid(
                "Площадка" to device.site.name,
                "Стойка" to (device.rack?.name ?: "—"),
                "Позиция" to (device.position?.let { "U$it" } ?: "—"),
                "Высота" to (device.uHeight?.let { "${it}U" } ?: "—"),
            )
        }

        SectionCard(title = "Hardware", icon = Icons.Filled.Memory) {
            KvGrid(
                "Серийный №" to device.serial.ifBlank { "—" },
                "Инв. номер" to (device.assetTag ?: "—"),
                "Производитель" to (device.manufacturer?.name ?: "—"),
                "Роль" to (device.deviceRole?.name ?: "—"),
            )
        }

        SectionCard(title = "Network", icon = Icons.Filled.Lan) {
            KvGrid(
                "Primary IPv4" to (device.primaryIp4 ?: "—"),
                "Primary IPv6" to (device.primaryIp6 ?: "—"),
            )
        }

        device.comments.takeIf { it.isNotBlank() }?.let {
            SectionCard(title = "Comments", icon = Icons.Filled.Comment) {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Leave space above the fixed bottom bar.
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun StatusChip(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = DcInvTheme.extra.accent.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = DcInvTheme.extra.accent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                label,
                color = DcInvTheme.extra.accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, DcInvTheme.extra.border),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = DcInvTheme.extra.accent, modifier = Modifier.size(18.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Two-column key/value grid. */
@Composable
private fun KvGrid(vararg pairs: Pair<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        pairs.toList().chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { (k, v) ->
                    KvItem(k, v, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KvItem(key: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = key.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = DcInvTheme.extra.secondaryText,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun BottomActionBar(onComment: () -> Unit, onEdit: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onComment,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Icon(Icons.Filled.Comment, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Comment", modifier = Modifier.padding(start = 6.dp))
            }
            Button(
                onClick = onEdit,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(2f).height(50.dp),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Edit Device", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
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
