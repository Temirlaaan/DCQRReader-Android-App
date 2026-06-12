package kz.tcloud.dcinv.ui.racks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.tcloud.dcinv.data.network.dto.DeviceData
import kz.tcloud.dcinv.ui.theme.DcInvTheme

/** Height of one rack unit on screen. */
private val UnitHeight = 28.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RackDetailScreen(
    onBack: () -> Unit,
    onOpenDevice: (deviceId: Int) -> Unit,
    viewModel: RackDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.rackName.ifBlank { "Стойка" }, fontWeight = FontWeight.SemiBold)
                        if (state.uHeight > 0) {
                            Text(
                                "${state.uHeight}U · занято ${state.occupiedUnits}",
                                style = MaterialTheme.typography.labelMedium,
                                color = DcInvTheme.extra.secondaryText,
                            )
                        }
                    }
                },
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
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Ошибка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        state.error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DcInvTheme.extra.secondaryText,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Button(onClick = viewModel::load, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Повторить")
                    }
                }
                else -> Elevation(state, onOpenDevice)
            }
        }
    }
}

@Composable
private fun Elevation(state: RackDetailUiState, onOpenDevice: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        state.rows.forEach { row ->
            when (row) {
                is RackRowItem.FreeUnit -> FreeUnitRow(row.unit)
                is RackRowItem.DeviceBlock -> DeviceBlockRow(row, onClick = { onOpenDevice(row.device.id) })
            }
        }

        if (state.unplaced.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                "Без позиции (${state.unplaced.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            state.unplaced.forEach { device ->
                UnplacedRow(device, onClick = { onOpenDevice(device.id) })
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun UnitGutter(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        color = DcInvTheme.extra.secondaryText,
        modifier = Modifier.width(30.dp),
    )
}

@Composable
private fun FreeUnitRow(unit: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(UnitHeight)) {
        UnitGutter("$unit")
        Box(
            modifier = Modifier
                .weight(1f)
                .height(UnitHeight - 4.dp)
                .border(1.dp, DcInvTheme.extra.border, RoundedCornerShape(6.dp)),
        )
    }
}

@Composable
private fun DeviceBlockRow(block: RackRowItem.DeviceBlock, onClick: () -> Unit) {
    val color = statusColor(block.device)
    Row(modifier = Modifier.height(UnitHeight * block.span)) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.height(UnitHeight * block.span).padding(vertical = 2.dp),
        ) {
            UnitGutter("${block.topUnit}")
            if (block.span > 1) UnitGutter("${block.topUnit - block.span + 1}")
        }
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(vertical = 2.dp)
                .background(color.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                .border(1.5.dp, color.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        block.device.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (block.span > 1) {
                        Text(
                            block.device.deviceType?.name ?: block.device.status.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = DcInvTheme.extra.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    "${block.span}U",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun UnplacedRow(device: DeviceData, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .border(1.dp, DcInvTheme.extra.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Icon(
            Icons.Filled.Inventory2,
            contentDescription = null,
            tint = DcInvTheme.extra.secondaryText,
            modifier = Modifier.size(18.dp),
        )
        Text(
            device.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        Text(
            device.status.label,
            style = MaterialTheme.typography.labelSmall,
            color = DcInvTheme.extra.secondaryText,
        )
    }
}

@Composable
private fun statusColor(device: DeviceData): Color = when (device.status.value) {
    "active" -> DcInvTheme.extra.accent
    "failed" -> MaterialTheme.colorScheme.error
    "offline" -> DcInvTheme.extra.secondaryText
    else -> MaterialTheme.colorScheme.primary
}
