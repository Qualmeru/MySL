package se.mybus

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import se.mybus.widget.WidgetCache
import se.mybus.widget.WidgetUpdates
import se.mybus.widget.redrawFromCache
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
    // The widgets use the app's theme and language; draw them again after either changes.
    private fun redrawWidgetsSoon() {
        lifecycleScope.launch { redrawFromCache(this@MainActivity) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Settings restored from a backup (new phone, reinstall) can have alerts on without the
        // background check scheduled; this puts that right. Does nothing if it already is.
        WidgetUpdates.sync(this)
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
            var alerts by remember { mutableStateOf(Settings.loadAlerts(this)) }
            val strings = stringsFor(language)
            fun setAlerts(on: Boolean) {
                alerts = on
                Settings.saveAlerts(this, on)
                if (on) DisruptionAlerts.forgetSeen(this)
                WidgetUpdates.sync(this)
                // Check right away rather than in up to 15 minutes.
                if (on) WidgetUpdates.refreshNow(this)
            }
            val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) setAlerts(true)
                else Toast.makeText(this, strings.notificationsDenied, Toast.LENGTH_LONG).show()
            }
            MaterialTheme(colorScheme = appColorScheme(dark, LocalContext.current)) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    App(
                        settingsMenu = { favorites, onImport ->
                            SettingsMenu(
                                favorites = favorites,
                                onImport = onImport,
                                themeMode = themeMode,
                                onThemeMode = { themeMode = it; Settings.saveTheme(this, it); redrawWidgetsSoon() },
                                language = language,
                                onLanguage = { language = it; Settings.saveLanguage(this, it); redrawWidgetsSoon() },
                                alerts = alerts,
                                onAlerts = { on ->
                                    if (on && !DisruptionAlerts.canNotify(this)) {
                                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        setAlerts(on)
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

// settingsMenu gets the favourites to share, and a way to add imported ones.
@Composable
fun App(settingsMenu: @Composable (List<Stop>, (List<Stop>) -> Unit) -> Unit) {
    val context = LocalContext.current
    var favorites by remember { mutableStateOf(Favorites.load(context)) }
    var searching by rememberSaveable { mutableStateOf(false) }
    // Kept here so departures survive a trip to the search screen.
    val states = remember { mutableStateMapOf<Int, StopState>() }
    // A newer release on GitHub, looked for at most once a day.
    var update by remember { mutableStateOf<AvailableUpdate?>(null) }
    LaunchedEffect(Unit) { update = UpdateCheck.available(context) }

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
            update = update,
            onDismissUpdate = { update?.let { UpdateCheck.dismiss(context, it) }; update = null },
            settingsMenu = { settingsMenu(favorites) { imported -> setFavorites(favorites.mergedWith(imported)) } },
            onAdd = { searching = true },
            onRemove = { stop -> setFavorites(favorites.filter { it.siteId != stop.siteId }) },
            onMove = { stop, by -> setFavorites(favorites.move(stop.siteId, by)) },
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
    update: AvailableUpdate?,
    onDismissUpdate: () -> Unit,
    settingsMenu: @Composable () -> Unit,
    onAdd: () -> Unit,
    onRemove: (Stop) -> Unit,
    onMove: (Stop, Int) -> Unit,
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
                update?.let { item(key = "update") { UpdateBanner(it, onDismissUpdate) } }
                if (favorites.isEmpty()) {
                    item { Message(s.noFavorites) }
                }
                favorites.forEachIndexed { index, stop ->
                    // Departures within the hour that this stop's filters are keeping out of the list.
                    val hiddenDepartures = (states[stop.siteId] as? StopState.Loaded)
                        ?.departures?.let { it.size - stop.visible(it).size } ?: 0
                    item(key = "h${stop.siteId}") {
                        StopHeader(
                            stop = stop,
                            hiddenDepartures = hiddenDepartures,
                            canMoveUp = index > 0,
                            canMoveDown = index < favorites.lastIndex,
                            onMove = { by -> onMove(stop, by) },
                            onFilter = { filtering = stop },
                            onRemove = { confirmRemove = stop },
                        )
                    }
                    when (val state = states[stop.siteId] ?: StopState.Loading) {
                        StopState.Loading -> item { Message(s.loading) }
                        is StopState.Failed -> item { Message(s.couldNotFetch(state.message)) }
                        is StopState.Loaded -> {
                            state.refreshError?.let { item { Message(s.couldNotRefresh(it)) } }
                            val shown = stop.visible(state.departures).take(MAX_PER_STOP)
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
    alerts: Boolean,
    onAlerts: (Boolean) -> Unit,
    favorites: List<Stop>,
    onImport: (List<Stop>) -> Unit,
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
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
            HorizontalDivider()
            CheckedMenuItem(
                s.disruptionAlerts,
                checked = alerts,
                leading = { Icon(Icons.Default.Notifications, contentDescription = null) },
            ) {
                open = false
                onAlerts(!alerts)
            }
            HorizontalDivider()
            if (favorites.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text(s.exportFavorites) },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = {
                        open = false
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, s.exportMessage(FavoritesCode.export(favorites)))
                        context.startActivity(Intent.createChooser(send, s.exportFavorites))
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(s.importFavorites) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { open = false; importing = true },
            )
        }
    }

    if (importing) {
        ImportDialog(
            onImport = { stops ->
                onImport(stops)
                importing = false
                Toast.makeText(context, s.imported(stops.size), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { importing = false },
        )
    }
}

@Composable
private fun ImportDialog(onImport: (List<Stop>) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    var text by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.importFavorites) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; invalid = false },
                placeholder = { Text(s.importHint) },
                isError = invalid,
                supportingText = if (invalid) {
                    { Text(s.importInvalid) }
                } else null,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    val stops = FavoritesCode.import(text)
                    if (stops.isNullOrEmpty()) invalid = true else onImport(stops)
                },
            ) { Text(s.importAction) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
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
private fun StopHeader(
    stop: Stop,
    hiddenDepartures: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onFilter: () -> Unit,
    onRemove: () -> Unit,
) {
    val s = LocalStrings.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stop.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val hiddenNote = listOfNotNull(
                s.directionsHidden(stop.hiddenStopPoints.size).takeIf { stop.hiddenStopPoints.isNotEmpty() },
                s.linesHidden(stop.hiddenLines.size).takeIf { stop.hiddenLines.isNotEmpty() },
                s.departuresHidden(hiddenDepartures).takeIf { hiddenDepartures > 0 },
            ).joinToString(" · ")
            if (hiddenNote.isNotEmpty()) {
                Text(
                    hiddenNote,
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
                if (canMoveUp) {
                    DropdownMenuItem(
                        text = { Text(s.moveUp) },
                        onClick = { menuOpen = false; onMove(-1) },
                    )
                }
                if (canMoveDown) {
                    DropdownMenuItem(
                        text = { Text(s.moveDown) },
                        onClick = { menuOpen = false; onMove(1) },
                    )
                }
                DropdownMenuItem(
                    text = { Text(s.remove) },
                    onClick = { menuOpen = false; onRemove() },
                )
            }
        }
    }
}

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

// "MySL 1.3.0 is available": Download opens the release page, Not now hides it for that version.
@Composable
private fun UpdateBanner(update: AvailableUpdate, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(s.updateAvailable(update.version), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(s.updateHint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, end = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(s.notNow) }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.url))) }) {
                    Text(s.download)
                }
            }
        }
    }
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
        modifier = Modifier
            .fillMaxWidth()
            // TalkBack reads the row as one sentence instead of each piece of text on its own.
            .clearAndSetSemantics { contentDescription = s.describe(d, s.display(d.display)) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
