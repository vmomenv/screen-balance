package io.momen.screenbalance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSessionPolicyTest {
    private val policy = FocusSessionPolicy()
    private val sessionLimitMillis = 120_000L

    @Test
    fun startsTwoMinuteSessionForProtectedApp() {
        val decision = policy.update(
            currentSession = FocusSession(),
            foregroundPackage = "com.example.video",
            shouldTrack = true,
            nowMillis = 1_000L,
            sessionLimitMillis = sessionLimitMillis,
        )

        assertTrue(decision is SessionDecision.Tracking)
        val tracking = decision as SessionDecision.Tracking
        assertEquals(1_000L, tracking.session.startedAtMillis)
        assertEquals(120_000L, tracking.remainingMillis)
    }

    @Test
    fun selectedAppsShareOneContinuousSession() {
        val first = policy.update(
            currentSession = FocusSession(),
            foregroundPackage = "com.example.first",
            shouldTrack = true,
            nowMillis = 1_000L,
            sessionLimitMillis = sessionLimitMillis,
        ) as SessionDecision.Tracking

        val switched = policy.update(
            currentSession = first.session,
            foregroundPackage = "com.example.second",
            shouldTrack = true,
            nowMillis = 61_000L,
            sessionLimitMillis = sessionLimitMillis,
        )

        assertTrue(switched is SessionDecision.Tracking)
        val tracking = switched as SessionDecision.Tracking
        assertEquals(1_000L, tracking.session.startedAtMillis)
        assertEquals("com.example.second", tracking.session.currentPackage)
        assertEquals(60_000L, tracking.remainingMillis)
    }

    @Test
    fun timesOutExactlyAtTwoMinutes() {
        val decision = policy.update(
            currentSession = FocusSession(
                startedAtMillis = 1_000L,
                currentPackage = "com.example.video",
            ),
            foregroundPackage = "com.example.video",
            shouldTrack = true,
            nowMillis = 121_000L,
            sessionLimitMillis = sessionLimitMillis,
        )

        assertTrue(decision is SessionDecision.TimedOut)
        assertEquals("com.example.video", (decision as SessionDecision.TimedOut).packageName)
    }

    @Test
    fun appliesShortenedLimitToActiveSession() {
        val decision = policy.update(
            currentSession = FocusSession(
                startedAtMillis = 1_000L,
                currentPackage = "com.example.video",
            ),
            foregroundPackage = "com.example.video",
            shouldTrack = true,
            nowMillis = 2_000L,
            sessionLimitMillis = GuardDuration.MIN_MILLIS,
        )

        assertTrue(decision is SessionDecision.TimedOut)
    }

    @Test
    fun nonTrackedForegroundClearsSession() {
        val decision = policy.update(
            currentSession = FocusSession(
                startedAtMillis = 1_000L,
                currentPackage = "com.example.video",
            ),
            foregroundPackage = "com.example.mail",
            shouldTrack = false,
            nowMillis = 2_000L,
            sessionLimitMillis = sessionLimitMillis,
        )

        assertTrue(decision is SessionDecision.Clear)
    }
}
