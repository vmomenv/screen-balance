package io.momen.screenbalance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryPassDurationTest {
    @Test
    fun acceptsOneToTenMinuteEndpoints() {
        assertTrue(TemporaryPassDuration.isSelectable(1))
        assertTrue(TemporaryPassDuration.isSelectable(10))
    }

    @Test
    fun rejectsValuesOutsideRange() {
        assertFalse(TemporaryPassDuration.isSelectable(0))
        assertFalse(TemporaryPassDuration.isSelectable(11))
        assertFalse(TemporaryPassDuration.isSelectable(-1))
    }

    @Test
    fun formatsMinutesForDisplay() {
        assertEquals("1 分钟", TemporaryPassDuration.format(1))
        assertEquals("10 分钟", TemporaryPassDuration.format(10))
    }
}
