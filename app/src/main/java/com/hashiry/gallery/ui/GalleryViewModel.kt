package com.hashiry.gallery.ui

import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hashiry.gallery.data.FileOps
import com.hashiry.gallery.data.MediaRepository
import com.hashiry.gallery.data.MetaStore
import com.hashiry.gallery.data.AppSettings
import com.hashiry.gallery.data.SettingsStore
import com.hashiry.gallery.model.DeviceAlbum
import com.hashiry.gallery.model.ManagedAlbum
import com.hashiry.gallery.model.MediaItem
import com.hashiry.gallery.model.MetaState
import com.hashiry.gallery.model.TrashEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TrashOutcome(
    /** Entries waiting for Android's confirmation, or already moved when no dialog is needed. */
    val entries: List<TrashEntry>,
    val consent: IntentSender?,
    /** Items successfully moved before a remaining batch required confirmation. */
    val alreadyMovedCount: Int = 0,
    /** Explains why Android could not provide a supported delete mechanism. */
    val failureMessage: String? = null
)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MediaRepository(app)
    private val metaStore = MetaStore(app)
    private val settingsStore = SettingsStore(app)

    val settings: StateFlow<AppSettings> = settingsStore.state

    val meta: StateFlow<MetaState> = metaStore.state

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _deviceAlbums = MutableStateFlow<List<DeviceAlbum>>(emptyList())
    val deviceAlbums: StateFlow<List<DeviceAlbum>> = _deviceAlbums.asStateFlow()

    private var initialLoadJob: Job? = null
    private var refreshJob: Job? = null
    private val mediaObserver = repository.observeMediaChanges {
        // MediaStore may emit several notifications for one camera burst or file operation.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(450)
            initialLoadJob?.join()
            load(showSpinner = false)
        }
    }

    init {
        initialLoadJob = viewModelScope.launch {
            metaStore.initialize()
            purgeExpiredTrash()
            load(showSpinner = true)
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            initialLoadJob?.join()
            load(showSpinner = false)
        }
    }

    private suspend fun load(showSpinner: Boolean) {
        if (showSpinner || _items.value.isEmpty()) _loading.value = true
        try {
            val loaded = repository.loadLibrary()
            // Grouping thousands of media rows into device albums is CPU work, not UI work.
            val albums = withContext(Dispatchers.Default) { repository.buildDeviceAlbums(loaded) }
            _items.value = loaded
            _deviceAlbums.value = albums
        } finally {
            _loading.value = false
        }
    }

    override fun onCleared() {
        initialLoadJob?.cancel()
        refreshJob?.cancel()
        runCatching { mediaObserver.close() }
        super.onCleared()
    }

    private suspend fun purgeExpiredTrash() {
        val cutoff = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L
        val expired = metaStore.state.value.trash.filter { it.deletedAt < cutoff }.map { it.id }
        if (expired.isNotEmpty()) metaStore.purgeTrash(expired)
    }

    // ------------------------------------------------------------ appearance

    fun setDockScale(value: Float) = settingsStore.setDockScale(value)


    fun setThemeMode(value: String) = settingsStore.setThemeMode(value)

    fun setGridColumns(value: Int) = settingsStore.setGridColumns(value)

    fun setAppName(value: String) = settingsStore.setAppName(value)

    fun setRecentlyDeletedEnabled(value: Boolean) {
        settingsStore.setRecentlyDeletedEnabled(value)
        if (!value) viewModelScope.launch {
            val entries = metaStore.state.value.trash
            if (entries.isNotEmpty()) metaStore.purgeTrash(entries.map { it.id })
        }
    }

    fun setDeleteConfirmationEnabled(value: Boolean) = settingsStore.setDeleteConfirmationEnabled(value)

    fun resetSettings() = settingsStore.reset()

    // ------------------------------------------------------------- favourites

    fun toggleFavorite(id: Long) {
        viewModelScope.launch { metaStore.toggleFavorite(id) }
    }

    fun setFavorite(ids: Collection<Long>, favorite: Boolean) {
        viewModelScope.launch { metaStore.setFavorites(ids, favorite) }
    }

    // ----------------------------------------------------------------- albums

    fun createAlbum(name: String, onCreated: (ManagedAlbum) -> Unit) {
        viewModelScope.launch { onCreated(metaStore.createAlbum(name)) }
    }

    fun renameAlbum(albumId: String, name: String) {
        viewModelScope.launch { metaStore.renameAlbum(albumId, name) }
    }

    fun deleteAlbum(albumId: String) {
        viewModelScope.launch { metaStore.deleteAlbum(albumId) }
    }

    fun addToAlbum(albumId: String, ids: Collection<Long>) {
        viewModelScope.launch { metaStore.addToAlbum(albumId, ids) }
    }

    fun removeFromAlbum(albumId: String, ids: Collection<Long>) {
        viewModelScope.launch { metaStore.removeFromAlbum(albumId, ids) }
    }

    // ------------------------------------------------------------------ trash

    /**
     * Soft delete: copies the bytes into the app's private trash first, then asks
     * Android to remove the original. If the system needs confirmation, the
     * returned IntentSender must be launched by the UI.
     */
    suspend fun moveToTrash(targets: List<MediaItem>): TrashOutcome {
        val context = getApplication<Application>()
        val trashDir = metaStore.trashDirectory()
        val staged = mutableListOf<Pair<MediaItem, TrashEntry>>()

        // Stage a private recovery copy first. Never remove an original unless its
        // backup was created successfully.
        for (item in targets.distinctBy { it.uri }) {
            val entry = FileOps.copyIntoTrash(context, item, trashDir) ?: continue
            staged += item to entry
        }
        if (staged.isEmpty()) return TrashOutcome(emptyList(), null)

        val isAndroid11OrNewer = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
        val canManageMedia = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            runCatching { android.provider.MediaStore.canManageMedia(context) }.getOrDefault(false)

        // PSDs are indexed through MediaStore.Files rather than Images. Route
        // any selection containing a PSD through the MediaStore delete request
        // API instead of calling ContentResolver.delete() directly. Android
        // suppresses its confirmation when MANAGE_MEDIA is granted; without
        // that permission it presents the normal system confirmation dialog.
        // This also keeps mixed PSD/photo/video selections in one atomic request.
        if (isAndroid11OrNewer && staged.all { it.first.isPsd } &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
            runCatching { android.os.Environment.isExternalStorageManager() }.getOrDefault(false)) {
            val deleted = mutableListOf<Pair<MediaItem, TrashEntry>>()
            for (pair in staged) {
                if (FileOps.deletePsdWithAllFilesAccess(context, pair.first)) deleted += pair
            }
            val failedDirect = staged.filterNot { pair -> deleted.any { it.first.uri == pair.first.uri } }
            if (failedDirect.isNotEmpty()) {
                val sender = FileOps.deleteConsent(context, failedDirect.map { it.first.uri })
                if (sender != null) {
                    if (settingsStore.state.value.recentlyDeletedEnabled) {
                        deleted.forEach { metaStore.addTrashEntry(it.second) }
                    } else {
                        deleted.forEach { FileOps.deleteTrashFile(trashDir, it.second) }
                    }
                    if (deleted.isNotEmpty()) load(showSpinner = false)
                    return TrashOutcome(
                        failedDirect.map { it.second }, sender,
                        alreadyMovedCount = deleted.size
                    )
                }
                failedDirect.forEach { FileOps.deleteTrashFile(trashDir, it.second) }
            }
            if (settingsStore.state.value.recentlyDeletedEnabled) {
                deleted.forEach { metaStore.addTrashEntry(it.second) }
            } else {
                deleted.forEach { FileOps.deleteTrashFile(trashDir, it.second) }
            }
            if (deleted.isNotEmpty()) load(showSpinner = false)
            return TrashOutcome(
                if (settingsStore.state.value.recentlyDeletedEnabled) deleted.map { it.second } else emptyList(),
                null,
                alreadyMovedCount = if (settingsStore.state.value.recentlyDeletedEnabled) 0 else deleted.size
            )
        }

        if (isAndroid11OrNewer && staged.any { it.first.isPsd }) {
            // With Media Management enabled, first try Android's direct media
            // deletion path. Without it, request the normal Android consent UI.
            if (canManageMedia) {
                val deletedUris = FileOps.deleteNow(context, staged.map { it.first.uri })
                val deleted = staged.filter { it.first.uri in deletedUris }
                val failed = staged.filter { it.first.uri !in deletedUris }
                if (deleted.isNotEmpty()) {
                    if (settingsStore.state.value.recentlyDeletedEnabled) deleted.forEach { metaStore.addTrashEntry(it.second) }
                    else deleted.forEach { FileOps.deleteTrashFile(trashDir, it.second) }
                    load(showSpinner = false)
                }
                if (failed.isEmpty()) {
                    return TrashOutcome(
                        if (settingsStore.state.value.recentlyDeletedEnabled) deleted.map { it.second } else emptyList(),
                        null,
                        alreadyMovedCount = if (settingsStore.state.value.recentlyDeletedEnabled) 0 else deleted.size
                    )
                }
                val sender = FileOps.deleteConsent(context, failed.map { it.first.uri })
                if (sender != null) return TrashOutcome(failed.map { it.second }, sender, alreadyMovedCount = deleted.size)
                failed.forEach { FileOps.deleteTrashFile(trashDir, it.second) }
                return TrashOutcome(
                    if (settingsStore.state.value.recentlyDeletedEnabled) deleted.map { it.second } else emptyList(),
                    null,
                    alreadyMovedCount = if (settingsStore.state.value.recentlyDeletedEnabled) 0 else deleted.size,
                    failureMessage = "Hashiry couldn't delete this PSD through Android's media deletion system. Enable All files access for Hashiry Gallery in Settings > File management, then try again."
                )
            }

            val sender = FileOps.deleteConsent(context, staged.map { it.first.uri })
            if (sender != null) return TrashOutcome(staged.map { it.second }, sender)

            // Never pretend a PSD was deleted when Android cannot provide a
            // supported consent request. Preserve the original and explain the
            // optional broader permission that may support direct deletion.
            staged.forEach { FileOps.deleteTrashFile(trashDir, it.second) }
            return TrashOutcome(
                emptyList(), null,
                failureMessage = "Android couldn't prepare deletion for this PSD file. To delete it, enable All files access for Hashiry Gallery in Settings > File management, then try again."
            )
        }

        // Without Media Management access, Android's system approval is the
        // supported path. Keep every staged backup pending until the user decides.
        if (isAndroid11OrNewer && !canManageMedia) {
            val sender = FileOps.deleteConsent(context, staged.map { it.first.uri })
            if (sender != null) return TrashOutcome(staged.map { it.second }, sender)
        }

        // With Media Management access (or on older Android), try direct deletion.
        // Crucially, failures are NOT treated as final: Android 11+ gets a system
        // delete-request fallback for only the URIs that direct deletion refused.
        val deletedUris = FileOps.deleteNow(context, staged.map { it.first.uri })
        val deleted = staged.filter { it.first.uri in deletedUris }
        val failed = staged.filter { it.first.uri !in deletedUris }

        if (settingsStore.state.value.recentlyDeletedEnabled) {
            deleted.forEach { metaStore.addTrashEntry(it.second) }
        } else {
            deleted.forEach { FileOps.deleteTrashFile(trashDir, it.second) }
        }
        if (deleted.isNotEmpty()) load(showSpinner = false)

        if (failed.isNotEmpty() && isAndroid11OrNewer) {
            val sender = FileOps.deleteConsent(context, failed.map { it.first.uri })
            if (sender != null) {
                return TrashOutcome(
                    entries = failed.map { it.second },
                    consent = sender,
                    alreadyMovedCount = deleted.size
                )
            }
        }

        // No system confirmation could be created, or we're on an older release.
        // Keep successful items in Recently Deleted and roll back failed backups.
        failed.forEach { FileOps.deleteTrashFile(trashDir, it.second) }
        return TrashOutcome(
            if (settingsStore.state.value.recentlyDeletedEnabled) deleted.map { it.second } else emptyList(),
            null,
            alreadyMovedCount = if (settingsStore.state.value.recentlyDeletedEnabled) 0 else deleted.size,
            failureMessage = if (failed.isNotEmpty() && failed.any { it.first.isPsd })
                "Hashiry couldn't delete this PSD through Android's media deletion system. Enable All files access for Hashiry Gallery in Settings > File management, then try again."
            else null
        )
    }

    /** Commit staged backups only after Android confirms the original deletion. */
    fun commitTrash(entries: List<TrashEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            if (settingsStore.state.value.recentlyDeletedEnabled) {
                entries.forEach { metaStore.addTrashEntry(it) }
            } else {
                val dir = metaStore.trashDirectory()
                entries.forEach { FileOps.deleteTrashFile(dir, it) }
            }
            load(showSpinner = false)
        }
    }

    fun discardTrash(entries: List<TrashEntry>) {
        viewModelScope.launch {
            val dir = metaStore.trashDirectory()
            entries.forEach { FileOps.deleteTrashFile(dir, it) }
            metaStore.purgeTrash(entries.map { it.id })
        }
    }

    fun restoreFromTrash(entry: TrashEntry, onDone: (Boolean) -> Unit = {}) {
        restoreFromTrash(listOf(entry)) { restored, failed -> onDone(restored == 1 && failed == 0) }
    }

    /** Restore multiple selected items and refresh the library once at the end. */
    fun restoreFromTrash(entries: List<TrashEntry>, onDone: (restored: Int, failed: Int) -> Unit = { _, _ -> }) {
        if (entries.isEmpty()) { onDone(0, 0); return }
        viewModelScope.launch {
            val context = getApplication<Application>()
            val trashDir = metaStore.trashDirectory()
            var restored = 0
            var failed = 0
            for (entry in entries) {
                val ok = FileOps.restore(context, entry, trashDir)
                if (ok) {
                    metaStore.removeTrashEntry(entry.id)
                    restored++
                } else failed++
            }
            if (restored > 0) load(showSpinner = false)
            onDone(restored, failed)
        }
    }

    fun deletePermanently(entries: List<TrashEntry>, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val dir = metaStore.trashDirectory()
            entries.forEach { FileOps.deleteTrashFile(dir, it) }
            metaStore.purgeTrash(entries.map { it.id })
            onDone()
        }
    }

    // ------------------------------------------------------------- file ops

    fun renameMedia(item: MediaItem, newName: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = FileOps.rename(getApplication<Application>(), item, newName)
            if (ok) load(showSpinner = false)
            onDone(ok)
        }
    }
}
