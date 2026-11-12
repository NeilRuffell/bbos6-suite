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
import androidx.compose.ui.platform.LocalContext
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
                openIndexed = ::openOrShareIndexed,
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
            launchFileIntent(uri, file.name, mime, share)
        } catch (_: SecurityException) {
            "Android prevented access to this file."
        } catch (_: IllegalArgumentException) {
            "This file cannot be shared using Android's file provider."
        }
    }

    /** Indexed MediaStore files already have Android-managed content URIs. */
    private fun openOrShareIndexed(entry: MediaCategoryBrowser.Entry, share: Boolean): String? {
        if (!Environment.isExternalStorageManager()) return "Storage access was revoked."
        return launchFileIntent(entry.uri, entry.name, entry.mime, share)
    }

    private fun launchFileIntent(uri: Uri, name: String, mime: String, share: Boolean): String? =
        try {
            val intent = if (share) {
                Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                }
            } else Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mime) }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newUri(contentResolver, name, uri)
            if (share) startActivity(Intent.createChooser(intent, "Share file"))
            else startActivity(intent)
            null
        } catch (_: ActivityNotFoundException) {
            "No installed application can handle this file type."
        } catch (_: SecurityException) {
            "Android prevented access to this file."
        } catch (_: IllegalArgumentException) {
            "Android could not open this file URI."
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
    openIndexed: (MediaCategoryBrowser.Entry, Boolean) -> String?,
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
    val context = LocalContext.current
    val favoritesStore = remember(context) { FavoritesStore(context) }
    var favorites by remember(favoritesStore) { mutableStateOf(favoritesStore.load()) }
    val favoriteById = remember(favorites) { favorites.associateBy { it.id } }
    // Home is navigation only. Pinned shortcuts appear inside Favourites.
    val homeItems = remember(roots) {
        buildList {
            roots.forEach { add(Bbos6ListSelectionItem(it.id, it.label)) }
            add(Bbos6ListSelectionItem("recent", "Recent Files"))
            add(Bbos6ListSelectionItem("favorites", "Favourites"))
            add(Bbos6ListSelectionItem("images", "Images"))
            add(Bbos6ListSelectionItem("downloads", "Downloads"))
            add(Bbos6ListSelectionItem("audio", "Audio"))
            add(Bbos6ListSelectionItem("documents", "Documents"))
            add(Bbos6ListSelectionItem("videos", "Videos"))
        }
    }

    var currentRoot by remember { mutableStateOf<StorageRoot?>(null) }
    var currentFolder by remember { mutableStateOf<File?>(null) }
    var shortcutRoot by remember { mutableStateOf<File?>(null) }
    var shortcutLabel by remember { mutableStateOf<String?>(null) }
    var returnToFavorites by remember { mutableStateOf(false) }
    var favoritesPage by remember { mutableStateOf(false) }
    var indexedCategory by remember { mutableStateOf<IndexedMediaCategory?>(null) }
    var mediaMenuEntry by remember { mutableStateOf<MediaCategoryBrowser.Entry?>(null) }
    var accessTarget by remember { mutableStateOf<String?>(null) }
    var pendingAfterPermission by remember { mutableStateOf<String?>(null) }
    var popup by remember { mutableStateOf<InfoPopup?>(null) }
    var menuEntry by remember { mutableStateOf<FileBrowser.Entry?>(null) }
    var favoriteMenu by remember { mutableStateOf<FileFavorite?>(null) }
    var folderMenu by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FileBrowser.Entry?>(null) }
    var clipboard by remember { mutableStateOf<ClipboardItem?>(null) }
    var refreshCounter by remember { mutableIntStateOf(0) }
    var operationBusy by remember { mutableStateOf(false) }

    fun updateFavorites(items: List<FileFavorite>) {
        favoritesStore.save(items)
        favorites = items
    }

    fun activateFavorite(favorite: FileFavorite) {
        if (!accessGranted) {
            accessTarget = favorite.id
            return
        }
        val volume = roots.firstOrNull { it.id == favorite.storageId }
        val file = volume?.let { FavoritePolicy.resolve(it.directory, favorite) }
        if (volume == null || file == null) {
            popup = InfoPopup(
                "Favorite Unavailable",
                "The original file or folder cannot be accessed. " +
                    "Long-press this shortcut in Favourites to remove it."
            )
            return
        }
        if (favorite.isDirectory) {
            currentRoot = volume
            currentFolder = file
            shortcutRoot = file
            shortcutLabel = favorite.name
            returnToFavorites = favoritesPage
            favoritesPage = false
        } else {
            openOrShare(volume.directory, file, false)?.let {
                popup = InfoPopup(favorite.name, it)
            }
        }
    }

    fun openHomeItem(id: String) {
        if (id == "favorites") {
            favoritesPage = true
            return
        }
        if (id == "recent") {
            popup = InfoPopup("Recent Files", "Recent Files is not implemented yet.")
            return
        }
        if (!accessGranted) {
            accessTarget = id
            return
        }
        val volume = roots.firstOrNull { it.id == id }
        if (volume != null) {
            currentRoot = volume
            currentFolder = volume.directory
            shortcutRoot = null
            shortcutLabel = null
            returnToFavorites = false
            return
        }
        val category = IndexedMediaCategory.fromId(id)
        if (category != null) {
            indexedCategory = category
            return
        }
        val directoryName = when (id) {
            "downloads" -> Environment.DIRECTORY_DOWNLOADS
            "documents" -> Environment.DIRECTORY_DOCUMENTS
            else -> return
        }
        val internal = roots.firstOrNull { it.id == "internal" } ?: return
        val destination = File(internal.directory, directoryName)
        if (!FileBrowser.isWithin(internal.directory, destination)) {
            popup = InfoPopup("Storage Access", "This directory is outside internal storage.")
            return
        }
        currentRoot = internal
        currentFolder = destination
        shortcutRoot = destination
        shortcutLabel = if (id == "downloads") "Downloads" else "Documents"
        returnToFavorites = false
    }

    LaunchedEffect(accessGranted, pendingAfterPermission, roots, favorites) {
        if (accessGranted && pendingAfterPermission != null) {
            val requested = pendingAfterPermission ?: ""
            pendingAfterPermission = null
            val bookmarked = favoriteById[requested]
            if (bookmarked != null) activateFavorite(bookmarked)
            else openHomeItem(requested)
        }
        if (!accessGranted && currentRoot != null) {
            currentRoot = null
            currentFolder = null
            shortcutRoot = null
            shortcutLabel = null
            clipboard = null
            returnToFavorites = false
        }
        if (!accessGranted) indexedCategory = null
        if (currentRoot?.id == "removable" && removableDirectory == null) {
            currentRoot = null
            currentFolder = null
            shortcutRoot = null
            shortcutLabel = null
            returnToFavorites = false
        }
    }

    fun navigateUp() {
        val root = currentRoot ?: return
        val folder = currentFolder ?: return
        val boundary = shortcutRoot ?: root.directory
        val parent = FileBrowser.parentWithin(boundary, folder)
        if (parent == null) {
            currentRoot = null
            currentFolder = null
            shortcutRoot = null
            shortcutLabel = null
            favoritesPage = returnToFavorites
            returnToFavorites = false
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
        enabled = (currentFolder != null || favoritesPage || indexedCategory != null) &&
            accessTarget == null && popup == null && menuEntry == null &&
            favoriteMenu == null && mediaMenuEntry == null &&
            !folderMenu && deleteTarget == null && !operationBusy
    ) {
        when {
            currentFolder != null -> navigateUp()
            indexedCategory != null -> indexedCategory = null
            favoritesPage -> favoritesPage = false
        }
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

    var indexedListing by remember(indexedCategory, accessGranted, resumeCounter, refreshCounter) {
        mutableStateOf<MediaCategoryBrowser.Listing?>(null)
    }
    LaunchedEffect(indexedCategory, accessGranted, resumeCounter, refreshCounter) {
        val category = indexedCategory
        if (category != null && accessGranted) {
            indexedListing = withContext(Dispatchers.IO) {
                MediaCategoryBrowser.query(context.contentResolver, category)
            }
        }
    }

    val visibleCategory = indexedCategory
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Bbos6SectionHeader(
            title = when {
                favoritesPage -> "Favourites"
                visibleCategory != null -> visibleCategory.title
                root != null && folder != null -> FileBrowser.relativeTitle(
                    shortcutRoot ?: root.directory, folder, shortcutLabel ?: root.label
                )
                else -> "Files"
            }
        )
        Box(Modifier.weight(1f)) {
            if (favoritesPage) {
                if (favorites.isEmpty()) {
                    Bbos6EmptyState(
                        heading = "No Favourites",
                        description = "Long-press a file or folder and select Add to Favorites.",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    key("favorites") {
                        Bbos6PageList(
                            items = favorites.map {
                                Bbos6ListSelectionItem(it.id, it.name)
                            },
                            rowHeightDp = 52,
                            artworkSlotSizeDp = 36,
                            textHorizontalPaddingDp = 4,
                            artworkForId = { id ->
                                if (favoriteById[id]?.isDirectory == true) folderIcon else null
                            },
                            onActivate = { id ->
                                favoriteById[id]?.let { activateFavorite(it) }
                            },
                            onLongPress = { id -> favoriteMenu = favoriteById[id] },
                            onMenu = { id -> favoriteMenu = favoriteById[id] }
                        )
                    }
                }
            } else if (visibleCategory != null) {
                when (val contents = indexedListing) {
                    null -> Unit
                    is MediaCategoryBrowser.Listing.Unavailable -> {
                        Bbos6EmptyState(
                            heading = "Media Unavailable",
                            description = contents.reason,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is MediaCategoryBrowser.Listing.Available -> {
                        if (contents.entries.isEmpty()) {
                            Bbos6EmptyState(
                                heading = "No ${visibleCategory.title}",
                                description = "Android's media index contains no matching items.",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val byId = remember(contents) {
                                contents.entries.associateBy { it.id }
                            }
                            key(visibleCategory.id) {
                                Bbos6PageList(
                                    items = contents.entries.map {
                                        Bbos6ListSelectionItem(it.id, it.name)
                                    },
                                    rowHeightDp = 52,
                                    artworkSlotSizeDp = 36,
                                    textHorizontalPaddingDp = 4,
                                    onActivate = { id ->
                                        byId[id]?.let { entry ->
                                            openIndexed(entry, false)?.let {
                                                popup = InfoPopup(entry.name, it)
                                            }
                                        }
                                    },
                                    onLongPress = { id -> mediaMenuEntry = byId[id] },
                                    onMenu = { id -> mediaMenuEntry = byId[id] }
                                )
                            }
                        }
                    }
                }
            } else if (root == null || folder == null) {
                key("home") {
                    Bbos6PageList(
                        items = homeItems,
                        rowHeightDp = 52,
                        artworkSlotSizeDp = 36,
                        textHorizontalPaddingDp = 4,
                        artworkForId = { id ->
                            if (id == "internal" || id == "removable" ||
                                id == "downloads" || id == "documents"
                            ) folderIcon else null
                        },
                        onActivate = { id -> openHomeItem(id) }
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
        if (operationBusy ||
            (root != null && folder != null && listing == null && accessGranted) ||
            (visibleCategory != null && indexedListing == null && accessGranted)
        ) {
            Bbos6BackgroundLoadingIndicator(accessibilityLabel = "Loading files")
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
        val bookmark = root?.let {
            FavoritePolicy.create(it.id, it.directory, selected.file)
        }
        val alreadyPinned = bookmark != null && FavoritePolicy.contains(favorites, bookmark)
        val actions = buildList {
            add(Bbos6ContextAction.text("open", "Open"))
            if (!selected.isDirectory) add(Bbos6ContextAction.text("share", "Share"))
            add(Bbos6ContextAction.text("copy", "Copy"))
            add(Bbos6ContextAction.text("cut", "Cut"))
            add(Bbos6ContextAction.text("rename", "Rename"))
            add(Bbos6ContextAction.text("delete", "Delete"))
            if (bookmark != null) {
                add(Bbos6ContextAction.text(
                    if (alreadyPinned) "unfavorite" else "favorite",
                    if (alreadyPinned) "Remove from Favorites" else "Add to Favorites"
                ))
            }
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
                    "favorite" -> if (bookmark != null) {
                        updateFavorites(FavoritePolicy.add(favorites, bookmark))
                    }
                    "unfavorite" -> if (bookmark != null) {
                        updateFavorites(FavoritePolicy.remove(favorites, bookmark))
                    }
                    "new" -> createFolder()
                    "paste" -> pasteItem()
                }
            }
        )
    }

    val pinnedShortcut = favoriteMenu
    if (pinnedShortcut != null) {
        Bbos6ContextMenuDialog(
            groups = listOf(listOf(
                Bbos6ContextAction.text("open", "Open"),
                Bbos6ContextAction.text("remove", "Remove from Favorites")
            )),
            onDismissRequest = { favoriteMenu = null },
            onAction = { action ->
                favoriteMenu = null
                when (action.id) {
                    "open" -> activateFavorite(pinnedShortcut)
                    "remove" ->
                        updateFavorites(FavoritePolicy.remove(favorites, pinnedShortcut))
                }
            }
        )
    }

    val selectedMedia = mediaMenuEntry
    if (selectedMedia != null) {
        Bbos6ContextMenuDialog(
            groups = listOf(listOf(
                Bbos6ContextAction.text("open", "Open"),
                Bbos6ContextAction.text("share", "Share")
            )),
            onDismissRequest = { mediaMenuEntry = null },
            onAction = { action ->
                mediaMenuEntry = null
                openIndexed(selectedMedia, action.id == "share")?.let {
                    popup = InfoPopup(selectedMedia.name, it)
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
