package se.mybus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {

    private val sites = listOf(
        Site(1, "Helenelunds station", ""),
        Site(2, "Kista centrum", ""),
        Site(3, "Sollentuna station", ""),
        Site(4, "Södra station", ""),
        Site(5, "Stationsvägen", ""),
        Site(6, "Gamla stan", ""),
    )

    @Test
    fun `ranks name start, then word start, then anywhere`() {
        assertEquals(
            listOf("Stationsvägen", "Helenelunds station", "Sollentuna station", "Södra station"),
            searchSites(sites, "station").map { it.name },
        )
    }

    @Test
    fun `is case and whitespace insensitive`() {
        assertEquals(listOf(2), searchSites(sites, "  KISTA ").map { it.id })
    }

    @Test
    fun `blank query finds nothing`() {
        assertTrue(searchSites(sites, "").isEmpty())
        assertTrue(searchSites(sites, "   ").isEmpty())
    }

    @Test
    fun `caps the number of results`() {
        assertEquals(2, searchSites(sites, "s", max = 2).size)
    }

    @Test
    fun `measures distance sensibly`() {
        // One degree of latitude is about 111 km anywhere.
        val d = distanceMetres(59.0, 18.0, 60.0, 18.0)
        assertTrue("was $d", d in 111_000.0..111_400.0)
        assertEquals(0.0, distanceMetres(59.33, 18.06, 59.33, 18.06), 0.001)
    }

    @Test
    fun `nearest first, skipping far and unplaced sites`() {
        // About 100 m, 500 m and 5 km north of the start, plus one without a position.
        val here = 59.3300 to 18.0600
        val near = Site(1, "Near", "", 59.3309, 18.0600)
        val mid = Site(2, "Mid", "", 59.3345, 18.0600)
        val far = Site(3, "Far", "", 59.3750, 18.0600)
        val nowhere = Site(4, "Nowhere", "")
        val result = nearestSites(listOf(far, mid, nowhere, near), here.first, here.second)
        assertEquals(listOf("Near", "Mid"), result.map { it.site.name })
        assertTrue(result[0].metres in 90..110)
        assertTrue(result[1].metres in 490..510)
    }

    @Test
    fun `limits how many nearby sites it returns`() {
        val many = (1..30).map { Site(it, "S$it", "", 59.33 + it * 0.0001, 18.06) }
        assertEquals(15, nearestSites(many, 59.33, 18.06).size)
        assertEquals(listOf(1, 2, 3), nearestSites(many, 59.33, 18.06, count = 3).map { it.site.id })
    }
}
