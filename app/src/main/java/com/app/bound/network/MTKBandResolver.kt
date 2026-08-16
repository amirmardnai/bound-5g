package com.app.bound.network

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.quicksettings.TileService
import com.app.bound.util.AppLogger

/**
 * Universal Hardware Activity & MediaTek EngineerMode Discovery Engine.
 * Dynamically resolves and launches:
 * 1. MediaTek BandSelect (Hardware Band Toggling on Xiaomi/Poco Dimensity)
 * 2. MediaTek Carrier Aggregation Config (CA State & Band Combos)
 * 3. MediaTek ModemTest Activity
 * 4. AOSP / Stock RadioInfo Testing Menu (*#*#4636#*#*)
 */
object MTKBandResolver {

    // Primary MediaTek Band Selection Activities
    val MTK_BAND_COMPONENTS = listOf(
        "com.mediatek.engineermode/com.mediatek.engineermode.bandselect.BandSelect",
        "com.mediatek.engineermode/com.mediatek.engineermode.bandselect.BandMode",
        "com.mediatek.engineermode/com.mediatek.engineermode.modemtest.ModemTestActivity",
        "com.mediatek.engineermode/com.mediatek.engineermode.EngineerMode",
    )

    // MediaTek Carrier Aggregation (CA) Configuration Activities
    val MTK_CA_COMPONENTS = listOf(
        "com.mediatek.engineermode/com.mediatek.engineermode.ca.CaActivity",
        "com.mediatek.engineermode/com.mediatek.engineermode.ca.CaConfigActivity",
        "com.mediatek.engineermode/com.mediatek.engineermode.nr.NrConfigActivity",
        "com.mediatek.engineermode/com.mediatek.engineermode.EngineerMode",
    )

    // Universal RadioInfo Testing Menus
    val RADIO_INFO_COMPONENTS = listOf(
        "com.android.settings/com.android.settings.RadioInfo",
        "com.android.settings/com.android.settings.Settings\$RadioInfoActivity",
        "com.android.settings/com.android.settings.TestingSettings",
        "com.android.phone/com.android.phone.settings.RadioInfo",
        "com.android.phone/com.android.phone.RadioInfo",
        "com.android.phone/com.android.phone.MobileNetworkSettings",
    )

    fun launchComponent(
        context: Context,
        componentString: String,
        shizukuManager: ShizukuBandManager? = null,
        onLaunched: (Boolean, String) -> Unit,
    ) {
        val parts = componentString.split("/")
        if (parts.size != 2) {
            onLaunched(false, "Invalid component format: $componentString")
            return
        }
        val pkg = parts[0]
        val cls = parts[1]
        val componentName = ComponentName(pkg, cls)

        AppLogger.i("MTKBandResolver", "Attempting to launch $componentString")

        // Strategy 1: If Shizuku is authorized, use Shell UID to launch non-exported system activities!
        if (shizukuManager != null && shizukuManager.isAuthorized()) {
            val shellSuccess = shizukuManager.launchShellActivity(componentString)
            if (shellSuccess) {
                AppLogger.i("MTKBandResolver", "Launched $componentString via Shizuku Shell IPC")
                onLaunched(true, "Launched via Shizuku Shell IPC ($cls)")
                return
            }
        }

        // Strategy 2: Direct standard Intent launch
        val intent = Intent().apply {
            component = componentName
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
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
        } catch (e: Exception) {
            AppLogger.w("MTKBandResolver", "Direct launch failed for $componentString: ${e.message}")
            onLaunched(false, "Direct launch restricted (${e.message}). Enable Shizuku for 1-tap unrooted launch.")
        }
    }

    fun launchFirstWorking(
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
        onResult(false, "Could not open MediaTek activities directly. Start Shizuku Wireless Debugging to bypass HyperOS permissions.")
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
