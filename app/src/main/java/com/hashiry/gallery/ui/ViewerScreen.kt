package com.hashiry.gallery.ui

import androidx.compose.foundation.border
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import android.app.Activity
import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hashiry.gallery.data.FileOps
import com.hashiry.gallery.data.PsdPreviewDecoder
import com.hashiry.gallery.model.MediaItem
import com.hashiry.gallery.ui.theme.GalleryTheme
import com.hashiry.gallery.util.formatDateTime
import com.hashiry.gallery.util.formatDuration
import com.hashiry.gallery.util.formatResolution
import com.hashiry.gallery.util.formatSize
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ViewerRequest(val items: List<MediaItem>, val index: Int)

private data class PsdLoadState(val bitmap: Bitmap? = null, val loading: Boolean = true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    request: ViewerRequest,
    favorites: Set<Long>,
    vm: GalleryViewModel,
    onClose: () -> Unit,
    onMessage: (String) -> Unit
) {
    val palette = GalleryTheme.palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (request.items.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = request.index.coerceIn(0, request.items.lastIndex)
    ) { request.items.size }

    var zoomed by remember { mutableStateOf(false) }
    // A stale zoom flag used to leave the pager scroll-locked after a page change.
    LaunchedEffect(pagerState.currentPage) { zoomed = false }
    var chromeVisible by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var pendingCopy by remember { mutableStateOf<Pair<MediaItem, Boolean>?>(null) }
    var moveUri by remember { mutableStateOf<Uri?>(null) }

    val current = request.items.getOrNull(pagerState.currentPage) ?: request.items.first()
    val isFavorite = current.id in favorites
    val trash = rememberTrashFlow(vm, onMessage) { onClose() }

    BackHandler { onClose() }

    // Enter the viewer in true immersive mode. System bars only return when
    // the user taps to reveal the viewer controls, and are restored on exit.
    val activity = remember(context) { context as? Activity }
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(activity, chromeVisible) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chromeVisible) {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ---- copy / move into a folder the user picks ----------------------------
    val moveConsent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val uri = moveUri
        moveUri = null
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            scope.launch {
                FileOps.deleteNow(context, listOf(uri))
                vm.refresh()
                onMessage("Moved")
            }
        } else {
            onMessage("Copied — the original was kept")
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        val job = pendingCopy
        pendingCopy = null
        if (treeUri == null || job == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val dir = DocumentFile.fromTreeUri(context, treeUri)
            if (dir == null || !dir.canWrite()) {
                onMessage("That folder can't be written to")
                return@launch
            }
            val copied = FileOps.copyInto(context, job.first, dir)
            if (copied == null) {
                onMessage("Couldn't copy that item")
                return@launch
            }
            if (job.second) {
                val deleted = FileOps.deleteNow(context, listOf(job.first.uri))
                if (job.first.uri in deleted) {
                    vm.refresh()
                    onMessage("Moved")
                } else {
                    val sender = FileOps.deleteConsent(context, listOf(job.first.uri))
                    if (sender != null) {
                        moveUri = job.first.uri
                        runCatching {
                            moveConsent.launch(IntentSenderRequest.Builder(sender).build())
                        }.onFailure { onMessage("Copied — the original was kept") }
                    } else {
                        onMessage("Copied — the original was kept")
                    }
                }
            } else {
                onMessage("Copied to the chosen folder")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = request.items[page]
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (item.isVideo) {
                    VideoPage(
                        item = item,
                        active = page == pagerState.currentPage,
                        galleryChromeVisible = chromeVisible,
                        onDoubleTap = { chromeVisible = !chromeVisible }
                    )
                } else {
                    ZoomableImage(
                        item = item,
                        onZoomChanged = { zoomed = it },
                        onTap = { chromeVisible = !chromeVisible }
                    )
                }
            }
        }

        // ---- top chrome: top bar and action circles -----------------
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 13.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(palette.card)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ViewerCircleButton(
                        icon = GalleryIcons.Back,
                        contentDescription = "Close",
                        onClick = onClose,
                        tint = palette.primaryText
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = current.name,
                            color = if (palette.isDark) Color.White else palette.primaryText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (request.items.size > 1)
                                "${pagerState.currentPage + 1} of ${request.items.size}"
                            else when { current.isVideo -> "Video"; current.isPsd -> "PSD document"; else -> "Photo" },
                            color = palette.primaryText,
                            fontSize = 12.sp
                        )
                    }
                    ViewerCircleButton(
                        icon = if (isFavorite) GalleryIcons.HeartFilled else GalleryIcons.Heart,
                        contentDescription = "Favorite",
                        onClick = {
                            vm.toggleFavorite(current.id)
                            onMessage(
                                if (isFavorite) "Removed from Favorites"
                                else "Added to Favorites"
                            )
                        },
                        tint = if (isFavorite) Color(0xFFFF375F) else palette.primaryText
                    )
                    Spacer(Modifier.width(8.dp))
                    ViewerCircleButton(
                        icon = GalleryIcons.More,
                        tint = palette.primaryText,
                        contentDescription = "More",
                        onClick = { showMenu = true }
                    )
                }
            }
        }

        // ---- bottom chrome: bottom action bar ----------------------------
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 17.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(palette.card),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ViewerAction(GalleryIcons.Share, "Share") {
                        context.startActivity(FileOps.shareIntent(listOf(current)))
                    }
                    ViewerAction(
                        icon = if (isFavorite) GalleryIcons.HeartFilled else GalleryIcons.Heart,
                        label = if (isFavorite) "Favorited" else "Favorite",
                        tint = if (isFavorite) Color(0xFFFF375F) else null
                    ) {
                        vm.toggleFavorite(current.id)
                    }
                    ViewerAction(GalleryIcons.Info, "Details") { showInfo = true }
                    ViewerAction(GalleryIcons.Trash, "Delete", destructive = true) {
                        // Keep the viewer alive while the app or Android handles
                        // the delete confirmation/authorization flow. Close only
                        // after the item has successfully moved to Recently Deleted.
                        trash(listOf(current))
                    }
                }
            }
        }
    }

    GalleryActionSheet(
        visible = showMenu,
        title = current.name,
        actions = listOf(
            SheetAction("Share", GalleryIcons.Share) {
                context.startActivity(FileOps.shareIntent(listOf(current)))
            },
            SheetAction("Copy to Folder…", GalleryIcons.Copy) {
                pendingCopy = current to false
                folderPicker.launch(null)
            },
            SheetAction("Move to Folder…", GalleryIcons.Move) {
                pendingCopy = current to true
                folderPicker.launch(null)
            },
            SheetAction("Rename", GalleryIcons.Pencil) { showRename = true },
            SheetAction("Details", GalleryIcons.Info) { showInfo = true },
            SheetAction("Delete", GalleryIcons.Trash, destructive = true) {
                showMenu = false
                // Do not dispose the viewer before the confirmation or system
                // authorization flow has completed.
                trash(listOf(current))
            }
        ),
        onDismiss = { showMenu = false }
    )

    if (showRename) {
        TextInputDialog(
            title = "Rename",
            initial = current.name,
            placeholder = "File name",
            confirmLabel = "Save",
            onDismiss = { showRename = false },
            onConfirm = { name ->
                vm.renameMedia(current, name) { ok ->
                    onMessage(if (ok) "Renamed" else "Android didn't allow renaming this file")
                }
                showRename = false
            }
        )
    }

    if (showInfo) {
        InfoSheet(item = current, onDismiss = { showInfo = false })
    }
}

/** Viewer circle btn used for dark/light viewer bar. */
@Composable
private fun ViewerCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    val palette = GalleryTheme.palette
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(palette.groupedBackground)
            .border(0.7.dp, palette.separator, CircleShape)
            .simpleClickable(pressedScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun ViewerAction(
    icon: ImageVector,
    label: String,
    tint: Color? = null,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val palette = GalleryTheme.palette
    val color = when {
        tint != null -> tint
        destructive -> Color(0xFFFF453A)
        else -> palette.accent
    }
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(5.dp))
        Text(text = label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ZoomableImage(
    item: MediaItem,
    onZoomChanged: (Boolean) -> Unit,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember(item.id) { mutableStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }
    var container by remember(item.id) { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(scale) { onZoomChanged(scale > 1.02f) }

    fun clampOffset(candidate: Offset, currentScale: Float): Offset {
        val maxX = (container.width * (currentScale - 1f)) / 2f
        val maxY = (container.height * (currentScale - 1f)) / 2f
        return Offset(
            x = candidate.x.coerceIn(-maxX.coerceAtLeast(0f), maxX.coerceAtLeast(0f)),
            y = candidate.y.coerceIn(-maxY.coerceAtLeast(0f), maxY.coerceAtLeast(0f))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { container = it }
            .pointerInput(item.id) {
                // At normal scale, leave single-finger drags unconsumed so the
                // HorizontalPager can move between photos and videos. Only take
                // over gestures for pinch zoom or panning an already-zoomed image.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        val isPinching = pressed.size > 1
                        if (isPinching || scale > 1.02f) {
                            // Calculate pan/zoom locally instead of relying on
                            // PointerEvent extensions unavailable in this Compose version.
                            val currentCentroid = Offset(
                                pressed.map { it.position.x }.average().toFloat(),
                                pressed.map { it.position.y }.average().toFloat()
                            )
                            val previousCentroid = Offset(
                                pressed.map { it.previousPosition.x }.average().toFloat(),
                                pressed.map { it.previousPosition.y }.average().toFloat()
                            )
                            val currentDistance = pressed.map { change ->
                                (change.position - currentCentroid).getDistance()
                            }.average().toFloat()
                            val previousDistance = pressed.map { change ->
                                (change.previousPosition - previousCentroid).getDistance()
                            }.average().toFloat()
                            val zoomChange = if (previousDistance > 0.5f && isPinching) {
                                currentDistance / previousDistance
                            } else 1f
                            val newScale = (scale * zoomChange).coerceIn(1f, 6f)
                            val pan = currentCentroid - previousCentroid
                            scale = newScale
                            offset = if (newScale <= 1.001f) Offset.Zero
                            else clampOffset(offset + pan, newScale)
                            event.changes.forEach { if (it.pressed) it.consume() }
                        }
                    } while (true)
                }
            }
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1.02f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.6f
                        }
                    }
                )
            }
    ) {
        var imageLoading by remember(item.uri) { mutableStateOf(true) }
        val psdLoad by androidx.compose.runtime.produceState(
            initialValue = PsdLoadState(loading = item.isPsd), key1 = item.uri, key2 = item.isPsd
        ) {
            if (item.isPsd) {
                val decoded = withContext(Dispatchers.IO) {
                    PsdPreviewDecoder.decode(context, item.uri, 4096)
                }
                value = PsdLoadState(bitmap = decoded, loading = false)
            } else value = PsdLoadState(loading = false)
        }
        val psdBitmap = psdLoad.bitmap
        val viewerImageRequest = remember(item.uri, context, item.isPsd) {
            if (item.isPsd) null else ImageRequest.Builder(context)
                .data(item.uri).crossfade(false).build()
        }
        if (item.isPsd) {
            val imageBitmap = remember(psdBitmap) { psdBitmap?.asImageBitmap() }
            if (imageBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = imageBitmap, contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offset.x, translationY = offset.y
                    )
                )
            } else if (!psdLoad.loading) {
                Text(
                    text = "PSD preview isn't supported for this document",
                    color = GalleryTheme.palette.secondaryText,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            AsyncImage(
                model = viewerImageRequest, contentDescription = item.name,
                contentScale = ContentScale.Fit,
                onLoading = { imageLoading = true }, onSuccess = { imageLoading = false },
                onError = { imageLoading = false },
                modifier = Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale, scaleY = scale,
                    translationX = offset.x, translationY = offset.y
                )
            )
        }
        if (if (item.isPsd) psdLoad.loading else imageLoading) {
            CircularProgressIndicator(
                color = GalleryTheme.palette.tertiaryText,
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.Center).size(24.dp)
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPage(
    item: MediaItem,
    active: Boolean,
    galleryChromeVisible: Boolean,
    onDoubleTap: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Lock the exact orientation the user is currently watching in. Unlike
    // forcing portrait/landscape, SCREEN_ORIENTATION_LOCKED preserves the
    // current rotation until the user taps the control again.
    var orientationLocked by remember(item.id) { mutableStateOf(false) }

    // Restore the activity's previous orientation policy when leaving the video.
    DisposableEffect(activity, active, item.id) {
        val previousOrientation = activity?.requestedOrientation
        onDispose {
            if (active && activity != null && previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
        }
    }
    LaunchedEffect(activity, active, orientationLocked, item.id) {
        if (active && activity != null) {
            activity.requestedOrientation = if (orientationLocked) {
                ActivityInfo.SCREEN_ORIENTATION_LOCKED
            } else {
                ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            }
        }
    }

    var player by remember(item.id) { mutableStateOf<ExoPlayer?>(null) }
    var playing by remember(item.id) { mutableStateOf(true) }
    var positionMs by remember(item.id) { mutableLongStateOf(0L) }
    var durationMs by remember(item.id) { mutableLongStateOf(0L) }
    var dragging by remember(item.id) { mutableStateOf(false) }
    var scrubPosition by remember(item.id) { mutableStateOf(0f) }
    var showVideoSettings by remember(item.id) { mutableStateOf(false) }
    var smartEnhance by remember(item.id) { mutableStateOf(false) }
    var playbackControlsVisible by remember(item.id) { mutableStateOf(false) }

    DisposableEffect(item.id, active) {
        if (active) {
            val exo = ExoPlayer.Builder(context).build()
            // Install the effects pipeline before prepare. HDR-capable sources still
            // use Media3/platform HDR playback; this optional preset gently enhances contrast.
            exo.setVideoEffects(emptyList())
            exo.setMediaItem(Media3Item.fromUri(item.uri))
            exo.prepare()
            exo.playWhenReady = true
            player = exo
        }
        onDispose {
            player?.release()
            player = null
        }
    }

    DisposableEffect(lifecycleOwner, item.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player?.pause()
            if (event == Lifecycle.Event.ON_START && active) player?.playWhenReady = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // A single tap reveals only the playback UI. Let it fade away after a short
    // idle period; it must not reveal the gallery actions or Android system bars.
    LaunchedEffect(playbackControlsVisible, active, item.id) {
        if (playbackControlsVisible && active) {
            kotlinx.coroutines.delay(3_500)
            playbackControlsVisible = false
        }
    }

    LaunchedEffect(player) {
        while (player != null) {
            val p = player
            if (p != null) {
                if (!dragging) positionMs = p.currentPosition.coerceAtLeast(0L)
                durationMs = p.duration.takeIf { it > 0L && it != androidx.media3.common.C.TIME_UNSET } ?: 0L
                playing = p.isPlaying || p.playWhenReady
            }
            kotlinx.coroutines.delay(250)
        }
    }

    fun applyEnhancement(enabled: Boolean) {
        smartEnhance = enabled
        player?.setVideoEffects(
            if (enabled) listOf(Contrast(0.10f), Brightness(0.025f)) else emptyList()
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                }
            },
            update = { view ->
                view.player = player
                view.useController = false
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(item.id, galleryChromeVisible) {
                    detectTapGestures(
                        onTap = { playbackControlsVisible = !playbackControlsVisible },
                        // Two quick taps are reserved for the gallery chrome: top bar,
                        // bottom actions, status bar and navigation gesture bar together.
                        onDoubleTap = {
                            // Double tap is reserved for toggling the full gallery chrome.
                            onDoubleTap()
                        }
                    )
                }
        )

        if (player == null) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.8f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(26.dp)
            )
        }

        if (playbackControlsVisible && player != null) {
            // Custom controls replace Media3's generic web-player-looking chrome.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xD9000000))
                        )
                    )
                    .padding(horizontal = 22.dp, vertical = 18.dp)
            ) {
                Slider(
                    value = (if (dragging) scrubPosition else positionMs.toFloat())
                        .coerceIn(0f, durationMs.coerceAtLeast(1L).toFloat()),
                    onValueChange = {
                        dragging = true
                        scrubPosition = it
                    },
                    onValueChangeFinished = {
                        player?.seekTo(scrubPosition.toLong())
                        positionMs = scrubPosition.toLong()
                        dragging = false
                    },
                    valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatPlayerTime(if (dragging) scrubPosition.toLong() else positionMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("  /  ", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                        Text(
                            text = formatPlayerTime(durationMs),
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 12.sp
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Dedicated orientation-lock control, placed immediately beside HDR.
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (orientationLocked) Color(0xFFB8D7FF).copy(alpha = 0.24f)
                                    else Color.White.copy(alpha = 0.14f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (orientationLocked) Color(0xFFB8D7FF).copy(alpha = 0.75f)
                                    else Color.White.copy(alpha = 0.16f),
                                    shape = CircleShape
                                )
                                .simpleClickable(onClick = { orientationLocked = !orientationLocked }),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = GalleryIcons.OrientationLock,
                                contentDescription = if (orientationLocked) "Unlock orientation" else "Lock current orientation",
                                tint = if (orientationLocked) Color(0xFFB8D7FF) else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .simpleClickable(onClick = { showVideoSettings = true })
                                .padding(horizontal = 13.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (smartEnhance) "HDR ✦" else "HDR",
                                color = if (smartEnhance) Color(0xFFB8D7FF) else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VideoRoundControl(label = "−10", onClick = {
                    player?.let { it.seekTo((it.currentPosition - 10_000L).coerceAtLeast(0L)) }
                })
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.96f))
                        .simpleClickable(onClick = {
                            player?.let { if (it.isPlaying) it.pause() else it.play() }
                        }),
                    contentAlignment = Alignment.Center
                ) {
                    if (playing) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.width(5.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF17171B)))
                            Box(Modifier.width(5.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF17171B)))
                        }
                    } else {
                        Icon(GalleryIcons.Play, contentDescription = "Play", tint = Color(0xFF17171B), modifier = Modifier.size(30.dp))
                    }
                }
                VideoRoundControl(label = "+10", onClick = {
                    player?.let { p -> p.seekTo((p.currentPosition + 10_000L).coerceAtMost(durationMs)) }
                })
            }
        }
    }

    if (showVideoSettings) {
        val palette = GalleryTheme.palette
        AlertDialog(
            onDismissRequest = { showVideoSettings = false },
            containerColor = palette.card,
            title = { Text("Video settings", color = palette.primaryText, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Smart HDR", color = palette.primaryText, fontWeight = FontWeight.Medium)
                    Text(
                        "Keeps native HDR playback when the video and device support it. The optional enhancement adds a subtle contrast and brightness boost.",
                        color = palette.primaryText,
                        fontSize = 13.sp
                    )
                    TextButton(onClick = { applyEnhancement(!smartEnhance) }) {
                        Text(if (smartEnhance) "Turn enhancement off" else "Enable smart enhancement")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVideoSettings = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun VideoRoundControl(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.36f))
            .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
            .simpleClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatPlayerTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(item: MediaItem, onDismiss: () -> Unit) {
    val palette = GalleryTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.card,
        scrimColor = palette.scrim,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = item.name,
                color = palette.primaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))
            Hairline()
            Spacer(Modifier.height(10.dp))
            InfoRow("File name", item.name)
            InfoRow("Type", if (item.isVideo) "Video · ${item.mimeType}" else "Photo · ${item.mimeType}")
            InfoRow("Date", formatDateTime(item.dateMillis))
            InfoRow("Resolution", formatResolution(item.width, item.height))
            InfoRow("Size", formatSize(item.sizeBytes))
            if (item.isVideo) {
                InfoRow("Duration", formatDuration(item.durationMs))
            }
            InfoRow("Folder", item.bucketName)
            Spacer(Modifier.height(10.dp))
            Hairline()
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = GalleryIcons.Shield,
                    contentDescription = null,
                    tint = palette.secondaryText,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Stored on this device only",
                    color = palette.secondaryText,
                    fontSize = 11.sp
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
