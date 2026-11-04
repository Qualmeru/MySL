package se.mybus

import androidx.compose.ui.graphics.Color

// Roughly SL's own line colours, so badges look like the signs at the stop.
fun lineColor(d: Departure): Color = when (d.mode) {
    Mode.BUS -> if (d.lineGroup == "Blåbuss") Color(0xFF0089CA) else Color(0xFFD71D24)
    Mode.TRAIN -> Color(0xFFEC619F)
    Mode.METRO -> when {
        "gröna" in d.lineGroup -> Color(0xFF179D4D)
        "röda" in d.lineGroup -> Color(0xFFE3242B)
        else -> Color(0xFF0089CA) // blå linje
    }
    Mode.TRAM -> Color(0xFFD77D00)
    Mode.SHIP -> Color(0xFF00A3A8)
    Mode.OTHER -> Color(0xFF6E6E6E)
}
