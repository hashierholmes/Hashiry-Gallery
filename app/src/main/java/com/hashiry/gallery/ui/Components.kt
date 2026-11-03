package com.hashiry.gallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hashiry.gallery.data.MediaThumbnailStore
import com.hashiry.gallery.model.MediaItem
import com.hashiry.gallery.ui.theme.GalleryTheme
import com.hashiry.gallery.util.DateGroup
import com.hashiry.gallery.util.formatDuration
import com.hashiry.gallery.util.formatNumber

val LocalGridColumns = androidx.compose.runtime.compositionLocalOf { 4 }

enum class GalleryTab(val label: String, val icon: ImageVector) {
    Library("Library", GalleryIcons.Library),
    Albums("Albums", GalleryIcons.Albums),
    Favorites("Favorites", GalleryIcons.Heart),
    Search("Search", GalleryIcons.Search)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.8.dp)
            .background(GalleryTheme.palette.separator)
    )
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    background: Color? = null,
    size: Dp = 38.dp,
    iconSize: Dp = 18.dp
) {
    SolidIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        tint = tint,
        background = background,
        size = size,
        iconSize = iconSize
    )
}

@Composable
fun LargeTitleBar(
    title: String,
    subtitle: String? = null,
    eyebrow: String? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val palette = GalleryTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.background)
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 5.dp, bottom = 2.dp)
    ) {
        SolidPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = 0.dp,
            contentPadding = PaddingValues(start = 16.dp, end = 10.dp, top = 9.dp, bottom = 9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    if (eyebrow != null) {
                        Text(
                            text = eyebrow,
                            color = palette.secondaryText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.1.sp
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        text = title,
                        color = palette.primaryText,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.7).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            color = palette.secondaryText,
                            fontSize = 12.sp
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
            }
        }
    }
}

@Composable
fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val palette = GalleryTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
    ) {
        SolidPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            elevation = 16.dp,
            contentPadding = PaddingValues(start = 6.dp, end = 10.dp, top = 7.dp, bottom = 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.groupedBackground)
                        .simpleClickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
                        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(
                            color = palette.primaryText,
                            start = Offset(size.width * 0.62f, size.height * 0.18f),
                            end = Offset(size.width * 0.30f, size.height * 0.50f),
                            strokeWidth = stroke.width,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = palette.primaryText,
                            start = Offset(size.width * 0.30f, size.height * 0.50f),
                            end = Offset(size.width * 0.62f, size.height * 0.82f),
                            strokeWidth = stroke.width,
                            cap = StrokeCap.Round
                        )
                    }
                }
                Text(
                    text = title,
                    color = palette.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, trailing: String? = null) {
    val palette = GalleryTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = palette.primaryText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(text = trailing, color = palette.secondaryText, fontSize = 12.sp)
        }
    }
}

@Composable
fun GalleryBottomBar(
    current: GalleryTab,
    onSelect: (GalleryTab) -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            DockPill(current = current, onSelect = onSelect, scale = scale)
        }
    }
}

@Composable
fun DockPill(
    current: GalleryTab,
    onSelect: (GalleryTab) -> Unit,
    scale: Float = 1f,
    modifier: Modifier = Modifier
) {
    val palette = GalleryTheme.palette
    val tabs = GalleryTab.entries
    val tile = 44.dp * scale
    val gap = 7.dp * scale
    val inset = 9.dp * scale
    val dockShape = RoundedCornerShape(24.dp * scale)
    val dockWidth = tile * tabs.size + gap * (tabs.size - 1) + inset * 2
    Row(
        modifier = modifier
            .width(dockWidth)
            .clip(dockShape)
            .background(palette.card)
            .border(1.dp * scale, palette.separator, dockShape)
            .padding(inset),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            DockTile(
                tab = tab,
                selected = tab == current,
                size = tile,
                cornerRadius = 13.dp * scale,
                iconSize = 23.dp * scale,
                onClick = { onSelect(tab) }
            )
        }
    }
}

private fun dockColor(tab: GalleryTab): Color = when (tab) {
    GalleryTab.Library -> Color(0xFF1684F8)
    GalleryTab.Albums -> Color(0xFF8956D8)
    GalleryTab.Favorites -> Color(0xFFE83F69)
    GalleryTab.Search -> Color(0xFF657084)
}

@Composable
private fun DockTile(
    tab: GalleryTab,
    selected: Boolean,
    size: Dp,
    cornerRadius: Dp,
    iconSize: Dp,
    onClick: () -> Unit
) {
    val fill = dockColor(tab)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(if (selected) fill else fill.copy(alpha = 0.78f))
            .simpleClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun MediaThumbnail(
    item: MediaItem,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    cornerRadius: Dp = 8.dp,
    onClick: (MediaItem) -> Unit,
    onLongClick: (MediaItem) -> Unit
) {
    val palette = GalleryTheme.palette
    val context = LocalContext.current
    val shape = remember(cornerRadius) {
        if (cornerRadius > 0.dp) RoundedCornerShape(cornerRadius) else null
    }

    val thumbnailKey = remember(item.id, item.dateMillis, item.sizeBytes) {
        MediaThumbnailStore.getThumbnailKey(item)
    }

    var bitmap by remember(thumbnailKey) {
        mutableStateOf(MediaThumbnailStore.getMemory(thumbnailKey))
    }

    LaunchedEffect(thumbnailKey) {
        if (bitmap == null) {
            val loaded = MediaThumbnailStore.load(context, item, thumbnailKey)
            if (loaded != null) {
                bitmap = loaded
            }
        }
    }

    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
    val click = remember(item, onClick) { { onClick(item) } }
    val longClick = remember(item, onLongClick) { { onLongClick(item) } }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .then(if (shape != null) Modifier.clip(shape) else Modifier)
            .background(palette.background)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = click,
                onLongClick = longClick
            )
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (item.isVideo) {
            val durationText = remember(item.durationMs) { formatDuration(item.durationMs) }
            SolidChip(
                text = durationText,
                icon = GalleryIcons.VideoBadge,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            )
        }

        if (isFavorite && !selectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
                    .border(0.7.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = GalleryIcons.HeartFilled,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(9.dp)
                )
            }
        }

        if (selectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (selected) palette.accent.copy(alpha = 0.18f) else Color.Transparent)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) palette.accent else Color.Black.copy(alpha = 0.28f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) Color.White.copy(alpha = 0.6f)
                        else Color.White.copy(alpha = 0.7f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = GalleryIcons.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

private data class MediaGridEntry(
    val key: String,
    val header: String? = null,
    val headerItems: List<MediaItem>? = null,
    val media: MediaItem? = null
)

@Composable
fun MediaGrid(
    groups: List<DateGroup>,
    favorites: Set<Long>,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    contentPadding: PaddingValues,
    bottomPadding: Dp,
    showHeaders: Boolean = false,
    onSelectGroup: (List<MediaItem>) -> Unit = {},
    onItemClick: (MediaItem) -> Unit,
    onItemLongClick: (MediaItem) -> Unit
) {
    val palette = GalleryTheme.palette
    val columns = LocalGridColumns.current

    val entries = remember(groups, showHeaders) {
        buildList {
            groups.forEachIndexed { groupIndex, group ->
                if (showHeaders) {
                    add(
                        MediaGridEntry(
                            key = "header-${group.label}_$groupIndex",
                            header = group.label,
                            headerItems = group.items
                        )
                    )
                }
                group.items.forEach { media ->
                    add(
                        MediaGridEntry(
                            key = "${if (media.isVideo) "v" else "i"}_${media.id}",
                            media = media
                        )
                    )
                }
            }
            add(MediaGridEntry(key = "grid-bottom-spacer"))
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            count = entries.size,
            key = { index -> entries[index].key },
            span = { index ->
                val entry = entries[index]
                if (entry.header != null || entry.key == "grid-bottom-spacer")
                    GridItemSpan(maxLineSpan)
                else GridItemSpan(1)
            },
            contentType = { index ->
                val entry = entries[index]
                when {
                    entry.header != null -> "date-header"
                    entry.media != null -> "media-thumbnail"
                    else -> "bottom-spacer"
                }
            }
        ) { index ->
            val entry = entries[index]
            when {
                entry.header != null -> {
                    val groupItems = entry.headerItems.orEmpty()
                    val allGroupSelected = selectionMode && groupItems.isNotEmpty() &&
                        groupItems.all { it.id in selectedIds }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 9.dp, end = 9.dp, top = 20.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.header,
                            color = palette.primaryText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (selectionMode) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .semantics {
                                        contentDescription = if (allGroupSelected) "Deselect ${entry.header}"
                                        else "Select all ${entry.header}"
                                    }
                                    .background(
                                        if (allGroupSelected) palette.accent else Color.Transparent
                                    )
                                    .then(
                                        if (allGroupSelected) Modifier
                                        else Modifier.border(2.dp, Color.White, CircleShape)
                                    )
                                    .simpleClickable(
                                        pressedScale = 0.94f,
                                        onClick = { onSelectGroup(groupItems) }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (allGroupSelected) {
                                    Icon(
                                        imageVector = GalleryIcons.Check,
                                        contentDescription = "Deselect ${entry.header}",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                entry.media != null -> {
                    val media = entry.media
                    MediaThumbnail(
                        item = media,
                        isFavorite = media.id in favorites,
                        selectionMode = selectionMode,
                        selected = selectionMode && media.id in selectedIds,
                        onClick = onItemClick,
                        onLongClick = onItemLongClick
                    )
                }
                else -> Spacer(Modifier.height(bottomPadding))
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = GalleryTheme.palette
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        SolidPanel(
            shape = RoundedCornerShape(28.dp),
            elevation = 20.dp,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(palette.accentSoft)
                        .border(
                            0.9.dp,
                            Color.White.copy(alpha = 0.5f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = title,
                    color = palette.primaryText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    color = palette.secondaryText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton(label = actionLabel, onClick = onAction)
                }
            }
        }
    }
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true
) {
    val palette = GalleryTheme.palette
    SolidPanel(
        modifier = modifier.simpleClickable(pressedScale = 0.95f, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        elevation = if (filled) 14.dp else 8.dp,
        tint = if (filled) palette.accent.copy(alpha = 0.92f) else null,
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 11.dp)
    ) {
        Text(
            text = label,
            color = if (filled) Color.White else palette.primaryText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun LoadingState(message: String = "Loading your library…") {
    val palette = GalleryTheme.palette
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = palette.accent,
            strokeWidth = 2.4.dp,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(text = message, color = palette.secondaryText, fontSize = 13.sp)
    }
}

@Composable
fun GallerySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {}
) {
    val palette = GalleryTheme.palette
    SolidPanel(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = 12.dp,
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            placeholder = {
                Text(placeholder, color = palette.tertiaryText, fontSize = 15.sp)
            },
            leadingIcon = {
                Icon(
                    imageVector = GalleryIcons.Search,
                    contentDescription = null,
                    tint = palette.tertiaryText,
                    modifier = Modifier.size(17.dp)
                )
            },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    Icon(
                        imageVector = GalleryIcons.Close,
                        contentDescription = "Clear",
                        tint = palette.tertiaryText,
                        modifier = Modifier
                            .size(15.dp)
                            .simpleClickable(pressedScale = 0.85f, onClick = { onValueChange("") })
                    )
                }
            },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = palette.accent,
                focusedTextColor = palette.primaryText,
                unfocusedTextColor = palette.primaryText
            )
        )
    }
}

data class SheetAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryActionSheet(
    visible: Boolean,
    title: String? = null,
    actions: List<SheetAction>,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val palette = GalleryTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = palette.scrim.copy(alpha = 0.45f),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = null
    ) {
        SolidPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            elevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 8.dp)
                        .align(Alignment.CenterHorizontally)
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.separator)
                )
                if (title != null) {
                    Text(
                        text = title,
                        color = palette.secondaryText,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            start = 24.dp, end = 24.dp, top = 4.dp, bottom = 10.dp
                        )
                    )
                }
                actions.forEach { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .simpleClickable(pressedScale = 0.98f, onClick = {
                                onDismiss()
                                action.onClick()
                            })
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            tint = if (action.destructive) Color(0xFFFF3B30) else palette.primaryText,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = action.label,
                            color = if (action.destructive) Color(0xFFFF3B30) else palette.primaryText,
                            fontSize = 15.5.sp
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.accentSoft)
                        .border(
                            0.8.dp,
                            Color.White.copy(alpha = 0.35f),
                            RoundedCornerShape(18.dp)
                        )
                        .simpleClickable(pressedScale = 0.97f, onClick = onDismiss)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        color = palette.accent,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(
                    Modifier
                        .height(10.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, mono: Boolean = false) {
    val palette = GalleryTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = palette.secondaryText,
            fontSize = 12.5.sp,
            modifier = Modifier.width(104.dp)
        )
        Text(
            text = value,
            color = palette.primaryText,
            fontSize = 12.5.sp,
            modifier = Modifier.weight(1f),
            fontFamily = if (mono) androidx.compose.ui.text.font.FontFamily.Monospace else null
        )
    }
}

@Composable
fun SubtitleLine(items: Int, videos: Int): String {
    val photos = items - videos
    val photoText = if (photos == 1) "1 photo" else "${formatNumber(photos)} photos"
    val videoText = if (videos == 1) "1 video" else "${formatNumber(videos)} videos"
    return if (videos == 0) photoText else "$photoText · $videoText"
}