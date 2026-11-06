package se.mybus

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class SlCooldownTest {

    private var now = 1_000_000L

    @Before
    fun setUp() {
        SlCooldown.clock = { now }
        SlCooldown.reset()
    }

    @After
    fun tearDown() {
        SlCooldown.reset()
        SlCooldown.clock = System::currentTimeMillis
    }

    private fun assertBlocked() {
        try {
            SlCooldown.check()
            fail("expected a cool-down")
        } catch (e: SlApiException) {
            // Same kind of error as SL's own refusal, so the app shows "SL is busy, try later".
            assertTrue(e.quota)
        }
    }

    @Test
    fun `nothing is blocked until SL refuses`() {
        SlCooldown.check()
        assertEquals(0L, SlCooldown.remainingMs())
    }

    @Test
    fun `a refusal blocks everyone for a minute`() {
        SlCooldown.refused()
        assertBlocked()
        now += SlCooldown.FIRST_MS - 1
        assertBlocked()
        now += 1
        SlCooldown.check()
    }

    @Test
    fun `refusals in a row double the pause up to the maximum`() {
        val lengths = (1..6).map {
            SlCooldown.refused()
            SlCooldown.remainingMs().also { now += it }
        }
        assertEquals(listOf(1L, 2L, 4L, 8L, 15L, 15L).map { it * 60_000L }, lengths)
    }

    @Test
    fun `a longer Retry-After from SL wins`() {
        SlCooldown.refused(retryAfterMs = 5 * 60_000L)
        assertEquals(5 * 60_000L, SlCooldown.remainingMs())
    }

    @Test
    fun `a good answer ends the cool-down and starts the next one at a minute again`() {
        SlCooldown.refused()
        now += SlCooldown.FIRST_MS
        SlCooldown.refused()
        assertEquals(2 * 60_000L, SlCooldown.remainingMs())
        SlCooldown.succeeded()
        SlCooldown.check()
        SlCooldown.refused()
        assertEquals(SlCooldown.FIRST_MS, SlCooldown.remainingMs())
    }
}
