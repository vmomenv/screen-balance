package io.momen.screenbalance

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

enum class ShizukuStatus {
    NOT_INSTALLED,
    NOT_RUNNING,
    NEEDS_PERMISSION,
    AUTHORIZED,
}

object ShizukuAccess {
    const val PERMISSION_REQUEST_CODE = 7_301
    private const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"

    fun status(context: Context): ShizukuStatus = when {
        !isInstalled(context) -> ShizukuStatus.NOT_INSTALLED
        !isRunning() -> ShizukuStatus.NOT_RUNNING
        !isAuthorized() -> ShizukuStatus.NEEDS_PERMISSION
        else -> ShizukuStatus.AUTHORIZED
    }

    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isAuthorized(): Boolean =
        isRunning() && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    fun requestPermission(): Boolean {
        if (!isRunning()) return false
        if (isAuthorized()) return true
        return runCatching {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            true
        }.getOrDefault(false)
    }

    fun openManager(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(MANAGER_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return false
        context.startActivity(intent)
        return true
    }

    private fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(MANAGER_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
