package dev.q25.bbos6.ui.compose

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class Bbos6MediaListRowColorSchemeTest {
    @Test fun darkSchemePreservesExistingUnselectedColors() {
        val scheme = Bbos6MediaListRowColorScheme.DARK
        assertEquals(Color.Black, Bbos6MediaListRowPalette.background(scheme))
        assertEquals(Color.White, Bbos6MediaListRowPalette.primaryText(scheme, false))
        assertEquals(Color(0xFFC9C9C9), Bbos6MediaListRowPalette.trackNumberText(scheme, false))
        assertEquals(Color(0xFFB3C1CE), Bbos6MediaListRowPalette.secondaryText(scheme, false))
    }

    @Test fun lightSchemeUsesWhiteBackgroundAndDarkText() {
        val scheme = Bbos6MediaListRowColorScheme.LIGHT
        assertEquals(Color.White, Bbos6MediaListRowPalette.background(scheme))
        assertEquals(Color.Black, Bbos6MediaListRowPalette.primaryText(scheme, false))
        assertEquals(Color.Black, Bbos6MediaListRowPalette.trackNumberText(scheme, false))
        assertEquals(Color(0xFF424242), Bbos6MediaListRowPalette.secondaryText(scheme, false))
    }

    @Test fun systemSchemeTracksCurrentAndroidTheme() {
        val scheme = Bbos6MediaListRowColorScheme.SYSTEM
        val dark = Bbos6MediaListRowPalette.resolve(scheme, systemDark = true)
        val light = Bbos6MediaListRowPalette.resolve(scheme, systemDark = false)
        assertEquals(Bbos6MediaListRowColorScheme.DARK, dark)
        assertEquals(Color.Black, Bbos6MediaListRowPalette.background(dark))
        assertEquals(Color.White, Bbos6MediaListRowPalette.primaryText(dark, false))
        assertEquals(Bbos6MediaListRowColorScheme.LIGHT, light)
        assertEquals(Color.White, Bbos6MediaListRowPalette.background(light))
        assertEquals(Color.Black, Bbos6MediaListRowPalette.primaryText(light, false))
    }

    @Test fun explicitSchemesIgnoreSystemTheme() {
        for (systemDark in listOf(true, false)) {
            assertEquals(
                Bbos6MediaListRowColorScheme.DARK,
                Bbos6MediaListRowPalette.resolve(Bbos6MediaListRowColorScheme.DARK, systemDark)
            )
            assertEquals(
                Bbos6MediaListRowColorScheme.LIGHT,
                Bbos6MediaListRowPalette.resolve(Bbos6MediaListRowColorScheme.LIGHT, systemDark)
            )
        }
    }

    @Test fun highlightedForegroundIsIdenticalInBothSchemes() {
        for (scheme in Bbos6MediaListRowColorScheme.entries) {
            assertEquals(Color.White, Bbos6MediaListRowPalette.primaryText(scheme, true))
            assertEquals(Color.White, Bbos6MediaListRowPalette.trackNumberText(scheme, true))
            assertEquals(Color(0xFFEDF4FF), Bbos6MediaListRowPalette.secondaryText(scheme, true))
        }
    }
}
