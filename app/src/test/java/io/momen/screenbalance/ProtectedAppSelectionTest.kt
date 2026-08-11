package io.momen.screenbalance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedAppSelectionTest {
    private val recommended = setOf(
        "com.tencent.mm",
        "tv.danmaku.bili",
    )

    @Test
    fun customPackagesExcludeRecommendedApps() {
        assertEquals(
            setOf("com.example.reader"),
            ProtectedAppSelection.customPackages(
                selectedPackages = setOf(
                    "com.tencent.mm",
                    "com.example.reader",
                ),
                recommendedPackages = recommended,
            ),
        )
    }

    @Test
    fun replacingCustomAppsPreservesRecommendedCheckboxes() {
        val updated = ProtectedAppSelection.replaceCustomPackages(
            currentPackages = setOf(
                "com.tencent.mm",
                "com.example.old",
            ),
            recommendedPackages = recommended,
            customPackages = setOf("com.example.new"),
        )

        assertTrue("com.tencent.mm" in updated)
        assertFalse("tv.danmaku.bili" in updated)
        assertFalse("com.example.old" in updated)
        assertTrue("com.example.new" in updated)
    }

    @Test(expected = IllegalArgumentException::class)
    fun replacingCustomAppsRejectsInvalidPackageNames() {
        ProtectedAppSelection.replaceCustomPackages(
            currentPackages = emptySet(),
            recommendedPackages = recommended,
            customPackages = setOf("com.example; reboot"),
        )
    }
}
