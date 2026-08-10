package io.momen.screenbalance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundComponentParserTest {
    @Test
    fun parsesTopResumedActivityForClonedUser() {
        val output = "topResumedActivity=ActivityRecord{abc123 u999 com.tencent.mm/.plugin.finder.ui.FinderHomeAffinityUI t42}"

        val activity = ForegroundComponentParser.parse(output)

        assertEquals(999, activity?.userId)
        assertEquals(
            "com.tencent.mm/.plugin.finder.ui.FinderHomeAffinityUI",
            activity?.componentName,
        )
        assertEquals("com.tencent.mm", activity?.packageName)
        assertEquals(
            "com.tencent.mm.plugin.finder.ui.FinderHomeAffinityUI",
            activity?.className,
        )
    }

    @Test
    fun parsesResumedActivityFallbackForMainUser() {
        val output = "ResumedActivity: ActivityRecord{def456 u0 com.ss.android.ugc.aweme/.main.MainActivity t7}"

        val activity = ForegroundComponentParser.parse(output)

        assertEquals(0, activity?.userId)
        assertEquals("com.ss.android.ugc.aweme", activity?.packageName)
        assertEquals("com.ss.android.ugc.aweme.main.MainActivity", activity?.className)
    }

    @Test
    fun rejectsMissingActivityRecord() {
        assertNull(ForegroundComponentParser.parse("ACTIVITY MANAGER ACTIVITIES"))
    }
}
