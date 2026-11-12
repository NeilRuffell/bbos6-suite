package dev.q25.bbos6.ui

/**
 * App-neutral, immutable Properties row. Java-callable factories and accessor
 * methods remain available for existing callers and Compose dialog bridges.
 */
class Bbos6PropertiesItem private constructor(
    private val itemKind: Kind,
    private val itemLabel: String,
    private val itemValue: String,
    private val spacingDp: Int
) {
    enum class Kind { TEXT, INLINE_PROPERTY, TRAILING_PROPERTY, SPACER }

    fun kind(): Kind = itemKind
    fun label(): String = itemLabel
    fun value(): String = itemValue
    fun spaceDp(): Int = spacingDp

    companion object {
        @JvmStatic
        fun text(text: String): Bbos6PropertiesItem =
            Bbos6PropertiesItem(Kind.TEXT, text, "", 0)

        @JvmStatic
        fun `inline`(label: String, value: String): Bbos6PropertiesItem =
            Bbos6PropertiesItem(Kind.INLINE_PROPERTY, label, value, 0)

        @JvmStatic
        fun trailing(label: String, value: String): Bbos6PropertiesItem =
            Bbos6PropertiesItem(Kind.TRAILING_PROPERTY, label, value, 0)

        @JvmStatic
        fun spacer(dp: Int): Bbos6PropertiesItem {
            require(dp >= 0) { "Spacer cannot be negative" }
            return Bbos6PropertiesItem(Kind.SPACER, "", "", dp)
        }
    }
}
