package com.example.chobi.ui.main

import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chobi.data.AppDatabase
import com.example.chobi.data.BackupHelper
import com.example.chobi.data.Category
import com.example.chobi.data.Expense
import com.example.chobi.data.Budget
import com.example.chobi.data.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainScreenViewModel(
  private val expenseRepository: ExpenseRepository,
  private val savedStateHandle: SavedStateHandle
) : ViewModel() {
  private val _activeSnackbars = MutableStateFlow<List<Expense>>(emptyList())
  val activeSnackbars: StateFlow<List<Expense>> = _activeSnackbars.asStateFlow()

  private val _currentSnackbar = MutableStateFlow<Expense?>(null)
  val currentSnackbar: StateFlow<Expense?> = _currentSnackbar.asStateFlow()
  private val deletionMutex = Mutex()

  val selectedBudgetId: StateFlow<Long?> = savedStateHandle.getStateFlow("selected_budget_id", -1L)

  val uiState: StateFlow<MainScreenUiState> =
    combine(
      expenseRepository.getAllExpenses(),
      expenseRepository.getAllCategories(),
      expenseRepository.getAllBudgets(),
      selectedBudgetId,
      _activeSnackbars
    ) { expenses, categories, budgets, selectedId, activeSnackbars ->
      val activeIds = activeSnackbars.map { it.id }.toSet()
      val filteredExpenses = expenses.filter { it.id !in activeIds }

      val resolvedBudget = if (selectedId == -1L) {
        budgets.firstOrNull { it.endTimestamp == null } ?: budgets.firstOrNull()
      } else if (selectedId == null) {
        null
      } else {
        budgets.firstOrNull { it.id == selectedId }
          ?: budgets.firstOrNull { it.endTimestamp == null }
          ?: budgets.firstOrNull()
      }

      MainScreenUiState.Success(
        expenses = filteredExpenses,
        categories = categories,
        budgets = budgets,
        selectedBudget = resolvedBudget
      ) as MainScreenUiState
    }
      .flowOn(Dispatchers.Default)
      .catch { emit(MainScreenUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)

  fun addExpense(
    title: String,
    amount: Double,
    categoryName: String,
    timestamp: Long = System.currentTimeMillis(),
    budgetId: Long? = null
  ) {
    viewModelScope.launch {
      val targetBudgetId = budgetId ?: expenseRepository.getActiveBudget().first()?.id
      expenseRepository.insertExpense(
        Expense(
          title = title,
          amount = amount,
          category = categoryName,
          timestamp = timestamp,
          budgetId = targetBudgetId
        )
      )
    }
  }

  fun updateExpense(expense: Expense) {
    viewModelScope.launch {
      expenseRepository.insertExpense(expense)
    }
  }

  fun swipeToDelete(expense: Expense) {
    _activeSnackbars.update { it + expense }
    _currentSnackbar.value = expense
    viewModelScope.launch {
      deletionMutex.withLock {
        expenseRepository.deleteExpense(expense)
      }
    }
  }

  fun reportSnackbarResult(result: SnackbarResult) {
    val current = _activeSnackbars.value.lastOrNull() ?: return
    reportSnackbarResult(current, result)
  }

  fun reportSnackbarResult(expense: Expense, result: SnackbarResult) {
    _activeSnackbars.update { list -> list.filter { it.id != expense.id } }
    _currentSnackbar.value = _activeSnackbars.value.lastOrNull()
    if (result == SnackbarResult.ActionPerformed) {
      viewModelScope.launch {
        deletionMutex.withLock {
          expenseRepository.insertExpense(expense)
        }
      }
    }
  }

  fun undoLastDelete() {
    val current = _activeSnackbars.value.lastOrNull() ?: return
    reportSnackbarResult(current, SnackbarResult.ActionPerformed)
  }

  fun clearSnackbar() {
    val current = _activeSnackbars.value.lastOrNull() ?: return
    reportSnackbarResult(current, SnackbarResult.Dismissed)
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

  fun createNewBudget(title: String, limitAmount: Double) {
    viewModelScope.launch {
      expenseRepository.createBudget(title, limitAmount, System.currentTimeMillis())
    }
  }

  fun deleteBudget(budget: Budget) {
    viewModelScope.launch {
      expenseRepository.deleteBudget(budget)
    }
  }

  fun selectBudget(budget: Budget?) {
    savedStateHandle["selected_budget_id"] = budget?.id
  }

  fun exportDataToJson(
    context: android.content.Context,
    uri: android.net.Uri,
    categories: List<Category>,
    expenses: List<Expense>,
    budgets: List<Budget>,
    onSuccess: () -> Unit,
    onError: (Throwable) -> Unit
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val jsonString = BackupHelper.exportToJson(categories, expenses, budgets)
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
        val (categories, expenses, budgets) = BackupHelper.importFromJson(jsonString)
        expenseRepository.importData(categories, expenses, budgets, overwrite)
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
        (context.applicationContext as? com.example.chobi.ChobiApplication)?.reloadDatabase()
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
  data class Success(
    val expenses: List<Expense>,
    val categories: List<Category>,
    val budgets: List<Budget>,
    val selectedBudget: Budget?
  ) : MainScreenUiState
}
