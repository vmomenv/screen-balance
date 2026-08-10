package io.momen.screenbalance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundComponentParserTest {
    @Test
    fun parsesTopResumedActivity() {
        val output = "topResumedActivity=ActivityRecord{abc123 u0 com.tencent.mm/.plugin.finder.ui.FinderHomeAffinityUI t42}"

        val component = ForegroundComponentParser.parse(output)

        assertEquals(
            "com.tencent.mm/.plugin.finder.ui.FinderHomeAffinityUI",
            component,
        )
        assertEquals("com.tencent.mm", ForegroundComponentParser.packageName(component!!))
        assertEquals(
            "com.tencent.mm.plugin.finder.ui.FinderHomeAffinityUI",
            ForegroundComponentParser.className(component),
        )
    }

    @Test
    fun parsesResumedActivityFallback() {
        val output = "ResumedActivity: ActivityRecord{def456 u0 com.ss.android.ugc.aweme/.main.MainActivity t7}"

        assertEquals(
            "com.ss.android.ugc.aweme/.main.MainActivity",
            ForegroundComponentParser.parse(output),
        )
    }

    @Test
    fun rejectsMissingActivityRecord() {
        assertNull(ForegroundComponentParser.parse("ACTIVITY MANAGER ACTIVITIES"))
    }
}
