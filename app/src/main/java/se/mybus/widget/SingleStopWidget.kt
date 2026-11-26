package se.mybus.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import se.mybus.Favorites
import se.mybus.Settings
import se.mybus.Stop
import se.mybus.stringsFor

// Which stop each one-stop widget shows, by Android's widget id: any stop, not only a favourite,
// saved with its name so it keeps working if it's removed from favourites. Set by
// PickStopActivity when the widget is placed (or reconfigured), removed when the widget is.
object SingleStopWidgets {
    private const val PREFS = "single_stop_widgets"
    private const val STOP = "stop_"
    // Widgets set up before 1.4 saved only the id of a favourite, under this key.
    private const val OLD_ID = "widget_"

    fun stopFor(context: Context, appWidgetId: Int): Stop? {
        val p = prefs(context)
        p.getString(STOP + appWidgetId, null)?.let { json ->
            runCatching { Favorites.decode(json).firstOrNull() }.getOrNull()?.let { return it }
        }
        val oldId = p.getInt(OLD_ID + appWidgetId, -1).takeIf { it >= 0 } ?: return null
        return Favorites.load(context).firstOrNull { it.siteId == oldId }
    }

    // Saved without filters: a favourite's own filters (and later changes to them) apply on top.
    fun set(context: Context, appWidgetId: Int, stop: Stop) {
        prefs(context).edit()
            .putString(STOP + appWidgetId, Favorites.encode(listOf(Stop(stop.siteId, stop.name))))
            .remove(OLD_ID + appWidgetId)
            .apply()
    }

    fun remove(context: Context, appWidgetId: Int) {
        prefs(context).edit().remove(STOP + appWidgetId).remove(OLD_ID + appWidgetId).apply()
    }

    // Every stop a one-stop widget shows, for the background job to fetch.
    fun allStops(context: Context): List<Stop> =
        prefs(context).all.entries
            .filter { it.key.startsWith(STOP) }
            .mapNotNull { (it.value as? String)?.let { json -> runCatching { Favorites.decode(json).firstOrNull() }.getOrNull() } }
            .distinctBy { it.siteId }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

// The stop as the widget shows it: a favourite with its filters, or the saved stop as it is.
internal fun widgetStop(context: Context, saved: Stop): Stop =
    Favorites.load(context).firstOrNull { it.siteId == saved.siteId } ?: saved

// Like DeparturesWidget, but for one stop: small enough for a 2×2 spot, with room for more of that
// stop's departures. Uses a favourite's filters, and the same cache and refresh job.
class SingleStopWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        ensureFetching(context)
        provideContent {
            currentState(CacheVersionKey)
            val ctx = LocalContext.current
            val s = stringsFor(Settings.loadLanguage(ctx))
            val stop = SingleStopWidgets.stopFor(ctx, appWidgetId)?.let { widgetStop(ctx, it) }
            val theme = Settings.loadTheme(ctx)
            val refreshing = isRefreshing(currentState(RefreshStartedKey), System.currentTimeMillis())
            if (stop == null) {
                // Drawn while the stop is being picked, or for an old widget whose favourite was
                // removed: a tap opens the picker for this widget.
                val choose = actionStartActivity(
                    Intent(ctx, PickStopActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                WidgetContent(
                    s, s.title, emptyList(), lastFetch = null, showStopNames = false,
                    emptyText = s.tapToChooseStop, theme = theme, tap = choose, refreshing = refreshing,
                )
            } else {
                val views = loadViews(ctx, listOf(stop))
                WidgetContent(
                    s, stop.name, views, lastFetchOf(ctx, listOf(stop)),
                    showStopNames = false, maxPerStop = 8, theme = theme, refreshing = refreshing,
                )
            }
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        SingleStopWidgets.remove(context, GlanceAppWidgetManager(context).getAppWidgetId(glanceId))
        super.onDelete(context, glanceId)
    }
}

class SingleStopWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleStopWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdates.schedule(context)
        MinuteRedraw.start(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        stopIfUnused(context)
    }
}
