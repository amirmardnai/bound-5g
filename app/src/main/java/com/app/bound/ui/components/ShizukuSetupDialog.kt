package com.app.bound.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.bound.network.ShizukuBandManager

@Composable
fun ShizukuSetupDialog(
    shizukuManager: ShizukuBandManager,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    var isAuthorized by remember { mutableStateOf(shizukuManager.isAuthorized()) }
    var isAvailable by remember { mutableStateOf(shizukuManager.isAvailable()) }

    val adbCommand = "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/files/start.sh"

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Button(onClick = onDismissRequest) {
                Text("Done")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    isAvailable = shizukuManager.isAvailable()
                    isAuthorized = shizukuManager.isAuthorized()
                    val msg = if (isAuthorized) "✅ Shizuku is authorized & active!" else if (isAvailable) "⚠️ Shizuku running, please authorize" else "❌ Shizuku not started yet"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Check Status")
            }
        },
        icon = {
            Icon(
                Icons.Rounded.Security,
                contentDescription = null,
                tint = if (isAuthorized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text("Shizuku Wireless Setup (No Root)", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isAuthorized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            if (isAuthorized) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                            contentDescription = null,
                            tint = if (isAuthorized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                        )
                        Column {
                            Text(
                                text = if (isAuthorized) "Shizuku Active" else if (isAvailable) "Authorization Required" else "Shizuku Service Disconnected",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = if (isAuthorized) "Ready to launch hidden MTK BandMode and lock 5G!" else "Follow steps below to enable 1-tap unrooted control.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                if (!isAuthorized) {
                    if (isAvailable) {
                        Button(
                            onClick = { shizukuManager.requestPermission() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Authorize Bound in Shizuku")
                        }
                    } else {
                        Button(
                            onClick = {
                                val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                if (intent != null) context.startActivity(intent)
                                else {
                                    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app"))
                                    context.startActivity(web)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Shizuku App")
                        }
                    }
                }

                Text(
                    text = "Recommended for Xiaomi / Poco (HyperOS):",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )

                Text(
                    text = "1. Enable 'Wireless Debugging' in Developer Options.\n" +
                            "2. In Developer Options on Xiaomi, also enable:\n" +
                            "   • Install via USB\n" +
                            "   • USB debugging (Security settings)\n" +
                            "3. Open Shizuku -> Tap 'Pairing' under Wireless Debugging -> Enter the 6-digit pairing code.\n" +
                            "4. Tap 'Start' in Shizuku!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("PC ADB Command", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            IconButton(
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("ADB Command", adbCommand))
                                    Toast.makeText(context, "Command copied!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                            }
                        }
                        Text(
                            text = adbCommand,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        )
                    }
                }
            }
        },
    )
}
