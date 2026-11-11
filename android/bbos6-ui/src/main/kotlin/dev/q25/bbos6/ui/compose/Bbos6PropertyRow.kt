package dev.q25.bbos6.ui.compose

/**
 * Kotlin-native, app-neutral Properties content. No app state, file access,
 * metadata queries or presentation framework dependencies.
 */
sealed interface Bbos6PropertyRow {
    data class Text(val content: String) : Bbos6PropertyRow
    data class Inline(val label: String, val value: String) : Bbos6PropertyRow
    data class Trailing(val label: String, val value: String) : Bbos6PropertyRow
    data class Spacer(val heightDp: Int) : Bbos6PropertyRow {
        init { require(heightDp >= 0) { "Spacer cannot be negative" } }
    }
}
