package io.momen.screenbalance

object GuardDuration {
    const val MIN_MILLIS = 1_000L
    const val MAX_MILLIS = 3 * 60 * 1_000L
    const val STEP_MILLIS = 1_000L
    const val DEFAULT_MILLIS = 2 * 60 * 1_000L

    fun isSelectable(millis: Long): Boolean =
        millis in MIN_MILLIS..MAX_MILLIS && millis % STEP_MILLIS == 0L

    fun format(millis: Long): String {
        val totalSeconds = (millis / STEP_MILLIS).coerceAtLeast(1L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L

        return when {
            minutes == 0L -> "${seconds} 秒"
            seconds == 0L -> "${minutes} 分钟"
            else -> "${minutes} 分 ${seconds} 秒"
        }
    }
}
