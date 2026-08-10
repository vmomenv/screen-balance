package io.momen.screenbalance

object ForegroundComponentParser {
    private val activityRecord = Regex(
        "ActivityRecord\\{\\S+\\s+u\\d+\\s+([^\\s}]+)",
    )

    fun parse(dumpsysOutput: String): String? =
        activityRecord.find(dumpsysOutput)?.groupValues?.getOrNull(1)

    fun packageName(componentName: String): String? =
        componentName.substringBefore('/').takeIf(PACKAGE_NAME::matches)

    fun className(componentName: String): String? {
        val packageName = packageName(componentName) ?: return null
        val rawClassName = componentName.substringAfter('/', "")
        if (rawClassName.isEmpty()) return null
        return when {
            rawClassName.startsWith('.') -> packageName + rawClassName
            '.' !in rawClassName -> "$packageName.$rawClassName"
            else -> rawClassName
        }
    }

    private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
}
