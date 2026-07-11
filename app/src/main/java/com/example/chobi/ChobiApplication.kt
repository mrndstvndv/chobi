package com.example.chobi

import android.app.Application
import com.example.chobi.data.AppDatabase
import com.example.chobi.data.DefaultExpenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChobiApplication : Application() {
    @Volatile
    private var databaseInstance: AppDatabase? = null

    @Volatile
    private var repositoryInstance: DefaultExpenseRepository? = null

    val database: AppDatabase
        get() = synchronized(this) {
            databaseInstance ?: AppDatabase.getDatabase(this).also { databaseInstance = it }
        }

    val expenseRepository: DefaultExpenseRepository
        get() = synchronized(this) {
            repositoryInstance ?: DefaultExpenseRepository(
                database.expenseDao(),
                database.categoryDao(),
                database.budgetDao(),
                database
            ).also { repositoryInstance = it }
        }

    fun reloadDatabase() {
        synchronized(this) {
            databaseInstance?.close()
            databaseInstance = null
            repositoryInstance = null
        }
    }

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
