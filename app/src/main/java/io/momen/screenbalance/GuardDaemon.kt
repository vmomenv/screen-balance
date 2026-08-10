package io.momen.screenbalance

import android.os.SystemClock
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

internal data class GuardDaemonConfiguration(
    val guardEnabled: Boolean,
    val guardDurationMillis: Long,
    val temporaryPassUntilMillis: Long,
    val cooldownUntilMillis: Long,
    val protectedPackages: Set<String>,
)

internal object GuardDaemonArguments {
    private const val GUARD_ENABLED = "--guard-enabled="
    private const val GUARD_DURATION = "--guard-duration-millis="
    private const val TEMPORARY_PASS_UNTIL = "--temporary-pass-until-millis="
    private const val COOLDOWN_UNTIL = "--cooldown-until-millis="
    private const val PROTECTED_PACKAGE = "--protected-package="
    private val packageNamePattern = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")

    fun encode(configuration: GuardDaemonConfiguration): Array<String> = buildList {
        add("$GUARD_ENABLED${configuration.guardEnabled}")
        add("$GUARD_DURATION${configuration.guardDurationMillis}")
        add("$TEMPORARY_PASS_UNTIL${configuration.temporaryPassUntilMillis}")
        add("$COOLDOWN_UNTIL${configuration.cooldownUntilMillis}")
        configuration.protectedPackages.sorted().forEach { packageName ->
            require(packageNamePattern.matches(packageName)) { "Invalid package name: $packageName" }
            add("$PROTECTED_PACKAGE$packageName")
        }
    }.toTypedArray()

    fun decode(arguments: Array<String>): GuardDaemonConfiguration {
        var guardEnabled: Boolean? = null
        var guardDurationMillis: Long? = null
        var temporaryPassUntilMillis: Long? = null
        var cooldownUntilMillis: Long? = null
        val protectedPackages = linkedSetOf<String>()

        arguments.forEach { argument ->
            when {
                argument.startsWith(GUARD_ENABLED) -> {
                    guardEnabled = argument.valueAfter(GUARD_ENABLED).toBooleanStrict()
                }
                argument.startsWith(GUARD_DURATION) -> {
                    guardDurationMillis = argument.valueAfter(GUARD_DURATION).toLong()
                }
                argument.startsWith(TEMPORARY_PASS_UNTIL) -> {
                    temporaryPassUntilMillis = argument.valueAfter(TEMPORARY_PASS_UNTIL).toLong()
                }
                argument.startsWith(COOLDOWN_UNTIL) -> {
                    cooldownUntilMillis = argument.valueAfter(COOLDOWN_UNTIL).toLong()
                }
                argument.startsWith(PROTECTED_PACKAGE) -> {
                    val packageName = argument.valueAfter(PROTECTED_PACKAGE)
                    require(packageNamePattern.matches(packageName)) {
                        "Invalid package name: $packageName"
                    }
                    protectedPackages += packageName
                }
                else -> throw IllegalArgumentException("Unknown guard argument: $argument")
            }
        }

        val durationMillis = requireNotNull(guardDurationMillis) {
            "Missing guard duration"
        }
        require(durationMillis >= 1_000L) { "Guard duration must be at least one second" }
        val passUntilMillis = requireNotNull(temporaryPassUntilMillis) {
            "Missing temporary pass expiry"
        }
        val cooldownMillis = requireNotNull(cooldownUntilMillis) {
            "Missing cooldown expiry"
        }
        require(passUntilMillis >= 0L) { "Temporary pass expiry cannot be negative" }
        require(cooldownMillis >= 0L) { "Cooldown expiry cannot be negative" }

        return GuardDaemonConfiguration(
            guardEnabled = requireNotNull(guardEnabled) { "Missing guard state" },
            guardDurationMillis = durationMillis,
            temporaryPassUntilMillis = passUntilMillis,
            cooldownUntilMillis = cooldownMillis,
            protectedPackages = protectedPackages,
        )
    }

    private fun String.valueAfter(prefix: String): String =
        substring(prefix.length).also { value ->
            require(value.isNotEmpty()) { "Missing value for $prefix" }
        }
}

object GuardDaemon {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val configuration = try {
            GuardDaemonArguments.decode(arguments)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Invalid guard configuration", error)
            return
        }

        Log.i(
            TAG,
            "Independent guard started: duration=${configuration.guardDurationMillis}, " +
                "packages=${configuration.protectedPackages}",
        )
        GuardMonitor(configuration).run()
    }

    private const val TAG = "ScreenBalanceDaemon"
}

private class GuardMonitor(
    private val configuration: GuardDaemonConfiguration,
) {
    private val policy = FocusSessionPolicy()
    private var cooldownUntilMillis = configuration.cooldownUntilMillis
    private var session = FocusSession()
    private var lastForegroundActivity: ForegroundActivity? = null

    fun run() {
        while (true) {
            try {
                monitorOnce()
                Thread.sleep(MONITOR_INTERVAL_MILLIS)
            } catch (_: InterruptedException) {
                return
            } catch (error: Exception) {
                Log.e(TAG, "Unable to monitor foreground activity", error)
                clearSession()
                SystemClock.sleep(ERROR_BACKOFF_MILLIS)
            }
        }
    }

    private fun monitorOnce() {
        val nowWallMillis = System.currentTimeMillis()
        if (
            !configuration.guardEnabled ||
            configuration.protectedPackages.isEmpty() ||
            configuration.temporaryPassUntilMillis > nowWallMillis
        ) {
            clearSession()
            return
        }

        val activity = foregroundActivity() ?: run {
            clearSession()
            return
        }
        if (activity != lastForegroundActivity) {
            lastForegroundActivity = activity
            Log.i(
                TAG,
                "Foreground activity: user=${activity.userId} component=${activity.componentName}",
            )
        }
        val packageName = activity.packageName
        val pageState = GuardedPageClassifier.classify(
            packageName = packageName,
            className = activity.className,
            isPackageSelected = packageName in configuration.protectedPackages,
        )
        if (pageState != GuardedPageState.GUARDED) {
            clearSession()
            return
        }

        if (cooldownUntilMillis > nowWallMillis) {
            forceStop(activity.userId, packageName)
            clearSession()
            return
        }

        when (
            val decision = policy.update(
                currentSession = session,
                foregroundPackage = packageName,
                shouldTrack = true,
                nowMillis = SystemClock.elapsedRealtime(),
                sessionLimitMillis = configuration.guardDurationMillis,
            )
        ) {
            SessionDecision.Clear -> clearSession()
            is SessionDecision.Tracking -> session = decision.session
            is SessionDecision.TimedOut -> {
                cooldownUntilMillis = nowWallMillis + FocusGuardPreferences.COOLDOWN_MILLIS
                forceStop(activity.userId, decision.packageName)
                clearSession()
            }
        }
    }

    private fun foregroundActivity(): ForegroundActivity? =
        ForegroundComponentParser.parse(
            runCommand(SHELL_PATH, "-c", FOREGROUND_ACTIVITY_COMMAND),
        )

    private fun forceStop(userId: Int, packageName: String) {
        runCommand(*ActivityManagerCommand.forceStop(userId, packageName))
        Log.i(TAG, "Force-stopped package: user=$userId package=$packageName")
    }

    private fun runCommand(vararg command: String): String {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).use(BufferedReader::readText)
        check(process.waitFor() == 0) {
            "Command failed: ${command.joinToString(" ")}: $output"
        }
        return output
    }

    private fun clearSession() {
        session = FocusSession()
    }

    private companion object {
        const val TAG = "ScreenBalanceDaemon"
        const val MONITOR_INTERVAL_MILLIS = 750L
        const val ERROR_BACKOFF_MILLIS = 2_000L
        const val SHELL_PATH = "/system/bin/sh"
        const val FOREGROUND_ACTIVITY_COMMAND =
            "/system/bin/dumpsys activity activities | " +
                "/system/bin/grep -m 1 -E 'topResumedActivity=|ResumedActivity:' || true"
    }
}
