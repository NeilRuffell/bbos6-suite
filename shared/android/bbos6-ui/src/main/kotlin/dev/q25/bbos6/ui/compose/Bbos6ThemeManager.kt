package dev.q25.bbos6.ui.compose

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    var mode: Bbos6ThemeMode by mutableStateOf(
        Bbos6ThemeMode.fromStoredValue(preferences.getString(MODE_KEY, null))
    )
        private set

    fun setMode(next: Bbos6ThemeMode) {
        if (mode == next) return
        preferences.edit().putString(MODE_KEY, next.name).apply()
        mode = next
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
