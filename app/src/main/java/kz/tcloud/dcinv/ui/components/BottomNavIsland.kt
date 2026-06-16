package kz.tcloud.dcinv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.tcloud.dcinv.ui.navigation.Routes
import kz.tcloud.dcinv.ui.theme.DcInvTheme

private data class IslandItem(val route: String, val icon: ImageVector, val label: String)

private val LeftItems = listOf(
    IslandItem(Routes.HOME, Icons.Filled.Home, "Главная"),
    IslandItem(Routes.RACKS, Icons.Filled.Dns, "Стойки"),
)
private val RightItems = listOf(
    IslandItem(Routes.DEVICES, Icons.Filled.Inventory2, "Устройства"),
    IslandItem(Routes.SETTINGS, Icons.Filled.Settings, "Настройки"),
)

/**
 * Floating bottom-nav "island": four top-level destinations around a raised
 * green scan button. Rendered above the NavHost only on top-level screens.
 */
@Composable
fun BottomNavIsland(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, DcInvTheme.extra.border),
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 10.dp)
                .fillMaxWidth()
                .height(68.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            ) {
                LeftItems.forEach { NavSlot(it, currentRoute, onNavigate) }
                // Reserve room under the raised scan button.
                Box(Modifier.width(64.dp))
                RightItems.forEach { NavSlot(it, currentRoute, onNavigate) }
            }
        }

        ScanButton(onScan)
    }
}

@Composable
private fun ScanButton(onScan: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Squash on press, spring back on release.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scanScale",
    )

    Surface(
        shape = CircleShape,
        color = DcInvTheme.extra.accent,
        shadowElevation = 8.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = 10.dp)
            .offset(y = (-22).dp)
            .size(60.dp)
            .scale(scale)
            .border(3.dp, MaterialTheme.colorScheme.background, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onScan),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.QrCodeScanner,
                contentDescription = "Сканировать",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun NavSlot(item: IslandItem, currentRoute: String?, onNavigate: (String) -> Unit) {
    val selected = currentRoute == item.route

    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else DcInvTheme.extra.secondaryText,
        animationSpec = tween(220),
        label = "tint",
    )
    val pillColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(220),
        label = "pill",
    )
    // Little bounce when a tab becomes active.
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "iconScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { if (!selected) onNavigate(item.route) }
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(pillColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = tint,
                modifier = Modifier.size(24.dp).scale(iconScale),
            )
        }
        Text(
            text = item.label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
