package io.momen.screenbalance

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.text.Collator
import java.util.Locale

data class InstalledApp(
    val label: String,
    val packageName: String,
)

class InstalledAppCatalog(context: Context) {
    private val applicationContext = context.applicationContext
    private val packageManager = applicationContext.packageManager

    fun launchableApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        val byPackage = linkedMapOf<String, InstalledApp>()
        activities.forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@forEach
            if (packageName == applicationContext.packageName || packageName in byPackage) {
                return@forEach
            }
            val label = resolveInfo.loadLabel(packageManager)
                ?.toString()
                ?.trim()
                .orEmpty()
                .ifBlank { packageName }
            byPackage[packageName] = InstalledApp(label, packageName)
        }

        val collator = Collator.getInstance(Locale.getDefault())
        return byPackage.values.sortedWith { first, second ->
            collator.compare(first.label, second.label).takeIf { it != 0 }
                ?: first.packageName.compareTo(second.packageName)
        }
    }

    fun labelForPackage(packageName: String): String? = runCatching {
        val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
        packageManager.getApplicationLabel(applicationInfo).toString().trim().ifBlank { null }
    }.getOrNull()
}
