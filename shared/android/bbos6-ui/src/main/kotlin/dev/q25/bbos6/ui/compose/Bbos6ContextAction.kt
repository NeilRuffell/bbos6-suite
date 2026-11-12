package dev.q25.bbos6.ui.compose

import android.graphics.drawable.Drawable

/**
 * App-neutral Kotlin action model for the Compose context menu.
 * Stable action IDs, icons, enabled state and callbacks belong to the caller.
 */
data class Bbos6ContextAction(
    val id: String,
    val label: String,
    val iconResourceId: Int = 0,
    val iconDrawable: Drawable? = null,
    val enabled: Boolean = true
) {
    init {
        require(id.isNotBlank()) { "Action ID must not be blank" }
        require(label.isNotBlank()) { "Action label must not be blank" }
        require(iconResourceId >= 0) { "Icon resource ID must not be negative" }
        require(iconResourceId == 0 || iconDrawable == null) {
            "Supply either an icon resource or drawable, not both"
        }
    }

    companion object {
        fun text(id: String, label: String) = Bbos6ContextAction(id, label)

        fun resource(id: String, label: String, resourceId: Int): Bbos6ContextAction {
            require(resourceId != 0) { "Icon resource ID must not be zero" }
            return Bbos6ContextAction(id, label, iconResourceId = resourceId)
        }

        fun drawable(id: String, label: String, image: Drawable) =
            Bbos6ContextAction(id, label, iconDrawable = image)
    }
}
