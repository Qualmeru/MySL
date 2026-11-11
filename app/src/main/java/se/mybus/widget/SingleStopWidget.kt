package se.mybus.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import se.mybus.Favorites
import se.mybus.Settings
import se.mybus.stringsFor

// Which favourite each one-stop widget shows, by Android's widget id. Set by PickStopActivity
// when the widget is placed (or reconfigured), removed when the widget is.
object SingleStopWidgets {
    private const val PREFS = "single_stop_widgets"

    fun siteFor(context: Context, appWidgetId: Int): Int? =
        prefs(context).getInt(key(appWidgetId), -1).takeIf { it >= 0 }

    fun set(context: Context, appWidgetId: Int, siteId: Int) {
        prefs(context).edit().putInt(key(appWidgetId), siteId).apply()
    }

    fun remove(context: Context, appWidgetId: Int) {
        prefs(context).edit().remove(key(appWidgetId)).apply()
    }

    private fun key(appWidgetId: Int) = "widget_$appWidgetId"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

// Like DeparturesWidget, but for one favourite: small enough for a 2×2 spot, with room for more
// of that stop's departures. Uses the stop's filters and the same cache and refresh job.
class SingleStopWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        ensureFetching(context)
        provideContent {
            currentState(CacheVersionKey)
            val ctx = LocalContext.current
            val s = stringsFor(Settings.loadLanguage(ctx))
            val siteId = SingleStopWidgets.siteFor(ctx, appWidgetId)
            val stop = Favorites.load(ctx).firstOrNull { it.siteId == siteId }
            val theme = Settings.loadTheme(ctx)
            if (stop == null) {
                // Drawn for a moment while the stop is being picked, or the stop was removed from
                // favourites since.
                val text = if (siteId == null) s.pickStopTitle else s.stopRemoved
                WidgetContent(s, s.title, emptyList(), lastFetch = null, showStopNames = false, emptyText = text, theme = theme)
            } else {
                val views = loadViews(ctx, listOf(stop))
                WidgetContent(
                    s, stop.name, views, lastFetchOf(ctx, listOf(stop)),
                    showStopNames = false, maxPerStop = 8, theme = theme,
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
