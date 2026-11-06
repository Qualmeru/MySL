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
import androidx.glance.action.actionStartActivity
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
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import se.mybus.Departure
import se.mybus.Favorites
import se.mybus.MainActivity
import se.mybus.Settings
import se.mybus.SlApi
import se.mybus.Stop
import se.mybus.Strings
import se.mybus.lineColor
import se.mybus.notLeftBy
import se.mybus.stringsFor
import se.mybus.timeLeft
import se.mybus.visible
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MAX_PER_STOP_WIDGET = 5

private val WidgetBackground = ColorProvider(day = Color.White, night = Color(0xFF1C1B1F))
private val WidgetForeground = ColorProvider(day = Color(0xFF1C1B1F), night = Color.White)
private val WidgetSecondary = ColorProvider(day = Color(0xFF49454F), night = Color(0xFFCAC4D0))
private val WidgetOnBadge = ColorProvider(day = Color.White, night = Color.White)
// Material's error colours, as the app uses for cancellations and disruptions.
private val WidgetError = ColorProvider(day = Color(0xFFB3261E), night = Color(0xFFF2B8B5))

private val TitleStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = WidgetForeground)
private val UpdatedStyle = TextStyle(fontSize = 13.sp, color = WidgetSecondary)
private val StopStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = WidgetForeground)
private val MessageStyle = TextStyle(fontSize = 15.sp, color = WidgetSecondary)
private val BadgeStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = WidgetOnBadge)
private val DestinationStyle = TextStyle(fontSize = 17.sp, color = WidgetForeground)
private val TimeStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = WidgetForeground)
private val NoticeStyle = TextStyle(fontSize = 13.sp, color = WidgetError)

private val HourMinute = DateTimeFormatter.ofPattern("HH:mm")

// What one stop shows: cached departures (possibly older, if the last refresh failed) and/or an error.
internal class StopView(val stop: Stop, val departures: List<Departure>?, val error: String?)

internal fun loadViews(context: Context, stops: List<Stop>): List<StopView> = stops.map { stop ->
    val entry = WidgetCache.load(context, stop.siteId)
    // A cache entry that no longer parses is treated like no data, rather than breaking the widget.
    val departures = entry?.body?.let { body -> runCatching { SlApi.parseDepartures(body) }.getOrNull() }
    StopView(stop, departures, entry?.error)
}

internal fun lastFetchOf(context: Context, stops: List<Stop>): Long? =
    stops.mapNotNull { WidgetCache.load(context, it.siteId)?.fetchedAt }.filter { it > 0 }.maxOrNull()

// E.g. right after installing this version, or after adding a stop: nothing cached yet, so fetch.
internal fun ensureFetching(context: Context) {
    if (Favorites.load(context).any { WidgetCache.load(context, it.siteId) == null }) {
        WidgetUpdates.schedule(context)
        WidgetUpdates.refreshNow(context)
        MinuteRedraw.start(context)
    }
}

class DeparturesWidget : GlanceAppWidget() {

    // Never fetches: WidgetUpdateWorker does that when the app has network, then bumps
    // CacheVersionKey, which recomposes this from the cache. MinuteRedraw bumps it too, so
    // departures that have left drop off within a minute.
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        ensureFetching(context)
        provideContent {
            currentState(CacheVersionKey)
            val ctx = LocalContext.current
            val s = stringsFor(Settings.loadLanguage(ctx))
            val stops = Favorites.load(ctx)
            WidgetContent(s, s.title, loadViews(ctx, stops), lastFetchOf(ctx, stops), showStopNames = true)
        }
    }
}

// Tapping anywhere but ↻ opens the app. Rows inside the LazyColumn need it set on each item,
// because taps on list items don't reach the outer column.
private fun openApp() = actionStartActivity<MainActivity>()

@Composable
internal fun WidgetContent(
    s: Strings,
    title: String,
    views: List<StopView>,
    lastFetch: Long?,
    showStopNames: Boolean,
    maxPerStop: Int = MAX_PER_STOP_WIDGET,
    emptyText: String = s.noFavorites,
) {
    Column(modifier = GlanceModifier.fillMaxSize().background(WidgetBackground).clickable(openApp())) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight().clickable(openApp())) {
                Text(title, style = TitleStyle, maxLines = 1)
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
        val now = LocalDateTime.now()

        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            if (views.isEmpty()) {
                item(itemId = nid()) { Message(emptyText) }
            }
            for (view in views) {
                val stop = view.stop
                if (showStopNames) {
                    item(itemId = nid()) {
                        Text(
                            stop.name,
                            style = StopStyle,
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .clickable(openApp())
                                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
                        )
                    }
                }
                val departures = view.departures
                when {
                    departures == null && view.error != null -> item(itemId = nid()) { Message(s.couldNotFetch(view.error)) }
                    departures == null -> item(itemId = nid()) { Message(s.loading) }
                    else -> {
                        view.error?.let { item(itemId = nid()) { Message(s.couldNotRefresh(it)) } }
                        val upcoming = departures.notLeftBy(now)
                        val shown = stop.visible(upcoming).take(maxPerStop)
                        when {
                            upcoming.isEmpty() -> item(itemId = nid()) { Message(s.noDepartures) }
                            shown.isEmpty() -> item(itemId = nid()) { Message(s.noDeparturesSelectedDirections) }
                            else -> for (d in shown) {
                                item(itemId = nid()) { DepartureRowGlance(s, d, now) }
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
    Text(
        text,
        style = MessageStyle,
        modifier = GlanceModifier.fillMaxWidth().clickable(openApp()).padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

@Composable
private fun DepartureRowGlance(s: Strings, d: Departure, now: LocalDateTime) {
    Row(
        verticalAlignment = Alignment.Vertical.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth().clickable(openApp()).padding(horizontal = 14.dp, vertical = 5.dp),
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
        // Same as the app: a cancelled departure is crossed out and says so, and SL's disruption
        // notice (delays, track faults...) goes in red under the destination.
        val strike = if (d.cancelled) TextDecoration.LineThrough else null
        val notice = listOfNotNull(s.cancelled.takeIf { d.cancelled }, d.deviation).joinToString(" · ")
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                d.destination,
                style = DestinationStyle.copy(textDecoration = strike),
                maxLines = 1,
            )
            if (notice.isNotEmpty()) {
                Text(notice, style = NoticeStyle, maxLines = 2)
            }
        }
        Spacer(modifier = GlanceModifier.width(10.dp))
        // Time left in bold, as in the app, recounted at every minute's redraw. The clock time
        // under it stays right even if the screen has been off and the redraw is a moment late.
        Column(horizontalAlignment = Alignment.Horizontal.End) {
            Text(
                timeLeft(d, now, s),
                style = if (d.cancelled) TimeStyle.copy(color = WidgetError, textDecoration = strike) else TimeStyle,
            )
            Text(d.expected, style = UpdatedStyle)
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetUpdates.refreshNow(context)
    }
}
