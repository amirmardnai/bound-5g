package com.app.bound.screens

import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.bound.network.*
import com.app.bound.ui.components.ShizukuSetupDialog
import com.app.bound.ui.components.bouncyClickable
import com.app.bound.util.AppPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    prefs: AppPreferences,
    telemetryEngine: TelemetryEngine,
    shizukuManager: ShizukuBandManager,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cellularState by telemetryEngine.cellularState.collectAsState()

    var availableSubIds by remember { mutableStateOf<List<Int>>(listOf(1, 2)) }
    var selectedSubId by remember { mutableStateOf<Int?>(null) }
    var isSwitchingMode by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Ready to configure MediaTek modem & cellular bands.") }
    var showSetupDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var isShizukuActive by remember { mutableStateOf(shizukuManager.isAuthorized()) }

    DisposableEffect(Unit) {
        val unregister = shizukuManager.registerListeners { _, authorized ->
            isShizukuActive = authorized
        }
        telemetryEngine.startPolling(selectedSubId)
        onDispose {
            unregister()
            telemetryEngine.stopPolling()
        }
    }

    LaunchedEffect(selectedSubId) {
        telemetryEngine.refreshNow(selectedSubId)
        scope.launch {
            if (isShizukuActive) {
                availableSubIds = shizukuManager.getAvailableSubIds()
            }
        }
    }

    if (showSetupDialog) {
        ShizukuSetupDialog(
            shizukuManager = shizukuManager,
            onDismissRequest = { showSetupDialog = false },
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) },
            title = { Text("Reset to Factory Default Network?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = "This will restore your phone's network configuration back to default:\n\n" +
                            "• Network Mode will be reset to Auto (5G / 4G / 3G / 2G).\n" +
                            "• All radio bitmasks and restrictions will be cleared.\n" +
                            "• Tip: In MediaTek BandSelect, tap 'Select All' then 'SET' to re-enable all bands.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showResetConfirmDialog = false
                        isSwitchingMode = true
                        scope.launch {
                            val res = shizukuManager.resetToDefault(selectedSubId)
                            val msg = when (res) {
                                is SwitchResult.Success -> "✅ Network reset to Factory Default!"
                                is SwitchResult.Failure -> "⚠️ ${res.reason}"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            isSwitchingMode = false
                        }
                    },
                ) {
                    Text("Yes, Reset to Default")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Bound 5G",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "MediaTek Band & Carrier Aggregation Controller",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Status Pill
            Surface(
                shape = CircleShape,
                color = if (isShizukuActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier
                    .bouncyClickable { showSetupDialog = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (isShizukuActive) Icons.Rounded.CheckCircle else Icons.Rounded.Security,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isShizukuActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        text = if (isShizukuActive) "Shizuku Shell Active" else "Setup Shizuku",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isShizukuActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }

        // Live RF Cellular & Carrier Aggregation Monitor Card
        ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                        ),
                    ),
                    shape = RoundedCornerShape(24.dp),
                ),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Carrier + Generation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.CellTower,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column {
                            Text(
                                text = cellularState.carrierName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = cellularState.networkGeneration,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // CA Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (cellularState.isCarrierAggregationActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = if (cellularState.isCarrierAggregationActive) "⚡ 4.5G+ CA ACTIVE" else "SINGLE CARRIER",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 9.sp),
                            color = if (cellularState.isCarrierAggregationActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Primary & Secondary Aggregated Bands
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Active Serving Frequency Bands:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Primary Band Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Primary (PCell)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                )
                                Text(
                                    text = "${cellularState.primaryBand.bandNumber} (${cellularState.primaryBand.frequencyMhz})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }

                        // Secondary CA Bands Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (cellularState.secondaryBands.isNotEmpty()) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Aggregated (SCells)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                )
                                val secondaryText = if (cellularState.secondaryBands.isNotEmpty()) {
                                    cellularState.secondaryBands.joinToString(" + ") { it.bandNumber }
                                } else {
                                    "No SCell Active"
                                }
                                Text(
                                    text = secondaryText,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }

                // Signal Metrics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetricItem(label = "Signal (RSRP)", value = "${cellularState.rsrpDbm} dBm", sub = "Power")
                    MetricItem(label = "Quality (RSRQ)", value = "${cellularState.rsrqDb} dB", sub = "Interference")
                    MetricItem(label = "SNR (SINR)", value = "${cellularState.sinrDb} dB", sub = "Clarity")
                    MetricItem(label = "Bandwidth", value = cellularState.totalBandwidthText, sub = "Carrier Width")
                }
            }
        }

        // Dual-SIM Slot Chooser
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.SimCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Target SIM:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    availableSubIds.forEach { subId ->
                        FilterChip(
                            selected = (selectedSubId ?: 1) == subId,
                            onClick = { selectedSubId = subId },
                            label = { Text("SIM $subId") },
                        )
                    }
                }
            }
        }

        // 1-TAP MEDIA TEK HARDWARE BAND ACTION GRID
        Text(
            text = "MediaTek Hardware Band & CA Controls:",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionCard(
                title = "MTK BandMode",
                description = "Direct 1-tap lock for B1, B3, B7, B42 & n78 bands",
                icon = Icons.Rounded.Tune,
                primaryColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = {
                    MTKBandResolver.launchFirstWorking(context, MTKBandResolver.MTK_BAND_COMPONENTS, shizukuManager) { _, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
            )

            ActionCard(
                title = "MTK CA Config",
                description = "Carrier Aggregation & EN-DC Band combination",
                icon = Icons.Rounded.Bolt,
                primaryColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
                onClick = {
                    MTKBandResolver.launchFirstWorking(context, MTKBandResolver.MTK_CA_COMPONENTS, shizukuManager) { _, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionCard(
                title = "System RadioInfo",
                description = "AOSP Testing Menu (*#*#4636#*#*)",
                icon = Icons.Rounded.NetworkCheck,
                primaryColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
                onClick = {
                    MTKBandResolver.launchFirstWorking(context, MTKBandResolver.RADIO_INFO_COMPONENTS, shizukuManager) { _, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
            )

            ActionCard(
                title = "MTK Secret Dialer",
                description = "Dial *#*#3646633#*#* directly",
                icon = Icons.Rounded.Dialpad,
                primaryColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = {
                    MTKBandResolver.openDialerWithCode(context, "*#*#3646633#*#*")
                },
            )
        }

        // Fast Network Mode Switcher Section
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Force Network Mode (via Shizuku Shell):",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        enabled = !isSwitchingMode,
                        onClick = {
                            isSwitchingMode = true
                            scope.launch {
                                val res = shizukuManager.switchNetworkMode(BoundNetworkMode.NR_ONLY, selectedSubId)
                                statusText = when (res) {
                                    is SwitchResult.Success -> "✅ ${res.message}"
                                    is SwitchResult.Failure -> "⚠️ ${res.reason}"
                                }
                                Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                                isSwitchingMode = false
                            }
                        },
                        modifier = Modifier.weight(1f).bouncyClickable {},
                    ) {
                        Text("5G SA", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }

                    Button(
                        enabled = !isSwitchingMode,
                        onClick = {
                            isSwitchingMode = true
                            scope.launch {
                                val res = shizukuManager.switchNetworkMode(BoundNetworkMode.NR_LTE, selectedSubId)
                                statusText = when (res) {
                                    is SwitchResult.Success -> "✅ ${res.message}"
                                    is SwitchResult.Failure -> "⚠️ ${res.reason}"
                                }
                                Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                                isSwitchingMode = false
                            }
                        },
                        modifier = Modifier.weight(1f).bouncyClickable {},
                    ) {
                        Text("5G NSA", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }

                    OutlinedButton(
                        enabled = !isSwitchingMode,
                        onClick = {
                            isSwitchingMode = true
                            scope.launch {
                                val res = shizukuManager.switchNetworkMode(BoundNetworkMode.LTE_ONLY, selectedSubId)
                                statusText = when (res) {
                                    is SwitchResult.Success -> "✅ ${res.message}"
                                    is SwitchResult.Failure -> "⚠️ ${res.reason}"
                                }
                                Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                                isSwitchingMode = false
                            }
                        },
                        modifier = Modifier.weight(1f).bouncyClickable {},
                    ) {
                        Text("4G LTE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // RESET TO DEFAULT BUTTON
                OutlinedButton(
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = { showResetConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth().bouncyClickable {},
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🔄 Reset Network to Factory Default (Auto 5G/4G/3G/2G)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, sub: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        Text(text = sub, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    primaryColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.bouncyClickable { onClick() },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(primaryColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = primaryColor, modifier = Modifier.size(20.dp))
            }
            Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Text(text = description, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
