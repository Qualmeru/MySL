package se.mybus.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetUpdatesTest {
    @Test
    fun `refresh indicator expires if a worker never completes`() {
        val started = 1_000_000L
        assertFalse(isRefreshing(null, started))
        assertTrue(isRefreshing(started, started))
        assertTrue(isRefreshing(started, started + 119_999))
        assertFalse(isRefreshing(started, started + 120_000))
        assertFalse(isRefreshing(started, started - 1))
    }
}
