package io.momen.screenbalance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundEventTrackerTest {
    private val tracker = ForegroundEventTracker()

    @Test
    fun guardedInteractionIdentifiesForegroundPackage() {
        tracker.record(
            packageName = "tv.danmaku.bili",
            isWindowStateChange = false,
            pageState = GuardedPageState.GUARDED,
        )

        assertEquals("tv.danmaku.bili", tracker.currentPackage)
    }

    @Test
    fun unrelatedUnknownEventDoesNotReplaceGuardedPage() {
        tracker.record(
            packageName = "tv.danmaku.bili",
            isWindowStateChange = true,
            pageState = GuardedPageState.GUARDED,
        )
        tracker.record(
            packageName = "com.android.systemui",
            isWindowStateChange = false,
            pageState = GuardedPageState.UNKNOWN,
        )

        assertEquals("tv.danmaku.bili", tracker.currentPackage)
    }

    @Test
    fun unguardedWindowChangeStopsTracking() {
        tracker.record(
            packageName = GuardedPageClassifier.WECHAT_PACKAGE,
            isWindowStateChange = true,
            pageState = GuardedPageState.GUARDED,
        )
        tracker.record(
            packageName = GuardedPageClassifier.WECHAT_PACKAGE,
            isWindowStateChange = true,
            pageState = GuardedPageState.UNGUARDED,
        )

        assertNull(tracker.currentPackage)
    }

    @Test
    fun unknownWechatEventDoesNotAccidentallyStopActiveFinderSession() {
        tracker.record(
            packageName = GuardedPageClassifier.WECHAT_PACKAGE,
            isWindowStateChange = true,
            pageState = GuardedPageState.GUARDED,
        )
        tracker.record(
            packageName = GuardedPageClassifier.WECHAT_PACKAGE,
            isWindowStateChange = false,
            pageState = GuardedPageState.UNKNOWN,
        )

        assertEquals(GuardedPageClassifier.WECHAT_PACKAGE, tracker.currentPackage)
    }

    @Test
    fun clearForgetsCachedPackage() {
        tracker.record(
            packageName = "tv.danmaku.bili",
            isWindowStateChange = true,
            pageState = GuardedPageState.GUARDED,
        )

        tracker.clear()

        assertNull(tracker.currentPackage)
    }
}
