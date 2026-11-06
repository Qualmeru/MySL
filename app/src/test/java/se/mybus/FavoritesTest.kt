package se.mybus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FavoritesTest {

    private val a = Stop(1, "A")
    private val b = Stop(2, "B", hiddenStopPoints = setOf(10, 11), hiddenLines = setOf("178", "553H"))
    private val c = Stop(3, "Ö-stop / Söder")

    @Test
    fun `round trips stops with their filters and order`() {
        val stops = listOf(c, a, b)
        assertEquals(stops, Favorites.decode(Favorites.encode(stops)))
    }

    @Test
    fun `round trips an empty list`() {
        assertEquals(emptyList<Stop>(), Favorites.decode(Favorites.encode(emptyList())))
    }

    @Test
    fun `reads saves from before line filters existed`() {
        val old = """[{"id": 5875, "name": "Frihetsvägen", "hidden": [52649]}]"""
        assertEquals(listOf(Stop(5875, "Frihetsvägen", setOf(52649), emptySet())), Favorites.decode(old))
    }

    @Test
    fun `reads saves from before any filters existed`() {
        val oldest = """[{"id": 9507, "name": "Helenelunds station"}]"""
        assertEquals(listOf(Stop(9507, "Helenelunds station")), Favorites.decode(oldest))
    }

    @Test
    fun `drops duplicate stops, keeping the first`() {
        val dup = """[{"id": 1, "name": "First"}, {"id": 1, "name": "Second"}]"""
        assertEquals(listOf(Stop(1, "First")), Favorites.decode(dup))
    }

    @Test
    fun `moves a stop up and down`() {
        val stops = listOf(a, b, c)
        assertEquals(listOf(b, a, c), stops.move(2, -1))
        assertEquals(listOf(a, c, b), stops.move(2, 1))
    }

    @Test
    fun `moving past either end changes nothing`() {
        val stops = listOf(a, b, c)
        assertSame(stops, stops.move(1, -1))
        assertSame(stops, stops.move(3, 1))
        assertSame(stops, stops.move(99, 1))
        assertEquals(emptyList<Stop>(), emptyList<Stop>().move(1, 1))
    }
}
