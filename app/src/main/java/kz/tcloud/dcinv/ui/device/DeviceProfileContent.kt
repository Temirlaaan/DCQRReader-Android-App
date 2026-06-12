package kz.tcloud.dcinv.ui.device

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.tcloud.dcinv.data.network.dto.DeviceData
import kz.tcloud.dcinv.ui.theme.DcInvTheme

/**
 * Full device profile (header, location, hardware, network, comments).
 * Shared by the QR result screen and the rack-elevation device screen.
 */
@Composable
fun DeviceProfile(device: DeviceData?) {
    if (device == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Устройство недоступно", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Данные устройства не получены.",
                style = MaterialTheme.typography.bodyMedium,
                color = DcInvTheme.extra.secondaryText,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
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

        // Leave space above a fixed bottom bar / nav island.
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
fun SectionCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
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
