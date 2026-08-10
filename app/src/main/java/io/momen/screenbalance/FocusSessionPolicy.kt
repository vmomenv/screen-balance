package io.momen.screenbalance

data class FocusSession(
    val startedAtMillis: Long = 0L,
    val currentPackage: String? = null,
) {
    val isActive: Boolean
        get() = startedAtMillis > 0L && currentPackage != null
}

sealed interface SessionDecision {
    data object Clear : SessionDecision

    data class Tracking(
        val session: FocusSession,
        val remainingMillis: Long,
    ) : SessionDecision

    data class TimedOut(
        val packageName: String,
    ) : SessionDecision
}

class FocusSessionPolicy {

    fun update(
        currentSession: FocusSession,
        foregroundPackage: String?,
        shouldTrack: Boolean,
        nowMillis: Long,
        sessionLimitMillis: Long,
    ): SessionDecision {
        require(sessionLimitMillis > 0L)
        if (!shouldTrack || foregroundPackage == null) {
            return SessionDecision.Clear
        }

        val session = if (currentSession.isActive) {
            currentSession.copy(currentPackage = foregroundPackage)
        } else {
            FocusSession(startedAtMillis = nowMillis, currentPackage = foregroundPackage)
        }
        val elapsedMillis = (nowMillis - session.startedAtMillis).coerceAtLeast(0L)

        return if (elapsedMillis >= sessionLimitMillis) {
            SessionDecision.TimedOut(foregroundPackage)
        } else {
            SessionDecision.Tracking(
                session = session,
                remainingMillis = sessionLimitMillis - elapsedMillis,
            )
        }
    }

}
