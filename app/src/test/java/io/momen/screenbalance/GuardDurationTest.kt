package io.momen.screenbalance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardDurationTest {
    @Test
    fun acceptsOneSecondAndThreeMinuteEndpoints() {
        assertTrue(GuardDuration.isSelectable(1_000L))
        assertTrue(GuardDuration.isSelectable(180_000L))
    }

    @Test
    fun rejectsValuesOutsideRangeOrBetweenSliderSteps() {
        assertFalse(GuardDuration.isSelectable(999L))
        assertFalse(GuardDuration.isSelectable(1_500L))
        assertFalse(GuardDuration.isSelectable(181_000L))
    }

    @Test
    fun formatsSliderValuesForDisplay() {
        assertEquals("1 秒", GuardDuration.format(1_000L))
        assertEquals("1 分 1 秒", GuardDuration.format(61_000L))
        assertEquals("3 分钟", GuardDuration.format(180_000L))
    }
}
