package io.momen.screenbalance

import android.content.Context

data class RecommendedApp(
    val label: String,
    val packageName: String,
)

class FocusGuardPreferences(context: Context) {
    private val applicationContext = context.applicationContext
    private val store = applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val installedAppCatalog = InstalledAppCatalog(applicationContext)

    fun isGuardEnabled(): Boolean = store.getBoolean(KEY_GUARD_ENABLED, true)

    fun setGuardEnabled(enabled: Boolean) {
        store.edit().putBoolean(KEY_GUARD_ENABLED, enabled).apply()
    }
    fun guardDurationMillis(): Long {
        val storedMillis = store.getLong(KEY_GUARD_DURATION_MILLIS, GuardDuration.DEFAULT_MILLIS)
        return storedMillis.takeIf(GuardDuration::isSelectable) ?: GuardDuration.DEFAULT_MILLIS
    }

    fun setGuardDurationMillis(millis: Long) {
        require(GuardDuration.isSelectable(millis))
        store.edit().putLong(KEY_GUARD_DURATION_MILLIS, millis).apply()
    }


    fun protectedPackages(): Set<String> = selectedPackages()

    fun customProtectedPackages(): Set<String> = ProtectedAppSelection.customPackages(
        selectedPackages = selectedPackages(),
        recommendedPackages = RECOMMENDED_PACKAGES,
    )

    fun isProtected(packageName: String): Boolean = packageName in selectedPackages()

    fun setProtected(packageName: String, protected: Boolean) {
        ProtectedAppSelection.requireValidPackageName(packageName)
        val selected = selectedPackages().toMutableSet()
        if (protected) selected += packageName else selected -= packageName
        store.edit().putStringSet(KEY_TARGET_PACKAGES, selected).apply()
    }

    fun replaceCustomProtectedPackages(packageNames: Set<String>) {
        val selected = ProtectedAppSelection.replaceCustomPackages(
            currentPackages = selectedPackages(),
            recommendedPackages = RECOMMENDED_PACKAGES,
            customPackages = packageNames,
        )
        store.edit().putStringSet(KEY_TARGET_PACKAGES, selected).apply()
    }

    fun startTemporaryPass(minutes: Int, nowMillis: Long = System.currentTimeMillis()): Long {
        require(TemporaryPassDuration.isSelectable(minutes))
        val untilMillis = nowMillis + minutes * 60 * 1_000L
        store.edit().putLong(KEY_TEMPORARY_PASS_UNTIL, untilMillis).apply()
        return untilMillis
    }

    fun temporaryPassMinutes(): Int {
        val storedMinutes = store.getInt(
            KEY_TEMPORARY_PASS_MINUTES,
            TemporaryPassDuration.DEFAULT_MINUTES,
        )
        return storedMinutes.takeIf(TemporaryPassDuration::isSelectable)
            ?: TemporaryPassDuration.DEFAULT_MINUTES
    }

    fun setTemporaryPassMinutes(minutes: Int) {
        require(TemporaryPassDuration.isSelectable(minutes))
        store.edit().putInt(KEY_TEMPORARY_PASS_MINUTES, minutes).apply()
    }

    fun endTemporaryPass() {
        store.edit().remove(KEY_TEMPORARY_PASS_UNTIL).apply()
    }

    fun temporaryPassUntilMillis(): Long = store.getLong(KEY_TEMPORARY_PASS_UNTIL, 0L)

    fun isTemporaryPassActive(nowMillis: Long = System.currentTimeMillis()): Boolean =
        temporaryPassUntilMillis() > nowMillis

    fun temporaryPassRemainingMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        (temporaryPassUntilMillis() - nowMillis).coerceAtLeast(0L)

    fun cooldownUntilMillis(): Long = store.getLong(KEY_COOLDOWN_UNTIL, 0L)

    fun setCooldownUntilMillis(untilMillis: Long) {
        check(store.edit().putLong(KEY_COOLDOWN_UNTIL, untilMillis).commit()) {
            "Unable to persist cooldown"
        }
    }

    fun clearExpiredCooldown(nowMillis: Long = System.currentTimeMillis()) {
        if (cooldownUntilMillis() <= nowMillis) {
            store.edit().remove(KEY_COOLDOWN_UNTIL).apply()
        }
    }

    fun labelFor(packageName: String): String =
        RECOMMENDED_APPS.firstOrNull { it.packageName == packageName }?.label
            ?: installedAppCatalog.labelForPackage(packageName)
            ?: packageName

    private fun selectedPackages(): Set<String> =
        store.getStringSet(KEY_TARGET_PACKAGES, null)?.toSet()
            ?: RECOMMENDED_PACKAGES

    companion object {
        const val COOLDOWN_MILLIS = 5 * 60 * 1_000L

        val RECOMMENDED_APPS = listOf(
            RecommendedApp("抖音", "com.ss.android.ugc.aweme"),
            RecommendedApp("抖音极速版", "com.ss.android.ugc.aweme.lite"),
            RecommendedApp("哔哩哔哩", "tv.danmaku.bili"),
            RecommendedApp("知乎", "com.zhihu.android"),
            RecommendedApp("YouTube", "com.google.android.youtube"),
            RecommendedApp("Instagram", "com.instagram.android"),
            RecommendedApp("微信视频号（不限制聊天/扫码/付款）", "com.tencent.mm"),
        )
        val RECOMMENDED_PACKAGES = RECOMMENDED_APPS
            .mapTo(linkedSetOf()) { app -> app.packageName }

        private const val PREFERENCES_NAME = "focus_guard"
        private const val KEY_GUARD_ENABLED = "guard_enabled"
        private const val KEY_GUARD_DURATION_MILLIS = "guard_duration_millis"
        private const val KEY_TARGET_PACKAGES = "target_packages"
        private const val KEY_TEMPORARY_PASS_UNTIL = "temporary_pass_until"
        private const val KEY_TEMPORARY_PASS_MINUTES = "temporary_pass_minutes"
        private const val KEY_COOLDOWN_UNTIL = "cooldown_until"
    }
}
