package com.app.bound.network

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.quicksettings.TileService
import com.app.bound.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Universal Hardware Activity & Xiaomi / MediaTek Band Discovery Engine.
 * Dynamically resolves and launches:
 * 1. Xiaomi Native MiuiBandMode (Hardware Band Selection on Poco / HyperOS)
 * 2. Xiaomi RadioInfo Testing Menu
 * 3. MediaTek EngineerMode & BandSelect
 * 4. MediaTek Carrier Aggregation Config
 */
object MTKBandResolver {

    // Xiaomi & MediaTek Hardware Band Selection Activities
    val MTK_BAND_COMPONENTS = listOf(
        "com.android.phone/com.android.phone.settings.MiuiBandMode",
        "com.android.phone/com.android.phone.settings.RadioInfo",
        "com.mediatek.engineermode/com.mediatek.engineermode.bandselect.BandSelect",
        "com.mediatek.engineermode/com.mediatek.engineermode.bandselect.BandMode",
        "com.mediatek.engineermode/com.mediatek.engineermode.modemtest.ModemTestActivity",
        "com.mediatek.engineermode/com.mediatek.engineermode.EngineerMode",
    )

    // MediaTek Carrier Aggregation (CA) Configuration Activities
    val MTK_CA_COMPONENTS = listOf(
        "com.android.phone/com.android.phone.settings.MobileNetworkSettings",
        "com.android.phone/com.android.phone.settings.NetworkSetting",
        "com.mediatek.engineermode/com.mediatek.engineermode.ca.CaActivity",
        "com.mediatek.engineermode/com.mediatek.engineermode.ca.CaConfigActivity",
        "com.mediatek.engineermode/com.mediatek.engineermode.nr.NrConfigActivity",
        "com.mediatek.engineermode/com.mediatek.engineermode.EngineerMode",
    )

    // Universal RadioInfo Testing Menus
    val RADIO_INFO_COMPONENTS = listOf(
        "com.android.phone/com.android.phone.settings.RadioInfo",
        "com.android.settings/com.android.settings.RadioInfo",
        "com.android.settings/com.android.settings.Settings\$RadioInfoActivity",
        "com.android.settings/com.android.settings.TestingSettings",
        "com.android.phone/com.android.phone.RadioInfo",
        "com.android.phone/com.android.phone.MobileNetworkSettings",
    )

    suspend fun launchComponent(
        context: Context,
        componentString: String,
        shizukuManager: ShizukuBandManager? = null,
        onLaunched: (Boolean, String) -> Unit,
    ) = withContext(Dispatchers.Main) {
        val parts = componentString.split("/")
        if (parts.size != 2) {
            onLaunched(false, "Invalid component format: $componentString")
            return@withContext
        }
        val pkg = parts[0]
        val cls = parts[1]
        val componentName = ComponentName(pkg, cls)

        AppLogger.i("MTKBandResolver", "Attempting to launch $componentString")

        // Strategy 1: Direct standard Intent launch (Works natively for MiuiBandMode and RadioInfo!)
        val intent = Intent().apply {
            component = componentName
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        try {
            if (context is TileService) {
                startActivityFromTile(context, intent)
            } else {
                context.startActivity(intent)
            }
            AppLogger.i("MTKBandResolver", "Launched $componentString via standard Intent")
            onLaunched(true, "Launched $cls")
            return@withContext
        } catch (e: Exception) {
            AppLogger.w("MTKBandResolver", "Direct launch failed for $componentString: ${e.message}")
        }

        // Strategy 2: If direct launch had permission denial, try via Shizuku Shell IPC
        if (shizukuManager != null && shizukuManager.isAuthorized()) {
            val shellSuccess = shizukuManager.launchShellActivity(componentString)
            if (shellSuccess) {
                AppLogger.i("MTKBandResolver", "Launched $componentString via Shizuku Shell IPC")
                onLaunched(true, "Launched via Shizuku Shell IPC ($cls)")
                return@withContext
            }
        }

        onLaunched(false, "Could not open $cls")
    }

    suspend fun launchFirstWorking(
        context: Context,
        components: List<String>,
        shizukuManager: ShizukuBandManager? = null,
        onResult: (Boolean, String) -> Unit,
    ) {
        for (comp in components) {
            var success = false
            var msg = ""
            launchComponent(context, comp, shizukuManager) { s, m ->
                success = s
                msg = m
            }
            if (success) {
                onResult(true, msg)
                return
            }
        }
        onResult(false, "Could not open band activities directly. Please check Developer Options permissions.")
    }

    fun openDialerWithCode(context: Context, secretCode: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$secretCode")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            AppLogger.e("MTKBandResolver", "Failed to launch dialer", t)
        }
    }

    private fun startActivityFromTile(tileService: TileService, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val pendingIntent = PendingIntent.getActivity(
                    tileService,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val method = TileService::class.java.getMethod("startActivityAndCollapse", PendingIntent::class.java)
                method.invoke(tileService, pendingIntent)
                return true
            } catch (_: Throwable) {}
        }

        return try {
            val method = TileService::class.java.getMethod("startActivityAndCollapse", Intent::class.java)
            method.invoke(tileService, intent)
            true
        } catch (_: Throwable) {
            try {
                tileService.applicationContext.startActivity(intent)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }
}
