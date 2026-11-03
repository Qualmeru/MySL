package se.mybus

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Monochrome theme icons on a 24x24 grid; Icon() tints them with the current text colour.
private val ink = SolidColor(Color.Black)

private fun icon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(build).build()

// Disc of radius 4.5 with eight rounded rays from radius 7.5 to 10.
private val Sun = icon("Sun") {
    addPath(addPathNodes("M7.5,12a4.5,4.5 0 1,0 9,0a4.5,4.5 0 1,0 -9,0z"), fill = ink)
    addPath(
        addPathNodes(
            "M19.5,12H22 M4.5,12H2 M12,19.5V22 M12,4.5V2 " +
                "M17.3,17.3L19.07,19.07 M6.7,17.3L4.93,19.07 M6.7,6.7L4.93,4.93 M17.3,6.7L19.07,4.93"
        ),
        stroke = ink,
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
    )
}

// Circle of radius 9 at the centre with a radius-7 circle centred at (17,7) cut out of it.
private val Moon = icon("Moon") {
    addPath(addPathNodes("M11.23,3.03A9,9 0 1,0 20.97,12.77A7,7 0 0,1 11.23,3.03Z"), fill = ink)
}

// Ring with its left half filled.
private val HalfCircle = icon("HalfCircle") {
    addPath(addPathNodes("M12,3A9,9 0 0,0 12,21Z"), fill = ink)
    addPath(
        addPathNodes("M3,12a9,9 0 1,0 18,0a9,9 0 1,0 -18,0"),
        stroke = ink,
        strokeLineWidth = 2f,
    )
}

val ThemeMode.icon: ImageVector
    get() = when (this) {
        ThemeMode.SYSTEM -> HalfCircle
        ThemeMode.LIGHT -> Sun
        ThemeMode.DARK -> Moon
    }
