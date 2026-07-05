package com.example.chobi

import android.app.Application
import com.example.chobi.data.AppDatabase
import com.example.chobi.data.DefaultExpenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChobiApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val expenseRepository by lazy { DefaultExpenseRepository(database.expenseDao(), database.categoryDao()) }

    override fun onCreate() {
        super.onCreate()
        // Prepopulate default categories if database is empty
        CoroutineScope(Dispatchers.IO).launch {
            val currentCategories = expenseRepository.getAllCategories().first()
            if (currentCategories.isEmpty()) {
                expenseRepository.prepopulateDefaultCategories()
            }
        }
    }
}
