package se.mybus

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Flat flags drawn at 24x16 dp. Shown with tint = Color.Unspecified so they keep their colours.

private fun fill(hex: Long) = SolidColor(Color(hex))

// Official 16:10 layout: 5-2-9 across, 4-2-4 down.
private val Sweden = ImageVector.Builder("Sweden", 24.dp, 16.dp, 16f, 10f).apply {
    addPath(addPathNodes("M0,0H16V10H0Z"), fill = fill(0xFF006AA7))
    addPath(addPathNodes("M5,0H7V10H5Z M0,4H16V6H0Z"), fill = fill(0xFFFECC02))
}.build()

// Union Jack on its usual 60x30 grid.
private val UnitedKingdom = ImageVector.Builder("UnitedKingdom", 24.dp, 16.dp, 60f, 30f).apply {
    val diagonals = addPathNodes("M0,0L60,30M60,0L0,30")
    val cross = addPathNodes("M30,0V30M0,15H60")
    addGroup(clipPathData = addPathNodes("M0,0H60V30H0Z"))
    addPath(addPathNodes("M0,0H60V30H0Z"), fill = fill(0xFF012169))
    addPath(diagonals, stroke = fill(0xFFFFFFFF), strokeLineWidth = 6f)
    // The red diagonals are offset ("counterchanged"), so each quarter only shows one side.
    addGroup(clipPathData = addPathNodes("M30,15H60V30ZV30H0ZH0V0ZV0H60Z"))
    addPath(diagonals, stroke = fill(0xFFC8102E), strokeLineWidth = 4f)
    clearGroup()
    addPath(cross, stroke = fill(0xFFFFFFFF), strokeLineWidth = 10f)
    addPath(cross, stroke = fill(0xFFC8102E), strokeLineWidth = 6f)
    clearGroup()
}.build()

val Language.flag: ImageVector?
    get() = when (this) {
        Language.SYSTEM -> null
        Language.SWEDISH -> Sweden
        Language.ENGLISH -> UnitedKingdom
    }
