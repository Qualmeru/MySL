package se.mybus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDateTime

class SlApiTest {

    // Trimmed from a real answer for Helenelunds station.
    private val departuresBody = """
        {"departures": [
          {"destination": "Södertälje centrum", "state": "ATSTOP", "display": "Nu",
           "scheduled": "2026-11-05T15:02:00", "expected": "2026-11-05T15:02:30",
           "stop_point": {"id": 5051, "name": "Helenelund", "designation": "3"},
           "line": {"id": 41, "designation": "41", "transport_mode": "TRAIN", "group_of_lines": "Pendeltåg"},
           "deviations": [{"importance_level": 8, "message": "Stopp mellan Älvsjö och Flemingsberg."}]},
          {"destination": "Mörby station", "state": "CANCELLED", "display": "4 min",
           "scheduled": "2026-11-05T15:08:00",
           "stop_point": {"id": 52649, "designation": "B"},
           "line": {"designation": "178", "transport_mode": "BUS", "group_of_lines": "Blåbuss"},
           "deviations": []},
          {"destination": "Vaxholm", "display": "15:40", "expected": "2026-11-05T15:40:00",
           "line": {"designation": "80", "transport_mode": "FERRY"}},
          {"destination": "Somewhere", "display": "?", "expected": "not a time",
           "line": {"designation": "X", "transport_mode": "HOVERCRAFT"},
           "deviations": [{"message": "   "}]}
        ], "stop_deviations": []}
    """.trimIndent()

    @Test
    fun `parses the fields the app shows`() {
        val d = SlApi.parseDepartures(departuresBody)
        assertEquals(4, d.size)

        val train = d[0]
        assertEquals("41", train.line)
        assertEquals(Mode.TRAIN, train.mode)
        assertEquals("Pendeltåg", train.lineGroup)
        assertEquals("Södertälje centrum", train.destination)
        assertEquals("Nu", train.display)
        assertEquals("15:02", train.expected)
        assertEquals(LocalDateTime.of(2026, 11, 5, 15, 2, 30), train.expectedAt)
        assertEquals("3", train.platform)
        assertEquals(5051, train.stopPointId)
        assertFalse(train.cancelled)
        assertEquals("Stopp mellan Älvsjö och Flemingsberg.", train.deviation)
    }

    @Test
    fun `falls back to the scheduled time and reads cancellations`() {
        val bus = SlApi.parseDepartures(departuresBody)[1]
        assertEquals("15:08", bus.expected)
        assertTrue(bus.cancelled)
        assertNull(bus.deviation)
        assertEquals(Mode.BUS, bus.mode)
    }

    @Test
    fun `maps ferries to boats and unknown modes to other`() {
        val d = SlApi.parseDepartures(departuresBody)
        assertEquals(Mode.SHIP, d[2].mode)
        assertEquals(Mode.OTHER, d[3].mode)
        // No stop_point at all.
        assertEquals("", d[2].platform)
        assertEquals(0, d[2].stopPointId)
    }

    @Test
    fun `tolerates an unparsable time and a blank deviation`() {
        val odd = SlApi.parseDepartures(departuresBody)[3]
        assertNull(odd.expectedAt)
        assertNull(odd.deviation)
    }

    @Test
    fun `no departures key means no departures`() {
        assertTrue(SlApi.parseDepartures("""{"stop_deviations": []}""").isEmpty())
        assertTrue(SlApi.parseDepartures("""{"departures": []}""").isEmpty())
    }

    @Test
    fun `quota error is an error, not an empty list`() {
        try {
            SlApi.checkNotError("""{"error": "Quota has been exceeded"}""")
            fail("expected SlApiException")
        } catch (e: SlApiException) {
            assertTrue(e.quota)
            assertEquals("Quota has been exceeded", e.message)
        }
    }

    @Test
    fun `other errors are reported but not as quota`() {
        try {
            SlApi.checkNotError("""{"error": "Site not found"}""")
            fail("expected SlApiException")
        } catch (e: SlApiException) {
            assertFalse(e.quota)
            assertEquals("Site not found", e.message)
        }
    }

    @Test
    fun `real answers pass through unchanged`() {
        assertEquals(departuresBody, SlApi.checkNotError(departuresBody))
        val sites = """[{"id": 1, "name": "A"}]"""
        assertEquals(sites, SlApi.checkNotError(sites))
        // Departures that happen to carry an "error" field are still departures.
        val both = """{"departures": [], "error": "x"}"""
        assertEquals(both, SlApi.checkNotError(both))
        assertEquals("not json", SlApi.checkNotError("not json"))
    }

    @Test
    fun `parses sites with coordinates and skips closed ones`() {
        val body = """
            [
              {"id": 102, "name": "Styrsvik", "note": "Runmarö", "lat": 59.28, "lon": 18.73,
               "valid": {"from": "2012-06-23T00:00:00"}},
              {"id": 105, "name": "Idöborg", "lat": 59.2, "lon": 18.75},
              {"id": 200, "name": "Gone", "valid": {"from": "2010-01-01T00:00:00", "to": "2020-01-01T00:00:00"}},
              {"id": 201, "name": "Closes later", "valid": {"to": "2030-01-01T00:00:00"}},
              {"id": 202, "name": "No position"}
            ]
        """.trimIndent()
        val sites = SlApi.parseSites(body, today = "2026-11-05")
        assertEquals(listOf(102, 105, 201, 202), sites.map { it.id })
        assertEquals(Site(102, "Styrsvik", "Runmarö", 59.28, 18.73), sites[0])
        assertEquals("", sites[1].note)
        assertNull(sites[3].lat)
        assertNull(sites[3].lon)
    }
}
