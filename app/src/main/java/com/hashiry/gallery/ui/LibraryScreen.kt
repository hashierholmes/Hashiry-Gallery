package com.hashiry.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hashiry.gallery.data.FileOps
import com.hashiry.gallery.model.ManagedAlbum
import com.hashiry.gallery.model.MediaItem
import com.hashiry.gallery.ui.theme.GalleryTheme
import com.hashiry.gallery.util.DateGroup
import com.hashiry.gallery.util.formatNumber
import com.hashiry.gallery.util.groupByDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(
    items: List<MediaItem>,
    favorites: Set<Long>,
    loading: Boolean,
    albums: List<ManagedAlbum>,
    bottomBarHeight: Dp,
    vm: GalleryViewModel,
    onOpenViewer: (List<MediaItem>, Int) -> Unit,
    onSelectionActive: (Boolean) -> Unit,
    onOpenTrash: () -> Unit,
    recentlyDeletedEnabled: Boolean,
    onOpenCustomize: () -> Unit,
    onOpenSettings: () -> Unit,
    onMessage: (String) -> Unit
) {
    val palette = GalleryTheme.palette
    val context = LocalContext.current

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showMenu by remember { mutableStateOf(false) }
    var showAlbumPicker by remember { mutableStateOf(false) }
    var showNewAlbum by remember { mutableStateOf(false) }
    var pendingAlbumName by remember { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var newestFirst by rememberSaveable { mutableStateOf(true) }

    val trash = rememberTrashFlow(vm, onMessage)

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
        onSelectionActive(false)
    }

    fun enterSelection(item: MediaItem) {
        selectionMode = true
        selectedIds = setOf(item.id)
        onSelectionActive(true)
    }

    BackHandler(enabled = selectionMode) { exitSelection() }

    var visible by remember { mutableStateOf(items) }
    var groups by remember { mutableStateOf(emptyList<DateGroup>()) }
    // Only re-run date grouping on favorites change if favoritesOnly is active.
    LaunchedEffect(items, favoritesOnly, if (favoritesOnly) favorites else Unit, newestFirst) {
        val result = withContext(Dispatchers.Default) {
            val base = if (favoritesOnly) items.filter { it.id in favorites } else items
            val ordered = if (newestFirst) base else base.reversed()
            ordered to groupByDate(ordered)
        }
        visible = result.first
        groups = result.second
    }
    val videoCount = remember(items) { items.count { it.isVideo } }
    val selectedItems = remember(selectedIds, items) { items.filter { it.id in selectedIds } }

    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selectionMode) {
                SelectionTopBar(
                    count = selectedIds.size,
                    onClose = { exitSelection() }
                )
            } else {
                LargeTitleBar(
                    title = "Library",
                    subtitle = if (items.isEmpty()) "No items yet" else SubtitleLine(items.size, videoCount),
                    eyebrow = "Hashiry Gallery"
                ) {
                    listOf(
                        Triple(GalleryIcons.Adjust, "Library options", { showMenu = true }),
                        Triple(GalleryIcons.Settings, "Settings", onOpenSettings)
                    ).forEach { (icon, description, action) ->
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(palette.groupedBackground)
                                .border(0.7.dp, palette.separator, CircleShape)
                                .simpleClickable(pressedScale = 0.9f, onClick = action),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = description,
                                tint = palette.primaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    loading && items.isEmpty() -> LoadingState()
                    items.isEmpty() -> EmptyState(
                        icon = GalleryIcons.Camera,
                        title = "No photos or videos",
                        message = "Anything you capture or copy to this device will appear here automatically."
                    )
                    else -> MediaGrid(
                        groups = groups,
                        favorites = favorites,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp),
                        bottomPadding = bottomBarHeight + 66.dp,
                        showHeaders = true,
                        onSelectGroup = { groupItems ->
                            val groupIds = groupItems.map { it.id }.toSet()
                            selectedIds = if (selectedIds.containsAll(groupIds)) selectedIds - groupIds
                            else selectedIds + groupIds
                        },
                        onItemClick = { item ->
                            if (selectionMode) {
                                selectedIds = if (item.id in selectedIds) selectedIds - item.id
                                else selectedIds + item.id
                            } else {
                                val index = visible.indexOfFirst { it.id == item.id }
                                onOpenViewer(visible, if (index >= 0) index else 0)
                            }
                        },
                        onItemLongClick = { item ->
                            if (selectionMode) {
                                selectedIds = if (item.id in selectedIds) selectedIds - item.id
                                else selectedIds + item.id
                            } else {
                                enterSelection(item)
                            }
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selectionMode,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                SolidPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    elevation = 26.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SelectionAction(
                            icon = GalleryIcons.Share,
                            label = "Share",
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            context.startActivity(FileOps.shareIntent(selectedItems))
                            exitSelection()
                        }
                        SelectionAction(
                            icon = GalleryIcons.Heart,
                            label = "Favorite",
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            vm.setFavorite(selectedIds, true)
                            onMessage(if (selectedIds.size == 1) "Added to Favorites" else "Added to Favorites")
                            exitSelection()
                        }
                        SelectionAction(
                            icon = GalleryIcons.Plus,
                            label = "Add to Album",
                            enabled = selectedIds.isNotEmpty()
                        ) { showAlbumPicker = true }
                        SelectionAction(
                            icon = GalleryIcons.Trash,
                            label = "Delete",
                            enabled = selectedIds.isNotEmpty(),
                            destructive = true
                        ) {
                            trash(selectedItems)
                            exitSelection()
                        }
                    }
                }
            }
        }
    }

    GalleryActionSheet(
        visible = showMenu,
        title = "Hashiry Gallery",
        actions = listOfNotNull(
            SheetAction(
                if (favoritesOnly) "Show all photos" else "Show favorites only",
                GalleryIcons.Heart
            ) { favoritesOnly = !favoritesOnly },
            SheetAction(
                if (newestFirst) "Sort: oldest first" else "Sort: newest first",
                GalleryIcons.Restore
            ) { newestFirst = !newestFirst },
            if (recentlyDeletedEnabled) SheetAction("Recently Deleted", GalleryIcons.Trash) { onOpenTrash() } else null,
            SheetAction("Refresh library", GalleryIcons.Restore) {
                vm.refresh()
                onMessage("Library refreshed")
            },
        ),
        onDismiss = { showMenu = false }
    )

    AlbumPickerSheet(
        visible = showAlbumPicker,
        albums = albums,
        selectionCount = selectedIds.size,
        onDismiss = { showAlbumPicker = false },
        onPick = { album ->
            vm.addToAlbum(album.id, selectedIds)
            onMessage("Added to “${album.name}”")
            showAlbumPicker = false
            exitSelection()
        },
        onCreateNew = {
            showAlbumPicker = false
            pendingAlbumName = ""
            showNewAlbum = true
        }
    )

    if (showNewAlbum) {
        TextInputDialog(
            title = "New Album",
            initial = "",
            placeholder = "Album name",
            confirmLabel = "Create",
            onDismiss = { showNewAlbum = false },
            onConfirm = { name ->
                vm.createAlbum(name) { album ->
                    vm.addToAlbum(album.id, selectedIds)
                }
                onMessage("Created “$name”")
                showNewAlbum = false
                exitSelection()
            }
        )
    }
}

@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit
) {
    val palette = GalleryTheme.palette
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
    ) {
        SolidPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            elevation = 16.dp,
            contentPadding = PaddingValues(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(palette.groupedBackground)
                        .simpleClickable(pressedScale = 0.92f, onClick = onClose),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = GalleryIcons.Close,
                        contentDescription = "Cancel selection",
                        tint = palette.primaryText,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Text(
                    text = if (count == 1) "1 selected" else "${formatNumber(count)} selected",
                    color = palette.primaryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectionAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val palette = GalleryTheme.palette
    val tint = when {
        !enabled -> palette.tertiaryText
        destructive -> Color(0xFFFF3B30)
        else -> palette.accent
    }
    Column(
        modifier = Modifier
            .width(78.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(21.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(text = label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}
