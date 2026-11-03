package com.hashiry.gallery

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hashiry.gallery.ui.AlbumDetailScreen
import com.hashiry.gallery.ui.AlbumsScreen
import com.hashiry.gallery.ui.CustomizeScreen
import com.hashiry.gallery.ui.EmptyState
import com.hashiry.gallery.ui.FavoritesScreen
import com.hashiry.gallery.ui.GalleryBottomBar
import com.hashiry.gallery.ui.GalleryIcons
import com.hashiry.gallery.ui.GalleryTab
import com.hashiry.gallery.ui.GalleryViewModel
import com.hashiry.gallery.ui.SolidPanel
import com.hashiry.gallery.ui.LibraryScreen
import com.hashiry.gallery.ui.PrimaryButton
import com.hashiry.gallery.ui.SearchScreen
import com.hashiry.gallery.ui.SettingsScreen
import com.hashiry.gallery.ui.TrashScreen
import com.hashiry.gallery.ui.ViewerRequest
import com.hashiry.gallery.model.MediaItem
import com.hashiry.gallery.ui.ViewerScreen
import com.hashiry.gallery.ui.simpleClickable
import com.hashiry.gallery.ui.theme.GalleryTheme
import com.hashiry.gallery.ui.theme.HashiryGalleryTheme
import com.hashiry.gallery.util.hasMediaPermission
import com.hashiry.gallery.util.openAppSettings
import com.hashiry.gallery.util.requiredMediaPermissions
import com.hashiry.gallery.util.shouldExplainAgain
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // iOS-style zoom: the window grows out of the icon and shrinks back into it.
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.app_open_enter,
                R.anim.app_open_exit
            )
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.app_close_enter,
                R.anim.app_close_exit
            )
        }
        setContent {
            val vm: GalleryViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            HashiryGalleryTheme(
                darkTheme = settings.darkOverride ?: isSystemInDarkTheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = GalleryTheme.palette.background
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.hashiry.gallery.ui.LocalGridColumns provides settings.gridColumns
                    ) {
                        GalleryRoot(vm = vm)
                    }
                }
            }
        }
    }
}

private sealed interface Screen {
    data object Tabs : Screen
    data object Trash : Screen
    data object Customize : Screen
    data object Settings : Screen
    data class DeviceAlbumDetail(val bucketId: Long) : Screen
    data class ManagedAlbumDetail(val albumId: String) : Screen
}

/** Height reserved for the floating tab bar (background + pill + system inset). */
private val BottomBarHeight = 108.dp

@Composable
fun GalleryRoot(vm: GalleryViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as? Activity
    val palette = GalleryTheme.palette

    val items by vm.items.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val meta by vm.meta.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    var granted by remember { mutableStateOf(hasMediaPermission(context)) }
    var asked by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(GalleryTab.Library) }
    var screen by remember { mutableStateOf<Screen>(Screen.Tabs) }
    var customizeReturnToSettings by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }

    // Open files passed by Android's Sharesheet / "Open with" chooser directly in the viewer.
    LaunchedEffect(activity?.intent?.action, activity?.intent?.data) {
        val incoming = activity?.intent
        val uri = incoming?.data
        if (uri != null && incoming.action in listOf(Intent.ACTION_VIEW, Intent.ACTION_EDIT)) {
            runCatching {
                val resolver = context.contentResolver
                var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "External media"
                var sizeBytes = 0L
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
                val mime = resolver.getType(uri) ?: when {
                    displayName.endsWith(".psd", true) -> "image/vnd.adobe.photoshop"
                    displayName.endsWith(".mp4", true) || displayName.endsWith(".mkv", true) || displayName.endsWith(".webm", true) -> "video/*"
                    displayName.endsWith(".png", true) -> "image/png"
                    displayName.endsWith(".webp", true) -> "image/webp"
                    displayName.endsWith(".jpg", true) || displayName.endsWith(".jpeg", true) -> "image/jpeg"
                    else -> "application/octet-stream"
                }
                val isVideo = mime.startsWith("video/") || displayName.substringAfterLast('.', "").lowercase() in setOf("mp4", "mkv", "webm", "mov", "3gp", "avi", "m4v")
                val externalItem = MediaItem(
                    id = -uri.toString().hashCode().toLong().let { if (it == 0L) 1L else kotlin.math.abs(it) },
                    uri = uri,
                    name = displayName,
                    mimeType = mime,
                    isVideo = isVideo,
                    dateMillis = System.currentTimeMillis(),
                    sizeBytes = sizeBytes,
                    width = 0,
                    height = 0,
                    bucketId = -1L,
                    bucketName = "Opened from another app",
                    durationMs = 0L
                )
                viewer = ViewerRequest(listOf(externalItem), 0)
            }.onFailure {
                android.widget.Toast.makeText(context, "Hashiry couldn't open this file", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    var librarySelection by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    // The content only carries a graphics layer while it is actually animating: a
    // permanent layer costs a full-screen buffer and breaks SurfaceView (video).
    var animating by remember { mutableStateOf(false) }

    // The gallery renders immediately; only the exit transition uses a temporary layer.
    val contentScale = remember { Animatable(1f) }
    val contentAlpha = remember { Animatable(1f) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val message: (String) -> Unit = { text ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(text)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        asked = true
        val now = hasMediaPermission(context)
        granted = now
        blocked = !now && result.values.isNotEmpty() && result.values.none { it } &&
            !shouldExplainAgain(activity)
        if (now) vm.refresh()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, granted) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = hasMediaPermission(context)
                if (now != granted) {
                    granted = now
                    if (now) vm.refresh()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = viewer == null && screen != Screen.Tabs) {
        screen = if (screen == Screen.Customize && customizeReturnToSettings) {
            Screen.Settings
        } else {
            Screen.Tabs
        }
    }

    BackHandler(enabled = viewer == null && screen == Screen.Tabs && !closing) {
        closing = true
    }

    // Closing: shrink back towards the icon, then hand over to the system transition.
    LaunchedEffect(closing) {
        if (closing) {
            animating = true
            coroutineScope {
                launch {
                    contentScale.animateTo(
                        targetValue = 0.90f,
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    contentAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 200)
                    )
                }
            }
            activity?.finish()
        }
    }

    val deviceAlbums by vm.deviceAlbums.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (animating) {
                        Modifier.graphicsLayer {
                            scaleX = contentScale.value
                            scaleY = contentScale.value
                            alpha = contentAlpha.value
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
        if (!granted) {
            PermissionGate(
                appName = "Hashiry Gallery",
                asked = asked,
                blocked = blocked,
                onRequest = { permissionLauncher.launch(requiredMediaPermissions()) },
                onOpenSettings = { openAppSettings(context) }
            )
        } else {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    if (targetState !is Screen.Tabs) {
                        // push: the new screen settles forward, the old one drifts back
                        (fadeIn(tween(240)) + scaleIn(
                            initialScale = 0.965f,
                            animationSpec = spring(
                                dampingRatio = 0.9f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + slideInHorizontally { it / 16 }) togetherWith
                            (fadeOut(tween(150)) + scaleOut(
                                targetScale = 1.015f,
                                animationSpec = tween(200)
                            ))
                    } else {
                        // pop: mirrored, so going back feels like the same movement reversed
                        (fadeIn(tween(220)) + scaleIn(
                            initialScale = 1.02f,
                            animationSpec = spring(
                                dampingRatio = 0.9f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )) togetherWith
                            (fadeOut(tween(180)) + scaleOut(
                                targetScale = 0.97f,
                                animationSpec = tween(220)
                            ))
                    }
                },
                label = "screen"
            ) { current ->
                when (current) {
                    Screen.Tabs -> AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            // The content travels with the rounded tab container.
                            val direction =
                                if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (slideInHorizontally(
                                animationSpec = spring(
                                    dampingRatio = 0.92f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) { width -> direction * width / 7 } +
                                fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                                scaleIn(
                                    initialScale = 0.985f,
                                    animationSpec = tween(
                                        durationMillis = 280,
                                        easing = FastOutSlowInEasing
                                    )
                                )) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = tween(
                                        durationMillis = 240,
                                        easing = FastOutSlowInEasing
                                    )
                                ) { width -> -direction * width / 11 } +
                                    fadeOut(tween(180)) +
                                    scaleOut(targetScale = 0.995f, animationSpec = tween(200)))
                        },
                        label = "tab"
                    ) { currentTab ->
                        when (currentTab) {
                            GalleryTab.Library -> LibraryScreen(
                                items = items,
                                favorites = meta.favorites,
                                loading = loading,
                                albums = meta.albums,
                                bottomBarHeight = BottomBarHeight,
                                vm = vm,
                                onOpenViewer = { list, index -> viewer = ViewerRequest(list, index) },
                                onSelectionActive = { librarySelection = it },
                                onOpenCustomize = {
                                    customizeReturnToSettings = false
                                    screen = Screen.Customize
                                },
                                onOpenSettings = { screen = Screen.Settings },
                                onOpenTrash = { screen = Screen.Trash },
                                recentlyDeletedEnabled = settings.recentlyDeletedEnabled,
                                onMessage = message
                            )

                            GalleryTab.Albums -> AlbumsScreen(
                                items = items,
                                favorites = meta.favorites,
                                deviceAlbums = deviceAlbums,
                                managedAlbums = meta.albums,
                                trashCount = meta.trash.size,
                                recentlyDeletedEnabled = settings.recentlyDeletedEnabled,
                                bottomBarHeight = BottomBarHeight,
                                vm = vm,
                                onOpenDeviceAlbum = { album ->
                                    screen = Screen.DeviceAlbumDetail(album.bucketId)
                                },
                                onOpenManagedAlbum = { album ->
                                    screen = Screen.ManagedAlbumDetail(album.id)
                                },
                                onOpenTrash = { screen = Screen.Trash },
                                onMessage = message
                            )

                            GalleryTab.Favorites -> FavoritesScreen(
                                items = items,
                                favorites = meta.favorites,
                                bottomBarHeight = BottomBarHeight,
                                onOpenViewer = { list, index -> viewer = ViewerRequest(list, index) }
                            )

                            GalleryTab.Search -> SearchScreen(
                                items = items,
                                favorites = meta.favorites,
                                deviceAlbums = deviceAlbums,
                                managedAlbums = meta.albums,
                                bottomBarHeight = BottomBarHeight,
                                onOpenViewer = { list, index -> viewer = ViewerRequest(list, index) },
                                onOpenDeviceAlbum = { album ->
                                    screen = Screen.DeviceAlbumDetail(album.bucketId)
                                },
                                onOpenManagedAlbum = { album ->
                                    screen = Screen.ManagedAlbumDetail(album.id)
                                }
                            )
                        }
                    }

                    Screen.Customize -> CustomizeScreen(
                        settings = settings,
                        onDockScale = { vm.setDockScale(it) },
                        onThemeMode = { vm.setThemeMode(it) },
                        onGridColumns = { vm.setGridColumns(it) },
                        onReset = { vm.resetSettings() },
                        onBack = {
                            screen = if (customizeReturnToSettings) Screen.Settings else Screen.Tabs
                        }
                    )

                    Screen.Settings -> SettingsScreen(
                        appName = settings.appName,
                        settings = settings,
                        onAppNameChange = { vm.setAppName(it) },
                        onRecentlyDeletedChange = { vm.setRecentlyDeletedEnabled(it) },
                        onDeleteConfirmationChange = { vm.setDeleteConfirmationEnabled(it) },
                        onBack = { screen = Screen.Tabs },
                        onCustomizeAppearance = {
                            customizeReturnToSettings = true
                            screen = Screen.Customize
                        }
                    )

                    Screen.Trash -> if (settings.recentlyDeletedEnabled) TrashScreen(
                        entries = meta.trash,
                        trashDir = File(context.filesDir, "trash"),
                        vm = vm,
                        onBack = { screen = Screen.Tabs },
                        onMessage = message
                    ) else { screen = Screen.Tabs }

                    is Screen.DeviceAlbumDetail -> {
                        val album = deviceAlbums.firstOrNull { it.bucketId == current.bucketId }
                        if (album == null) {
                            MissingAlbum(onBack = { screen = Screen.Tabs })
                        } else {
                            AlbumDetailScreen(
                                title = album.name,
                                items = album.items,
                                allItems = items,
                                favorites = meta.favorites,
                                managedAlbum = null,
                                bottomBarHeight = BottomBarHeight,
                                vm = vm,
                                onBack = { screen = Screen.Tabs },
                                onOpenViewer = { list, index -> viewer = ViewerRequest(list, index) },
                                onMessage = message
                            )
                        }
                    }

                    is Screen.ManagedAlbumDetail -> {
                        val album = meta.albums.firstOrNull { it.id == current.albumId }
                        if (album == null) {
                            MissingAlbum(onBack = { screen = Screen.Tabs })
                        } else {
                            val albumItems = remember(album.itemIds, items) {
                                val byId = items.associateBy { it.id }
                                album.itemIds.mapNotNull { byId[it] }
                            }
                            AlbumDetailScreen(
                                title = album.name,
                                items = albumItems,
                                allItems = items,
                                favorites = meta.favorites,
                                managedAlbum = album,
                                bottomBarHeight = BottomBarHeight,
                                vm = vm,
                                onBack = { screen = Screen.Tabs },
                                onOpenViewer = { list, index -> viewer = ViewerRequest(list, index) },
                                onMessage = message
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = screen == Screen.Tabs && !librarySelection && viewer == null,
                enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { it / 4 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(170)) { it / 4 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                GalleryBottomBar(
                    current = tab,
                    onSelect = { tab = it },
                    scale = settings.dockScale
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 108.dp, start = 12.dp, end = 12.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (palette.isDark) Color(0xFF2C2C33) else Color(0xFF1B1D24),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        // ---- full-screen viewer, with a zoom transition -------------
        val request = viewer
        var lastRequest by remember { mutableStateOf<ViewerRequest?>(null) }
        // SideEffect (not an assignment in the composition body) so the first frame
        // already composes the viewer instead of flashing an empty overlay.
        SideEffect { if (request != null) lastRequest = request }
        AnimatedVisibility(
            visible = request != null,
            enter = fadeIn(tween(180)) + scaleIn(
                initialScale = 0.94f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
            ),
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.96f, animationSpec = tween(160))
        ) {
            (request ?: lastRequest)?.let { safe ->
                ViewerScreen(
                    request = safe,
                    favorites = meta.favorites,
                    vm = vm,
                    onClose = { viewer = null },
                    onMessage = message
                )
            }
        }
    }
}
}

@Composable
private fun MissingAlbum(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.statusBarsPadding().fillMaxWidth().padding(12.dp)) {
            com.hashiry.gallery.ui.SolidIconButton(
                icon = GalleryIcons.Back,
                contentDescription = "Back",
                onClick = onBack,
                tint = GalleryTheme.palette.primaryText,
                size = 38.dp
            )
        }
        EmptyState(
            icon = GalleryIcons.Albums,
            title = "Album unavailable",
            message = "This album no longer exists."
        )
    }
}

// ---------------------------------------------------------------- permissions

@Composable
private fun PermissionGate(
    appName: String,
    asked: Boolean,
    blocked: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val palette = GalleryTheme.palette
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        SolidPanel(
            shape = RoundedCornerShape(32.dp),
            elevation = 26.dp,
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 30.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = com.hashiry.gallery.R.drawable.ic_launcher_transparent),
                    contentDescription = "Hashiry app icon",
                    modifier = Modifier.size(76.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = if (asked) "Photo access is off" else "Welcome to $appName",
                    color = palette.primaryText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (asked)
                        "$appName needs access to the photos and videos on this device to show them. You can grant access again below, or allow it in system settings."
                    else
                        "A simple, private gallery for the photos and videos already on your device. Grant access and everything appears in one place.",
                    color = palette.secondaryText,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "No account · No internet · No tracking",
                    color = palette.tertiaryText,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(26.dp))
                PrimaryButton(
                    label = if (asked) "Allow Access" else "Continue",
                    onClick = onRequest
                )
                if (blocked) {
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        label = "Open Settings",
                        onClick = onOpenSettings,
                        filled = false
                    )
                }
                Spacer(Modifier.height(26.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = GalleryIcons.Shield,
                        contentDescription = null,
                        tint = palette.tertiaryText,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "This app has no internet permission at all",
                        color = palette.tertiaryText,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
