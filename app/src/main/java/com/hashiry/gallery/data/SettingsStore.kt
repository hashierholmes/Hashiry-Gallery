package com.hashiry.gallery.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything the user can change about how the app looks.
 * Stored in private SharedPreferences — on the device, nowhere else.
 */
data class AppSettings(
    /** Dock tile scale: 0.75 (compact) … 1.35 (large). */
    val dockScale: Float = 1.1f,
    /** "system" | "light" | "dark". */
    val themeMode: String = "system",
    /** Thumbnails per row: 2 … 5. */
    val gridColumns: Int = 3,
    /** Display name used in the welcome and About screens. */
    val appName: String = "Hashiry",
    val recentlyDeletedEnabled: Boolean = true,
    val deleteConfirmationEnabled: Boolean = true
) {
    val darkOverride: Boolean?
        get() = when (themeMode) {
            "light" -> false
            "dark" -> true
            else -> null
        }
}

class SettingsStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext
        .getSharedPreferences("ios_gallery_settings", Context.MODE_PRIVATE)

    // One-time layout migration: update the default dock size and grid density.
    // Existing explicit preferences are migrated once for the new polished layout.
    init {
        if (prefs.getInt(KEY_LAYOUT_VERSION, 0) < 4) {
            prefs.edit()
                .putInt(KEY_COLUMNS, 3)
                .putFloat(KEY_DOCK, 1.1f)
                .putInt(KEY_LAYOUT_VERSION, 4)
                .apply()
        }
    }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    init {
        // Keep the launcher alias in sync with the saved preference on app start.
        applyLauncherLabel(_state.value.appName)
    }

    private fun read(): AppSettings = AppSettings(
        dockScale = prefs.getFloat(KEY_DOCK, 1.1f).coerceIn(MIN_DOCK, MAX_DOCK),
        themeMode = prefs.getString(KEY_THEME, "system") ?: "system",
        gridColumns = prefs.getInt(KEY_COLUMNS, 3).coerceIn(MIN_COLUMNS, MAX_COLUMNS),
        appName = (prefs.getString(KEY_APP_NAME, "Hashiry") ?: "Hashiry").let { if (it == "Gallery") "Gallery" else "Hashiry" },
        recentlyDeletedEnabled = prefs.getBoolean(KEY_RECENTLY_DELETED, true),
        deleteConfirmationEnabled = prefs.getBoolean(KEY_DELETE_CONFIRMATION, true)
    )

    fun setDockScale(value: Float) = update { it.copy(dockScale = value.coerceIn(MIN_DOCK, MAX_DOCK)) }

    fun setThemeMode(value: String) = update { it.copy(themeMode = value) }

    fun setGridColumns(value: Int) =
        update { it.copy(gridColumns = value.coerceIn(MIN_COLUMNS, MAX_COLUMNS)) }

    fun setRecentlyDeletedEnabled(value: Boolean) = update { it.copy(recentlyDeletedEnabled = value) }

    fun setDeleteConfirmationEnabled(value: Boolean) = update { it.copy(deleteConfirmationEnabled = value) }

    fun setAppName(value: String) {
        val launcherName = if (value == "Gallery") "Gallery" else "Hashiry"
        update { it.copy(appName = launcherName) }
        applyLauncherLabel(launcherName)
    }

    private fun applyLauncherLabel(name: String) {
        val hashiryAlias = ComponentName(appContext, "${appContext.packageName}.LauncherHashiry")
        val galleryAlias = ComponentName(appContext, "${appContext.packageName}.LauncherGallery")
        val hashiryState = if (name == "Gallery") PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        val galleryState = if (name == "Gallery") PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        runCatching {
            appContext.packageManager.setComponentEnabledSetting(
                hashiryAlias, hashiryState, PackageManager.DONT_KILL_APP
            )
            appContext.packageManager.setComponentEnabledSetting(
                galleryAlias, galleryState, PackageManager.DONT_KILL_APP
            )
        }
    }

    fun reset() {
        prefs.edit().clear().apply()
        _state.value = AppSettings()
        applyLauncherLabel("Hashiry")
    }

    private fun update(block: (AppSettings) -> AppSettings) {
        val next = block(_state.value)
        _state.value = next
        prefs.edit()
            .putFloat(KEY_DOCK, next.dockScale)
            .putString(KEY_THEME, next.themeMode)
            .putInt(KEY_COLUMNS, next.gridColumns)
            .putString(KEY_APP_NAME, next.appName)
            .putBoolean(KEY_RECENTLY_DELETED, next.recentlyDeletedEnabled)
            .putBoolean(KEY_DELETE_CONFIRMATION, next.deleteConfirmationEnabled)
            .apply()
    }

    companion object {
        const val MIN_DOCK = 0.75f
        const val MAX_DOCK = 1.35f
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 5
        private const val KEY_COLUMNS = "gridColumns"
        private const val KEY_APP_NAME = "appName"
        private const val KEY_LAYOUT_VERSION = "layoutVersion"
        private const val KEY_DOCK = "dockScale"
        private const val KEY_THEME = "themeMode"
        private const val KEY_RECENTLY_DELETED = "recentlyDeletedEnabled"
        private const val KEY_DELETE_CONFIRMATION = "deleteConfirmationEnabled"
    }
}
