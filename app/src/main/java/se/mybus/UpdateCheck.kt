package se.mybus

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// A newer release on GitHub. tag is as written there ("1.3.0" or "v1.3.0"), url is its page.
data class AvailableUpdate(val tag: String, val url: String) {
    val version: String get() = tag.removePrefix("v")
}

// Sideloaded apps never update by themselves, so at most once a day ask GitHub for the latest
// release and offer it in the app. That one request is far below GitHub's limit for apps
// without a login. If GitHub can't be reached it just tries again next time.
object UpdateCheck {
    private const val LATEST = "https://api.github.com/repos/Qualmeru/MySL/releases/latest"
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val PREFS = "update_check"
    private const val CHECKED_AT = "checked_at"
    private const val TAG = "tag"
    private const val URL_KEY = "url"
    private const val DISMISSED = "dismissed"

    // Null when this is the newest version, or "Not now" was tapped for that version.
    suspend fun available(context: Context, current: String = BuildConfig.VERSION_NAME): AvailableUpdate? {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(CHECKED_AT, 0L) >= DAY_MS) {
            fetchLatest()?.let { latest ->
                prefs.edit()
                    .putLong(CHECKED_AT, now)
                    .putString(TAG, latest.tag)
                    .putString(URL_KEY, latest.url)
                    .apply()
            }
        }
        val tag = prefs.getString(TAG, null) ?: return null
        val url = prefs.getString(URL_KEY, null) ?: return null
        if (!isNewer(tag, current) || prefs.getString(DISMISSED, null) == tag) return null
        return AvailableUpdate(tag, url)
    }

    // Hides the banner until an even newer version comes out.
    fun dismiss(context: Context, update: AvailableUpdate) {
        prefs(context).edit().putString(DISMISSED, update.tag).apply()
    }

    // Compares "1.10.0" > "1.9.2" by numbers, ignoring a leading "v" and anything after a "-".
    internal fun isNewer(tag: String, current: String): Boolean {
        val latest = numbers(tag)
        val installed = numbers(current)
        if (latest.isEmpty()) return false
        for (i in 0 until maxOf(latest.size, installed.size)) {
            val a = latest.getOrElse(i) { 0 }
            val b = installed.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun numbers(version: String): List<Int> =
        version.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: return emptyList() }

    internal fun parseLatest(body: String): AvailableUpdate? {
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val tag = o.optString("tag_name").ifBlank { return null }
        val url = o.optString("html_url").ifBlank { return null }
        return AvailableUpdate(tag, url)
    }

    private suspend fun fetchLatest(): AvailableUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(LATEST).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (conn.responseCode != 200) null
                else parseLatest(conn.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
