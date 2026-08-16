package com.app.bound.util

import android.content.Context

object AppInfo {
    const val APP_NAME = "Bound 5G"
    const val VERSION_NAME = "1.0.0"
    const val VERSION_CODE = 1
    const val RELEASE_TITLE = "MediaTek Band Controller & CA Engine"

    fun getAppVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: VERSION_NAME
        } catch (_: Throwable) {
            VERSION_NAME
        }
    }
}
