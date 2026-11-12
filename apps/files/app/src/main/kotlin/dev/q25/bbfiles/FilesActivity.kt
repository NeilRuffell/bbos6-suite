package dev.q25.bbfiles

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import android.webkit.MimeTypeMap
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.core.content.FileProvider
import dev.q25.bbos6.ui.Bbos6PopupButtonList
import dev.q25.bbos6.ui.R as Bbos6UiR
import dev.q25.bbos6.ui.compose.Bbos6BackgroundLoadingIndicator
import dev.q25.bbos6.ui.compose.Bbos6ContextAction
import dev.q25.bbos6.ui.compose.Bbos6ContextMenuDialog
import dev.q25.bbos6.ui.compose.Bbos6EmptyState
import dev.q25.bbos6.ui.compose.Bbos6ListSelectionItem
import dev.q25.bbos6.ui.compose.Bbos6PageList
import dev.q25.bbos6.ui.compose.Bbos6PopupAction
import dev.q25.bbos6.ui.compose.Bbos6PopupButtonDialog
import dev.q25.bbos6.ui.compose.Bbos6SectionHeader
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class StorageRoot(val id: String, val label: String, val directory: File)
private data class InfoPopup(val title: String, val message: String)
private data class ClipboardItem(val root: File, val source: File, val cut: Boolean)

/** File-manager UI: no privileged operations beyond explicitly granted Android access. */
class FilesActivity : ComponentActivity() {
    private var allFilesAccess by mutableStateOf(false)
    private var removableDirectory by mutableStateOf<File?>(null)
    private var resumeCounter by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshStorage()
        setContent {
            FilesPage(
                internalDirectory = Environment.getExternalStorageDirectory(),
                removableDirectory = removableDirectory,
                accessGranted = allFilesAccess,
                resumeCounter = resumeCounter,
                requestPermission = ::openAllFilesSettings,
                openOrShare = ::openOrShareFile,
                promptName = ::promptForName
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStorage()
        resumeCounter++
    }

    private fun refreshStorage() {
        allFilesAccess = Environment.isExternalStorageManager()
        removableDirectory = getSystemService(StorageManager::class.java)
            .storageVolumes.firstOrNull {
                it.isRemovable && (it.state == Environment.MEDIA_MOUNTED ||
                    it.state == Environment.MEDIA_MOUNTED_READ_ONLY)
            }?.directory
    }

    private fun openAllFilesSettings(): Boolean = try {
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

    /**
     * FileProvider grants one selected file to the receiving Android app.
     * No file:// URI, exported filesystem service or broad read grant.
     */
    private fun openOrShareFile(root: File, file: File, share: Boolean): String? {
        if (!Environment.isExternalStorageManager()) return "Storage access was revoked."
        if (!FileBrowser.isWithin(root, file) || !file.isFile || !file.canRead()) {
            return "The file is unavailable or cannot be read."
        }
        val extension = file.extension.lowercase(Locale.ROOT)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
        return try {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val intent = if (share) {
                Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                }
            } else {
                Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mime) }
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newUri(contentResolver, file.name, uri)
            if (share) startActivity(Intent.createChooser(intent, "Share file"))
            else startActivity(intent)
            null
        } catch (_: ActivityNotFoundException) {
            "No installed application can handle this file type."
        } catch (_: SecurityException) {
            "Android prevented access to this file."
        } catch (_: IllegalArgumentException) {
            "This file cannot be shared using Android's file provider."
        }
    }

    /** Shared Q25-tested native text-field popup; storage actions remain in Files. */
    private fun promptForName(
        title: String, initialValue: String, onSave: (String) -> Unit
    ) {
        Bbos6PopupButtonList.builder()
            .title(title)
            .textInput(initialValue, "Name")
            .horizontalButtons()
            .addButton("cancel", "Cancel")
            .addButton("save", "Save")
            .initiallySelected("cancel")
            .onTextAction { actionId, text ->
                if (actionId == "save") onSave(text)
            }
            .show(this)
    }
}

@Composable
private fun FilesPage(
    internalDirectory: File,
    removableDirectory: File?,
    accessGranted: Boolean,
    resumeCounter: Int,
    requestPermission: () -> Boolean,
    openOrShare: (File, File, Boolean) -> String?,
    promptName: (String, String, (String) -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    val folderIcon = painterResource(Bbos6UiR.drawable.bbos6_menu_folders)
    val roots = remember(internalDirectory, removableDirectory) {
        buildList {
            add(StorageRoot("internal", "Internal Storage", internalDirectory))
            if (removableDirectory != null) add(
                StorageRoot("removable", "SD Card", removableDirectory)
            )
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
    var menuEntry by remember { mutableStateOf<FileBrowser.Entry?>(null) }
    var folderMenu by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FileBrowser.Entry?>(null) }
    var clipboard by remember { mutableStateOf<ClipboardItem?>(null) }
    var refreshCounter by remember { mutableIntStateOf(0) }
    var operationBusy by remember { mutableStateOf(false) }

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
            clipboard = null
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
        } else currentFolder = parent
    }

    fun mutate(operation: () -> FileOperations.Result, onSuccess: () -> Unit = {}) {
        if (operationBusy || !accessGranted) return
        operationBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { operation() }
            operationBusy = false
            refreshCounter++
            when (result) {
                is FileOperations.Result.Success -> onSuccess()
                is FileOperations.Result.Failure ->
                    popup = InfoPopup("File Operation", result.message)
            }
        }
    }

    fun createFolder() {
        val root = currentRoot ?: return
        val folder = currentFolder ?: return
        promptName("New Folder", "") { name ->
            mutate({ FileOperations.newFolder(root.directory, folder, name) })
        }
    }

    fun pasteItem() {
        val staged = clipboard ?: return
        val root = currentRoot ?: return
        val folder = currentFolder ?: return
        mutate(
            {
                FileOperations.paste(
                    staged.root, staged.source, root.directory, folder, staged.cut
                )
            },
            onSuccess = { if (staged.cut) clipboard = null }
        )
    }

    BackHandler(
        enabled = currentFolder != null && accessTarget == null &&
            popup == null && menuEntry == null && !folderMenu &&
            deleteTarget == null && !operationBusy
    ) {
        navigateUp()
    }

    val root = currentRoot
    val folder = currentFolder
    var listing by remember(
        root?.directory?.path, folder?.path, accessGranted, refreshCounter, resumeCounter
    ) {
        mutableStateOf<FileBrowser.Listing?>(null)
    }
    LaunchedEffect(root?.directory?.path, folder?.path, accessGranted, refreshCounter, resumeCounter) {
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
                        rowHeightDp = 52,
                        artworkSlotSizeDp = 36,
                        textHorizontalPaddingDp = 4,
                        artworkForId = { id ->
                            if (id == "internal" || id == "removable") folderIcon else null
                        },
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
                        val byPath = remember(contents) {
                            contents.entries.associateBy { it.file.path }
                        }
                        // The list contains filesystem entries only. Folder commands
                        // belong to the BBOS6 context menu, not synthetic rows.
                        val rows = contents.entries.map { entry ->
                            Bbos6ListSelectionItem(
                                id = entry.file.path,
                                label = if (entry.isDirectory) "${entry.name}/" else entry.name
                            )
                        }
                        key(folder.path) {
                            Box(Modifier.fillMaxSize()) {
                                Bbos6PageList(
                                    items = rows,
                                    rowHeightDp = 52,
                                    artworkSlotSizeDp = 36,
                                    textHorizontalPaddingDp = 4,
                                    artworkForId = { id ->
                                        if (byPath[id]?.isDirectory == true) folderIcon else null
                                    },
                                    onActivate = { id ->
                                        if (operationBusy) return@Bbos6PageList
                                        byPath[id]?.let { entry ->
                                            if (entry.isDirectory) currentFolder = entry.file
                                            else {
                                                val failure = openOrShare(
                                                    root.directory, entry.file, false
                                                )
                                                if (failure != null) {
                                                    popup = InfoPopup(entry.name, failure)
                                                }
                                            }
                                        }
                                    },
                                    onLongPress = { id ->
                                        if (!operationBusy) {
                                            val entry = byPath[id]
                                            if (entry == null) folderMenu = true
                                            else menuEntry = entry
                                        }
                                    },
                                    onMenu = { id ->
                                        if (!operationBusy) {
                                            val entry = byPath[id]
                                            if (entry == null) folderMenu = true
                                            else menuEntry = entry
                                        }
                                    }
                                )
                                if (rows.isEmpty()) {
                                    // Passive overlay: shared list remains the Menu
                                    // focus owner and handles blank-area long press.
                                    Bbos6EmptyState(
                                        heading = "Empty Folder",
                                        description = "This folder contains no files.",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (operationBusy || (root != null && folder != null && listing == null && accessGranted)) {
            Bbos6BackgroundLoadingIndicator(accessibilityLabel = "File operation in progress")
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

    val selected = menuEntry
    if (selected != null) {
        val actions = buildList {
            add(Bbos6ContextAction.text("open", "Open"))
            if (!selected.isDirectory) add(Bbos6ContextAction.text("share", "Share"))
            add(Bbos6ContextAction.text("copy", "Copy"))
            add(Bbos6ContextAction.text("cut", "Cut"))
            add(Bbos6ContextAction.text("rename", "Rename"))
            add(Bbos6ContextAction.text("delete", "Delete"))
            // Directory-level actions remain available when an item is selected.
            add(Bbos6ContextAction.text("new", "New Folder"))
            if (clipboard != null) add(Bbos6ContextAction.text("paste", "Paste"))
        }
        Bbos6ContextMenuDialog(
            groups = listOf(actions),
            onDismissRequest = { menuEntry = null },
            onAction = { action ->
                menuEntry = null
                val selectedRoot = root ?: return@Bbos6ContextMenuDialog
                if (!accessGranted || operationBusy) return@Bbos6ContextMenuDialog
                when (action.id) {
                    "open" -> {
                        if (selected.isDirectory) currentFolder = selected.file
                        else openOrShare(selectedRoot.directory, selected.file, false)?.let {
                            popup = InfoPopup(selected.name, it)
                        }
                    }
                    "share" -> openOrShare(selectedRoot.directory, selected.file, true)?.let {
                        popup = InfoPopup(selected.name, it)
                    }
                    "copy", "cut" -> {
                        clipboard = ClipboardItem(
                            selectedRoot.directory, selected.file, action.id == "cut"
                        )
                    }
                    "rename" -> promptName("Rename", selected.name) { newName ->
                        mutate({ FileOperations.rename(selectedRoot.directory, selected.file, newName) })
                    }
                    "delete" -> deleteTarget = selected
                    "new" -> createFolder()
                    "paste" -> pasteItem()
                }
            }
        )
    }

    if (folderMenu && root != null && folder != null) {
        val actions = buildList {
            add(Bbos6ContextAction.text("new", "New Folder"))
            if (clipboard != null) add(Bbos6ContextAction.text("paste", "Paste"))
            add(Bbos6ContextAction.text("refresh", "Refresh"))
        }
        Bbos6ContextMenuDialog(
            groups = listOf(actions),
            onDismissRequest = { folderMenu = false },
            onAction = { action ->
                folderMenu = false
                when (action.id) {
                    "new" -> createFolder()
                    "paste" -> pasteItem()
                    "refresh" -> refreshCounter++
                }
            }
        )
    }

    val deleting = deleteTarget
    if (deleting != null && root != null) {
        Bbos6PopupButtonDialog(
            title = "Delete ${deleting.name}?",
            message = if (deleting.isDirectory)
                "Delete this folder and all its contents permanently? This cannot be undone."
            else "Delete this file permanently? This cannot be undone.",
            actions = listOf(
                Bbos6PopupAction("cancel", "Cancel"),
                Bbos6PopupAction("delete", "Delete")
            ),
            initialSelectionId = "cancel",
            onDismissRequest = { deleteTarget = null },
            onAction = { id, _ ->
                deleteTarget = null
                if (id == "delete") {
                    mutate({ FileOperations.delete(root.directory, deleting.file) })
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
