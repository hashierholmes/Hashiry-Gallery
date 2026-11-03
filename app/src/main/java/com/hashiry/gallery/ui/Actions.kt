package com.hashiry.gallery.ui

import android.app.Activity
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import com.hashiry.gallery.model.MediaItem
import com.hashiry.gallery.model.TrashEntry
import kotlinx.coroutines.launch

/**
 * Shared "move to Recently Deleted" flow.
 *
 * The bytes are copied into the app's private trash first, then Android is asked
 * to remove the original. When the system shows its own confirmation dialog and
 * the user declines, the copies are rolled back so nothing is left behind.
 */
@Composable
fun rememberTrashFlow(
    vm: GalleryViewModel,
    onMessage: (String) -> Unit,
    onCompleted: (() -> Unit)? = null
): (List<MediaItem>) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<TrashEntry>>(emptyList()) }
    var alreadyMovedCount by remember { mutableStateOf(0) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var confirmationTargets by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var showAccessGuide by remember { mutableStateOf(false) }
    var queuedTargets by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.commitTrash(pending)
            val total = pending.size + alreadyMovedCount
            val keepTrash = vm.settings.value.recentlyDeletedEnabled
            onMessage(if (keepTrash) "Moved $total ${if (total == 1) "item" else "items"} to Recently Deleted" else "Deleted $total ${if (total == 1) "item" else "items"}")
            onCompleted?.invoke()
        } else {
            vm.discardTrash(pending)
            onMessage(if (alreadyMovedCount > 0) "Moved $alreadyMovedCount items · remaining items were not deleted" else "Nothing was deleted")
        }
        alreadyMovedCount = 0
        pending = emptyList()
        vm.refresh()
    }

    fun hasMediaManagementAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { MediaStore.canManageMedia(context) }.getOrDefault(false)

    fun performTrash(targets: List<MediaItem>) {
        if (targets.isEmpty()) return
        scope.launch {
            val outcome = vm.moveToTrash(targets)
            pending = outcome.entries
            alreadyMovedCount = outcome.alreadyMovedCount
            val consent = outcome.consent
            if (consent != null) {
                runCatching {
                    launcher.launch(IntentSenderRequest.Builder(consent).build())
                }.onFailure {
                    vm.discardTrash(outcome.entries)
                    onMessage(if (outcome.alreadyMovedCount > 0) "Moved ${outcome.alreadyMovedCount} items · remaining items were not deleted" else outcome.failureMessage ?: "Android couldn't start the deletion confirmation. Nothing was deleted.")
                    alreadyMovedCount = 0
                    pending = emptyList()
                    vm.refresh()
                }
            } else if (outcome.entries.isEmpty() && outcome.alreadyMovedCount == 0) {
                onMessage(outcome.failureMessage ?: "Android couldn't delete the selected items. Check the file access permissions and try again.")
            } else {
                onMessage(if (outcome.entries.isEmpty()) "Deleted ${outcome.alreadyMovedCount} ${if (outcome.alreadyMovedCount == 1) "item" else "items"}" else "Moved ${outcome.entries.size} ${if (outcome.entries.size == 1) "item" else "items"} to Recently Deleted")
                vm.refresh()
                onCompleted?.invoke()
            }
        }
    }

    if (showAccessGuide) {
        AlertDialog(
            onDismissRequest = {
                showAccessGuide = false
                queuedTargets = emptyList()
            },
            title = { Text("Avoid repeated delete confirmations", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text("Android can ask you to confirm every deletion. You can optionally allow Hashiry Gallery to manage media so supported delete actions can happen without that extra system prompt.")
                    Spacer(Modifier.height(12.dp))
                    Text("How to enable it", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        """1. Tap Open settings below.
2. Find Hashiry Gallery in Media management apps.
3. Turn on Allow managing media.

This is optional. If you skip it, Android may still show its normal confirmation dialog.""",
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    }.onFailure {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"))
                            )
                        }
                    }
                }) { Text("Open settings") }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = {
                        val targets = queuedTargets
                        queuedTargets = emptyList()
                        showAccessGuide = false
                        performTrash(targets)
                    }) { Text("Continue anyway") }
                    TextButton(onClick = {
                        queuedTargets = emptyList()
                        showAccessGuide = false
                    }) { Text("Cancel") }
                }
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false; confirmationTargets = emptyList() },
            title = { Text(if (confirmationTargets.size == 1) "Delete this item?" else "Delete ${confirmationTargets.size} items?", fontWeight = FontWeight.SemiBold) },
            text = { Text("This will delete the selected media. Continue only if you're sure.") },
            confirmButton = {
                TextButton(onClick = {
                    val targets = confirmationTargets
                    confirmationTargets = emptyList()
                    showDeleteConfirmation = false
                    performTrash(targets)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false; confirmationTargets = emptyList() }) { Text("Cancel") } }
        )
    }

    val settings by vm.settings.collectAsState()
    return { targets: List<MediaItem> ->
        if (targets.isNotEmpty()) {
            val isAndroid11OrNewer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            val hasManagementAccess = isAndroid11OrNewer && hasMediaManagementAccess()

            if (isAndroid11OrNewer && !hasManagementAccess) {
                // Explain the optional Media Management access first. If the
                // user skips it, continue to Android's required delete consent.
                queuedTargets = targets
                showAccessGuide = true
            } else if (settings.deleteConfirmationEnabled) {
                // Media Management enabled, or Android 10 and below:
                // retain Hashiry's normal delete confirmation.
                confirmationTargets = targets
                showDeleteConfirmation = true
            } else {
                performTrash(targets)
            }
        }
    }
}
