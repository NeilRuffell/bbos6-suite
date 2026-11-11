package dev.q25.bbos6.ui

import android.graphics.drawable.Drawable

/**
 * Immutable, application-defined context action model. Preserves the original
 * Java-callable factory and accessor ABI for existing Android consumers.
 */
class Bbos6ContextMenuItem private constructor(
    private val actionId: String,
    private val actionLabel: String,
    private val resource: Int,
    private val drawable: Drawable?,
    private val isEnabled: Boolean
) {
    fun id(): String = actionId
    fun label(): String = actionLabel
    fun iconResourceId(): Int = resource
    fun iconDrawable(): Drawable? = drawable
    fun enabled(): Boolean = isEnabled

    fun withEnabled(enabled: Boolean) =
        Bbos6ContextMenuItem(actionId, actionLabel, resource, drawable, enabled)

    companion object {
        private fun required(value: String, name: String): String {
            require(value.isNotBlank()) { "$name must not be blank" }
            return value
        }

        @JvmStatic
        fun resource(id: String, label: String, iconResourceId: Int): Bbos6ContextMenuItem {
            require(iconResourceId != 0) { "iconResourceId must not be zero" }
            return Bbos6ContextMenuItem(
                required(id, "id"), required(label, "label"), iconResourceId, null, true
            )
        }

        @JvmStatic
        fun drawable(id: String, label: String, icon: Drawable): Bbos6ContextMenuItem =
            Bbos6ContextMenuItem(required(id, "id"), required(label, "label"), 0, icon, true)

        @JvmStatic
        fun text(id: String, label: String): Bbos6ContextMenuItem =
            Bbos6ContextMenuItem(required(id, "id"), required(label, "label"), 0, null, true)
    }
}
