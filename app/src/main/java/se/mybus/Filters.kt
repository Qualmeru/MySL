package se.mybus

import java.time.LocalDateTime

// Drops cached departures that have already left, with a minute's grace so "Nu" doesn't vanish
// the moment it's due. Ones without a parsable time are kept.
fun List<Departure>.notLeftBy(now: LocalDateTime): List<Departure> {
    val cutoff = now.minusMinutes(1)
    return filter { it.expectedAt == null || !it.expectedAt.isBefore(cutoff) }
}

// "Nu" / "7 min" counted from now, for the widget. SL's own display text is only as fresh as the
// last fetch (up to 15 minutes old there), so the widget works it out at each minute's redraw.
// An hour or more away, or with no parsable time, it falls back to the clock time.
fun timeLeft(d: Departure, now: LocalDateTime, s: Strings): String {
    val at = d.expectedAt ?: return d.expected
    val minutes = java.time.Duration.between(now, at).toMinutes()
    return when {
        minutes <= 0 -> s.now
        minutes < 60 -> "$minutes min"
        else -> d.expected
    }
}

// The departures this stop shows, after its direction and line filters. The app and the widget
// both use this so they always agree.
fun Stop.visible(departures: List<Departure>): List<Departure> =
    departures.filter { it.stopPointId !in hiddenStopPoints && it.line !in hiddenLines }

// A stop point is one side of the street or one platform/track, so it stands for a direction.
// Trains and buses at the same station use different stop points, so this also filters by mode.
internal data class Direction(
    val stopPointId: Int,
    val mode: Mode,
    val platform: String,
    // Most frequent first.
    val destinations: List<String>,
)

internal fun directionsOf(departures: List<Departure>): List<Direction> =
    departures.groupBy { it.stopPointId }.map { (id, deps) ->
        Direction(
            stopPointId = id,
            mode = deps.first().mode,
            platform = deps.first().platform,
            destinations = deps.groupingBy { it.destination }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key },
        )
    }.sortedWith(compareBy({ it.mode }, { it.platform }, { it.destinations.firstOrNull() }))

internal data class LineEntry(val line: String, val mode: Mode)

// By number, so "4" < "41" < "178", with suffixed lines next to their number ("553" < "553H" < "553V").
internal fun linesOf(departures: List<Departure>): List<LineEntry> =
    departures.map { LineEntry(it.line, it.mode) }.distinct()
        .sortedWith(compareBy({ it.mode }, { leadingNumber(it.line) }, { it.line }))

private fun leadingNumber(line: String): Int =
    line.takeWhile { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
