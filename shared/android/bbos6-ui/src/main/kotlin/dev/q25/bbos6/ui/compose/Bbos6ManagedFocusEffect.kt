package dev.q25.bbos6.ui.compose

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import dev.q25.bbos6.ui.Bbos6FocusPolicy

/**
 * Reusable Compose -> native BBOS6 focus policy integration.
 *
 * LocalView is the actual AndroidComposeView, which does not exist when
 * the outer ComposeView is initially assembled. Apply after composition
 * so the platform's lighter default focus highlight never paints over
 * BBOS6-owned dialog/panel surfaces. Native EditText leaf focus is intact.
 */
@Composable
fun Bbos6ManagedFocusEffect() {
    val root = LocalView.current
    SideEffect {
        if (root is ViewGroup) Bbos6FocusPolicy.forManagedContainers(root)
    }
}
