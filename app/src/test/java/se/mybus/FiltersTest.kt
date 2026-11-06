package se.mybus

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class FiltersTest {

    @Test
    fun `hides filtered directions and lines`() {
        val toCity = departure(line = "178", stopPointId = 1)
        val fromCity = departure(line = "178", stopPointId = 2)
        val other = departure(line = "523", stopPointId = 1)
        val stop = Stop(1, "S", hiddenStopPoints = setOf(2), hiddenLines = setOf("523"))
        assertEquals(listOf(toCity), stop.visible(listOf(toCity, fromCity, other)))
    }

    @Test
    fun `no filters shows everything`() {
        val all = listOf(departure(line = "1"), departure(line = "2", stopPointId = 9))
        assertEquals(all, Stop(1, "S").visible(all))
    }

    @Test
    fun `drops departures that have left, with a minute's grace`() {
        val now = LocalDateTime.of(2026, 11, 5, 15, 0)
        val left = departure(expectedAt = now.minusMinutes(2))
        val justDue = departure(expectedAt = now.minusSeconds(30))
        val exactlyGrace = departure(expectedAt = now.minusMinutes(1))
        val later = departure(expectedAt = now.plusMinutes(5))
        val unknown = departure(expectedAt = null)
        assertEquals(
            listOf(justDue, exactlyGrace, later, unknown),
            listOf(left, justDue, exactlyGrace, later, unknown).notLeftBy(now),
        )
    }

    @Test
    fun `counts the minutes left from now, not from the fetch`() {
        val now = LocalDateTime.of(2026, 11, 5, 15, 0, 0)
        assertEquals("7 min", timeLeft(departure(expectedAt = now.plusMinutes(7)), now, English))
        // Partial minutes round down, like SL's own countdown.
        assertEquals("7 min", timeLeft(departure(expectedAt = now.plusMinutes(7).plusSeconds(50)), now, English))
        assertEquals("59 min", timeLeft(departure(expectedAt = now.plusMinutes(59)), now, English))
    }

    @Test
    fun `due or just left says now, in the widget's language`() {
        val now = LocalDateTime.of(2026, 11, 5, 15, 0, 0)
        assertEquals("Now", timeLeft(departure(expectedAt = now.plusSeconds(40)), now, English))
        assertEquals("Nu", timeLeft(departure(expectedAt = now), now, Swedish))
        assertEquals("Nu", timeLeft(departure(expectedAt = now.minusSeconds(50)), now, Swedish))
    }

    @Test
    fun `an hour or more away, or no time, shows the clock time`() {
        val now = LocalDateTime.of(2026, 11, 5, 15, 0, 0)
        assertEquals("16:05", timeLeft(departure(expectedAt = now.plusMinutes(65)), now, English))
        val unknown = departure(expectedAt = null).copy(expected = "15:30")
        assertEquals("15:30", timeLeft(unknown, now, English))
    }

    @Test
    fun `lists lines by mode then number, suffixes next to their number`() {
        val deps = listOf("553V", "178", "41", "553H", "4", "553", "C1", "178").map { departure(line = it) } +
            departure(line = "43", mode = Mode.TRAIN)
        assertEquals(
            listOf("4", "41", "178", "553", "553H", "553V", "C1", "43"),
            linesOf(deps).map { it.line },
        )
    }

    @Test
    fun `groups directions by stop point with the commonest destination first`() {
        val deps = listOf(
            departure(stopPointId = 2, platform = "B", destination = "Mörby"),
            departure(stopPointId = 1, platform = "A", destination = "Kista"),
            departure(stopPointId = 1, platform = "A", destination = "Jakobsberg"),
            departure(stopPointId = 1, platform = "A", destination = "Jakobsberg"),
        )
        val directions = directionsOf(deps)
        assertEquals(listOf(1, 2), directions.map { it.stopPointId })
        assertEquals(listOf("Jakobsberg", "Kista"), directions[0].destinations)
        assertEquals("A", directions[0].platform)
    }
}
