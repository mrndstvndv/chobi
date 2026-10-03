package com.example.chobi

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data object Budgets : NavKey

@Serializable data class Dashboard(val budgetId: Long?) : NavKey
