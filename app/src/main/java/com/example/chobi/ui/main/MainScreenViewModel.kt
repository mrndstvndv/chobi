package com.example.chobi.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chobi.data.AppDatabase
import com.example.chobi.data.BackupHelper
import com.example.chobi.data.Category
import com.example.chobi.data.Expense
import com.example.chobi.data.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainScreenViewModel(private val expenseRepository: ExpenseRepository) : ViewModel() {
  val uiState: StateFlow<MainScreenUiState> =
    combine(
      expenseRepository.getAllExpenses(),
      expenseRepository.getAllCategories()
    ) { expenses, categories ->
      MainScreenUiState.Success(expenses, categories) as MainScreenUiState
    }
      .catch { emit(MainScreenUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)

  fun addExpense(title: String, amount: Double, categoryName: String, timestamp: Long = System.currentTimeMillis()) {
    viewModelScope.launch {
      expenseRepository.insertExpense(
        Expense(
          title = title,
          amount = amount,
          category = categoryName,
          timestamp = timestamp
        )
      )
    }
  }

  fun updateExpense(expense: Expense) {
    viewModelScope.launch {
      expenseRepository.insertExpense(expense)
    }
  }

  fun deleteExpense(expense: Expense) {
    viewModelScope.launch {
      expenseRepository.deleteExpense(expense)
    }
  }

  fun addCategory(name: String, iconName: String, colorHex: String) {
    viewModelScope.launch {
      expenseRepository.insertCategory(
        Category(
          name = name,
          iconName = iconName,
          colorHex = colorHex
        )
      )
    }
  }

  fun deleteCategory(category: Category) {
    viewModelScope.launch {
      expenseRepository.deleteCategory(category)
    }
  }

  fun updateCategory(category: Category, oldName: String) {
    viewModelScope.launch {
      expenseRepository.updateCategory(category, oldName)
    }
  }

  fun exportDataToJson(
    context: android.content.Context,
    uri: android.net.Uri,
    categories: List<Category>,
    expenses: List<Expense>,
    onSuccess: () -> Unit,
    onError: (Throwable) -> Unit
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val jsonString = BackupHelper.exportToJson(categories, expenses)
        context.contentResolver.openOutputStream(uri)?.use { outStream ->
          outStream.bufferedWriter().use { it.write(jsonString) }
        } ?: throw Exception("Failed to open output stream")
        withContext(Dispatchers.Main) {
          onSuccess()
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          onError(e)
        }
      }
    }
  }

  fun importDataFromJson(
    context: android.content.Context,
    uri: android.net.Uri,
    overwrite: Boolean,
    onSuccess: () -> Unit,
    onError: (Throwable) -> Unit
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val jsonString = context.contentResolver.openInputStream(uri)?.use { inStream ->
          inStream.bufferedReader().use { it.readText() }
        } ?: throw Exception("Failed to open input stream")
        val (categories, expenses) = BackupHelper.importFromJson(jsonString)
        expenseRepository.importData(categories, expenses, overwrite)
        withContext(Dispatchers.Main) {
          onSuccess()
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          onError(e)
        }
      }
    }
  }

  fun exportRawDb(
    context: android.content.Context,
    uri: android.net.Uri,
    onSuccess: () -> Unit,
    onError: (Throwable) -> Unit
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val db = AppDatabase.getDatabase(context)
        val cursor = db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)", emptyArray())
        try {
          cursor.moveToFirst()
        } finally {
          cursor.close()
        }
        val dbFile = context.getDatabasePath("expense_database")
        if (!dbFile.exists()) {
          throw Exception("Database file does not exist")
        }
        context.contentResolver.openOutputStream(uri)?.use { outStream ->
          dbFile.inputStream().use { inStream ->
            inStream.copyTo(outStream)
          }
        } ?: throw Exception("Failed to open output stream")
        withContext(Dispatchers.Main) {
          onSuccess()
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          onError(e)
        }
      }
    }
  }

  fun importRawDb(
    context: android.content.Context,
    uri: android.net.Uri,
    onSuccess: () -> Unit,
    onError: (Throwable) -> Unit
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val db = AppDatabase.getDatabase(context)
        db.close()
        val dbFile = context.getDatabasePath("expense_database")
        val dbWal = java.io.File(dbFile.path + "-wal")
        val dbShm = java.io.File(dbFile.path + "-shm")
        
        if (dbFile.exists()) dbFile.delete()
        if (dbWal.exists()) dbWal.delete()
        if (dbShm.exists()) dbShm.delete()

        context.contentResolver.openInputStream(uri)?.use { inStream ->
          dbFile.outputStream().use { outStream ->
            inStream.copyTo(outStream)
          }
        } ?: throw Exception("Failed to open input stream")
        withContext(Dispatchers.Main) {
          onSuccess()
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          onError(e)
        }
      }
    }
  }
}

sealed interface MainScreenUiState {
  data object Loading : MainScreenUiState
  data class Error(val throwable: Throwable) : MainScreenUiState
  data class Success(val expenses: List<Expense>, val categories: List<Category>) : MainScreenUiState
}
