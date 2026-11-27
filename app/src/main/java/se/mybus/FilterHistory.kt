package se.mybus

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

internal data class FilterOptions(
    val directions: List<Direction> = emptyList(),
    val lines: List<LineEntry> = emptyList(),
) {
    fun observe(departures: List<Departure>): FilterOptions {
        val directionsById = directions.associateBy { it.stopPointId }.toMutableMap()
        for (current in directionsOf(departures)) {
            val previous = directionsById[current.stopPointId]
            directionsById[current.stopPointId] = current.copy(
                mode = current.mode.takeUnless { it == Mode.OTHER } ?: previous?.mode ?: Mode.OTHER,
                platform = current.platform.ifBlank { previous?.platform.orEmpty() },
                destinations = (current.destinations + previous?.destinations.orEmpty())
                    .filter { it.isNotBlank() }.distinct().take(3),
            )
        }
        val newLines = linesOf(departures).filter { it.line.isNotBlank() }
        val linesByName = lines.associateBy { it.line }.toMutableMap()
        for (line in newLines) {
            val previous = linesByName[line.line]
            linesByName[line.line] = if (line.mode == Mode.OTHER && previous != null) previous else line
        }
        return FilterOptions(sortDirections(directionsById.values), sortLines(linesByName.values))
    }

    fun includingHidden(stop: Stop): FilterOptions {
        val knownDirections = directions.map { it.stopPointId }.toSet()
        val knownLines = lines.map { it.line }.toSet()
        return FilterOptions(
            sortDirections(directions + (stop.hiddenStopPoints - knownDirections).map {
                Direction(it, Mode.OTHER, "", emptyList())
            }),
            sortLines(lines + (stop.hiddenLines - knownLines).map { LineEntry(it, Mode.OTHER) }),
        )
    }
}

// A local history of options observed at a stop, independent of the user's saved filters.
internal object FilterHistory {
    private const val PREFS = "filter_history"

    @Synchronized
    fun record(context: Context, siteId: Int, departures: List<Departure>, previousBody: String?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "site_$siteId"
        val saved = prefs.getString(key, null)
        val before = decodeSafely(saved)
        val previous = if (saved == null && previousBody != null) {
            try {
                SlApi.parseDepartures(previousBody)
            } catch (e: org.json.JSONException) {
                Log.w("FilterHistory", "Could not read previously cached departures", e)
                emptyList()
            }
        } else emptyList()
        val after = before.observe(previous).observe(departures)
        if (saved == null || after != before) prefs.edit().putString(key, encode(after)).apply()
    }

    @Synchronized
    fun rememberChoices(context: Context, siteId: Int, stopPoints: Set<Int>, lines: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "site_$siteId"
        val before = decodeSafely(prefs.getString(key, null))
        val after = before.includingHidden(Stop(siteId, "", stopPoints, lines))
        if (after != before) prefs.edit().putString(key, encode(after)).apply()
    }

    fun load(context: Context, siteId: Int): FilterOptions =
        decodeSafely(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("site_$siteId", null))

    private fun decodeSafely(json: String?): FilterOptions {
        if (json == null) return FilterOptions()
        return try {
            decode(json)
        } catch (e: org.json.JSONException) {
            Log.w("FilterHistory", "Could not read saved filter options", e)
            FilterOptions()
        }
    }

    internal fun encode(options: FilterOptions): String = JSONObject()
        .put("directions", JSONArray().apply {
            options.directions.forEach { direction ->
                put(JSONObject().put("id", direction.stopPointId).put("mode", direction.mode.name)
                    .put("platform", direction.platform).put("destinations", JSONArray(direction.destinations)))
            }
        })
        .put("lines", JSONArray().apply {
            options.lines.forEach { line ->
                put(JSONObject().put("line", line.line).put("mode", line.mode.name))
            }
        }).toString()

    internal fun decode(json: String): FilterOptions {
        val obj = JSONObject(json)
        val directions = obj.getJSONArray("directions")
        val lines = obj.getJSONArray("lines")
        fun mode(value: String): Mode = Mode.entries.firstOrNull { it.name == value } ?: Mode.OTHER
        return FilterOptions(
            (0 until directions.length()).map { i ->
                val d = directions.getJSONObject(i)
                val destinations = d.getJSONArray("destinations")
                Direction(d.getInt("id"), mode(d.getString("mode")), d.getString("platform"),
                    (0 until destinations.length()).map { destinations.getString(it) })
            },
            (0 until lines.length()).map { i ->
                val line = lines.getJSONObject(i)
                LineEntry(line.getString("line"), mode(line.getString("mode")))
            },
        )
    }
}
