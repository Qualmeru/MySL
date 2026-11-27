package se.mybus.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import se.mybus.Departure
import se.mybus.DisruptionAlerts
import se.mybus.Favorites
import se.mybus.FilterHistory
import se.mybus.Settings
import se.mybus.SlApi
import se.mybus.Stop
import se.mybus.friendlyError
import se.mybus.stringsFor
import java.util.concurrent.TimeUnit

// The latest SL answer per stop, kept so the widget can always draw something.
// A failed refresh keeps the last good answer and records the error next to it.
object WidgetCache {
    private const val PREFS = "widget_cache"

    class Entry(val body: String?, val fetchedAt: Long, val error: String?)

    fun saveBody(context: Context, siteId: Int, body: String) {
        FilterHistory.record(context, siteId, SlApi.parseDepartures(body), load(context, siteId)?.body)
        put(context, siteId, Entry(body, System.currentTimeMillis(), null))
    }

    fun saveError(context: Context, siteId: Int, error: String) {
        val previous = load(context, siteId)
        put(context, siteId, Entry(previous?.body, previous?.fetchedAt ?: 0L, error))
    }

    // An unreadable entry counts as nothing cached (the next fetch replaces it) rather than
    // throwing inside the widget's drawing.
    fun load(context: Context, siteId: Int): Entry? {
        val json = prefs(context).getString(key(siteId), null) ?: return null
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
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
internal val RefreshStartedKey = longPreferencesKey("refresh_started")
internal fun isRefreshing(started: Long?, now: Long): Boolean =
    started != null && now - started in 0 until 120_000L

// After new data: redraw, and make sure the fetch job and minute redraws are running
// (widgets placed before these existed never started them; both calls are no-ops after).
suspend fun redrawWidgets(context: Context, refreshCompleted: Boolean = false) {
    if (redrawFromCache(context, refreshCompleted)) {
        WidgetUpdates.schedule(context)
        MinuteRedraw.start(context)
    }
}

// Redraws every widget (both kinds) from the cache without fetching. Returns false if none is placed.
suspend fun redrawFromCache(context: Context, refreshCompleted: Boolean = false): Boolean {
    val manager = GlanceAppWidgetManager(context)
    var any = false
    for (widget in listOf(DeparturesWidget(), SingleStopWidget())) {
        for (id in manager.getGlanceIds(widget.javaClass)) {
            any = true
            updateAppWidgetState(context, id) {
                it[CacheVersionKey] = System.currentTimeMillis()
                if (refreshCompleted) it.remove(RefreshStartedKey)
            }
            widget.update(context, id)
        }
    }
    return any
}

fun anyWidgetPlaced(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context) ?: return false
    return listOf(DeparturesWidgetReceiver::class.java, SingleStopWidgetReceiver::class.java).any {
        manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
    }
}

// After the last widget of a kind is removed: the minute redraws are only for widgets, but the
// 15-minute fetch also feeds disruption alerts, so it keeps running while those are on.
fun stopIfUnused(context: Context) {
    if (!anyWidgetPlaced(context)) MinuteRedraw.stop(context)
    WidgetUpdates.sync(context)
}

// Redraws the widget once a minute so departures drop off as they leave, without asking SL more
// often. The alarm is non-waking (RTC): it only fires while the phone is awake, and one that came
// due while the screen was off fires as soon as it turns on.
object MinuteRedraw {
    fun start(context: Context) {
        alarms(context).setRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + 60_000,
            60_000,
            pendingIntent(context),
        )
    }

    fun stop(context: Context) {
        alarms(context).cancel(pendingIntent(context))
    }

    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, MinuteRedrawReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class MinuteRedrawReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // The last widget was removed without onDisabled reaching us: stop ticking.
                if (!redrawFromCache(context)) MinuteRedraw.stop(context)
            } finally {
                pending.finish()
            }
        }
    }
}

// Fetching happens in WorkManager rather than the widget. Automatic work waits for a network;
// a manual refresh runs immediately so it can also report connection errors to the user.
// The same answers feed disruption alerts, avoiding duplicate requests to SL.
// Favourites, then any stop that only a one-stop widget shows.
internal fun stopsToFetch(context: Context): List<Stop> {
    val favorites = Favorites.load(context)
    val ids = favorites.map { it.siteId }.toSet()
    return favorites + SingleStopWidgets.allStops(context).filter { it.siteId !in ids }
}

class WidgetUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val s = stringsFor(Settings.loadLanguage(applicationContext))
        var failed = false
        val fetched = mutableMapOf<Stop, List<Departure>>()
        val favoriteIds = Favorites.load(applicationContext).map { it.siteId }.toSet()
        // One stop at a time: SL rejects simultaneous requests with HTTP 429.
        for (stop in stopsToFetch(applicationContext)) {
            try {
                val body = SlApi.departuresJson(stop)
                WidgetCache.saveBody(applicationContext, stop.siteId, body)
                // Alerts are for favourites; a stop only a one-stop widget shows doesn't notify.
                if (stop.siteId in favoriteIds) fetched[stop] = SlApi.parseDepartures(body)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
                WidgetCache.saveError(applicationContext, stop.siteId, friendlyError(e, s))
            }
        }
        redrawWidgets(applicationContext, refreshCompleted = true)
        DisruptionAlerts.check(applicationContext, fetched)
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

    fun refreshNow(context: Context, userInitiated: Boolean = false) {
        val builder = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
        if (userInitiated) {
            if (Build.VERSION.SDK_INT >= 31) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
        } else {
            builder.setConstraints(needsNetwork)
        }
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, builder.build())
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC)
            cancelUniqueWork(NOW)
        }
    }

    // Runs the 15-minute fetch while something needs it (a widget, or disruption alerts).
    fun sync(context: Context) {
        if (anyWidgetPlaced(context) || Settings.loadAlerts(context)) schedule(context) else cancel(context)
    }
}
