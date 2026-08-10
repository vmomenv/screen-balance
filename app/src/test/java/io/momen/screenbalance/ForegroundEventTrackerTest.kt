package io.momen.screenbalance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundEventTrackerTest {
    private val tracker = ForegroundEventTracker()

    @Test
    fun protectedInteractionIdentifiesForegroundPackage() {
        tracker.record(
            packageName = "tv.danmaku.bili",
            isWindowStateChange = false,
            isProtected = true,
        )

        assertEquals("tv.danmaku.bili", tracker.currentPackage)
    }

    @Test
    fun unrelatedClickDoesNotReplaceForegroundPackage() {
        tracker.record(
            packageName = "tv.danmaku.bili",
            isWindowStateChange = true,
            isProtected = true,
        )
        tracker.record(
            packageName = "com.android.systemui",
            isWindowStateChange = false,
            isProtected = false,
        )

        assertEquals("tv.danmaku.bili", tracker.currentPackage)
    }

    @Test
    fun windowChangeTracksLeavingProtectedApp() {
        tracker.record(
            packageName = "tv.danmaku.bili",
            isWindowStateChange = true,
            isProtected = true,
        )
        tracker.record(
            packageName = "com.miui.home",
            isWindowStateChange = true,
            isProtected = false,
        )

        assertEquals("com.miui.home", tracker.currentPackage)
    }

    @Test
    fun clearForgetsCachedPackage() {
        tracker.record(
            packageName = "tv.danmaku.bili",
            isWindowStateChange = true,
            isProtected = true,
        )

        tracker.clear()

        assertNull(tracker.currentPackage)
    }
}
