package dev.q25.bbos6.ui.compose

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Bbos6ThemeModeTest {
    @Test fun darkIsTheSafeDefaultForNewOrUnknownPreferences() {
        assertEquals(Bbos6ThemeMode.DARK, Bbos6ThemeMode.fromStoredValue(null))
        assertEquals(Bbos6ThemeMode.DARK, Bbos6ThemeMode.fromStoredValue(""))
        assertEquals(Bbos6ThemeMode.DARK, Bbos6ThemeMode.fromStoredValue("invalid"))
        assertEquals(Bbos6ThemeMode.DARK, Bbos6ThemeMode.fromStoredValue("light"))
    }

    @Test fun storedValuesRestoreAllThreeModes() {
        for (mode in Bbos6ThemeMode.entries) {
            assertEquals(mode, Bbos6ThemeMode.fromStoredValue(mode.name))
        }
    }

    @Test fun fixedModesAreIndependentOfSystemNightMode() {
        for (systemDark in listOf(false, true)) {
            assertTrue(Bbos6ThemeMode.DARK.resolvesToDark(systemDark))
            assertFalse(Bbos6ThemeMode.LIGHT.resolvesToDark(systemDark))
        }
    }

    @Test fun systemModeTracksCurrentSystemNightMode() {
        assertTrue(Bbos6ThemeMode.SYSTEM.resolvesToDark(true))
        assertFalse(Bbos6ThemeMode.SYSTEM.resolvesToDark(false))
    }

    @Test fun scopedPaletteKeepsDarkDefaultsAndSupportsLightSurfaces() {
        assertEquals(Color.Black, Bbos6ThemePalette.background(true))
        assertEquals(Color.White, Bbos6ThemePalette.foreground(true))
        assertEquals(Color.White, Bbos6ThemePalette.background(false))
        assertEquals(Color.Black, Bbos6ThemePalette.foreground(false))
    }
}
