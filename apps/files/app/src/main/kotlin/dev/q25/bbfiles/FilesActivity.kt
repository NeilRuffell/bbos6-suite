package dev.q25.bbfiles

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.q25.bbos6.ui.compose.Bbos6BackgroundLoadingIndicator
import dev.q25.bbos6.ui.compose.Bbos6EmptyState
import dev.q25.bbos6.ui.compose.Bbos6ListSelectionItem
import dev.q25.bbos6.ui.compose.Bbos6PageList
import dev.q25.bbos6.ui.compose.Bbos6PopupAction
import dev.q25.bbos6.ui.compose.Bbos6PopupButtonDialog
import dev.q25.bbos6.ui.compose.Bbos6SectionHeader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class StorageRoot(val id: String, val label: String, val directory: File)
private data class InfoPopup(val title: String, val message: String)

/**
 * Native Android shared-storage browser. Access is opt-in through Android
 * Settings; no private app data, root access, or file modifications.
 */
class FilesActivity : ComponentActivity() {
    private var allFilesAccess by mutableStateOf(false)
    private var removableDirectory by mutableStateOf<File?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshStorage()
        setContent {
            FilesPage(
                internalDirectory = Environment.getExternalStorageDirectory(),
                removableDirectory = removableDirectory,
                accessGranted = allFilesAccess,
                requestPermission = ::openAllFilesSettings
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStorage()
    }

    private fun refreshStorage() {
        allFilesAccess = Environment.isExternalStorageManager()
        removableDirectory = getSystemService(StorageManager::class.java)
            .storageVolumes.firstOrNull {
                it.isRemovable && (it.state == Environment.MEDIA_MOUNTED ||
                    it.state == Environment.MEDIA_MOUNTED_READ_ONLY)
            }?.directory
    }

    private fun openAllFilesSettings(): Boolean {
        return try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            true
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }
}

@Composable
private fun FilesPage(
    internalDirectory: File,
    removableDirectory: File?,
    accessGranted: Boolean,
    requestPermission: () -> Boolean
) {
    val roots = remember(internalDirectory, removableDirectory) {
        buildList {
            add(StorageRoot("internal", "Internal Storage", internalDirectory))
            if (removableDirectory != null) {
                add(StorageRoot("removable", "SD Card", removableDirectory))
            }
        }
    }
    val homeItems = remember(roots) {
        roots.map { Bbos6ListSelectionItem(it.id, it.label) } +
            Bbos6ListSelectionItem("recent", "Recent Files")
    }
    var currentRoot by remember { mutableStateOf<StorageRoot?>(null) }
    var currentFolder by remember { mutableStateOf<File?>(null) }
    var accessTarget by remember { mutableStateOf<String?>(null) }
    var pendingAfterPermission by remember { mutableStateOf<String?>(null) }
    var popup by remember { mutableStateOf<InfoPopup?>(null) }

    // Returning from Android Settings updates accessGranted in onResume.
    LaunchedEffect(accessGranted, pendingAfterPermission, roots) {
        if (accessGranted && pendingAfterPermission != null) {
            roots.firstOrNull { it.id == pendingAfterPermission }?.let {
                currentRoot = it
                currentFolder = it.directory
            }
            pendingAfterPermission = null
        }
        if (!accessGranted && currentRoot != null) {
            currentRoot = null
            currentFolder = null
        }
        if (currentRoot?.id == "removable" && removableDirectory == null) {
            currentRoot = null
            currentFolder = null
        }
    }

    fun openRoot(id: String) {
        val root = roots.firstOrNull { it.id == id } ?: return
        if (accessGranted) {
            currentRoot = root
            currentFolder = root.directory
        } else {
            accessTarget = id
        }
    }

    fun navigateUp() {
        val root = currentRoot ?: return
        val folder = currentFolder ?: return
        val parent = FileBrowser.parentWithin(root.directory, folder)
        if (parent == null) {
            currentRoot = null
            currentFolder = null
        } else {
            currentFolder = parent
        }
    }

    BackHandler(enabled = currentFolder != null && accessTarget == null && popup == null) {
        navigateUp()
    }

    val root = currentRoot
    val folder = currentFolder
    var listing by remember(root?.directory?.path, folder?.path, accessGranted) {
        mutableStateOf<FileBrowser.Listing?>(null)
    }
    LaunchedEffect(root?.directory?.path, folder?.path, accessGranted) {
        if (root != null && folder != null && accessGranted) {
            listing = withContext(Dispatchers.IO) {
                FileBrowser.list(root.directory, folder)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Bbos6SectionHeader(
            title = if (root != null && folder != null) {
                FileBrowser.relativeTitle(root.directory, folder, root.label)
            } else "Files"
        )
        Box(Modifier.weight(1f)) {
            if (root == null || folder == null) {
                key("home") {
                    Bbos6PageList(
                        items = homeItems,
                        onActivate = { id ->
                            if (id == "recent") {
                                popup = InfoPopup("Recent Files", "Recent Files is not implemented yet.")
                            } else openRoot(id)
                        }
                    )
                }
            } else {
                when (val contents = listing) {
                    null -> Unit
                    is FileBrowser.Listing.Unavailable -> {
                        Bbos6EmptyState(
                            heading = "Folder Unavailable",
                            description = contents.reason,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is FileBrowser.Listing.Available -> {
                        if (contents.entries.isEmpty()) {
                            Bbos6EmptyState(
                                heading = "Empty Folder",
                                description = "This folder contains no files.",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val byPath = remember(contents) {
                                contents.entries.associateBy { it.file.path }
                            }
                            key(folder.path) {
                                Bbos6PageList(
                                    items = contents.entries.map {
                                        Bbos6ListSelectionItem(
                                            id = it.file.path,
                                            label = if (it.isDirectory) "${it.name}/" else it.name
                                        )
                                    },
                                    onActivate = { id ->
                                        val selected = byPath[id]
                                        if (selected != null) {
                                            if (selected.isDirectory) {
                                                currentFolder = selected.file
                                            } else {
                                                popup = InfoPopup(
                                                    selected.name,
                                                    "Opening files is not implemented yet."
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (root != null && folder != null && listing == null && accessGranted) {
            Bbos6BackgroundLoadingIndicator(accessibilityLabel = "Loading folder")
        }
    }

    val target = accessTarget
    if (target != null) {
        Bbos6PopupButtonDialog(
            title = "Storage Access",
            message = "To browse files and folders, allow Files to manage files in Android Settings.",
            actions = listOf(
                Bbos6PopupAction("cancel", "Cancel"),
                Bbos6PopupAction("settings", "Open Settings")
            ),
            initialSelectionId = "cancel",
            onDismissRequest = { accessTarget = null },
            onAction = { id, _ ->
                accessTarget = null
                if (id == "settings") {
                    pendingAfterPermission = target
                    if (!requestPermission()) {
                        pendingAfterPermission = null
                        popup = InfoPopup(
                            "Storage Access",
                            "Android did not provide a storage permission settings screen."
                        )
                    }
                }
            }
        )
    }

    val info = popup
    if (info != null) {
        Bbos6PopupButtonDialog(
            title = info.title,
            message = info.message,
            actions = listOf(Bbos6PopupAction("ok", "OK")),
            initialSelectionId = "ok",
            onDismissRequest = { popup = null },
            onAction = { _, _ -> popup = null }
        )
    }
}
