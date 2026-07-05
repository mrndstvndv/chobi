package com.example.chobi.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Utility function to group expenses by date labels (Today, Yesterday, Day name, or Date).
 * Declared as a top-level function, which compiles to a static method under the hood in JVM.
 */
fun getGroupHeader(timestamp: Long): String {
    val now = Calendar.getInstance()
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val yesterday = Calendar.getInstance().apply {
        timeInMillis = today.timeInMillis
        add(Calendar.DAY_OF_YEAR, -1)
    }

    val itemDay = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    return when {
        itemDay.timeInMillis == today.timeInMillis -> "Today"
        itemDay.timeInMillis == yesterday.timeInMillis -> "Yesterday"
        now.timeInMillis - timestamp < 7 * 24 * 60 * 60 * 1000L && now.timeInMillis >= timestamp -> {
            val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
            dayFormat.format(Date(timestamp))
        }
        else -> {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
    }
}
