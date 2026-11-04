package se.mybus

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import se.mybus.widget.WidgetCache
import se.mybus.widget.WidgetUpdates
import se.mybus.widget.redrawWidgets
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val REFRESH_MS = 60_000L
private const val MAX_PER_STOP = 10

sealed interface StopState {
    data object Loading : StopState
    // refreshError is set when the latest refresh failed and these departures are older.
    data class Loaded(val departures: List<Departure>, val refreshError: String? = null) : StopState
    data class Failed(val message: String) : StopState
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var themeMode by remember { mutableStateOf(Settings.loadTheme(this)) }
            var language by remember { mutableStateOf(Settings.loadLanguage(this)) }
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status and navigation bar icons must follow the app's theme, not only the phone's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
                    ) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF),
                        android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b),
                    ) { dark },
                )
                onDispose {}
            }
            val ctx = LocalContext.current
            val colors = if (Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            } else {
                if (dark) darkColorScheme() else lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) {
                CompositionLocalProvider(LocalStrings provides stringsFor(language)) {
                    App(
                        settingsMenu = {
                            SettingsMenu(
                                themeMode = themeMode,
                                onThemeMode = { themeMode = it; Settings.saveTheme(this, it) },
                                language = language,
                                onLanguage = { language = it; Settings.saveLanguage(this, it) },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun App(settingsMenu: @Composable () -> Unit) {
    val context = LocalContext.current
    var favorites by remember { mutableStateOf(Favorites.load(context)) }
    var searching by rememberSaveable { mutableStateOf(false) }
    // Kept here so departures survive a trip to the search screen.
    val states = remember { mutableStateMapOf<Int, StopState>() }

    fun setFavorites(stops: List<Stop>) {
        favorites = stops
        Favorites.save(context, stops)
        // The home-screen widget shows the same stops and filters.
        WidgetUpdates.refreshNow(context)
    }

    if (searching) {
        StopSearchScreen(
            favoriteIds = favorites.map { it.siteId }.toSet(),
            onPick = { setFavorites(favorites + it); searching = false },
            onBack = { searching = false },
        )
    } else {
        DeparturesScreen(
            favorites = favorites,
            states = states,
            settingsMenu = settingsMenu,
            onAdd = { searching = true },
            onRemove = { stop -> setFavorites(favorites.filter { it.siteId != stop.siteId }) },
            onSetHidden = { stop, hidden, hiddenLines ->
                setFavorites(favorites.map {
                    if (it.siteId == stop.siteId) it.copy(hiddenStopPoints = hidden, hiddenLines = hiddenLines) else it
                })
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeparturesScreen(
    favorites: List<Stop>,
    states: SnapshotStateMap<Int, StopState>,
    settingsMenu: @Composable () -> Unit,
    onAdd: () -> Unit,
    onRemove: (Stop) -> Unit,
    onSetHidden: (Stop, Set<Int>, Set<String>) -> Unit,
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    var updatedAt by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Stop?>(null) }
    var filtering by remember { mutableStateOf<Stop?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // The polling loop outlives recompositions, so read the latest list through this.
    val currentFavorites by rememberUpdatedState(favorites)

    suspend fun refresh() {
        if (refreshing) return
        refreshing = true
        // One stop at a time: SL rejects simultaneous requests with HTTP 429.
        for (stop in currentFavorites) {
            states[stop.siteId] = try {
                val body = SlApi.departuresJson(stop)
                // Opening the app also freshens the widget, which can't always fetch on its own.
                WidgetCache.saveBody(context, stop.siteId, body)
                // Keep every departure; the direction filter needs all stop points to offer them.
                StopState.Loaded(SlApi.parseDepartures(body))
            } catch (e: Exception) {
                val message = friendlyError(e, s)
                when (val previous = states[stop.siteId]) {
                    is StopState.Loaded -> previous.copy(refreshError = message)
                    else -> StopState.Failed(message)
                }
            }
        }
        updatedAt = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        refreshing = false
        redrawWidgets(context)
    }

    // Poll every minute, but only while the app is on screen.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refresh()
                delay(REFRESH_MS)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(s.title)
                        Text(
                            updatedAt?.let { s.updated(it) } ?: s.fetching,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { refresh() } }, enabled = !refreshing) {
                        // The spinner takes the icon's place so the bar doesn't shift.
                        if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = s.refresh)
                    }
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = s.addStop)
                    }
                    settingsMenu()
                },
            )
        },
    ) { padding ->
        // Drag down at the top of the list to fetch right away.
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refresh() } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (favorites.isEmpty()) {
                    item { Message(s.noFavorites) }
                }
                for (stop in favorites) {
                    item(key = "h${stop.siteId}") {
                        StopHeader(
                            stop = stop,
                            onFilter = { filtering = stop },
                            onRemove = { confirmRemove = stop },
                        )
                    }
                    when (val state = states[stop.siteId] ?: StopState.Loading) {
                        StopState.Loading -> item { Message(s.loading) }
                        is StopState.Failed -> item { Message(s.couldNotFetch(state.message)) }
                        is StopState.Loaded -> {
                            state.refreshError?.let { item { Message(s.couldNotRefresh(it)) } }
                            val shown = state.departures
                                .filter { it.stopPointId !in stop.hiddenStopPoints && it.line !in stop.hiddenLines }
                                .take(MAX_PER_STOP)
                            when {
                                state.departures.isEmpty() -> item { Message(s.noDepartures) }
                                shown.isEmpty() -> item { Message(s.noDeparturesSelectedDirections) }
                                else -> items(shown) { DepartureRow(it) }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmRemove?.let { stop ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(s.removeTitle(stop.name)) },
            text = { Text(s.removeText) },
            confirmButton = {
                TextButton(onClick = { onRemove(stop); confirmRemove = null }) { Text(s.remove) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text(s.cancel) }
            },
        )
    }

    filtering?.let { stop ->
        DirectionFilterDialog(
            stop = stop,
            departures = (states[stop.siteId] as? StopState.Loaded)?.departures.orEmpty(),
            onSave = { hidden, hiddenLines -> onSetHidden(stop, hidden, hiddenLines); filtering = null },
            onDismiss = { filtering = null },
        )
    }
}

@Composable
private fun SettingsMenu(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    language: Language,
    onLanguage: (Language) -> Unit,
) {
    val s = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = s.settings)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuHeading(s.theme)
            for (mode in ThemeMode.entries) {
                CheckedMenuItem(
                    s.themeLabel(mode),
                    checked = mode == themeMode,
                    leading = { Icon(mode.icon, contentDescription = null) },
                ) {
                    open = false
                    onThemeMode(mode)
                }
            }
            HorizontalDivider()
            MenuHeading(s.language)
            for (lang in Language.entries) {
                // Flags and codes read the same whichever language is active.
                CheckedMenuItem(
                    lang.label ?: s.followPhone,
                    checked = lang == language,
                    leading = {
                        val flag = lang.flag
                        if (flag != null) {
                            Icon(
                                flag,
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.clip(RoundedCornerShape(2.dp)),
                            )
                        } else {
                            // Keeps "System default" lined up with the flagged rows.
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                ) {
                    open = false
                    onLanguage(lang)
                }
            }
        }
    }
}

@Composable
private fun MenuHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun CheckedMenuItem(
    text: String,
    checked: Boolean,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = leading,
        trailingIcon = {
            if (checked) Icon(Icons.Default.Check, contentDescription = LocalStrings.current.selected)
            else Spacer(Modifier.size(24.dp))
        },
        onClick = onClick,
    )
}

@Composable
private fun StopHeader(stop: Stop, onFilter: () -> Unit, onRemove: () -> Unit) {
    val s = LocalStrings.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stop.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (stop.hiddenStopPoints.isNotEmpty()) {
                Text(
                    s.directionsHidden(stop.hiddenStopPoints.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = s.optionsFor(stop.name))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(s.chooseDirections) },
                    onClick = { menuOpen = false; onFilter() },
                )
                DropdownMenuItem(
                    text = { Text(s.remove) },
                    onClick = { menuOpen = false; onRemove() },
                )
            }
        }
    }
}

// A stop point is one side of the street or one platform/track, so it stands for a direction.
// Trains and buses at the same station use different stop points, so this also filters by mode.
private data class Direction(
    val stopPointId: Int,
    val mode: Mode,
    val platform: String,
    val destinations: List<String>,
)

private fun directionsOf(departures: List<Departure>): List<Direction> =
    departures.groupBy { it.stopPointId }.map { (id, deps) ->
        Direction(
            stopPointId = id,
            mode = deps.first().mode,
            platform = deps.first().platform,
            destinations = deps.groupingBy { it.destination }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key },
        )
    }.sortedWith(compareBy({ it.mode }, { it.platform }, { it.destinations.firstOrNull() }))

private data class LineEntry(val line: String, val mode: Mode)

private fun linesOf(departures: List<Departure>): List<LineEntry> =
    departures.map { LineEntry(it.line, it.mode) }.distinct()
        .sortedWith(compareBy({ it.mode }, { it.line.toIntOrNull() ?: Int.MAX_VALUE }, { it.line }))

@Composable
private fun DirectionFilterDialog(
    stop: Stop,
    departures: List<Departure>,
    onSave: (Set<Int>, Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val directions = remember(departures) { directionsOf(departures) }
    val lines = remember(departures) { linesOf(departures) }
    // Only name the mode when the stop has more than one, e.g. buses and trains at a station.
    val mixedModes = directions.map { it.mode }.distinct().size > 1
    var hidden by remember { mutableStateOf(stop.hiddenStopPoints) }
    var hiddenLines by remember { mutableStateOf(stop.hiddenLines) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.directionsTitle(stop.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (directions.isEmpty()) {
                    Text(s.noDirectionsNow)
                } else {
                    if (directions.size == 1) {
                        Text(s.singleDirection)
                    } else {
                        Text(s.showDeparturesGoing, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        for (d in directions) {
                            val shown = d.stopPointId !in hidden
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { hidden = if (shown) hidden + d.stopPointId else hidden - d.stopPointId },
                            ) {
                                Checkbox(checked = shown, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.padding(vertical = 8.dp)) {
                                    Text(s.towards(d.destinations.take(3).joinToString(", ")))
                                    val detail = listOfNotNull(
                                        s.modeName(d.mode).takeIf { mixedModes },
                                        s.platform(d.platform, d.mode).takeIf { d.platform.isNotBlank() },
                                    ).joinToString(" · ")
                                    if (detail.isNotEmpty()) {
                                        Text(
                                            detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (lines.size > 1) {
                        Spacer(Modifier.height(16.dp))
                        Text(s.showLinesGoing, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        for (l in lines) {
                            val shown = l.line !in hiddenLines
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        hiddenLines = if (shown) hiddenLines + l.line else hiddenLines - l.line
                                    },
                            ) {
                                Checkbox(checked = shown, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.padding(vertical = 8.dp)) {
                                    Text(l.line, fontWeight = FontWeight.Bold)
                                    if (mixedModes) {
                                        Text(
                                            s.modeName(l.mode),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(hidden, hiddenLines) }) { Text(s.save) }
        },
        dismissButton = {
            Row {
                if (hidden.isNotEmpty() || hiddenLines.isNotEmpty()) {
                    TextButton(onClick = { onSave(emptySet(), emptySet()) }) { Text(s.showAll) }
                }
                TextButton(onClick = onDismiss) { Text(s.cancel) }
            }
        },
    )
}

@Composable
fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun DepartureRow(d: Departure) {
    val s = LocalStrings.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .widthIn(min = 52.dp)
                .background(lineColor(d), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(d.line, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                d.destination,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (d.cancelled) TextDecoration.LineThrough else null,
            )
            val sub = buildList {
                if (d.platform.isNotBlank()) add(s.platform(d.platform, d.mode))
                if (d.cancelled) add(s.cancelled)
                d.deviation?.let { add(it) }
            }.joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (d.cancelled || d.deviation != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(s.display(d.display), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(d.expected, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
}
