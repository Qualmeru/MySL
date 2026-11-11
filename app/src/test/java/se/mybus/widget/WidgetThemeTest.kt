package se.mybus.widget

import androidx.compose.ui.graphics.Color
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
