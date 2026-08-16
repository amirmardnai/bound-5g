package com.app.bound.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.bound.network.BoundNetworkMode
import com.app.bound.network.ShizukuBandManager
import com.app.bound.ui.components.ShizukuSetupDialog
import com.app.bound.ui.components.bouncyClickable
import com.app.bound.util.AppPreferences
import com.app.bound.util.AppThemeMode

@Composable
fun SettingsScreen(
    prefs: AppPreferences,
    shizukuManager: ShizukuBandManager,
    modifier: Modifier = Modifier,
) {
    var showSetupDialog by remember { mutableStateOf(false) }

    if (showSetupDialog) {
        ShizukuSetupDialog(
            shizukuManager = shizukuManager,
            onDismissRequest = { showSetupDialog = false },
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
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            )
        }

        // Appearance
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Appearance & Theming", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = prefs.themeMode == mode,
                            onClick = { prefs.themeMode = mode },
                            label = { Text(mode.name) },
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Pure AMOLED Black", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(text = "Deep #000000 background for OLED/AMOLED screens", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = prefs.amoled, onCheckedChange = { prefs.amoled = it })
                }
            }
        }

        // Network Defaults
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Network Defaults", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                Text(text = "Default Startup Network Mode:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoundNetworkMode.entries.forEach { mode ->
                        FilterChip(
                            selected = prefs.defaultNetworkMode == mode,
                            onClick = { prefs.defaultNetworkMode = mode },
                            label = {
                                Text(
                                    when (mode) {
                                        BoundNetworkMode.NR_ONLY -> "5G SA"
                                        BoundNetworkMode.NR_LTE -> "5G NSA"
                                        BoundNetworkMode.LTE_ONLY -> "4G LTE"
                                        BoundNetworkMode.AUTO_DEFAULT -> "Auto (Default)"
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }

        // Shizuku Setup Launcher Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Service & Permissions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    text = "Shizuku Wireless Debugging allows 1-tap unrooted control for MediaTek BandMode and 5G mode switching.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = { showSetupDialog = true },
                    modifier = Modifier.fillMaxWidth().bouncyClickable {},
                ) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Shizuku Wireless Setup Guide")
                }
            }
        }
    }
}
