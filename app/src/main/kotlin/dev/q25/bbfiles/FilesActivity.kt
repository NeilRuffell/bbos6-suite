package dev.q25.bbfiles

import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.q25.bbos6.ui.compose.Bbos6ListSelectionItem
import dev.q25.bbos6.ui.compose.Bbos6PageList
import dev.q25.bbos6.ui.compose.Bbos6PopupAction
import dev.q25.bbos6.ui.compose.Bbos6PopupButtonDialog
import dev.q25.bbos6.ui.compose.Bbos6SectionHeader

/**
 * First-stage Files home page. File browsing, access grants and recents are
 * deliberately not implemented here; do not imply that they are functional.
 */
class FilesActivity : ComponentActivity() {
    private fun hasMountedRemovableStorage(): Boolean =
        getSystemService(StorageManager::class.java).storageVolumes.any {
            it.isRemovable && it.state == Environment.MEDIA_MOUNTED
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FilesHomePage(hasMountedRemovableStorage())
        }
    }
}

@Composable
private fun FilesHomePage(showRemovableStorage: Boolean) {
    val entries = remember(showRemovableStorage) {
        buildList {
            add(Bbos6ListSelectionItem("internal", "Internal Storage"))
            if (showRemovableStorage) {
                add(Bbos6ListSelectionItem("removable", "SD Card"))
            }
            add(Bbos6ListSelectionItem("recent", "Recent Files"))
        }
    }
    var pendingAction by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Bbos6SectionHeader(title = "Files")
        Bbos6PageList(
            items = entries,
            onActivate = { pendingAction = it },
            modifier = Modifier.weight(1f)
        )
    }

    if (pendingAction != null) {
        val title = entries.firstOrNull { it.id == pendingAction }?.label ?: "Files"
        Bbos6PopupButtonDialog(
            actions = listOf(Bbos6PopupAction("ok", "OK")),
            onDismissRequest = { pendingAction = null },
            onAction = { _, _ -> pendingAction = null },
            title = title,
            message = "File browsing will be added in the next development stage.",
            initialSelectionId = "ok"
        )
    }
}
