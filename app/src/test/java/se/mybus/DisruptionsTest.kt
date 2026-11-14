package se.mybus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `minor information is skipped unless asked for`() {
        val deps = listOf(
            departure(deviation = "Enstaka förseningar pga halka", importance = 5, consequence = "INFORMATION"),
            departure(deviation = "Läge C är flyttat", importance = 7, consequence = "INFORMATION"),
        )
        assertEquals(listOf("Läge C är flyttat"), noticesFor(stop, deps, English).map { it.text })
        assertEquals(2, noticesFor(stop, deps, English, includeMinor = true).size)
    }

    @Test
    fun `SL's own Inställd notice isn't announced next to the cancellation`() {
        val at = LocalDateTime.of(2026, 11, 5, 15, 34)
        val deps = listOf(
            departure(line = "178", destination = "Mörby", cancelled = true, expectedAt = at, deviation = "Inställd", consequence = "CANCELLED"),
        )
        assertEquals(listOf("178 to Mörby at 15:34 is cancelled"), noticesFor(stop, deps, English).map { it.text })
    }

    @Test
    fun `a stop with alerts turned off never notifies`() {
        val quiet = stop.copy(alerts = false)
        val deps = listOf(departure(deviation = "Train trouble"), departure(cancelled = true))
        assertTrue(noticesFor(quiet, deps, English).isEmpty())
    }

    // 2026-11-09 is a Monday, 2026-11-14 a Saturday.
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 11, day, hour, 30)

    @Test
    fun `alerts are allowed all day by default`() {
        val options = AlertOptions()
        assertTrue(options.allDay)
        assertTrue(options.allows(at(9, 3)))
        assertTrue(options.allows(at(14, 23)))
    }

    @Test
    fun `a daytime window allows only its hours`() {
        val options = AlertOptions(fromHour = 6, toHour = 9)
        assertFalse(options.allows(at(9, 5)))
        assertTrue(options.allows(at(9, 6)))
        assertTrue(options.allows(at(9, 8)))
        assertFalse(options.allows(at(9, 9)))
    }

    @Test
    fun `a window can cross midnight`() {
        val options = AlertOptions(fromHour = 22, toHour = 6)
        assertTrue(options.allows(at(9, 23)))
        assertTrue(options.allows(at(9, 2)))
        assertFalse(options.allows(at(9, 12)))
    }

    @Test
    fun `weekdays only is quiet at the weekend`() {
        val options = AlertOptions(weekdaysOnly = true)
        assertTrue(options.allows(at(9, 12)))
        assertFalse(options.allows(at(14, 12)))
    }
}
