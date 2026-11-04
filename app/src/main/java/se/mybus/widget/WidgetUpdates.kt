package se.mybus.widget

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONObject
import se.mybus.Favorites
import se.mybus.Settings
import se.mybus.SlApi
import se.mybus.friendlyError
import se.mybus.stringsFor
import java.util.concurrent.TimeUnit

// The latest SL answer per stop, kept so the widget can always draw something.
// A failed refresh keeps the last good answer and records the error next to it.
object WidgetCache {
    private const val PREFS = "widget_cache"

    class Entry(val body: String?, val fetchedAt: Long, val error: String?)

    fun saveBody(context: Context, siteId: Int, body: String) {
        put(context, siteId, Entry(body, System.currentTimeMillis(), null))
    }

    fun saveError(context: Context, siteId: Int, error: String) {
        val previous = load(context, siteId)
        put(context, siteId, Entry(previous?.body, previous?.fetchedAt ?: 0L, error))
    }

    fun load(context: Context, siteId: Int): Entry? {
        val json = prefs(context).getString(key(siteId), null) ?: return null
        val o = JSONObject(json)
        return Entry(
            body = if (o.has("body")) o.getString("body") else null,
            fetchedAt = o.optLong("fetchedAt"),
            error = if (o.has("error")) o.getString("error") else null,
        )
    }

    private fun put(context: Context, siteId: Int, entry: Entry) {
        val o = JSONObject().put("fetchedAt", entry.fetchedAt)
        entry.body?.let { o.put("body", it) }
        entry.error?.let { o.put("error", it) }
        prefs(context).edit().putString(key(siteId), o.toString()).apply()
    }

    private fun key(siteId: Int) = "site_$siteId"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

// Bumped after every cache write; the widget reads it so Glance redraws from the new cache.
internal val CacheVersionKey = longPreferencesKey("cache_version")

suspend fun redrawWidgets(context: Context) {
    val ids = GlanceAppWidgetManager(context).getGlanceIds(DeparturesWidget::class.java)
    // Widgets placed before this version never got the periodic job; KEEP makes this a no-op after.
    if (ids.isNotEmpty()) WidgetUpdates.schedule(context)
    for (id in ids) {
        updateAppWidgetState(context, id) { it[CacheVersionKey] = System.currentTimeMillis() }
        DeparturesWidget().update(context, id)
    }
}

// Fetching happens here rather than in the widget itself: WorkManager only runs this once the
// app actually has network. A widget update runs in the background, where Android often blocks
// the app's network, so fetching there failed with "no internet" while the phone was online.
class WidgetUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val s = stringsFor(Settings.loadLanguage(applicationContext))
        var failed = false
        // One stop at a time: SL rejects simultaneous requests with HTTP 429.
        for (stop in Favorites.load(applicationContext)) {
            try {
                WidgetCache.saveBody(applicationContext, stop.siteId, SlApi.departuresJson(stop))
            } catch (e: Exception) {
                failed = true
                WidgetCache.saveError(applicationContext, stop.siteId, friendlyError(e, s))
            }
        }
        redrawWidgets(applicationContext)
        // Try again shortly rather than waiting for the next 15-minute round.
        return if (failed && runAttemptCount < 3) Result.retry() else Result.success()
    }
}

object WidgetUpdates {
    private const val PERIODIC = "widget-periodic"
    private const val NOW = "widget-now"

    private val needsNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    // Every 15 minutes, the shortest period Android allows for background work.
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(15, TimeUnit.MINUTES)
            .setConstraints(needsNetwork)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setConstraints(needsNetwork)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC)
            cancelUniqueWork(NOW)
        }
    }
}
