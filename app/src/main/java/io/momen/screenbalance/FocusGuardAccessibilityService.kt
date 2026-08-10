package io.momen.screenbalance

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class FocusGuardAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val preferences by lazy { FocusGuardPreferences(this) }
    private val policy = FocusSessionPolicy()
    private val appTerminator by lazy { ProtectedAppTerminator(this) }
    private val guardDaemonClient by lazy { GuardDaemonClient(this) }

    private var session = FocusSession()
    private var receiverRegistered = false
    private var lastBackgroundSweepAtMillis = 0L
    private var lastUnavailableNoticeAtMillis = 0L
    private val foregroundTracker = ForegroundEventTracker()

    private val monitorRunnable = object : Runnable {
        override fun run() {
            try {
                monitorApps()
            } catch (error: RuntimeException) {
                Log.e(TAG, "Unable to monitor protected apps", error)
                resetSession()
            } finally {
                mainHandler.postDelayed(this, MONITOR_INTERVAL_MILLIS)
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_USER_PRESENT,
                -> {
                    foregroundTracker.clear()
                    resetSession()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        startForeground(GuardNotification.ID, GuardNotification.create(this))
        registerScreenReceiver()
        appTerminator.start()
        guardDaemonClient.start()
        foregroundTracker.clear()
        resetSession()
        mainHandler.removeCallbacks(monitorRunnable)
        mainHandler.post(monitorRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val pageState = GuardedPageClassifier.classify(
            packageName = packageName,
            className = event.className?.toString(),
            isPackageSelected = preferences.isProtected(packageName),
        )
        val isWindowStateChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        foregroundTracker.record(
            packageName = packageName,
            isWindowStateChange = isWindowStateChange,
            pageState = pageState,
        )
        val nowMillis = System.currentTimeMillis()
        if (
            preferences.isGuardEnabled() &&
            !preferences.isTemporaryPassActive(nowMillis) &&
            preferences.cooldownUntilMillis() > nowMillis &&
            pageState == GuardedPageState.GUARDED
        ) {
            foregroundTracker.clear()
            performGlobalAction(GLOBAL_ACTION_HOME)
            sweepRunningProtectedApps(
                nowMillis = nowMillis,
                force = true,
                packages = setOf(packageName),
            )
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        mainHandler.removeCallbacks(monitorRunnable)
        guardDaemonClient.stop()
        appTerminator.stop()
        foregroundTracker.clear()
        resetSession()
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun monitorApps() {
        val nowMillis = System.currentTimeMillis()
        guardDaemonClient.syncConfiguration()
        if (!preferences.isGuardEnabled() || preferences.isTemporaryPassActive(nowMillis)) {
            resetSession()
            return
        }

        val protectedPackages = preferences.protectedPackages()
        if (protectedPackages.isEmpty()) {
            resetSession()
            return
        }

        val activePackage = foregroundTracker.currentPackage
        val cooldownUntilMillis = preferences.cooldownUntilMillis()
        if (cooldownUntilMillis > nowMillis) {
            resetSession()
            return
        }
        preferences.clearExpiredCooldown(nowMillis)

        if (activePackage !in protectedPackages) {
            resetSession()
            return
        }

        when (
            val decision = policy.update(
                currentSession = session,
                foregroundPackage = activePackage,
                shouldTrack = true,
                nowMillis = nowMillis,
                sessionLimitMillis = preferences.guardDurationMillis(),
            )
        ) {
            SessionDecision.Clear -> resetSession()
            is SessionDecision.Tracking -> {
                session = decision.session
            }
            is SessionDecision.TimedOut -> {
                beginCooldown(decision.packageName, nowMillis)
            }
        }
    }


    private fun beginCooldown(timedOutPackage: String, nowMillis: Long) {
        val cooldownUntilMillis = nowMillis + FocusGuardPreferences.COOLDOWN_MILLIS
        preferences.setCooldownUntilMillis(cooldownUntilMillis)
        foregroundTracker.clear()
        resetSession()
        performGlobalAction(GLOBAL_ACTION_HOME)
        Toast.makeText(
            this,
            "${GuardDuration.format(preferences.guardDurationMillis())}已到，正在强制停止受限应用",
            Toast.LENGTH_SHORT,
        ).show()
        sweepRunningProtectedApps(
            nowMillis = nowMillis,
            force = true,
            fallbackPackage = timedOutPackage,
            packages = setOf(timedOutPackage),
        )
    }

    private fun sweepRunningProtectedApps(
        nowMillis: Long,
        force: Boolean = false,
        fallbackPackage: String? = null,
        packages: Set<String> = preferences.protectedPackages(),
    ) {
        if (!force && nowMillis - lastBackgroundSweepAtMillis < BACKGROUND_SWEEP_INTERVAL_MILLIS) {
            return
        }
        lastBackgroundSweepAtMillis = nowMillis

        appTerminator.forceStopRunning(packages) { result ->
            when (result) {
                is TerminationResult.Stopped -> {
                    if (result.packageNames.isNotEmpty()) {
                        val labels = result.packageNames
                            .map(preferences::labelFor)
                            .distinct()
                            .joinToString("、")
                        Toast.makeText(this, "已强制停止：$labels", Toast.LENGTH_SHORT).show()
                    } else if (fallbackPackage != null) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                }

                TerminationResult.Busy -> Unit
                is TerminationResult.Unavailable -> {
                    if (nowMillis - lastUnavailableNoticeAtMillis >= UNAVAILABLE_NOTICE_INTERVAL_MILLIS) {
                        lastUnavailableNoticeAtMillis = nowMillis
                        Toast.makeText(
                            this,
                            "无法强制停止应用：请启动 Shizuku 并授权屏衡",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    private fun resetSession() {
        session = FocusSession()
    }

    private fun registerScreenReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
        receiverRegistered = true
    }

    private companion object {
        const val TAG = "ScreenBalanceGuard"
        const val MONITOR_INTERVAL_MILLIS = 250L
        const val BACKGROUND_SWEEP_INTERVAL_MILLIS = 1_000L
        const val UNAVAILABLE_NOTICE_INTERVAL_MILLIS = 10_000L
    }
}
