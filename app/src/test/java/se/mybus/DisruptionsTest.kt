package se.mybus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DisruptionsTest {

    private val stop = Stop(9507, "Helenelunds station")
    private val hour = 60 * 60_000L

    @Test
    fun `one notice per disruption message, however many departures carry it`() {
        val msg = "Stopp mellan Älvsjö och Flemingsberg."
        val notices = noticesFor(stop, List(3) { departure(line = "41", deviation = msg) }, English)
        assertEquals(listOf(msg), notices.map { it.text })
    }

    @Test
    fun `cancellations are notices too`() {
        val at = LocalDateTime.of(2026, 11, 5, 15, 34)
        val notices = noticesFor(stop, listOf(departure(line = "178", destination = "Mörby", cancelled = true, expectedAt = at)), English)
        assertEquals(listOf("178 to Mörby at 15:34 is cancelled"), notices.map { it.text })
    }

    @Test
    fun `hidden lines and directions don't notify`() {
        val filtered = stop.copy(hiddenLines = setOf("41"), hiddenStopPoints = setOf(7))
        val deps = listOf(
            departure(line = "41", deviation = "Train trouble"),
            departure(line = "178", stopPointId = 7, cancelled = true),
        )
        assertTrue(noticesFor(filtered, deps, English).isEmpty())
    }

    @Test
    fun `only new notices are announced`() {
        val a = Notice("a", "A")
        val b = Notice("b", "B")
        val (first, seen) = newNotices(listOf(a), emptyMap(), now = 0)
        assertEquals(listOf(a), first)
        val (second, _) = newNotices(listOf(a, b), seen, now = hour)
        assertEquals(listOf(b), second)
    }

    @Test
    fun `a notice that drops out briefly isn't announced again`() {
        val a = Notice("a", "A")
        val (_, seen1) = newNotices(listOf(a), emptyMap(), now = 0)
        val (_, seen2) = newNotices(emptyList(), seen1, now = hour)
        val (again, _) = newNotices(listOf(a), seen2, now = 2 * hour)
        assertTrue(again.isEmpty())
    }

    @Test
    fun `notices are forgotten once unseen for long enough`() {
        val a = Notice("a", "A")
        val (_, seen) = newNotices(listOf(a), emptyMap(), now = 0)
        val (again, updated) = newNotices(listOf(a), seen, now = 13 * hour)
        assertEquals(listOf(a), again)
        val (_, pruned) = newNotices(emptyList(), updated, now = 40 * hour)
        assertTrue(pruned.isEmpty())
    }

    @Test
    fun `duplicate keys in one batch are announced once`() {
        val (fresh, _) = newNotices(listOf(Notice("a", "A"), Notice("a", "A")), emptyMap(), now = 0)
        assertEquals(1, fresh.size)
    }
}
