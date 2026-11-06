package se.mybus

import androidx.compose.runtime.staticCompositionLocalOf
import java.net.UnknownHostException
import java.util.Locale

// Every text the app shows, in Swedish and English. Stop and destination names come from SL as-is.
abstract class Strings {
    abstract val title: String
    abstract fun updated(time: String): String
    abstract val fetching: String
    abstract val refresh: String
    abstract val addStop: String
    abstract val noFavorites: String
    abstract val loading: String
    abstract fun couldNotFetch(error: String): String
    abstract fun couldNotRefresh(error: String): String
    abstract val noDepartures: String
    abstract val noDeparturesSelectedDirections: String
    abstract fun removeTitle(stop: String): String
    abstract val removeText: String
    abstract val remove: String
    abstract val cancel: String
    abstract val settings: String
    abstract val theme: String
    abstract val language: String
    abstract val selected: String
    abstract fun themeLabel(mode: ThemeMode): String
    abstract val followPhone: String
    abstract fun directionsHidden(n: Int): String
    abstract fun optionsFor(stop: String): String
    abstract val chooseDirections: String
    abstract fun directionsTitle(stop: String): String
    abstract val noDirectionsNow: String
    abstract val singleDirection: String
    abstract val showDeparturesGoing: String
    abstract val showLinesGoing: String
    abstract fun towards(destinations: String): String
    abstract fun platform(p: String, mode: Mode): String
    abstract fun modeName(mode: Mode): String
    abstract val save: String
    abstract val showAll: String
    abstract val cancelled: String
    abstract val now: String
    abstract val back: String
    abstract val searchStop: String
    abstract fun couldNotFetchStops(error: String): String
    abstract val fetchingStops: String
    abstract val typeStopName: String
    abstract val noMatches: String
    abstract val added: String
    abstract val noInternet: String
    abstract val connectionFailed: String
    abstract val slBusy: String
    abstract fun updateAvailable(version: String): String
    abstract val updateHint: String
    abstract val download: String
    abstract val notNow: String
    abstract val moveUp: String
    abstract val moveDown: String
    abstract fun linesHidden(n: Int): String
    abstract fun departuresHidden(n: Int): String
    abstract val nearbyStops: String
    abstract val findingLocation: String
    abstract val locationDenied: String
    abstract val locationUnavailable: String
    abstract fun metresAway(m: Int): String
    abstract val disruptionAlerts: String
    abstract val notificationsDenied: String
    abstract fun disruptionTitle(stop: String): String
    abstract fun lineCancelled(line: String, destination: String, time: String): String
    abstract val pickStopTitle: String
    abstract val pickStopEmpty: String
    abstract val stopRemoved: String
    abstract val exportFavorites: String
    abstract val importFavorites: String
    abstract fun exportMessage(code: String): String
    abstract val importHint: String
    abstract val importAction: String
    abstract val importInvalid: String
    abstract fun imported(n: Int): String

    // SL sends "Nu" for departures leaving now; "3 min" and "21:22" read the same in both languages.
    fun display(slDisplay: String) = if (slDisplay == "Nu") now else slDisplay
}

object Swedish : Strings() {
    override val title = "Avgångar"
    override fun updated(time: String) = "Uppdaterad $time"
    override val fetching = "Hämtar…"
    override val refresh = "Uppdatera"
    override val addStop = "Lägg till hållplats"
    override val noFavorites = "Inga favoriter än. Tryck + för att lägga till en hållplats."
    override val loading = "Laddar…"
    override fun couldNotFetch(error: String) = "Kunde inte hämta: $error"
    override fun couldNotRefresh(error: String) = "Kunde inte uppdatera ($error), visar äldre tider"
    override val noDepartures = "Inga avgångar inom en timme"
    override val noDeparturesSelectedDirections = "Inga avgångar som matchar dina filter inom en timme"
    override fun removeTitle(stop: String) = "Ta bort $stop?"
    override val removeText = "Hållplatsen tas bort från dina favoriter."
    override val remove = "Ta bort"
    override val cancel = "Avbryt"
    override val settings = "Inställningar"
    override val theme = "Tema"
    override val language = "Språk"
    override val selected = "Vald"
    override fun themeLabel(mode: ThemeMode) = when (mode) {
        ThemeMode.SYSTEM -> followPhone
        ThemeMode.LIGHT -> "Ljust"
        ThemeMode.DARK -> "Mörkt"
    }
    override val followPhone = "Systemstandard"
    override fun directionsHidden(n: Int) = if (n == 1) "1 riktning dold" else "$n riktningar dolda"
    override fun optionsFor(stop: String) = "Alternativ för $stop"
    override val chooseDirections = "Välj riktningar…"
    override fun directionsTitle(stop: String) = "Riktningar – $stop"
    override val noDirectionsNow = "Inga avgångar just nu att välja riktning från. Försök igen när trafiken går."
    override val singleDirection = "Allt här avgår från samma läge, så det finns inget att filtrera."
    override val showDeparturesGoing = "Visa avgångar:"
    override val showLinesGoing = "Visa linjer:"
    override fun towards(destinations: String) = "mot $destinations"
    override fun platform(p: String, mode: Mode) = if (mode == Mode.BUS) "Läge $p" else "Spår $p"
    override fun modeName(mode: Mode) = when (mode) {
        Mode.BUS -> "Buss"
        Mode.TRAIN -> "Pendeltåg"
        Mode.METRO -> "Tunnelbana"
        Mode.TRAM -> "Spårvagn"
        Mode.SHIP -> "Båt"
        Mode.OTHER -> "Övrigt"
    }
    override val save = "Spara"
    override val showAll = "Visa alla"
    override val cancelled = "Inställd"
    override val now = "Nu"
    override val back = "Tillbaka"
    override val searchStop = "Sök hållplats"
    override fun couldNotFetchStops(error: String) = "Kunde inte hämta hållplatser: $error"
    override val fetchingStops = "Hämtar hållplatser…"
    override val typeStopName = "Skriv namnet på en hållplats"
    override val noMatches = "Inga träffar"
    override val added = "Tillagd"
    override val noInternet = "Ingen internetanslutning"
    override val connectionFailed = "Kunde inte nå SL, försök igen"
    override val slBusy = "SL har för många förfrågningar just nu, försök igen om en stund"
    override fun updateAvailable(version: String) = "MySL $version finns"
    override val updateHint = "Ladda ned den nya APK-filen från GitHub och öppna den. Dina favoriter finns kvar."
    override val download = "Ladda ned"
    override val notNow = "Inte nu"
    override val moveUp = "Flytta upp"
    override val moveDown = "Flytta ner"
    override fun linesHidden(n: Int) = if (n == 1) "1 linje dold" else "$n linjer dolda"
    override fun departuresHidden(n: Int) = if (n == 1) "1 avgång dold" else "$n avgångar dolda"
    override val nearbyStops = "Hållplatser nära mig"
    override val findingLocation = "Hämtar din position…"
    override val locationDenied = "Appen fick inte använda din position."
    override val locationUnavailable = "Kunde inte hitta din position. Är platstjänster på?"
    override fun metresAway(m: Int) = if (m < 1000) "$m m" else "%.1f km".format(Locale.ROOT, m / 1000.0).replace('.', ',')
    override val disruptionAlerts = "Störningsaviseringar"
    override val notificationsDenied = "Appen fick inte visa aviseringar."
    override fun disruptionTitle(stop: String) = "Störning vid $stop"
    override fun lineCancelled(line: String, destination: String, time: String) = "$line mot $destination $time är inställd"
    override val pickStopTitle = "Välj hållplats för widgeten"
    override val pickStopEmpty = "Lägg till en favorithållplats i appen först."
    override val stopRemoved = "Hållplatsen finns inte längre bland dina favoriter. Ta bort widgeten och lägg till den igen."
    override val exportFavorites = "Dela favoriter"
    override val importFavorites = "Importera favoriter"
    override fun exportMessage(code: String) =
        "Mina hållplatser i MySL. Kopiera hela meddelandet och välj ⋮ → Importera favoriter i appen:\n$code"
    override val importHint = "Klistra in meddelandet med favoriterna"
    override val importAction = "Importera"
    override val importInvalid = "Hittar inga favoriter i texten. Klistra in hela meddelandet från Dela favoriter."
    override fun imported(n: Int) = if (n == 1) "1 hållplats importerad" else "$n hållplatser importerade"
}

object English : Strings() {
    override val title = "Departures"
    override fun updated(time: String) = "Updated $time"
    override val fetching = "Loading…"
    override val refresh = "Refresh"
    override val addStop = "Add stop"
    override val noFavorites = "No favourites yet. Tap + to add a stop."
    override val loading = "Loading…"
    override fun couldNotFetch(error: String) = "Couldn't load: $error"
    override fun couldNotRefresh(error: String) = "Couldn't refresh ($error), showing older times"
    override val noDepartures = "No departures within an hour"
    override val noDeparturesSelectedDirections = "No departures matching your filters within an hour"
    override fun removeTitle(stop: String) = "Remove $stop?"
    override val removeText = "The stop will be removed from your favourites."
    override val remove = "Remove"
    override val cancel = "Cancel"
    override val settings = "Settings"
    override val theme = "Theme"
    override val language = "Language"
    override val selected = "Selected"
    override fun themeLabel(mode: ThemeMode) = when (mode) {
        ThemeMode.SYSTEM -> followPhone
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }
    override val followPhone = "System default"
    override fun directionsHidden(n: Int) = if (n == 1) "1 direction hidden" else "$n directions hidden"
    override fun optionsFor(stop: String) = "Options for $stop"
    override val chooseDirections = "Choose directions…"
    override fun directionsTitle(stop: String) = "Directions – $stop"
    override val noDirectionsNow = "No departures right now to pick a direction from. Try again when traffic is running."
    override val singleDirection = "Everything here leaves from the same spot, so there's nothing to filter."
    override val showDeparturesGoing = "Show departures:"
    override val showLinesGoing = "Show lines:"
    override fun towards(destinations: String) = "towards $destinations"
    override fun platform(p: String, mode: Mode) = if (mode == Mode.BUS) "Stand $p" else "Track $p"
    override fun modeName(mode: Mode) = when (mode) {
        Mode.BUS -> "Bus"
        Mode.TRAIN -> "Commuter train"
        Mode.METRO -> "Metro"
        Mode.TRAM -> "Tram"
        Mode.SHIP -> "Boat"
        Mode.OTHER -> "Other"
    }
    override val save = "Save"
    override val showAll = "Show all"
    override val cancelled = "Cancelled"
    override val now = "Now"
    override val back = "Back"
    override val searchStop = "Search stop"
    override fun couldNotFetchStops(error: String) = "Couldn't load stops: $error"
    override val fetchingStops = "Loading stops…"
    override val typeStopName = "Type the name of a stop"
    override val noMatches = "No matches"
    override val added = "Added"
    override val noInternet = "No internet connection"
    override val connectionFailed = "Couldn't reach SL, try again"
    override val slBusy = "SL is getting too many requests right now, try again in a while"
    override fun updateAvailable(version: String) = "MySL $version is available"
    override val updateHint = "Download the new APK from GitHub and open it. Your favourites are kept."
    override val download = "Download"
    override val notNow = "Not now"
    override val moveUp = "Move up"
    override val moveDown = "Move down"
    override fun linesHidden(n: Int) = if (n == 1) "1 line hidden" else "$n lines hidden"
    override fun departuresHidden(n: Int) = if (n == 1) "1 departure hidden" else "$n departures hidden"
    override val nearbyStops = "Stops near me"
    override val findingLocation = "Finding your location…"
    override val locationDenied = "The app wasn't allowed to use your location."
    override val locationUnavailable = "Couldn't find your location. Is location turned on?"
    override fun metresAway(m: Int) = if (m < 1000) "$m m" else "%.1f km".format(Locale.ROOT, m / 1000.0)
    override val disruptionAlerts = "Disruption alerts"
    override val notificationsDenied = "The app wasn't allowed to show notifications."
    override fun disruptionTitle(stop: String) = "Disruption at $stop"
    override fun lineCancelled(line: String, destination: String, time: String) = "$line to $destination at $time is cancelled"
    override val pickStopTitle = "Choose a stop for the widget"
    override val pickStopEmpty = "Add a favourite stop in the app first."
    override val stopRemoved = "This stop is no longer one of your favourites. Remove the widget and add it again."
    override val exportFavorites = "Share favourites"
    override val importFavorites = "Import favourites"
    override fun exportMessage(code: String) =
        "My stops in MySL. Copy this whole message and choose ⋮ → Import favourites in the app:\n$code"
    override val importHint = "Paste the message with the favourites"
    override val importAction = "Import"
    override val importInvalid = "No favourites found in that text. Paste the whole message from Share favourites."
    override fun imported(n: Int) = if (n == 1) "1 stop imported" else "$n stops imported"
}

fun stringsFor(language: Language): Strings = when (language) {
    Language.SWEDISH -> Swedish
    Language.ENGLISH -> English
    Language.SYSTEM -> if (Locale.getDefault().language == "sv") Swedish else English
}

val LocalStrings = staticCompositionLocalOf<Strings> { Swedish }

// Turn a connectivity exception into a message a rider understands, instead of the raw
// "Unable to resolve host" / "failed to connect" text from the underlying HTTP client.
// Only UnknownHostException (DNS can't even look up the address) means the device truly has
// no network path; a timeout or refused connection can happen for other reasons — e.g. a
// background widget update running under a restricted network window — even while the phone
// is otherwise online, so those get a distinct, less definitive message.
fun friendlyError(e: Exception, s: Strings): String {
    var cause: Throwable? = e
    while (cause != null) {
        if (cause is SlApiException && cause.quota) return s.slBusy
        if (cause is UnknownHostException) return s.noInternet
        if (cause is java.net.ConnectException || cause is java.net.SocketTimeoutException) return s.connectionFailed
        cause = cause.cause
    }
    return e.message ?: e.javaClass.simpleName
}
