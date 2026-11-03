package se.mybus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

// hiddenStopPoints: stop points (one per street side / platform, i.e. direction) the user filtered out.
data class Stop(val siteId: Int, val name: String, val hiddenStopPoints: Set<Int> = emptySet())

// A stop from SL's full site list; note is the area or street that tells same-named stops apart.
data class Site(val id: Int, val name: String, val note: String)

// SL's transport_mode values.
enum class Mode { BUS, TRAIN, METRO, TRAM, SHIP, OTHER }

data class Departure(
    val line: String,
    val mode: Mode,
    // e.g. "Blåbuss", "Tunnelbanans gröna linje"; picks the badge colour.
    val lineGroup: String,
    val destination: String,
    val display: String,
    val expected: String,
    val platform: String,
    val stopPointId: Int,
    val cancelled: Boolean,
    val deviation: String?,
)

// SL Transport API (Trafiklab), open and keyless:
// https://www.trafiklab.se/api/our-apis/sl/transport/
object SlApi {
    private const val BASE = "https://transport.integration.sl.se/v1"
    private const val MAX_ATTEMPTS = 3
    private const val EMPTY_ATTEMPTS = 3

    // ~6500 sites; the API has no name search, so fetch once and filter on the phone.
    private var sitesCache: List<Site>? = null

    // All modes (bus, train, metro, tram, boat). The API now and then answers 200 with an
    // empty list while traffic is running, so only believe "nothing" after hearing it a few times.
    suspend fun departures(stop: Stop): List<Departure> {
        val url = URL("$BASE/sites/${stop.siteId}/departures?forecast=60")
        repeat(EMPTY_ATTEMPTS - 1) {
            val departures = parseDepartures(fetch(url))
            if (departures.isNotEmpty()) return departures
            delay(1_000)
        }
        return parseDepartures(fetch(url))
    }

    suspend fun allSites(): List<Site> {
        sitesCache?.let { return it }
        val sites = withContext(Dispatchers.Default) { parseSites(fetch(URL("$BASE/sites?expand=false"))) }
        sitesCache = sites
        return sites
    }

    // The API answers 429 when requests arrive too close together, so back off and retry.
    private suspend fun fetch(url: URL): String {
        repeat(MAX_ATTEMPTS - 1) { attempt ->
            when (val r = get(url)) {
                is Response.Ok -> return r.body
                is Response.TooManyRequests -> delay(r.retryAfterMs ?: (1_000L shl attempt))
            }
        }
        return when (val r = get(url)) {
            is Response.Ok -> r.body
            is Response.TooManyRequests -> error("HTTP 429")
        }
    }

    private sealed interface Response {
        class Ok(val body: String) : Response
        class TooManyRequests(val retryAfterMs: Long?) : Response
    }

    private suspend fun get(url: URL): Response = withContext(Dispatchers.IO) {
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        try {
            when (conn.responseCode) {
                200 -> Response.Ok(conn.inputStream.bufferedReader().use { it.readText() })
                429 -> Response.TooManyRequests(
                    conn.getHeaderField("Retry-After")?.toLongOrNull()?.times(1_000)
                )
                else -> error("HTTP ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseDepartures(body: String): List<Departure> {
        val arr = JSONObject(body).optJSONArray("departures") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val d = arr.getJSONObject(i)
            val deviations = d.optJSONArray("deviations")
            val line = d.getJSONObject("line")
            Departure(
                line = line.optString("designation"),
                mode = when (line.optString("transport_mode")) {
                    "BUS" -> Mode.BUS
                    "TRAIN" -> Mode.TRAIN
                    "METRO" -> Mode.METRO
                    "TRAM" -> Mode.TRAM
                    "SHIP", "FERRY" -> Mode.SHIP
                    else -> Mode.OTHER
                },
                lineGroup = line.optString("group_of_lines"),
                destination = d.optString("destination"),
                display = d.optString("display"),
                // "2026-11-03T20:51:03" -> "20:51"
                expected = d.optString("expected", d.optString("scheduled")).substringAfter('T').take(5),
                platform = d.optJSONObject("stop_point")?.optString("designation").orEmpty(),
                stopPointId = d.optJSONObject("stop_point")?.optInt("id") ?: 0,
                cancelled = d.optString("state") == "CANCELLED",
                deviation = if (deviations != null && deviations.length() > 0)
                    deviations.getJSONObject(0).optString("message").ifBlank { null } else null,
            )
        }
    }

    private fun parseSites(body: String): List<Site> {
        val arr = JSONArray(body)
        val today = LocalDate.now().toString()
        return (0 until arr.length()).mapNotNull { i ->
            val s = arr.getJSONObject(i)
            // Skip stops that have been taken out of service.
            val validTo = s.optJSONObject("valid")?.optString("to").orEmpty()
            if (validTo.isNotEmpty() && validTo.take(10) < today) return@mapNotNull null
            Site(s.getInt("id"), s.optString("name"), s.optString("note"))
        }
    }
}
