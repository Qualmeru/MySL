package se.mybus.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import se.mybus.ThemeMode

class WidgetThemeTest {

    private val lightDark = Color.White to Color.Black

    @Test
    fun `system default leaves light and dark to the phone`() {
        assertEquals(lightDark, themed(ThemeMode.SYSTEM, lightDark))
    }

    @Test
    fun `forced light or dark ignores the phone's mode`() {
        assertEquals(Color.White to Color.White, themed(ThemeMode.LIGHT, lightDark))
        assertEquals(Color.Black to Color.Black, themed(ThemeMode.DARK, lightDark))
    }

    @Test
    fun `boxes holding text grow with the font size, up to double`() {
        assertEquals(58.dp, scaledDp(58, 1f))
        assertEquals(75.dp, scaledDp(58, 1.3f))
        assertEquals(116.dp, scaledDp(58, 2f))
        // Android's settings stop at 2x; never shrink below the normal size either.
        assertEquals(116.dp, scaledDp(58, 3f))
        assertEquals(58.dp, scaledDp(58, 0.85f))
    }

    @Test
    fun `text never matches its background, in any theme`() {
        for (mode in ThemeMode.entries) {
            val (bgDay, bgNight) = themed(mode, WidgetPalette.background)
            for (text in listOf(WidgetPalette.foreground, WidgetPalette.secondary, WidgetPalette.error)) {
                val (fgDay, fgNight) = themed(mode, text)
                assertNotEquals("$mode day", bgDay, fgDay)
                assertNotEquals("$mode night", bgNight, fgNight)
            }
        }
    }
}
