package se.mybus

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private sealed interface Nearby {
    data object Idle : Nearby
    data object Locating : Nearby
    data object Denied : Nearby
    data object Unavailable : Nearby
    data class Found(val sites: List<NearbySite>) : Nearby
}

private val LocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopSearchScreen(favoriteIds: Set<Int>, onPick: (Stop) -> Unit, onBack: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)
    var query by rememberSaveable { mutableStateOf("") }
    var sites by remember { mutableStateOf<List<Site>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var nearby by remember { mutableStateOf<Nearby>(Nearby.Idle) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        try {
            sites = SlApi.allSites()
        } catch (e: Exception) {
            error = friendlyError(e, s)
        }
    }
    val matches = remember(query, sites) { searchSites(sites.orEmpty(), query) }

    fun findNearby() {
        val all = sites ?: return
        nearby = Nearby.Locating
        scope.launch {
            // SecurityException if the permission was revoked in between; treat like no fix.
            val here = runCatching { PhoneLocation.current(context) }.getOrNull()
            nearby = if (here == null) Nearby.Unavailable else Nearby.Found(nearestSites(all, here.latitude, here.longitude))
        }
    }

    // Either permission will do: "approximate" still finds the right neighbourhood.
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) findNearby() else nearby = Nearby.Denied
    }

    fun onNearbyClick() {
        val hasPermission = LocationPermissions.any {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
        if (hasPermission) findNearby() else askLocation.launch(LocationPermissions)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.addStop) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(s.searchStop) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
            )
            when {
                error != null -> Message(s.couldNotFetchStops(error!!))
                sites == null -> Message(s.fetchingStops)
                query.isBlank() -> NearbySection(nearby, favoriteIds, ::onNearbyClick, onPick)
                matches.isEmpty() -> Message(s.noMatches)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(matches, key = { it.id }) { site ->
                        SiteRow(site, distance = null, added = site.id in favoriteIds, onPick = onPick)
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbySection(
    nearby: Nearby,
    favoriteIds: Set<Int>,
    onNearbyClick: () -> Unit,
    onPick: (Stop) -> Unit,
) {
    val s = LocalStrings.current
    Column {
        Message(s.typeStopName)
        OutlinedButton(
            onClick = onNearbyClick,
            enabled = nearby != Nearby.Locating,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(s.nearbyStops)
        }
        when (nearby) {
            Nearby.Idle -> {}
            Nearby.Locating -> Message(s.findingLocation)
            Nearby.Denied -> Message(s.locationDenied)
            Nearby.Unavailable -> Message(s.locationUnavailable)
            is Nearby.Found -> if (nearby.sites.isEmpty()) {
                Message(s.noMatches)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(nearby.sites, key = { it.site.id }) { n ->
                        SiteRow(n.site, distance = s.metresAway(n.metres), added = n.site.id in favoriteIds, onPick = onPick)
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteRow(site: Site, distance: String?, added: Boolean, onPick: (Stop) -> Unit) {
    val s = LocalStrings.current
    val detail = listOfNotNull(distance, site.note.takeIf { it.isNotBlank() }).joinToString(" · ")
    ListItem(
        headlineContent = { Text(site.name) },
        supportingContent = if (detail.isNotEmpty()) {
            { Text(detail) }
        } else null,
        trailingContent = if (added) {
            { Text(s.added) }
        } else null,
        modifier = Modifier.clickable(enabled = !added) { onPick(Stop(site.id, site.name)) },
    )
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
}
