package se.mybus

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Favourite stops, kept in SharedPreferences as a JSON array of
// {id, name, hidden: [stop point ids], hiddenLines: [line designations]}, in the order shown.
object Favorites {
    private const val PREFS = "favorites"
    private const val KEY = "stops"

    // Only used before anything has been saved, i.e. on a fresh install.
    private val DEFAULT = if (BuildConfig.STARTER_STOPS) {
        listOf(
            Stop(5875, "Frihetsvägen"),
            Stop(9507, "Helenelunds station"),
        )
    } else {
        emptyList()
    }

    fun load(context: Context): List<Stop> {
        val json = prefs(context).getString(KEY, null) ?: return DEFAULT
        // Unreadable data shouldn't stop the app (or the widget) from starting at all.
        return runCatching { decode(json) }.getOrDefault(DEFAULT)
    }

    fun save(context: Context, stops: List<Stop>) {
        prefs(context).edit().putString(KEY, encode(stops)).apply()
    }

    internal fun encode(stops: List<Stop>): String {
        val arr = JSONArray()
        stops.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.siteId)
                    .put("name", it.name)
                    .put("hidden", JSONArray(it.hiddenStopPoints.toList()))
                    .put("hiddenLines", JSONArray(it.hiddenLines.toList()))
            )
        }
        return arr.toString()
    }

    // Older saves have no "hiddenLines", and the first version had no "hidden" either.
    internal fun decode(json: String): List<Stop> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val hidden = o.optJSONArray("hidden")
            val hiddenLines = o.optJSONArray("hiddenLines")
            Stop(
                o.getInt("id"),
                o.getString("name"),
                if (hidden == null) emptySet() else (0 until hidden.length()).map { hidden.getInt(it) }.toSet(),
                if (hiddenLines == null) emptySet() else (0 until hiddenLines.length()).map { hiddenLines.getString(it) }.toSet(),
            )
        }.distinctBy { it.siteId }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

// Moves the stop with this id one place up (by = -1) or down (by = 1); unchanged at either end.
fun List<Stop>.move(siteId: Int, by: Int): List<Stop> {
    val from = indexOfFirst { it.siteId == siteId }
    val to = from + by
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
