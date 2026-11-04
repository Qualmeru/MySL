package se.mybus

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Favourite stops, kept in SharedPreferences as a JSON array of {id, name, hidden: [stop point ids]}.
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
        }
    }

    fun save(context: Context, stops: List<Stop>) {
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
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
