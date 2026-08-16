package com.app.bound.network

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import com.app.bound.IUserService
import com.app.bound.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

enum class BoundNetworkMode { NR_ONLY, NR_LTE, LTE_ONLY, AUTO_DEFAULT }

sealed class SwitchResult {
    data class Success(val message: String) : SwitchResult()
    data class Failure(val reason: String) : SwitchResult()
}

class ShizukuBandManager(private val context: Context) {

    private var service: IUserService? = null

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, BoundUserService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("boundservice")
            .debuggable(false)
            .version(1)
    }

    private var pendingConnect: kotlin.coroutines.Continuation<Boolean>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            AppLogger.i("ShizukuBandManager", "Bound user service connected")
            service = binder?.let { IUserService.Stub.asInterface(it) }
            pendingConnect?.resume(service != null)
            pendingConnect = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            AppLogger.w("ShizukuBandManager", "Bound user service disconnected")
            service = null
        }
    }

    fun isAvailable(): Boolean =
        try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }

    fun isAuthorized(): Boolean =
        isAvailable() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun requestPermission(requestCode: Int = 8848) {
        if (isAvailable() && !isAuthorized()) {
            Shizuku.requestPermission(requestCode)
        }
    }

    fun registerListeners(onStateChanged: (Boolean, Boolean) -> Unit): () -> Unit {
        val binderReceived = Shizuku.OnBinderReceivedListener { onStateChanged(isAvailable(), isAuthorized()) }
        val binderDead = Shizuku.OnBinderDeadListener { onStateChanged(false, false) }
        val permResult = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
            onStateChanged(isAvailable(), granted)
        }

        try {
            Shizuku.addBinderReceivedListener(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permResult)
        } catch (_: Throwable) {}

        onStateChanged(isAvailable(), isAuthorized())

        return {
            try {
                Shizuku.removeBinderReceivedListener(binderReceived)
                Shizuku.removeBinderDeadListener(binderDead)
                Shizuku.removeRequestPermissionResultListener(permResult)
            } catch (_: Throwable) {}
        }
    }

    suspend fun ensureBound(): Boolean {
        if (service != null) return true
        if (!isAuthorized()) return false
        return suspendCancellableCoroutine { cont ->
            pendingConnect = cont
            try {
                Shizuku.bindUserService(userServiceArgs, connection)
            } catch (t: Throwable) {
                AppLogger.e("ShizukuBandManager", "Failed to bind user service", t)
                cont.resume(false)
                pendingConnect = null
            }
        }
    }

    suspend fun switchNetworkMode(mode: BoundNetworkMode, subId: Int? = null): SwitchResult = withContext(Dispatchers.IO) {
        if (isAuthorized() && ensureBound()) {
            val svc = service
            if (svc != null) {
                try {
                    val targetSub = subId ?: svc.defaultDataSubId
                    val result = if (mode == BoundNetworkMode.AUTO_DEFAULT) {
                        svc.resetToDefaultNetworkMode(targetSub)
                    } else {
                        svc.setNetworkMode(targetSub, mode.name)
                    }
                    if (result.startsWith("OK")) {
                        return@withContext SwitchResult.Success(result)
                    }
                    return@withContext SwitchResult.Failure(result)
                } catch (t: Throwable) {
                    return@withContext SwitchResult.Failure("IPC error: ${t.message}")
                }
            }
        }
        SwitchResult.Failure("Shizuku service not authorized")
    }

    suspend fun resetToDefault(subId: Int? = null): SwitchResult = switchNetworkMode(BoundNetworkMode.AUTO_DEFAULT, subId)

    suspend fun launchShellActivity(componentName: String): Boolean = withContext(Dispatchers.IO) {
        if (isAuthorized() && ensureBound()) {
            try {
                return@withContext service?.launchShellActivity(componentName) ?: false
            } catch (t: Throwable) {
                AppLogger.e("ShizukuBandManager", "launchShellActivity IPC error", t)
            }
        }
        false
    }

    suspend fun getAvailableSubIds(): List<Int> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Int>()
        if (isAuthorized() && ensureBound()) {
            service?.let { svc ->
                try {
                    svc.availableSubIds.forEach { if (!list.contains(it)) list.add(it) }
                } catch (_: Throwable) {}
            }
        }
        if (!list.contains(1)) list.add(1)
        if (!list.contains(2)) list.add(2)
        list.sorted()
    }

    fun unbind() {
        try {
            Shizuku.unbindUserService(userServiceArgs, connection, true)
        } catch (_: Throwable) {}
        service = null
    }
}
