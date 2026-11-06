package se.mybus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime

// hiddenStopPoints: stop points (one per street side / platform, i.e. direction) the user filtered out.
// hiddenLines: line designations (e.g. "179") the user filtered out at this stop.
data class Stop(
    val siteId: Int,
    val name: String,
    val hiddenStopPoints: Set<Int> = emptySet(),
    val hiddenLines: Set<String> = emptySet(),
)

// A stop from SL's full site list; note is the area or street that tells same-named stops apart.
// lat/lon are missing for a handful of sites.
data class Site(val id: Int, val name: String, val note: String, val lat: Double? = null, val lon: Double? = null)

// SL answered with {"error": "..."} instead of data, e.g. "Quota has been exceeded" when too many
// requests reach it from the same network. quota tells that case apart so it can say "try later".
// retryAfterMs is SL's own Retry-After, when it sent one.
class SlApiException(message: String, val quota: Boolean, val retryAfterMs: Long? = null) : Exception(message)

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
    // Full local time, so cached departures that have already left can be dropped.
    val expectedAt: LocalDateTime?,
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
    suspend fun departures(stop: Stop): List<Departure> = parseDepartures(departuresJson(stop))

    // The raw answer, for callers that cache it (the widget) and parse it later.
    suspend fun departuresJson(stop: Stop): String {
        val url = URL("$BASE/sites/${stop.siteId}/departures?forecast=60")
        repeat(EMPTY_ATTEMPTS - 1) {
            val body = fetch(url)
            if (parseDepartures(body).isNotEmpty()) return body
            delay(1_000)
        }
        return fetch(url)
    }

    suspend fun allSites(): List<Site> {
        sitesCache?.let { return it }
        val sites = withContext(Dispatchers.Default) {
            parseSites(fetch(URL("$BASE/sites?expand=false")), LocalDate.now().toString())
        }
        sitesCache = sites
        return sites
    }

    // Every request goes through here, so it's also where the shared cool-down is kept: none is
    // sent while SL is refusing us, and a refusal starts (or lengthens) the pause for everyone.
    private suspend fun fetch(url: URL): String {
        SlCooldown.check()
        try {
            return fetchWithRetries(url).also { SlCooldown.succeeded() }
        } catch (e: SlApiException) {
            if (e.quota) SlCooldown.refused(e.retryAfterMs)
            throw e
        }
    }

    // The API answers 429 when requests arrive too close together, so back off and retry.
    private suspend fun fetchWithRetries(url: URL): String {
        repeat(MAX_ATTEMPTS - 1) { attempt ->
            when (val r = get(url)) {
                is Response.Ok -> return checkNotError(r.body)
                is Response.TooManyRequests -> delay(r.retryAfterMs ?: (1_000L shl attempt))
            }
        }
        return when (val r = get(url)) {
            is Response.Ok -> checkNotError(r.body)
            is Response.TooManyRequests -> throw SlApiException("HTTP 429", quota = true, retryAfterMs = r.retryAfterMs)
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
            when (val code = conn.responseCode) {
                200 -> Response.Ok(conn.inputStream.bufferedReader().use { it.readText() })
                429 -> Response.TooManyRequests(
                    conn.getHeaderField("Retry-After")?.toLongOrNull()?.times(1_000)
                )
                else -> {
                    // SL explains refusals (quota and the like) in the body; prefer that to a bare code.
                    val body = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
                    throw errorIn(body) ?: SlApiException("HTTP $code", quota = false)
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    // Throws if SL sent {"error": "..."} instead of data. That comes with HTTP 200 at times, and
    // would otherwise read as "no departures" and be cached by the widget as a good answer.
    internal fun checkNotError(body: String): String {
        errorIn(body)?.let { throw it }
        return body
    }

    private fun errorIn(body: String): SlApiException? {
        if (!body.trimStart().startsWith("{")) return null
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (o.has("departures") || !o.has("error")) return null
        val message = o.optString("error").ifBlank { "Unknown error" }
        return SlApiException(message, quota = "quota" in message.lowercase())
    }

    fun parseDepartures(body: String): List<Departure> {
        val arr = JSONObject(body).optJSONArray("departures") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val d = arr.getJSONObject(i)
            val deviations = d.optJSONArray("deviations")
            val line = d.getJSONObject("line")
            val time = d.optString("expected", d.optString("scheduled"))
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
                expected = time.substringAfter('T').take(5),
                expectedAt = runCatching { LocalDateTime.parse(time) }.getOrNull(),
                platform = d.optJSONObject("stop_point")?.optString("designation").orEmpty(),
                stopPointId = d.optJSONObject("stop_point")?.optInt("id") ?: 0,
                cancelled = d.optString("state") == "CANCELLED",
                deviation = if (deviations != null && deviations.length() > 0)
                    deviations.getJSONObject(0).optString("message").ifBlank { null } else null,
            )
        }
    }

    // today is "yyyy-MM-dd"; a parameter so tests don't depend on the date.
    internal fun parseSites(body: String, today: String): List<Site> {
        val arr = JSONArray(body)
        return (0 until arr.length()).mapNotNull { i ->
            val s = arr.getJSONObject(i)
            // Skip stops that have been taken out of service.
            val validTo = s.optJSONObject("valid")?.optString("to").orEmpty()
            if (validTo.isNotEmpty() && validTo.take(10) < today) return@mapNotNull null
            Site(
                id = s.getInt("id"),
                name = s.optString("name"),
                note = s.optString("note"),
                lat = if (s.has("lat")) s.optDouble("lat").takeUnless { it.isNaN() } else null,
                lon = if (s.has("lon")) s.optDouble("lon").takeUnless { it.isNaN() } else null,
            )
        }
    }
}
