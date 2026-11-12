package dev.q25.bbos6.ui.compose

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.CopyOnWriteArraySet

/**
 * One app-local appearance preference, independent of any particular BBOS6 widget.
 *
 * The preference belongs to the Android application package, not the shared
 * library or other suite apps. Compose hosts observe [mode] through snapshot
 * state, including independently mounted ComposeViews inside native screens.
 */
enum class Bbos6ThemeMode {
    DARK, LIGHT, SYSTEM;

    fun resolvesToDark(systemIsDark: Boolean): Boolean = when (this) {
        DARK -> true
        LIGHT -> false
        SYSTEM -> systemIsDark
    }

    companion object {
        fun fromStoredValue(raw: String?): Bbos6ThemeMode =
            entries.firstOrNull { it.name == raw } ?: DARK
    }
}

/**
 * Application-scoped theme state with Android SharedPreferences persistence.
 *
 * This manager does not recolor screens by itself. Future theme-aware helpers
 * opt in by reading [mode] or [isDarkTheme]. Existing components are unchanged.
 */
class Bbos6ThemeManager private constructor(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val modeListeners = CopyOnWriteArraySet<(Bbos6ThemeMode) -> Unit>()

    // Backing state is private so Kotlin cannot generate a setMode JVM
    // accessor that collides with the explicit persistence-aware setMode().
    private val modeState = mutableStateOf(
        Bbos6ThemeMode.fromStoredValue(preferences.getString(MODE_KEY, null))
    )
    val mode: Bbos6ThemeMode
        get() = modeState.value

    fun setMode(next: Bbos6ThemeMode) {
        if (mode == next) return
        preferences.edit().putString(MODE_KEY, next.name).apply()
        modeState.value = next
        modeListeners.forEach { listener -> listener(next) }
    }

    /** For native View controllers that cannot observe Compose snapshot state. */
    fun addModeListener(listener: (Bbos6ThemeMode) -> Unit) {
        modeListeners.add(listener)
    }

    fun removeModeListener(listener: (Bbos6ThemeMode) -> Unit) {
        modeListeners.remove(listener)
    }

    companion object {
        private const val PREFERENCES_NAME = "dev.q25.bbos6.ui.theme"
        private const val MODE_KEY = "appearance_mode"
        private val managers = mutableMapOf<String, Bbos6ThemeManager>()

        /** Always use the application context; no Activity is retained. */
        @Synchronized
        fun forApplication(context: Context): Bbos6ThemeManager {
            val app = context.applicationContext
            return managers.getOrPut(app.packageName) { Bbos6ThemeManager(app) }
        }
    }
}

/** The same manager instance is shared across independently hosted Compose UIs. */
@Composable
fun rememberBbos6ThemeManager(): Bbos6ThemeManager {
    val app = LocalContext.current.applicationContext
    return remember(app) { Bbos6ThemeManager.forApplication(app) }
}

/** Reads Compose's Android system night mode when this app selects SYSTEM. */
@Composable
fun Bbos6ThemeManager.isDarkTheme(): Boolean =
    mode.resolvesToDark(isSystemInDarkTheme())

/**
 * Optional theme scope. Without this scope, existing shared components retain
 * their original dark appearance; one app must explicitly opt in at its root.
 */
internal val LocalBbos6ThemeIsDark = compositionLocalOf<Boolean?> { null }

internal object Bbos6ThemePalette {
    fun background(isDark: Boolean): Color = if (isDark) Color.Black else Color.White
    fun foreground(isDark: Boolean): Color = if (isDark) Color.White else Color.Black
}

/**
 * App-owned theme preference is provided once to theme-aware BBOS6 helpers.
 * No focus owner, gesture handling, or system-bar insets are added here.
 */
@Composable
fun Bbos6ThemeSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isDark = rememberBbos6ThemeManager().isDarkTheme()
    CompositionLocalProvider(LocalBbos6ThemeIsDark provides isDark) {
        Box(modifier.background(Bbos6ThemePalette.background(isDark))) {
            content()
        }
    }
}
