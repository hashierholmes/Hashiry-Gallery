package com.hashiry.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import android.net.Uri
import com.hashiry.gallery.data.PsdPreviewDecoder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.hashiry.gallery.model.TrashEntry
import com.hashiry.gallery.ui.theme.GalleryTheme
import com.hashiry.gallery.util.formatCount
import com.hashiry.gallery.util.formatDate
import com.hashiry.gallery.util.formatSize
import java.io.File

@Composable
fun TrashScreen(
    entries: List<TrashEntry>,
    trashDir: File,
    vm: GalleryViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    val palette = GalleryTheme.palette
    var selected by remember { mutableStateOf<TrashEntry?>(null) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TrashEntry?>(null) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }

    fun toggleSelection(entry: TrashEntry) {
        selectedIds = if (entry.id in selectedIds) selectedIds - entry.id else selectedIds + entry.id
    }

    Column(modifier = Modifier.fillMaxSize().background(palette.background)) {
        DetailTopBar(
            title = if (selectedIds.isEmpty()) "Recently Deleted" else "${selectedIds.size} Selected",
            onBack = { if (selectedIds.isNotEmpty()) selectedIds = emptySet() else onBack() }
        ) {
            if (selectedIds.isNotEmpty()) {
                Text(
                    text = "Restore",
                    color = Color(0xFF0A84FF),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .combinedClickable(onClick = {
                            val targets = entries.filter { it.id in selectedIds }
                            vm.restoreFromTrash(targets) { restored, failed ->
                                onMessage(if (failed == 0) "Restored $restored ${if (restored == 1) "item" else "items"}" else "Restored $restored · failed $failed")
                            }
                            selectedIds = emptySet()
                        })
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                Text(
                    text = "Delete",
                    color = Color(0xFFFF3B30),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 2.dp).clip(RoundedCornerShape(8.dp))
                        .combinedClickable(onClick = { confirmDeleteSelected = true })
                        .padding(horizontal = 7.dp, vertical = 6.dp)
                )
                Text(
                    text = "Cancel",
                    color = palette.primaryText,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 1.dp).clip(RoundedCornerShape(8.dp))
                        .combinedClickable(onClick = { selectedIds = emptySet() })
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                )
            } else if (entries.isNotEmpty()) {
                Text(
                    text = "Empty",
                    color = Color(0xFFFF3B30),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .combinedClickable(onClick = { confirmEmpty = true })
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        if (entries.isEmpty()) {
            EmptyState(
                icon = GalleryIcons.Trash,
                title = "Nothing Deleted",
                message = "Photos you delete move here first, so you can always put them back. Items are removed automatically after 30 days."
            )
        } else {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Items here stay on this device for 30 days. Tap one to restore it or delete it permanently.",
                    color = palette.secondaryText,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(count = entries.size, key = { entries[it].id }) { index ->
                        val entry = entries[index]
                        TrashThumbnail(
                            entry = entry,
                            file = File(trashDir, entry.fileName),
                            isSelected = entry.id in selectedIds,
                            onClick = {
                                if (selectedIds.isNotEmpty()) toggleSelection(entry) else selected = entry
                            },
                            onLongClick = { toggleSelection(entry) }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }

    val current = selected
    GalleryActionSheet(
        visible = current != null,
        title = current?.let { "${it.name} · ${formatSize(it.sizeBytes)} · deleted ${formatDate(it.deletedAt)}" },
        actions = listOf(
            SheetAction("Restore", GalleryIcons.Restore) {
                current?.let { entry ->
                    vm.restoreFromTrash(entry) { ok ->
                        onMessage(if (ok) "Restored to your library" else "Couldn't restore that item")
                    }
                }
            },
            SheetAction("Delete Permanently", GalleryIcons.Trash, destructive = true) {
                confirmDelete = current
            }
        ),
        onDismiss = { selected = null }
    )

    val deleteTarget = confirmDelete
    if (deleteTarget != null) {
        ConfirmDialog(
            title = "Delete Permanently?",
            message = "“${deleteTarget.name}” will be removed from this device for good. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = null },
            onConfirm = {
                vm.deletePermanently(listOf(deleteTarget)) { onMessage("Deleted permanently") }
                confirmDelete = null
            }
        )
    }

    if (confirmDeleteSelected) {
        val targets = entries.filter { it.id in selectedIds }
        ConfirmDialog(
            title = "Delete ${formatCount(targets.size, "item")} permanently?",
            message = "The selected items will be removed from this device for good. This can't be undone.",
            confirmLabel = "Delete Permanently",
            destructive = true,
            onDismiss = { confirmDeleteSelected = false },
            onConfirm = {
                vm.deletePermanently(targets) {
                    onMessage("Deleted ${targets.size} ${if (targets.size == 1) "item" else "items"} permanently")
                }
                selectedIds = emptySet()
                confirmDeleteSelected = false
            }
        )
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = "Empty Recently Deleted?",
            message = "All ${formatCount(entries.size, "item")} here will be removed from this device for good. This can't be undone.",
            confirmLabel = "Delete All",
            destructive = true,
            onDismiss = { confirmEmpty = false },
            onConfirm = {
                vm.deletePermanently(entries) { onMessage("Recently Deleted is empty") }
                confirmEmpty = false
            }
        )
    }
}

@Composable
private fun TrashThumbnail(
    entry: TrashEntry,
    file: File,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val palette = GalleryTheme.palette
    val context = LocalContext.current
    val isPsd = entry.name.endsWith(".psd", true)
    val psdBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, file, isPsd) {
        value = if (isPsd && file.isFile) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            PsdPreviewDecoder.decode(context, Uri.fromFile(file), 256)
        } else null
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (isPsd) {
            val bitmap = psdBitmap
            if (bitmap != null) {
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("PSD", color = palette.secondaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .size(256, 256)
                    .scale(Scale.FILL)
                    .precision(Precision.INEXACT)
                    .allowRgb565(true)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (isSelected) {
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                .clip(RoundedCornerShape(50)).background(Color(0xFF0A84FF)), contentAlignment = Alignment.Center) {
                Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (entry.isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x73000000))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = GalleryIcons.VideoBadge,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(9.dp)
                )
            }
        }
    }
}
