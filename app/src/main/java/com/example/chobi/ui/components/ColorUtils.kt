package com.example.chobi.ui.components

import androidx.compose.ui.graphics.Color

// Helper to convert hex string to Compose Color
fun String.toColor(): Color {
    return try {
        Color(android.graphics.Color.parseColor(this))
    } catch (e: Exception) {
        Color.Gray
    }
}
