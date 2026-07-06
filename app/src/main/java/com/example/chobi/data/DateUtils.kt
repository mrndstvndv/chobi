package com.example.chobi.data

import java.util.Locale

/**
 * Utility function to group expenses by date labels (Today, Yesterday, Day name, or Date).
 * Declared as a top-level function, which compiles to a static method under the hood in JVM.
 */
fun getGroupHeader(timestamp: Long): String {
    val zoneId = java.time.ZoneId.systemDefault()
    val today = java.time.LocalDate.now(zoneId)
    val itemDate = java.time.Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()

    return when {
        itemDate == today -> "Today"
        itemDate == today.minusDays(1) -> "Yesterday"
        itemDate.isBefore(today) && java.time.temporal.ChronoUnit.DAYS.between(itemDate, today) < 7 -> {
            itemDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
        }
        else -> {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())
            itemDate.format(formatter)
        }
    }
}
