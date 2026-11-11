package se.mybus.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
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
import se.mybus.ThemeMode
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

// The widget's colours as (light, dark) pairs. Which one shows follows the app's theme setting:
// the phone's light/dark mode for "System default", otherwise always the chosen one.
internal object WidgetPalette {
    val background = Color.White to Color(0xFF1C1B1F)
    val foreground = Color(0xFF1C1B1F) to Color.White
    val secondary = Color(0xFF49454F) to Color(0xFFCAC4D0)
    val onBadge = Color.White to Color.White
    // Material's error colours, as the app uses for cancellations and disruptions.
    val error = Color(0xFFB3261E) to Color(0xFFF2B8B5)
}

// The (day, night) colours to hand Glance for this theme: forced light or dark shows the same
// colour whatever the phone's mode is.
internal fun themed(mode: ThemeMode, colors: Pair<Color, Color>): Pair<Color, Color> = when (mode) {
    ThemeMode.SYSTEM -> colors
    ThemeMode.LIGHT -> colors.first to colors.first
    ThemeMode.DARK -> colors.second to colors.second
}

internal class WidgetStyles(private val mode: ThemeMode) {
    private fun color(colors: Pair<Color, Color>) =
        themed(mode, colors).let { (day, night) -> ColorProvider(day = day, night = night) }

    val background = color(WidgetPalette.background)
    val foreground = color(WidgetPalette.foreground)
    val error = color(WidgetPalette.error)
    private val secondary = color(WidgetPalette.secondary)

    val title = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = foreground)
    val updated = TextStyle(fontSize = 13.sp, color = secondary)
    val stop = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = foreground)
    val message = TextStyle(fontSize = 15.sp, color = secondary)
    val badge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color(WidgetPalette.onBadge))
    val destination = TextStyle(fontSize = 17.sp, color = foreground)
    val time = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = foreground)
    val notice = TextStyle(fontSize = 13.sp, color = error)
}

// Provided by WidgetContent, so the rows don't each need it passed in.
private val LocalWidgetStyles = staticCompositionLocalOf { WidgetStyles(ThemeMode.SYSTEM) }

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
            WidgetContent(
                s, s.title, loadViews(ctx, stops), lastFetchOf(ctx, stops),
                showStopNames = true, theme = Settings.loadTheme(ctx),
            )
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
    theme: ThemeMode = ThemeMode.SYSTEM,
) {
    val styles = WidgetStyles(theme)
    CompositionLocalProvider(LocalWidgetStyles provides styles) {
        WidgetBody(s, title, views, lastFetch, showStopNames, maxPerStop, emptyText)
    }
}

@Composable
private fun WidgetBody(
    s: Strings,
    title: String,
    views: List<StopView>,
    lastFetch: Long?,
    showStopNames: Boolean,
    maxPerStop: Int,
    emptyText: String,
) {
    val styles = LocalWidgetStyles.current
    Column(modifier = GlanceModifier.fillMaxSize().background(styles.background).clickable(openApp())) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight().clickable(openApp())) {
                Text(title, style = styles.title, maxLines = 1)
                if (lastFetch != null) {
                    val time = Instant.ofEpochMilli(lastFetch).atZone(ZoneId.systemDefault()).format(HourMinute)
                    Text(s.updated(time), style = styles.updated)
                }
            }
            Text(
                "↻",
                style = TextStyle(fontSize = 24.sp, color = styles.foreground),
                modifier = GlanceModifier
                    .clickable(actionRunCallback<RefreshAction>())
                    .semantics { contentDescription = s.refresh }
                    .padding(8.dp),
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
                            style = LocalWidgetStyles.current.stop,
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
        style = LocalWidgetStyles.current.message,
        modifier = GlanceModifier.fillMaxWidth().clickable(openApp()).padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

@Composable
private fun DepartureRowGlance(s: Strings, d: Departure, now: LocalDateTime) {
    val styles = LocalWidgetStyles.current
    val left = timeLeft(d, now, s)
    Row(
        verticalAlignment = Alignment.Vertical.CenterVertically,
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(openApp())
            // TalkBack reads the row as one sentence instead of badge, destination and time apart.
            .semantics { contentDescription = s.describe(d, left) }
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        // Fixed width so "41" and "178" line up (Glance has no min-width); fits up to "553H".
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier
                .width(58.dp)
                .background(ColorProvider(day = lineColor(d), night = lineColor(d)))
                .padding(vertical = 3.dp),
        ) {
            Text(d.line, style = styles.badge)
        }
        Spacer(modifier = GlanceModifier.width(10.dp))
        // Same as the app: a cancelled departure is crossed out and says so, and SL's disruption
        // notice (delays, track faults...) goes in red under the destination.
        val strike = if (d.cancelled) TextDecoration.LineThrough else null
        val notice = listOfNotNull(s.cancelled.takeIf { d.cancelled }, d.deviation).joinToString(" · ")
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                d.destination,
                style = styles.destination.copy(textDecoration = strike),
                maxLines = 1,
            )
            if (notice.isNotEmpty()) {
                Text(notice, style = styles.notice, maxLines = 2)
            }
        }
        Spacer(modifier = GlanceModifier.width(10.dp))
        // Time left in bold, as in the app, recounted at every minute's redraw. The clock time
        // under it stays right even if the screen has been off and the redraw is a moment late.
        Column(horizontalAlignment = Alignment.Horizontal.End) {
            Text(
                left,
                style = if (d.cancelled) styles.time.copy(color = styles.error, textDecoration = strike) else styles.time,
            )
            Text(d.expected, style = styles.updated)
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetUpdates.refreshNow(context)
    }
}
