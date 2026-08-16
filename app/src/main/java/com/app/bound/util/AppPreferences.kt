package com.app.bound.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.app.bound.network.BoundNetworkMode

enum class AppThemeMode { SYSTEM, DARK, LIGHT }

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bound_settings", Context.MODE_PRIVATE)

    var defaultNetworkMode: BoundNetworkMode
        get() = defaultNetworkModeState
        set(value) {
            defaultNetworkModeState = value
            prefs.edit().putString("default_mode", value.name).apply()
        }

    var themeMode: AppThemeMode
        get() = themeModeState
        set(value) {
            themeModeState = value
            prefs.edit().putString("theme_mode", value.name).apply()
        }

    var amoled: Boolean
        get() = amoledState
        set(value) {
            amoledState = value
            prefs.edit().putBoolean("amoled", value).apply()
        }

    var autoScanSims: Boolean
        get() = autoScanSimsState
        set(value) {
            autoScanSimsState = value
            prefs.edit().putBoolean("auto_scan_sims", value).apply()
        }

    var hasDismissedShizukuBanner: Boolean
        get() = hasDismissedShizukuBannerState
        set(value) {
            hasDismissedShizukuBannerState = value
            prefs.edit().putBoolean("has_dismissed_shizuku_banner", value).apply()
        }

    var defaultNetworkModeState by mutableStateOf(readDefaultMode())
        private set

    var themeModeState by mutableStateOf(readThemeMode())
        private set

    var amoledState by mutableStateOf(readAmoled())
        private set

    var autoScanSimsState by mutableStateOf(readAutoScanSims())
        private set

    var hasDismissedShizukuBannerState by mutableStateOf(readHasDismissedBanner())
        private set

    private fun readDefaultMode(): BoundNetworkMode {
        val name = prefs.getString("default_mode", BoundNetworkMode.NR_LTE.name)
        return runCatching { BoundNetworkMode.valueOf(name ?: BoundNetworkMode.NR_LTE.name) }.getOrDefault(BoundNetworkMode.NR_LTE)
    }

    private fun readThemeMode(): AppThemeMode {
        val name = prefs.getString("theme_mode", AppThemeMode.SYSTEM.name)
        return runCatching { AppThemeMode.valueOf(name ?: AppThemeMode.SYSTEM.name) }.getOrDefault(AppThemeMode.SYSTEM)
    }

    private fun readAmoled(): Boolean = prefs.getBoolean("amoled", false)
    private fun readAutoScanSims(): Boolean = prefs.getBoolean("auto_scan_sims", true)
    private fun readHasDismissedBanner(): Boolean = prefs.getBoolean("has_dismissed_shizuku_banner", false)
}
