package app.tplay.ui.common

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 1_000) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1_000 && unit < units.lastIndex) {
        value /= 1_000
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}

fun formatSpeed(bytesPerSecond: Long): String = formatBytes(bytesPerSecond) + "/s"

fun formatDuration(ms: Long): String {
    val total = (ms / 1_000).coerceAtLeast(0)
    val hours = total / 3_600
    val minutes = (total % 3_600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatAge(thenMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val days = ((nowMillis - thenMillis) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "today"
        days == 1 -> "yesterday"
        else -> "$days days ago"
    }
}
