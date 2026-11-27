package se.mybus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterHistoryTest {
    @Test
    fun `keeps previously observed lines and directions when departures change`() {
        val first = departure(line = "178", stopPointId = 1, destination = "Jakobsberg")
        val second = departure(line = "41", mode = Mode.TRAIN, stopPointId = 2, destination = "Uppsala")
        val history = FilterOptions().observe(listOf(first)).observe(listOf(second)).observe(emptyList())

        assertEquals(setOf("178", "41"), history.lines.map { it.line }.toSet())
        assertEquals(setOf(1, 2), history.directions.map { it.stopPointId }.toSet())
        assertEquals(listOf("Jakobsberg"), history.directions.first { it.stopPointId == 1 }.destinations)
        assertEquals(history, FilterHistory.decode(FilterHistory.encode(history)))
    }

    @Test
    fun `keeps hidden choices available even without any history`() {
        val stop = Stop(1, "Stop", hiddenStopPoints = setOf(42), hiddenLines = setOf("591"))
        val options = FilterOptions().includingHidden(stop)

        assertEquals(listOf(42), options.directions.map { it.stopPointId })
        assertEquals(listOf("591"), options.lines.map { it.line })
        assertTrue(options.directions.single().destinations.isEmpty())
        assertEquals(Mode.OTHER, options.lines.single().mode)
    }

    @Test
    fun `restored filters remain available and gain details when later observed`() {
        val hidden = Stop(1, "Stop", hiddenStopPoints = setOf(42), hiddenLines = setOf("591"))
        val remembered = FilterHistory.decode(FilterHistory.encode(FilterOptions().includingHidden(hidden)))
        val restored = remembered.includingHidden(hidden.copy(hiddenStopPoints = emptySet(), hiddenLines = emptySet()))
        assertEquals(listOf("591"), restored.lines.map { it.line })
        assertEquals(listOf(42), restored.directions.map { it.stopPointId })

        val observed = restored.observe(listOf(departure(line = "591", stopPointId = 42, destination = "City")))
        assertEquals(Mode.BUS, observed.lines.single().mode)
        assertEquals(listOf("City"), observed.directions.single().destinations)
    }

    @Test
    fun `refreshing a known direction keeps old destinations and prefers current metadata`() {
        val old = departure(line = "178", stopPointId = 1, destination = "Kista", platform = "A")
        val current = departure(line = "178", stopPointId = 1, destination = "Jakobsberg", platform = "B")
        val options = FilterOptions().observe(listOf(old)).observe(listOf(current, current))

        assertEquals(listOf("Jakobsberg", "Kista"), options.directions.single().destinations)
        assertEquals("B", options.directions.single().platform)
        assertEquals(listOf("178"), options.lines.map { it.line })
    }
}
