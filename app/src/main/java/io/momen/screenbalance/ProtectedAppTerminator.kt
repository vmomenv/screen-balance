package io.momen.screenbalance

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import moe.shizuku.server.IShizukuService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

sealed interface TerminationResult {
    data class Stopped(val packageNames: Set<String>) : TerminationResult
    data class Unavailable(val reason: String) : TerminationResult
    data object Busy : TerminationResult
}

class ProtectedAppTerminator(context: Context) {
    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val requestInFlight = AtomicBoolean(false)

    @Volatile
    private var started = false

    fun start() {
        started = true
    }

    fun stop() {
        if (!started) return
        started = false
        executor.shutdownNow()
    }

    fun forceStopRunning(
        packageNames: Set<String>,
        onResult: (TerminationResult) -> Unit,
    ) {
        val validatedPackages = packageNames.filterTo(linkedSetOf()) { packageName ->
            PACKAGE_NAME.matches(packageName)
        }
        if (validatedPackages.isEmpty()) {
            onResult(TerminationResult.Stopped(emptySet()))
            return
        }
        if (!started) {
            onResult(TerminationResult.Unavailable("TERMINATOR_STOPPED"))
            return
        }

        val shizukuStatus = ShizukuAccess.status(applicationContext)
        if (shizukuStatus != ShizukuStatus.AUTHORIZED) {
            onResult(TerminationResult.Unavailable(shizukuStatus.name))
            return
        }
        if (!requestInFlight.compareAndSet(false, true)) {
            onResult(TerminationResult.Busy)
            return
        }

        executor.execute {
            val result = try {
                val shizukuService = requireNotNull(
                    IShizukuService.Stub.asInterface(rikka.shizuku.Shizuku.getBinder()),
                ) { "Shizuku service is unavailable" }

                validatedPackages.forEach { packageName ->
                    forceStopPackage(shizukuService, packageName)
                }
                Log.i(TAG, "Force-stopped packages: $validatedPackages")
                TerminationResult.Stopped(validatedPackages)
            } catch (error: Exception) {
                Log.e(TAG, "Unable to force-stop protected apps", error)
                TerminationResult.Unavailable(error.javaClass.simpleName)
            } finally {
                requestInFlight.set(false)
            }
            mainHandler.post { onResult(result) }
        }
    }

    private fun forceStopPackage(
        shizukuService: IShizukuService,
        packageName: String,
    ) {
        val process = shizukuService.newProcess(
            arrayOf(AM_PATH, "force-stop", "--user", "all", packageName),
            null,
            null,
        )
        try {
            ParcelFileDescriptor.AutoCloseInputStream(process.inputStream).use { input ->
                input.copyTo(java.io.OutputStream.nullOutputStream())
            }
            ParcelFileDescriptor.AutoCloseInputStream(process.errorStream).use { error ->
                error.copyTo(java.io.OutputStream.nullOutputStream())
            }
            check(process.waitFor() == 0) { "am force-stop failed for $packageName" }
        } finally {
            if (process.alive()) process.destroy()
        }
    }

    private companion object {
        const val TAG = "ScreenBalanceKiller"
        const val AM_PATH = "/system/bin/am"
        val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
    }
}
