package io.momen.screenbalance

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ActivityManagerCommandTest {
    @Test
    fun forceStopTargetsClonedAndroidUser() {
        assertArrayEquals(
            arrayOf(
                "/system/bin/am",
                "force-stop",
                "--user",
                "999",
                "com.tencent.mm",
            ),
            ActivityManagerCommand.forceStop(
                userId = 999,
                packageName = "com.tencent.mm",
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun forceStopRejectsInvalidPackageName() {
        ActivityManagerCommand.forceStop(
            userId = 999,
            packageName = "com.tencent.mm; reboot",
        )
    }
}
