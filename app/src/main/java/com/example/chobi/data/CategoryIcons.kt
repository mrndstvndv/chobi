package com.example.chobi.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryIcons {
    private val iconMap: Map<String, ImageVector> = mapOf(
        "Restaurant" to Icons.Default.Restaurant,
        "DirectionsCar" to Icons.Default.DirectionsCar,
        "Home" to Icons.Default.Home,
        "Movie" to Icons.Default.Movie,
        "ShoppingCart" to Icons.Default.ShoppingCart,
        "LocalHospital" to Icons.Default.LocalHospital,
        "School" to Icons.Default.School,
        "Flight" to Icons.Default.Flight,
        "FitnessCenter" to Icons.Default.FitnessCenter,
        "LocalCafe" to Icons.Default.LocalCafe,
        "Brush" to Icons.Default.Brush,
        "Pets" to Icons.Default.Pets,
        "PhoneAndroid" to Icons.Default.PhoneAndroid,
        "Work" to Icons.Default.Work,
        "Star" to Icons.Default.Star
    )

    fun getIcon(name: String): ImageVector {
        return iconMap[name] ?: Icons.Default.Star
    }

    fun getAllIcons(): List<Pair<String, ImageVector>> {
        return iconMap.toList()
    }
}
