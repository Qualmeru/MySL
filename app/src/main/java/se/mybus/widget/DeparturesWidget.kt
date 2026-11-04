package se.mybus.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import se.mybus.Departure
import se.mybus.Favorites
import se.mybus.Settings
import se.mybus.SlApi
import se.mybus.Stop
import se.mybus.Strings
import se.mybus.friendlyError
import se.mybus.lineColor
import se.mybus.stringsFor

private const val MAX_PER_STOP_WIDGET = 5

private val WidgetBackground = ColorProvider(day = Color.White, night = Color(0xFF1C1B1F))
private val WidgetForeground = ColorProvider(day = Color(0xFF1C1B1F), night = Color.White)
private val WidgetSecondary = ColorProvider(day = Color(0xFF49454F), night = Color(0xFFCAC4D0))
private val WidgetOnBadge = ColorProvider(day = Color.White, night = Color.White)

private sealed interface StopResult {
    data class Loaded(val departures: List<Departure>) : StopResult
    data class Failed(val message: String) : StopResult
}

class DeparturesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = stringsFor(Settings.loadLanguage(context))
        val stops = Favorites.load(context)
        val results: List<Pair<Stop, StopResult>> = coroutineScope {
            stops.map { stop ->
                async {
                    stop to try {
                        StopResult.Loaded(SlApi.departures(stop))
                    } catch (e: Exception) {
                        StopResult.Failed(friendlyError(e, s))
                    }
                }
            }.awaitAll()
        }

        provideContent {
            WidgetContent(s, stops, results)
        }
    }
}

@Composable
private fun WidgetContent(s: Strings, stops: List<Stop>, results: List<Pair<Stop, StopResult>>) {
    Column(modifier = GlanceModifier.fillMaxSize().background(WidgetBackground)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                s.title,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = WidgetForeground),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                "↻",
                style = TextStyle(fontSize = 18.sp, color = WidgetForeground),
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()).padding(4.dp),
            )
        }

        var nextId = 0L
        fun nid() = nextId++

        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            if (stops.isEmpty()) {
                item(itemId = nid()) {
                    Text(
                        s.noFavorites,
                        style = TextStyle(color = WidgetSecondary),
                        modifier = GlanceModifier.padding(horizontal = 12.dp),
                    )
                }
            }
            for ((stop, result) in results) {
                item(itemId = nid()) {
                    Text(
                        stop.name,
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = WidgetForeground),
                        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                when (result) {
                    is StopResult.Failed -> item(itemId = nid()) {
                        Text(
                            s.couldNotFetch(result.message),
                            style = TextStyle(color = WidgetSecondary),
                            modifier = GlanceModifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    is StopResult.Loaded -> {
                        val shown = result.departures
                            .filter { it.stopPointId !in stop.hiddenStopPoints && it.line !in stop.hiddenLines }
                            .take(MAX_PER_STOP_WIDGET)
                        when {
                            result.departures.isEmpty() -> item(itemId = nid()) {
                                Text(
                                    s.noDepartures,
                                    style = TextStyle(color = WidgetSecondary),
                                    modifier = GlanceModifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                            }
                            shown.isEmpty() -> item(itemId = nid()) {
                                Text(
                                    s.noDeparturesSelectedDirections,
                                    style = TextStyle(color = WidgetSecondary),
                                    modifier = GlanceModifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                            }
                            else -> for (d in shown) {
                                item(itemId = nid()) { DepartureRowGlance(s, d) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DepartureRowGlance(s: Strings, d: Departure) {
    Row(
        verticalAlignment = Alignment.Vertical.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier
                .background(ColorProvider(day = lineColor(d), night = lineColor(d)))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(d.line, style = TextStyle(color = WidgetOnBadge, fontWeight = FontWeight.Bold, fontSize = 13.sp))
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(d.destination, style = TextStyle(color = WidgetForeground, fontSize = 14.sp), maxLines = 1)
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            s.display(d.display),
            style = TextStyle(fontWeight = FontWeight.Bold, color = WidgetForeground, fontSize = 14.sp),
        )
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        DeparturesWidget().updateAll(context)
    }
}
