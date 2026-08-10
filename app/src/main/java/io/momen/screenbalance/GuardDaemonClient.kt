package io.momen.screenbalance

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

class GuardDaemonClient(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = FocusGuardPreferences(applicationContext)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "screen-balance-daemon-client")
    }
    private val updatePending = AtomicBoolean(false)

    @Volatile
    private var started = false

    private var remoteProcess: IRemoteProcess? = null
    private var launchedConfiguration: GuardDaemonConfiguration? = null
    private var nextUpdateAtElapsedMillis = 0L

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        scheduleUpdate(force = true)
    }

    fun start() {
        if (started) return
        started = true
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        scheduleUpdate(force = true)
    }

    fun stop() {
        if (!started) return
        started = false
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        runCatching {
            executor.execute(::destroyRemoteProcess)
            executor.shutdown()
        }.onFailure { error ->
            Log.w(TAG, "Unable to stop guard daemon", error)
        }
    }

    fun syncConfiguration() {
        val nowElapsedMillis = SystemClock.elapsedRealtime()
        if (nowElapsedMillis < nextUpdateAtElapsedMillis) return
        nextUpdateAtElapsedMillis = nowElapsedMillis + UPDATE_INTERVAL_MILLIS
        scheduleUpdate(force = false)
    }

    private fun scheduleUpdate(force: Boolean) {
        if (!started) return
        if (force) nextUpdateAtElapsedMillis = 0L
        if (!updatePending.compareAndSet(false, true)) return

        try {
            executor.execute {
                try {
                    updateDaemon()
                } catch (error: Exception) {
                    Log.e(TAG, "Unable to update guard daemon", error)
                    destroyRemoteProcess()
                } finally {
                    updatePending.set(false)
                }
            }
        } catch (_: RejectedExecutionException) {
            updatePending.set(false)
        }
    }

    private fun updateDaemon() {
        if (!started) return

        val desiredConfiguration = GuardDaemonConfiguration(
            guardEnabled = preferences.isGuardEnabled(),
            guardDurationMillis = preferences.guardDurationMillis(),
            temporaryPassUntilMillis = preferences.temporaryPassUntilMillis(),
            cooldownUntilMillis = preferences.cooldownUntilMillis(),
            protectedPackages = preferences.protectedPackages(),
        )
        val processAlive = remoteProcess?.let { process ->
            runCatching(process::alive).getOrDefault(false)
        } == true

        if (processAlive && desiredConfiguration == launchedConfiguration) return
        destroyRemoteProcess()

        if (
            !started ||
            !desiredConfiguration.guardEnabled ||
            desiredConfiguration.protectedPackages.isEmpty() ||
            ShizukuAccess.status(applicationContext) != ShizukuStatus.AUTHORIZED
        ) {
            return
        }

        val service = requireNotNull(
            IShizukuService.Stub.asInterface(Shizuku.getBinder()),
        ) { "Shizuku service is unavailable" }
        val command = arrayOf(
            ENV_PATH,
            "CLASSPATH=${applicationContext.applicationInfo.sourceDir}",
            APP_PROCESS_PATH,
            SYSTEM_BIN_PATH,
            "--nice-name=$PROCESS_NAME",
            GuardDaemon::class.java.name,
            *GuardDaemonArguments.encode(desiredConfiguration),
        )
        val process = service.newProcess(command, null, null)
        remoteProcess = process
        launchedConfiguration = desiredConfiguration
        Log.i(TAG, "Started independent guard process")
    }

    private fun destroyRemoteProcess() {
        remoteProcess?.let { process ->
            runCatching {
                if (process.alive()) process.destroy()
            }.onFailure { error ->
                Log.w(TAG, "Unable to destroy guard process", error)
            }
        }
        remoteProcess = null
        launchedConfiguration = null
    }

    private companion object {
        const val TAG = "ScreenBalanceDaemonClient"
        const val UPDATE_INTERVAL_MILLIS = 2_000L
        const val ENV_PATH = "/system/bin/env"
        const val APP_PROCESS_PATH = "/system/bin/app_process"
        const val SYSTEM_BIN_PATH = "/system/bin"
        const val PROCESS_NAME = "screen_balance_guard"
    }
}
