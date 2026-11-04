package se.mybus.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import se.mybus.Departure
import se.mybus.Favorites
import se.mybus.Settings
import se.mybus.SlApi
import se.mybus.Stop
import se.mybus.Strings
import se.mybus.lineColor
import se.mybus.stringsFor
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MAX_PER_STOP_WIDGET = 5

private val WidgetBackground = ColorProvider(day = Color.White, night = Color(0xFF1C1B1F))
private val WidgetForeground = ColorProvider(day = Color(0xFF1C1B1F), night = Color.White)
private val WidgetSecondary = ColorProvider(day = Color(0xFF49454F), night = Color(0xFFCAC4D0))
private val WidgetOnBadge = ColorProvider(day = Color.White, night = Color.White)

private val TitleStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = WidgetForeground)
private val UpdatedStyle = TextStyle(fontSize = 13.sp, color = WidgetSecondary)
private val StopStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = WidgetForeground)
private val MessageStyle = TextStyle(fontSize = 15.sp, color = WidgetSecondary)
private val BadgeStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = WidgetOnBadge)
private val DestinationStyle = TextStyle(fontSize = 17.sp, color = WidgetForeground)
private val TimeStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = WidgetForeground)

private val HourMinute = DateTimeFormatter.ofPattern("HH:mm")

// What one stop shows: cached departures (possibly older, if the last refresh failed) and/or an error.
private class StopView(val stop: Stop, val departures: List<Departure>?, val error: String?)

class DeparturesWidget : GlanceAppWidget() {

    // Never fetches: WidgetUpdateWorker does that when the app has network, then bumps
    // CacheVersionKey, which recomposes this from the cache.
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // E.g. right after installing this version, or after adding a stop: nothing cached yet.
        if (Favorites.load(context).any { WidgetCache.load(context, it.siteId) == null }) {
            WidgetUpdates.schedule(context)
            WidgetUpdates.refreshNow(context)
        }
        provideContent {
            currentState(CacheVersionKey)
            val ctx = LocalContext.current
            val s = stringsFor(Settings.loadLanguage(ctx))
            val stops = Favorites.load(ctx)
            val entries = stops.associate { it.siteId to WidgetCache.load(ctx, it.siteId) }
            val views = stops.map { stop ->
                val entry = entries[stop.siteId]
                StopView(stop, entry?.body?.let { SlApi.parseDepartures(it) }, entry?.error)
            }
            val lastFetch = entries.values.mapNotNull { it?.fetchedAt }.filter { it > 0 }.maxOrNull()
            WidgetContent(s, views, lastFetch)
        }
    }
}

@Composable
private fun WidgetContent(s: Strings, views: List<StopView>, lastFetch: Long?) {
    Column(modifier = GlanceModifier.fillMaxSize().background(WidgetBackground)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(s.title, style = TitleStyle)
                if (lastFetch != null) {
                    val time = Instant.ofEpochMilli(lastFetch).atZone(ZoneId.systemDefault()).format(HourMinute)
                    Text(s.updated(time), style = UpdatedStyle)
                }
            }
            Text(
                "↻",
                style = TextStyle(fontSize = 24.sp, color = WidgetForeground),
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()).padding(8.dp),
            )
        }

        var nextId = 0L
        fun nid() = nextId++
        // Departures that left since the last fetch are dropped (a minute's grace for "Nu").
        val now = LocalDateTime.now().minusMinutes(1)

        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            if (views.isEmpty()) {
                item(itemId = nid()) { Message(s.noFavorites) }
            }
            for (view in views) {
                val stop = view.stop
                item(itemId = nid()) {
                    Text(
                        stop.name,
                        style = StopStyle,
                        modifier = GlanceModifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                val departures = view.departures
                when {
                    departures == null && view.error != null -> item(itemId = nid()) { Message(s.couldNotFetch(view.error)) }
                    departures == null -> item(itemId = nid()) { Message(s.loading) }
                    else -> {
                        view.error?.let { item(itemId = nid()) { Message(s.couldNotRefresh(it)) } }
                        val upcoming = departures.filter { it.expectedAt == null || it.expectedAt >= now }
                        val shown = upcoming
                            .filter { it.stopPointId !in stop.hiddenStopPoints && it.line !in stop.hiddenLines }
                            .take(MAX_PER_STOP_WIDGET)
                        when {
                            upcoming.isEmpty() -> item(itemId = nid()) { Message(s.noDepartures) }
                            shown.isEmpty() -> item(itemId = nid()) { Message(s.noDeparturesSelectedDirections) }
                            else -> for (d in shown) {
                                item(itemId = nid()) { DepartureRowGlance(d) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MessageStyle, modifier = GlanceModifier.padding(horizontal = 14.dp, vertical = 4.dp))
}

@Composable
private fun DepartureRowGlance(d: Departure) {
    Row(
        verticalAlignment = Alignment.Vertical.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        // Fixed width so "41" and "178" line up (Glance has no min-width); fits up to "553H".
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier
                .width(58.dp)
                .background(ColorProvider(day = lineColor(d), night = lineColor(d)))
                .padding(vertical = 3.dp),
        ) {
            Text(d.line, style = BadgeStyle)
        }
        Spacer(modifier = GlanceModifier.width(10.dp))
        Text(d.destination, style = DestinationStyle, maxLines = 1, modifier = GlanceModifier.defaultWeight())
        Spacer(modifier = GlanceModifier.width(10.dp))
        // Clock time rather than "2 min": the widget only redraws every 15 minutes or so,
        // and a clock time stays right in between.
        Text(d.expected, style = TimeStyle)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetUpdates.refreshNow(context)
    }
}
