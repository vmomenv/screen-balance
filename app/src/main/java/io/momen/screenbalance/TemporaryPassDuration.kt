package io.momen.screenbalance

object TemporaryPassDuration {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 10
    const val DEFAULT_MINUTES = 10

    fun isSelectable(minutes: Int): Boolean = minutes in MIN_MINUTES..MAX_MINUTES

    fun format(minutes: Int): String = "$minutes 分钟"
}
