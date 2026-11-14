package se.mybus

import java.time.LocalDateTime

internal fun departure(
    line: String = "178",
    mode: Mode = Mode.BUS,
    destination: String = "Jakobsberg",
    stopPointId: Int = 1,
    platform: String = "A",
    expectedAt: LocalDateTime? = LocalDateTime.of(2026, 11, 5, 15, 0),
    cancelled: Boolean = false,
    deviation: String? = null,
    // SL's level and kind for that notice; 7 + DISRUPTION is one that's always alerted.
    importance: Int = 7,
    consequence: String = "DISRUPTION",
) = Departure(
    line = line,
    mode = mode,
    lineGroup = "",
    destination = destination,
    display = "5 min",
    expected = expectedAt?.toLocalTime()?.toString()?.take(5) ?: "--:--",
    expectedAt = expectedAt,
    platform = platform,
    stopPointId = stopPointId,
    cancelled = cancelled,
    deviation = deviation,
    deviations = listOfNotNull(deviation?.let { Deviation(it, importance, consequence) }),
)
