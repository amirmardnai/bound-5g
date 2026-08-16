package com.app.bound.network

import android.content.Context
import android.telephony.TelephonyManager
import com.app.bound.IUserService
import com.app.bound.util.AppLogger
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Privileged Shell Daemon process hosted by Shizuku for unrooted devices.
 * Executes Telephony hidden APIs and privileged shell commands with UID `shell`.
 */
class BoundUserService : IUserService.Stub() {

    init {
        try {
            HiddenApiBypass.addHiddenApiExemptions("L")
            AppLogger.i("BoundUserService", "HiddenApiBypass exemptions initialized")
        } catch (t: Throwable) {
            AppLogger.w("BoundUserService", "HiddenApiBypass failed: ${t.message}")
        }
    }

    private val BITMASK_NR = 1L shl (TelephonyManager.NETWORK_TYPE_NR - 1)       // 524288L
    private val BITMASK_LTE = 1L shl (TelephonyManager.NETWORK_TYPE_LTE - 1)     // 4096L
    private val BITMASK_UMTS = 1L shl (TelephonyManager.NETWORK_TYPE_UMTS - 1)   // 4L
    private val BITMASK_GSM = 1L shl (TelephonyManager.NETWORK_TYPE_GSM - 1)
    private val BITMASK_ALL = -1L                                                // Enable all radio types (Factory Default)

    private val NETWORK_MODE_NR_ONLY = 28                 // 5G SA Only
    private val NETWORK_MODE_NR_LTE_GSM_WCDMA = 26       // 5G NSA (NR + LTE + 3G + 2G Auto Default)
    private val NETWORK_MODE_LTE_ONLY = 11               // 4G LTE Only

    override fun setNetworkMode(subId: Int, mode: String): String {
        AppLogger.i("BoundUserService", "setNetworkMode requested: subId=$subId, mode=$mode")
        val targetSubId = if (subId <= 0 || subId == 2147483647) getDefaultDataSubId() else subId

        if (mode.uppercase() == "AUTO_DEFAULT" || mode.uppercase() == "DEFAULT") {
            return resetToDefaultNetworkMode(targetSubId)
        }

        val preferredModeInt = when (mode.uppercase()) {
            "NR_ONLY" -> NETWORK_MODE_NR_ONLY
            "NR_LTE" -> NETWORK_MODE_NR_LTE_GSM_WCDMA
            "LTE_ONLY" -> NETWORK_MODE_LTE_ONLY
            else -> return "ERROR: Unknown mode '$mode'"
        }

        val allowedMask = when (mode.uppercase()) {
            "NR_ONLY" -> BITMASK_NR
            "NR_LTE" -> BITMASK_NR or BITMASK_LTE or BITMASK_UMTS or BITMASK_GSM
            "LTE_ONLY" -> BITMASK_LTE
            else -> return "ERROR: Unknown mask for mode '$mode'"
        }

        return applyMode(targetSubId, preferredModeInt, allowedMask, mode)
    }

    override fun resetToDefaultNetworkMode(subId: Int): String {
        AppLogger.i("BoundUserService", "resetToDefaultNetworkMode requested for subId=$subId")
        val targetSubId = if (subId <= 0 || subId == 2147483647) getDefaultDataSubId() else subId
        return applyMode(targetSubId, NETWORK_MODE_NR_LTE_GSM_WCDMA, BITMASK_ALL, "FACTORY_AUTO_DEFAULT")
    }

    private fun applyMode(targetSubId: Int, preferredModeInt: Int, allowedMask: Long, label: String): String {
        // Strategy 1: TelephonyManager Reflection
        try {
            val tm = getTelephonyManagerForSubId(targetSubId)
            if (tm != null) {
                try {
                    val m = tm.javaClass.getMethod(
                        "setAllowedNetworkTypesForReason",
                        Int::class.javaPrimitiveType,
                        Long::class.javaPrimitiveType,
                    )
                    m.invoke(tm, 0, allowedMask)
                    return "OK: Mode reset to $label via TelephonyManager"
                } catch (_: Throwable) {}

                try {
                    val m = tm.javaClass.getMethod("setPreferredNetworkType", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    m.invoke(tm, targetSubId, preferredModeInt)
                    return "OK: Mode reset to $label on subId=$targetSubId"
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}

        // Strategy 2: Direct Shell `cmd phone`
        val shellCommands = listOf(
            arrayOf("cmd", "phone", "set-preferred-network-mode", "-s", targetSubId.toString(), preferredModeInt.toString()),
            arrayOf("cmd", "phone", "set-preferred-network-mode", preferredModeInt.toString()),
            arrayOf("cmd", "phone", "set-allowed-network-types", "-s", targetSubId.toString(), "-r", "0", allowedMask.toString()),
            arrayOf("cmd", "phone", "set-allowed-network-types", "-r", "0", allowedMask.toString()),
            arrayOf("settings", "put", "global", "preferred_network_mode$targetSubId", preferredModeInt.toString()),
        )

        for (cmd in shellCommands) {
            try {
                val proc = Runtime.getRuntime().exec(cmd)
                val exitCode = proc.waitFor()
                if (exitCode == 0) {
                    return "OK: Applied $label via '${cmd.joinToString(" ")}'"
                }
            } catch (_: Throwable) {}
        }

        return "ERROR: Mode switch command failed"
    }

    override fun launchShellActivity(componentName: String): Boolean {
        AppLogger.i("BoundUserService", "Launching shell activity: $componentName")
        return try {
            val cmd = arrayOf("am", "start", "-n", componentName)
            val proc = Runtime.getRuntime().exec(cmd)
            val exitCode = proc.waitFor()
            exitCode == 0
        } catch (t: Throwable) {
            AppLogger.e("BoundUserService", "am start failed", t)
            false
        }
    }

    override fun getDefaultDataSubId(): Int {
        val active = getAvailableSubIds()
        if (active.isNotEmpty()) return active[0]
        return 1
    }

    override fun getAvailableSubIds(): IntArray {
        val subIds = mutableListOf<Int>()
        try {
            val cls = Class.forName("android.telephony.SubscriptionManager")
            val getActiveList = cls.methods.firstOrNull { it.name == "getActiveSubscriptionInfoList" && it.parameterCount == 0 }
            if (getActiveList != null) {
                val list = getActiveList.invoke(null) as? List<*>
                if (!list.isNullOrEmpty()) {
                    for (info in list) {
                        if (info != null) {
                            val getSubId = info.javaClass.getMethod("getSubscriptionId")
                            val id = getSubId.invoke(info) as? Int ?: -1
                            if (id > 0 && id != 2147483647 && !subIds.contains(id)) {
                                subIds.add(id)
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        if (!subIds.contains(1)) subIds.add(1)
        if (!subIds.contains(2)) subIds.add(2)
        return subIds.sorted().toIntArray()
    }

    private fun getTelephonyManagerForSubId(subId: Int): TelephonyManager? {
        try {
            val ctor = TelephonyManager::class.java.getDeclaredConstructor(Context::class.java, Int::class.javaPrimitiveType)
            ctor.isAccessible = true
            return ctor.newInstance(null, subId)
        } catch (_: Throwable) {}
        return null
    }

    override fun destroy() {
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
