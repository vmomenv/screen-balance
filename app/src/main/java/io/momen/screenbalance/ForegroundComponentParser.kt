package io.momen.screenbalance

data class ForegroundActivity(
    val userId: Int,
    val componentName: String,
) {
    val packageName: String
        get() = componentName.substringBefore('/')

    val className: String?
        get() {
            val rawClassName = componentName.substringAfter('/', "")
            if (rawClassName.isEmpty()) return null
            return when {
                rawClassName.startsWith('.') -> packageName + rawClassName
                '.' !in rawClassName -> "$packageName.$rawClassName"
                else -> rawClassName
            }
        }
}

object ForegroundComponentParser {
    private val activityRecord = Regex(
        "ActivityRecord\\{\\S+\\s+u(\\d+)\\s+([^\\s}]+)",
    )

    fun parse(dumpsysOutput: String): ForegroundActivity? {
        val match = activityRecord.find(dumpsysOutput) ?: return null
        val userId = match.groupValues[1].toIntOrNull() ?: return null
        val componentName = match.groupValues[2]
        val packageName = componentName.substringBefore('/')
        if (!packageNamePattern.matches(packageName)) return null
        if ('/' !in componentName) return null
        return ForegroundActivity(
            userId = userId,
            componentName = componentName,
        )
    }

    private val packageNamePattern = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
}
