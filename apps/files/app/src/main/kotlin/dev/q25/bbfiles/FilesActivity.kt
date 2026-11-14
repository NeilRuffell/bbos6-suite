package dev.q25.bbfiles

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
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
import dev.q25.bbos6.ui.compose.rememberBbos6PageListMultiSelectionState
import dev.q25.bbos6.ui.compose.Bbos6PopupAction
import dev.q25.bbos6.ui.compose.Bbos6PropertiesDialog
import dev.q25.bbos6.ui.compose.Bbos6PropertyRow
import dev.q25.bbos6.ui.compose.Bbos6PopupButtonDialog
import dev.q25.bbos6.ui.compose.Bbos6SectionHeader
import dev.q25.bbos6.ui.compose.Bbos6SectionHeaderStyle
import dev.q25.bbos6.ui.compose.Bbos6ThemeSelector
import dev.q25.bbos6.ui.compose.Bbos6ThemeSurface
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class StorageRoot(val id: String, val label: String, val directory: File)
private data class InfoPopup(val title: String, val message: String)
private data class ClipboardItem(val root: File, val sources: List<File>, val cut: Boolean)
private data class PropertiesRequest(val root: File, val entry: FileBrowser.Entry)

/** Platform formatting stays in Files; the shared dialog accepts only presentation rows. */
private fun filePropertyRows(
    context: Context,
    metadata: FilePropertiesReader.Metadata
): List<Bbos6PropertyRow> = buildList {
    add(Bbos6PropertyRow.Text("Name: ${metadata.name}"))
    add(Bbos6PropertyRow.Text("Location: ${metadata.location}"))
    val type = if (metadata.isDirectory) "Folder" else {
        metadata.extension?.let { extension ->
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        } ?: "File"
    }
    add(Bbos6PropertyRow.Inline("Type:", type))
    metadata.sizeBytes?.let { size ->
        add(Bbos6PropertyRow.Trailing("Size:", Formatter.formatShortFileSize(context, size)))
    }
    metadata.modifiedMillis?.let { modified ->
        val date = Date(modified)
        val formatted = DateFormat.getMediumDateFormat(context).format(date) +
            " " + DateFormat.getTimeFormat(context).format(date)
        add(Bbos6PropertyRow.Trailing("Last Modified:", formatted))
    }
}

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
    val favouritedFolderIcon = painterResource(Bbos6UiR.drawable.bbos6_favourited_folder)
    val imagesHomeIcon = painterResource(Bbos6UiR.drawable.bbos6_files_home_images)
    val videosHomeIcon = painterResource(Bbos6UiR.drawable.bbos6_files_home_videos)
    val audioHomeIcon = painterResource(Bbos6UiR.drawable.bbos6_files_home_audio)
    val favouritesHomeIcon = painterResource(Bbos6UiR.drawable.bbos6_files_home_favourites)
    val recentHomeIcon = painterResource(Bbos6UiR.drawable.bbos6_files_home_recent)
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
    var favoriteFileSizes by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
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
            add(Bbos6ListSelectionItem("settings", "Settings"))
        }
    }

    var currentRoot by remember { mutableStateOf<StorageRoot?>(null) }
    var currentFolder by remember { mutableStateOf<File?>(null) }
    var shortcutRoot by remember { mutableStateOf<File?>(null) }
    var shortcutLabel by remember { mutableStateOf<String?>(null) }
    var returnToFavorites by remember { mutableStateOf(false) }
    var favoritesPage by remember { mutableStateOf(false) }
    var settingsPage by remember { mutableStateOf(false) }
    var indexedCategory by remember { mutableStateOf<IndexedMediaCategory?>(null) }
    var mediaMenuEntry by remember { mutableStateOf<MediaCategoryBrowser.Entry?>(null) }
    var accessTarget by remember { mutableStateOf<String?>(null) }
    var pendingAfterPermission by remember { mutableStateOf<String?>(null) }
    var popup by remember { mutableStateOf<InfoPopup?>(null) }
    var menuEntry by remember { mutableStateOf<FileBrowser.Entry?>(null) }
    var propertiesRequest by remember { mutableStateOf<PropertiesRequest?>(null) }
    var propertiesRows by remember { mutableStateOf<List<Bbos6PropertyRow>?>(null) }
    var favoriteMenu by remember { mutableStateOf<FileFavorite?>(null) }
    var folderMenu by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FileBrowser.Entry?>(null) }
    var clipboard by remember { mutableStateOf<ClipboardItem?>(null) }
    val multiSelection = rememberBbos6PageListMultiSelectionState()
    var multiSelectionMenu by remember { mutableStateOf(false) }
    var multiDeleteTargets by remember { mutableStateOf<List<FileBrowser.Entry>?>(null) }
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
        if (id == "settings") {
            // Settings is an app page, not a storage destination.
            settingsPage = true
            return
        }
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
        // Downloads remains a native directory shortcut. Documents is
        // handled by IndexedMediaCategory above, using MediaStore.Files.
        val directoryName = when (id) {
            "downloads" -> Environment.DIRECTORY_DOWNLOADS
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
        shortcutLabel = "Downloads"
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
        if (!accessGranted) {
            indexedCategory = null
            clipboard = null
        } else if (clipboard != null && roots.none { it.directory == clipboard?.root }) {
            // A staged transfer must not outlive its unmounted source volume.
            clipboard = null
        }
        if (currentRoot?.id == "removable" && removableDirectory == null) {
            currentRoot = null
            currentFolder = null
            shortcutRoot = null
            shortcutLabel = null
            returnToFavorites = false
        }
    }

    LaunchedEffect(propertiesRequest, accessGranted) {
        val request = propertiesRequest ?: return@LaunchedEffect
        propertiesRows = null
        if (!accessGranted) {
            propertiesRequest = null
            return@LaunchedEffect
        }
        val metadata = withContext(Dispatchers.IO) {
            FilePropertiesReader.read(request.root, request.entry.file, request.entry.name)
        }
        if (metadata == null) {
            propertiesRequest = null
            popup = InfoPopup(
                "Properties Unavailable",
                "The selected file or folder is no longer accessible."
            )
        } else {
            propertiesRows = filePropertyRows(context, metadata)
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

    fun mutate(
        operation: () -> FileOperations.Result,
        onSuccess: () -> Unit = {},
        onFailure: () -> Unit = {}
    ) {
        if (operationBusy || !accessGranted) return
        operationBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { operation() }
            operationBusy = false
            refreshCounter++
            when (result) {
                is FileOperations.Result.Success -> onSuccess()
                is FileOperations.Result.Failure -> {
                    onFailure()
                    popup = InfoPopup("File Operation", result.message)
                }
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
        var remainingSources = staged.sources
        mutate(
            operation = {
                val outcome = ClipboardTransfer.paste(
                    staged.root, staged.sources, root.directory, folder, staged.cut
                )
                remainingSources = outcome.remainingSources
                outcome.operation
            },
            onSuccess = {
                // Both Copy and Cut conclude after one successful paste.
                if (clipboard === staged) clipboard = null
            },
            onFailure = {
                // Retain only failed entries; successfully pasted items must
                // not be pasted again (or represented in the status count).
                if (clipboard === staged) clipboard = staged.copy(sources = remainingSources)
            }
        )
    }

    BackHandler(
        enabled = (currentFolder != null || favoritesPage || settingsPage ||
            indexedCategory != null || multiSelection.isActive) &&
            accessTarget == null && popup == null && menuEntry == null &&
            favoriteMenu == null && mediaMenuEntry == null &&
            !folderMenu && !multiSelectionMenu && deleteTarget == null &&
            multiDeleteTargets == null && propertiesRequest == null && !operationBusy
    ) {
        if (multiSelection.isActive) {
            multiSelection.exit()
        } else when {
            currentFolder != null -> navigateUp()
            indexedCategory != null -> indexedCategory = null
            favoritesPage -> favoritesPage = false
            settingsPage -> settingsPage = false
        }
    }

    val root = currentRoot
    val folder = currentFolder
    // Multi-selection must not leak into another directory or app page.
    val selectionPageKey = when {
        settingsPage -> "settings"
        favoritesPage -> "favorites"
        indexedCategory != null -> "indexed:${indexedCategory?.id}"
        root != null && folder != null -> "folder:${root.id}:${folder.path}"
        else -> "home"
    }
    LaunchedEffect(selectionPageKey) {
        multiSelection.exit()
        multiSelectionMenu = false
        multiDeleteTargets = null
    }
    val favouritedFolderPaths = remember(favorites, root?.id, root?.directory?.path) {
        val rootPath = try {
            root?.directory?.canonicalPath
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
        favorites.filter {
            it.isDirectory && it.storageId == root?.id && it.rootPath == rootPath
        }.map { it.path }.toSet()
    }
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

    LaunchedEffect(
        favoritesPage, favorites, roots, accessGranted, refreshCounter, resumeCounter
    ) {
        favoriteFileSizes = if (favoritesPage && accessGranted) {
            withContext(Dispatchers.IO) {
                favorites.mapNotNull { favorite ->
                    if (favorite.isDirectory) return@mapNotNull null
                    val volume = roots.firstOrNull { it.id == favorite.storageId }
                        ?: return@mapNotNull null
                    val file = FavoritePolicy.resolve(volume.directory, favorite)
                        ?: return@mapNotNull null
                    FileBrowser.fileSizeBytes(file)?.let { favorite.id to it }
                }.toMap()
            }
        } else emptyMap()
    }

    val visibleCategory = indexedCategory
    // The activity's root consumes the system status-bar inset so the
    // shared header and every Files screen start below clock/notification icons.
    // Keep system-bar policy at the window boundary, not in Bbos6SectionHeader.
    val pendingClipboard = clipboard
    Bbos6ThemeSurface(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
        Bbos6SectionHeader(
            title = when {
                settingsPage -> "Settings"
                favoritesPage -> "Favourites"
                visibleCategory != null -> visibleCategory.title
                root != null && folder != null -> FileBrowser.relativeTitle(
                    shortcutRoot ?: root.directory, folder, shortcutLabel ?: root.label
                ).let {
                    if (multiSelection.isActive) "$it (${multiSelection.selectedCount} selected)"
                    else it
                }
                else -> "Files"
            },
            style = Bbos6SectionHeaderStyle.FLAT_BLACK_COMPACT,
            trailingContent = if (pendingClipboard != null && accessGranted) {
                {
                    ClipboardStatusIndicator(
                        cut = pendingClipboard.cut,
                        count = pendingClipboard.sources.size
                    )
                }
            } else null
        )
        Box(Modifier.weight(1f)) {
            if (settingsPage) {
                key("settings") {
                    Bbos6ThemeSelector(modifier = Modifier.fillMaxSize())
                }
            } else if (favoritesPage) {
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
                                if (favoriteById[id]?.isDirectory == true) favouritedFolderIcon else null
                            },
                            trailingTextForId = { id ->
                                favoriteFileSizes[id]?.let { sizeBytes ->
                                    Formatter.formatShortFileSize(context, sizeBytes)
                                }
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
                                    trailingTextForId = { id ->
                                        byId[id]?.sizeBytes?.let { sizeBytes ->
                                            Formatter.formatShortFileSize(context, sizeBytes)
                                        }
                                    },
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
                        reserveArtworkSlotForMissing = true,
                        textHorizontalPaddingDp = 4,
                        artworkForId = { id ->
                            when (id) {
                                "internal", "removable", "downloads", "documents" -> folderIcon
                                "images" -> imagesHomeIcon
                                "videos" -> videosHomeIcon
                                "audio" -> audioHomeIcon
                                "favorites" -> favouritesHomeIcon
                                "recent" -> recentHomeIcon
                                else -> null
                            }
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
                                label = entry.name
                            )
                        }
                        key(folder.path) {
                            Box(Modifier.fillMaxSize()) {
                                Bbos6PageList(
                                    items = rows,
                                    multiSelectEnabled = true,
                                    multiSelectionState = multiSelection,
                                    rowHeightDp = 52,
                                    artworkSlotSizeDp = 36,
                                    textHorizontalPaddingDp = 4,
                                    artworkForId = { id ->
                                        if (byPath[id]?.isDirectory == true) {
                                            if (id in favouritedFolderPaths) favouritedFolderIcon
                                            else folderIcon
                                        } else null
                                    },
                                    trailingTextForId = { id ->
                                        byPath[id]?.sizeBytes?.let { sizeBytes ->
                                            Formatter.formatShortFileSize(context, sizeBytes)
                                        }
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
                                            if (multiSelection.isActive) multiSelectionMenu = true
                                            else {
                                                val entry = byPath[id]
                                                if (entry == null) folderMenu = true
                                                else menuEntry = entry
                                            }
                                        }
                                    },
                                    onMenu = { id ->
                                        if (!operationBusy) {
                                            if (multiSelection.isActive) multiSelectionMenu = true
                                            else {
                                                val entry = byPath[id]
                                                if (entry == null) folderMenu = true
                                                else menuEntry = entry
                                            }
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
        // Clipboard mode deliberately replaces all item actions. It never
        // pastes into a touched file/folder; paste targets currentFolder.
        val menuGroups = if (clipboard != null) {
            listOf(FilesClipboardMenu.actions(clipboard?.cut == true))
        } else {
            // Stable 3×3 primary grid when no transfer is pending.
            val primaryActions = listOf(
                Bbos6ContextAction.text("cut", "Cut"),
                Bbos6ContextAction.text("copy", "Copy"),
                Bbos6ContextAction.text("rename", "Rename"),
                Bbos6ContextAction.text("properties", "Properties"),
                Bbos6ContextAction.text("open", "Open"),
                Bbos6ContextAction.text("delete", "Delete"),
                Bbos6ContextAction.text("select", "Select"),
                Bbos6ContextAction("share", "Share", enabled = !selected.isDirectory),
                Bbos6ContextAction.text("new", "New Folder")
            )
            val favoriteActions = if (bookmark != null) listOf(
                Bbos6ContextAction.text(
                    if (alreadyPinned) "unfavorite" else "favorite",
                    if (alreadyPinned) "Remove from Favorites" else "Add to Favorites"
                )
            ) else emptyList()
            if (favoriteActions.isEmpty()) listOf(primaryActions)
            else listOf(primaryActions, favoriteActions)
        }
        Bbos6ContextMenuDialog(
            groups = menuGroups,
            onDismissRequest = { menuEntry = null },
            onAction = { action ->
                menuEntry = null
                val selectedRoot = root ?: return@Bbos6ContextMenuDialog
                if (!accessGranted || operationBusy) return@Bbos6ContextMenuDialog
                when (action.id) {
                    "select" -> multiSelection.enter(selected.file.path)
                    "properties" -> {
                        propertiesRows = null
                        propertiesRequest = PropertiesRequest(selectedRoot.directory, selected)
                    }
                    "open" -> {
                        if (selected.isDirectory) currentFolder = selected.file
                        else openOrShare(selectedRoot.directory, selected.file, false)?.let {
                            popup = InfoPopup(selected.name, it)
                        }
                    }
                    "share" -> if (!selected.isDirectory) {
                        openOrShare(selectedRoot.directory, selected.file, true)?.let {
                            popup = InfoPopup(selected.name, it)
                        }
                    }
                    "copy", "cut" -> {
                        clipboard = ClipboardItem(
                            selectedRoot.directory, listOf(selected.file), action.id == "cut"
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
                    "cancel_clipboard" -> clipboard = null
                }
            }
        )
    }

    if (propertiesRequest != null && accessGranted) {
        Bbos6PropertiesDialog(
            items = propertiesRows ?: listOf(Bbos6PropertyRow.Text("Loading properties…")),
            onDismissRequest = {
                propertiesRequest = null
                propertiesRows = null
            }
        )
    }

    // Bulk actions are owned by Files; the shared helper only tracks IDs.
    val currentEntries = (listing as? FileBrowser.Listing.Available)?.entries.orEmpty()
    val multiTargets = if (root != null && folder != null) {
        currentEntries.filter { it.file.path in multiSelection.selectedIds }
    } else emptyList()

    if (multiSelectionMenu && multiSelection.isActive && root != null && folder != null) {
        Bbos6ContextMenuDialog(
            groups = listOf(buildList {
                add(Bbos6ContextAction.text("select_all", "Select All"))
                add(Bbos6ContextAction.text("deselect_all", "Deselect All"))
                if (multiTargets.isNotEmpty()) {
                    add(Bbos6ContextAction.text("copy", "Copy Selected"))
                    add(Bbos6ContextAction.text("cut", "Cut Selected"))
                    add(Bbos6ContextAction.text("delete", "Delete Selected"))
                }
                add(Bbos6ContextAction.text("cancel", "Cancel Selection"))
            }),
            onDismissRequest = { multiSelectionMenu = false },
            onAction = { action ->
                multiSelectionMenu = false
                if (!accessGranted || operationBusy) return@Bbos6ContextMenuDialog
                when (action.id) {
                    "select_all" ->
                        multiSelection.selectAll(currentEntries.map { it.file.path })
                    "deselect_all" -> multiSelection.deselectAll()
                    "copy", "cut" -> {
                        if (multiTargets.isNotEmpty()) {
                            clipboard = ClipboardItem(
                                root.directory, multiTargets.map { it.file },
                                action.id == "cut"
                            )
                            multiSelection.exit()
                        }
                    }
                    "delete" -> if (multiTargets.isNotEmpty()) {
                        multiDeleteTargets = multiTargets.toList()
                    }
                    "cancel" -> multiSelection.exit()
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
        val actions = if (clipboard != null) {
            FilesClipboardMenu.actions(clipboard?.cut == true)
        } else listOf(
            Bbos6ContextAction.text("new", "New Folder"),
            Bbos6ContextAction.text("refresh", "Refresh")
        )
        Bbos6ContextMenuDialog(
            groups = listOf(actions),
            onDismissRequest = { folderMenu = false },
            onAction = { action ->
                folderMenu = false
                when (action.id) {
                    "new" -> createFolder()
                    "paste" -> pasteItem()
                    "cancel_clipboard" -> clipboard = null
                    "refresh" -> refreshCounter++
                }
            }
        )
    }

    val deletingMany = multiDeleteTargets
    if (deletingMany != null && root != null) {
        Bbos6PopupButtonDialog(
            title = "Delete ${deletingMany.size} selected items?",
            message = "Delete all selected files and folders permanently, including folder " +
                "contents? This cannot be undone.",
            actions = listOf(
                Bbos6PopupAction("cancel", "Cancel"),
                Bbos6PopupAction("delete", "Delete")
            ),
            initialSelectionId = "cancel",
            onDismissRequest = { multiDeleteTargets = null },
            onAction = { id, _ ->
                multiDeleteTargets = null
                if (id == "delete" && deletingMany.isNotEmpty()) {
                    mutate(
                        operation = {
                            val failures = deletingMany.mapNotNull { entry ->
                                when (val result = FileOperations.delete(root.directory, entry.file)) {
                                    is FileOperations.Result.Success -> null
                                    is FileOperations.Result.Failure -> "${entry.name}: ${result.message}"
                                }
                            }
                            if (failures.isEmpty()) FileOperations.Result.Success()
                            else FileOperations.Result.Failure(
                                "Deletion incomplete (${failures.size}/${deletingMany.size} failed): " +
                                    failures.joinToString("; ")
                            )
                        },
                        onSuccess = { multiSelection.exit() }
                    )
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
