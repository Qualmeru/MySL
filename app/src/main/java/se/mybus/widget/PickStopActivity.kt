package se.mybus.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import se.mybus.Favorites
import se.mybus.LocalStrings
import se.mybus.Message
import se.mybus.Settings
import se.mybus.Stop
import se.mybus.ThemeMode
import se.mybus.appColorScheme
import se.mybus.stringsFor

// Shown by the launcher when a one-stop widget is placed (and when it's reconfigured on Android
// 12+): pick which favourite it shows. Backing out cancels placing the widget.
class PickStopActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_CANCELED, result)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val stops = Favorites.load(this)
        val strings = stringsFor(Settings.loadLanguage(this))
        val themeMode = Settings.loadTheme(this)

        setContent {
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            MaterialTheme(colorScheme = appColorScheme(dark, LocalContext.current)) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    PickStopScreen(stops) { stop -> pick(appWidgetId, stop, result) }
                }
            }
        }
    }

    private fun pick(appWidgetId: Int, stop: Stop, result: Intent) {
        SingleStopWidgets.set(this, appWidgetId, stop.siteId)
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@PickStopActivity).getGlanceIdBy(appWidgetId)
            SingleStopWidget().update(this@PickStopActivity, glanceId)
            WidgetUpdates.schedule(this@PickStopActivity)
            MinuteRedraw.start(this@PickStopActivity)
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun PickStopScreen(stops: List<Stop>, onPick: (Stop) -> Unit) {
    val s = LocalStrings.current
    Scaffold(topBar = { TopAppBar(title = { Text(s.pickStopTitle) }) }) { padding ->
        if (stops.isEmpty()) {
            Column(Modifier.padding(padding)) { Message(s.pickStopEmpty) }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(stops, key = { it.siteId }) { stop ->
                    ListItem(
                        headlineContent = { Text(stop.name) },
                        modifier = Modifier.clickable { onPick(stop) },
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                }
            }
        }
    }
}
