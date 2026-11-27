package se.mybus

import java.time.LocalDateTime
import java.util.Locale

// Drops cached departures that have already left, with a minute's grace so "Nu" doesn't vanish
// the moment it's due. Ones without a parsable time are kept.
fun List<Departure>.notLeftBy(now: LocalDateTime): List<Departure> {
    val cutoff = now.minusMinutes(1)
    return filter { it.expectedAt == null || !it.expectedAt.isBefore(cutoff) }
}

// Count down from the expected time rather than SL's display text, which ages after a fetch.
fun timeLeft(d: Departure, now: LocalDateTime, s: Strings): String {
    val at = d.expectedAt ?: return d.expected
    val minutes = java.time.Duration.between(now, at).toMinutes()
    return if (minutes <= 0) s.now else "$minutes min"
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
    sortDirections(departures.groupBy { it.stopPointId }.map { (id, deps) ->
        Direction(
            stopPointId = id,
            mode = deps.first().mode,
            platform = deps.first().platform,
            destinations = deps.groupingBy { it.destination }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key },
        )
    })

internal fun sortDirections(directions: Collection<Direction>): List<Direction> =
    directions.sortedWith(compareBy({ it.mode }, { it.platform }, { it.destinations.firstOrNull() }))

internal data class LineEntry(val line: String, val mode: Mode)

// By number, so "4" < "41" < "178", with suffixed lines next to their number ("553" < "553H" < "553V").
internal fun linesOf(departures: List<Departure>): List<LineEntry> =
    sortLines(departures.map { LineEntry(it.line, it.mode) }.distinct())

internal fun sortLines(lines: Collection<LineEntry>): List<LineEntry> =
    lines.sortedWith(compareBy({ it.mode }, { leadingNumber(it.line) }, { it.line }))

private fun leadingNumber(line: String): Int =
    line.takeWhile { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE

private val LINE_INPUT = Regex("[\\p{L}\\p{N}-]{1,32}")

internal fun normalizeLineInput(input: String): String? =
    input.trim().uppercase(Locale.ROOT).takeIf { LINE_INPUT.matches(it) }
