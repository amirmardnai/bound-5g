package com.app.bound.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.bound.network.CellTower
import com.app.bound.network.MTKBandResolver
import com.app.bound.network.ShizukuBandManager
import com.app.bound.network.TelemetryEngine
import com.app.bound.ui.components.bouncyClickable

@Composable
fun ScannerScreen(
    telemetryEngine: TelemetryEngine,
    shizukuManager: ShizukuBandManager,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cellularState by telemetryEngine.cellularState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Column {
                    Text(
                        text = "Radio & Tower Scanner",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "Real-time Neighborhood Cells & Frequency Monitor",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            IconButton(onClick = { telemetryEngine.refreshNow() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Recommended Band Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Stars, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Strongest Recommended Band Near You:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
                Text(
                    text = cellularState.bestRecommendedBand,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Locking onto this frequency in BandMode will provide optimal speed and stability with minimum packet loss.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }

        // Total Visible Cells Count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Detected Cell Towers (${cellularState.allVisibleTowers.size}):",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "Live Scanning",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF00B050),
            )
        }

        // List of all visible towers
        if (cellularState.allVisibleTowers.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text("Scanning radio environment for cell towers…", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            cellularState.allVisibleTowers.forEach { tower ->
                TowerItemCard(tower = tower, onLockBand = {
                    MTKBandResolver.launchFirstWorking(context, MTKBandResolver.MTK_BAND_COMPONENTS, shizukuManager) { _, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }
    }
}

@Composable
private fun TowerItemCard(
    tower: CellTower,
    onLockBand: () -> Unit,
) {
    val signalColor = when {
        tower.rsrpDbm >= -85 -> Color(0xFF00B050) // Green (Excellent)
        tower.rsrpDbm >= -100 -> Color(0xFFFFB800) // Yellow (Good)
        else -> Color(0xFFE91E63)                  // Red (Weak)
    }

    val statusBadgeText = when {
        tower.isServing -> "🟢 CONNECTED (Serving PCell)"
        tower.isAggregated -> "⚡ CA AGGREGATED (SCell)"
        else -> "📡 NEIGHBOR CELL"
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(10.dp).clip(CircleShape).background(signalColor),
                    )
                    Text(
                        text = "${tower.rat} — ${tower.band.bandNumber}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (tower.isServing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = statusBadgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = if (tower.isServing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }

            Text(
                text = "Frequency: ${tower.band.frequencyMhz} | ${tower.band.operatorTag}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TowerMetric(label = "Signal (RSRP)", value = "${tower.rsrpDbm} dBm", color = signalColor)
                TowerMetric(label = "Quality (RSRQ)", value = "${tower.rsrqDb} dB", color = MaterialTheme.colorScheme.onSurface)
                TowerMetric(label = "SNR (SINR)", value = "${tower.sinrDb} dB", color = MaterialTheme.colorScheme.onSurface)
                TowerMetric(label = "PCI", value = "${tower.pci}", color = MaterialTheme.colorScheme.onSurface)
                TowerMetric(label = "Channel (ARFCN)", value = "${tower.earfcn}", color = MaterialTheme.colorScheme.primary)
            }

            if (!tower.isServing) {
                OutlinedButton(
                    onClick = onLockBand,
                    modifier = Modifier.fillMaxWidth().height(36.dp).bouncyClickable {},
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Lock to ${tower.band.bandNumber} in BandMode", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
private fun TowerMetric(label: String, value: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp), color = color)
    }
}
