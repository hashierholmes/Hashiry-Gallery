package com.hashiry.gallery.util

import com.hashiry.gallery.model.MediaItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class DateGroup(val label: String, val items: List<MediaItem>)

private val dayFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

/**
 * Groups sorted items into Today / Yesterday / "12 March 2025" sections.
 * Reuses a single Calendar instance and checks day boundaries to avoid allocating
 * thousands of Calendar objects during library scroll and grouping.
 */
fun groupByDate(items: List<MediaItem>): List<DateGroup> {
    if (items.isEmpty()) return emptyList()

    val cal = Calendar.getInstance()
    val now = System.currentTimeMillis()
    cal.timeInMillis = now
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val today = cal.timeInMillis
    val yesterday = today - TimeUnit.DAYS.toMillis(1)

    val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
    val groups = ArrayList<DateGroup>()
    var currentLabel: String? = null
    var currentGroupItems = ArrayList<MediaItem>()
    var currentDayStart = -1L
    var currentDayEnd = -1L

    for (item in items) {
        val millis = item.dateMillis
        if (millis in currentDayStart until currentDayEnd && currentLabel != null) {
            currentGroupItems.add(item)
        } else {
            if (currentLabel != null && currentGroupItems.isNotEmpty()) {
                groups.add(DateGroup(currentLabel, currentGroupItems))
                currentGroupItems = ArrayList()
            }
            cal.timeInMillis = millis
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            currentDayStart = cal.timeInMillis
            currentDayEnd = currentDayStart + TimeUnit.DAYS.toMillis(1)

            currentLabel = when (currentDayStart) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> dateFormat.format(Date(millis))
            }
            currentGroupItems.add(item)
        }
    }
    if (currentLabel != null && currentGroupItems.isNotEmpty()) {
        groups.add(DateGroup(currentLabel, currentGroupItems))
    }
    return groups
}

/** Groups items by an arbitrary SimpleDateFormat pattern, e.g. "yyyy" or "MMMM yyyy". */
fun groupByPattern(items: List<MediaItem>, pattern: String): List<DateGroup> {
    if (items.isEmpty()) return emptyList()
    val format = SimpleDateFormat(pattern, Locale.getDefault())
    val groups = LinkedHashMap<String, MutableList<MediaItem>>()
    for (item in items) {
        groups.getOrPut(format.format(Date(item.dateMillis))) { mutableListOf() }.add(item)
    }
    return groups.map { (label, list) -> DateGroup(label, list) }
}

fun formatDate(millis: Long): String = dayFormat.format(Date(millis))

fun formatDateTime(millis: Long): String =
    "${dayFormat.format(Date(millis))} at ${timeFormat.format(Date(millis))}"

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%d:%02d", minutes, seconds)
}

fun formatCount(count: Int, singular: String, plural: String = singular + "s"): String =
    if (count == 1) "1 $singular" else "$count $plural"

fun formatResolution(width: Int, height: Int): String =
    if (width > 0 && height > 0) "$width × $height" else "—"

fun formatNumber(value: Int): String = String.format(Locale.getDefault(), "%,d", value)
