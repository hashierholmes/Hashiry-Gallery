package com.hashiry.gallery.ui

import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.hashiry.gallery.data.AppSettings
import com.hashiry.gallery.ui.theme.GalleryTheme

@Composable
fun SettingsScreen(
    appName: String,
    settings: AppSettings,
    onAppNameChange: (String) -> Unit,
    onRecentlyDeletedChange: (Boolean) -> Unit,
    onDeleteConfirmationChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onCustomizeAppearance: () -> Unit
) {
    val palette = GalleryTheme.palette
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var allFilesAccessAllowed by remember { mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)) }
    var mediaManagementAllowed by remember {
        mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && runCatching { MediaStore.canManageMedia(context) }.getOrDefault(false))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allFilesAccessAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
                mediaManagementAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    runCatching { MediaStore.canManageMedia(context) }.getOrDefault(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                })
            }.onFailure {
                runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        }
    }

    fun openMediaManagementSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                    data = Uri.parse("package:${context.packageName}")
                })
            }.onFailure {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
            }
        }
    }
    val version = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Unknown"
    }

    fun openLink(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Column(Modifier.fillMaxSize().background(palette.background)) {
        DetailTopBar(title = "Settings", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp)
        ) {
            Column {
                SectionLabel("PREFERENCES")
                SettingsRow(
                    icon = GalleryIcons.Appearance,
                    title = "Customize Appearance",
                    subtitle = "Theme, grid layout and dock size",
                    onClick = onCustomizeAppearance
                )
                Spacer(Modifier.height(10.dp))
                SolidPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    elevation = 0.dp,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column {
                        Text("App name", color = palette.primaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Choose the name shown on the welcome and About screens.",
                            color = palette.secondaryText,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Hashiry", "Gallery").forEach { name ->
                                val selected = appName == name
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (selected) palette.accent.copy(alpha = 0.16f) else palette.primaryText.copy(alpha = 0.05f))
                                        .border(1.dp, if (selected) palette.accent.copy(alpha = 0.75f) else palette.separator, RoundedCornerShape(14.dp))
                                        .simpleClickable(onClick = { onAppNameChange(name) })
                                        .padding(vertical = 11.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(name, color = if (selected) palette.accent else palette.primaryText, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            Column {
                SectionLabel("LIBRARY")
                SolidPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    elevation = 0.dp,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Recently Deleted", color = palette.primaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("Keep deleted media recoverable for up to 30 days. Turning this off permanently removes existing recovery copies.", color = palette.secondaryText, fontSize = 12.sp, lineHeight = 17.sp)
                            }
                            Switch(checked = settings.recentlyDeletedEnabled, onCheckedChange = onRecentlyDeletedChange)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Delete confirmation", color = palette.primaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("Show Hashiry's confirmation before deleting selected media.", color = palette.secondaryText, fontSize = 12.sp, lineHeight = 17.sp)
                            }
                            Switch(checked = settings.deleteConfirmationEnabled, onCheckedChange = onDeleteConfirmationChange)
                        }
                    }
                }
            }

            Column {
                SectionLabel("FILE MANAGEMENT")
                SolidPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    elevation = 0.dp,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Top) {
                            SettingsIcon(GalleryIcons.Folder)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text("All files access", color = palette.primaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "Optional advanced access that may let Hashiry manage supported files, including PSD files that normal media access cannot delete.",
                                    color = palette.secondaryText, fontSize = 13.sp, lineHeight = 19.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && allFilesAccessAllowed) "Enabled" else "Currently disabled",
                                    color = palette.tertiaryText, fontSize = 11.sp
                                )
                                Text("Android controls this permission. It is optional and can be revoked at any time.", color = palette.tertiaryText, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                            Switch(
                                checked = allFilesAccessAllowed,
                                onCheckedChange = { openAllFilesAccessSettings() },
                                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                            )
                        }
                    }
                }
            }

            Column {
                SectionLabel("MEDIA MANAGEMENT")
                SolidPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    elevation = 0.dp,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        SettingsIcon(GalleryIcons.Camera)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Manage media access" else "System-managed media access",
                                color = palette.primaryText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                    "${if (mediaManagementAllowed) "Access is enabled." else "Access is currently off."} Allowing this optional Android special access can prevent repeated system confirmation prompts for supported media changes."
                                else
                                    "This optional Android special access is available on Android 12 and newer. Android may ask you to confirm media deletions on this version.",
                                color = palette.secondaryText,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Spacer(Modifier.height(9.dp))
                                Text(
                                    if (mediaManagementAllowed) "Manage access in system settings ›" else "Open Media management settings ›",
                                    color = palette.accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.fillMaxWidth().simpleClickable(onClick = ::openMediaManagementSettings).padding(vertical = 4.dp)
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "If the switch isn't shown: Settings → Apps → Special app access → Media management apps → Hashiry Gallery → Allow managing media.",
                                    color = palette.tertiaryText,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            Column {
                SectionLabel("PRIVACY")
                SolidPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    elevation = 0.dp,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        SettingsIcon(GalleryIcons.Privacy)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Your library stays yours", color = palette.primaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "Hashiry is designed to work locally. Your photos and videos stay on this device; the app has no accounts, ads, trackers or cloud sync.",
                                color = palette.secondaryText,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                            Spacer(Modifier.height(9.dp))
                            Text("No internet permission required", color = Color(0xFF35C98A), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Column {
                SectionLabel("ABOUT HASHIRY")
                SolidPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    elevation = 0.dp,
                    contentPadding = PaddingValues(15.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        SettingsIcon(GalleryIcons.Info)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Hashiry Gallery", color = palette.primaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Version $version", color = palette.secondaryText, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "A simple, local-first home for your photos and videos.",
                                color = palette.secondaryText,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "View project on GitHub  ›",
                                color = palette.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth().simpleClickable(onClick = { openLink("https://github.com/hashierholmes/Hashiry-Gallery") }).padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Column {
                SectionLabel("SPECIAL THANKS")
                SolidPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    elevation = 0.dp,
                    contentPadding = PaddingValues(15.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        SettingsIcon(GalleryIcons.SpecialThanks)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("iOS Gallery", color = palette.primaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Special thanks to the original iOS Gallery project for the source code and inspiration that helped make Hashiry possible.",
                                color = palette.secondaryText,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "View original GitHub repository  ›",
                                color = palette.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth().simpleClickable(onClick = { openLink("https://github.com/technicalnihilist-lgtm/ios-gallery") }).padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            Text(
                "Made with care for your everyday memories.",
                color = palette.tertiaryText,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = GalleryTheme.palette.secondaryText,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.0.sp,
        modifier = Modifier.padding(start = 9.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val palette = GalleryTheme.palette
    Box(
        Modifier.size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.primaryText.copy(alpha = 0.07f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = palette.primaryText, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val palette = GalleryTheme.palette
    SolidPanel(
        modifier = Modifier.fillMaxWidth().simpleClickable(pressedScale = 0.985f, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = 0.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = if (compact) 10.dp else 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(icon)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, color = palette.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = palette.secondaryText, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(GalleryIcons.ChevronRight, contentDescription = null, tint = palette.tertiaryText, modifier = Modifier.padding(start = 8.dp).size(17.dp))
        }
    }
}
