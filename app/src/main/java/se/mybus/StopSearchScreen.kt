package se.mybus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

private const val MAX_RESULTS = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopSearchScreen(favoriteIds: Set<Int>, onPick: (Stop) -> Unit, onBack: () -> Unit) {
    val s = LocalStrings.current
    BackHandler(onBack = onBack)
    var query by rememberSaveable { mutableStateOf("") }
    var sites by remember { mutableStateOf<List<Site>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        try {
            sites = SlApi.allSites()
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }
    val matches = remember(query, sites) { search(sites.orEmpty(), query) }

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
                query.isBlank() -> Message(s.typeStopName)
                matches.isEmpty() -> Message(s.noMatches)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(matches, key = { it.id }) { site ->
                        val added = site.id in favoriteIds
                        ListItem(
                            headlineContent = { Text(site.name) },
                            supportingContent = if (site.note.isNotBlank()) {
                                { Text(site.note) }
                            } else null,
                            trailingContent = if (added) {
                                { Text(s.added) }
                            } else null,
                            modifier = Modifier.clickable(enabled = !added) { onPick(Stop(site.id, site.name)) },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

// Names starting with the query first, then a later word starting with it, then any match.
private fun search(sites: List<Site>, query: String): List<Site> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return sites
        .mapNotNull { site ->
            val name = site.name.lowercase()
            val rank = when {
                name.startsWith(q) -> 0
                name.split(' ', '-', '/').any { it.startsWith(q) } -> 1
                q in name -> 2
                else -> return@mapNotNull null
            }
            rank to site
        }
        .sortedWith(compareBy({ it.first }, { it.second.name }))
        .take(MAX_RESULTS)
        .map { it.second }
}
