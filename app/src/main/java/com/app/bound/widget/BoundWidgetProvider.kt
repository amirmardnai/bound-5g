package com.app.bound.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import com.app.bound.MainActivity
import com.app.bound.R
import com.app.bound.network.BoundNetworkMode
import com.app.bound.network.MTKBandResolver
import com.app.bound.network.ShizukuBandManager
import com.app.bound.network.SwitchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BoundWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidgetView(context, appWidgetManager, appWidgetId, "Ready")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        if (action == ACTION_OPEN_MTK_BAND) {
            val shizuku = ShizukuBandManager(context.applicationContext)
            val pending = goAsync()
            CoroutineScope(Dispatchers.Main).launch {
                MTKBandResolver.launchFirstWorking(context, MTKBandResolver.MTK_BAND_COMPONENTS, shizuku) { _, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                pending.finish()
            }
            return
        }

        val targetMode = when (action) {
            ACTION_SWITCH_SA -> BoundNetworkMode.NR_ONLY
            ACTION_SWITCH_NSA -> BoundNetworkMode.NR_LTE
            ACTION_SWITCH_LTE -> BoundNetworkMode.LTE_ONLY
            else -> null
        }

        if (targetMode != null) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                val shizuku = ShizukuBandManager(context.applicationContext)
                val res = shizuku.switchNetworkMode(targetMode)
                val msg = when (res) {
                    is SwitchResult.Success -> "✅ Switched to ${targetMode.name}"
                    is SwitchResult.Failure -> "⚠️ ${res.reason}"
                }
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, BoundWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
                for (id in widgetIds) {
                    updateWidgetView(context, appWidgetManager, id, msg)
                }
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SWITCH_SA = "com.app.bound.widget.ACTION_SWITCH_SA"
        const val ACTION_SWITCH_NSA = "com.app.bound.widget.ACTION_SWITCH_NSA"
        const val ACTION_SWITCH_LTE = "com.app.bound.widget.ACTION_SWITCH_LTE"
        const val ACTION_OPEN_MTK_BAND = "com.app.bound.widget.ACTION_OPEN_MTK_BAND"

        fun updateWidgetView(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            statusText: String = "Ready",
        ) {
            val views = RemoteViews(context.packageName, R.layout.bound_widget_layout)
            views.setTextViewText(R.id.widget_title, "Bound 5G Controller")
            views.setTextViewText(R.id.widget_status, statusText)

            views.setOnClickPendingIntent(
                R.id.btn_widget_mtk,
                PendingIntent.getBroadcast(
                    context, 10,
                    Intent(context, BoundWidgetProvider::class.java).apply { action = ACTION_OPEN_MTK_BAND },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            views.setOnClickPendingIntent(
                R.id.btn_widget_sa,
                PendingIntent.getBroadcast(
                    context, 11,
                    Intent(context, BoundWidgetProvider::class.java).apply { action = ACTION_SWITCH_SA },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            views.setOnClickPendingIntent(
                R.id.btn_widget_nsa,
                PendingIntent.getBroadcast(
                    context, 12,
                    Intent(context, BoundWidgetProvider::class.java).apply { action = ACTION_SWITCH_NSA },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            views.setOnClickPendingIntent(
                R.id.widget_title,
                PendingIntent.getActivity(
                    context, 13,
                    Intent(context, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
