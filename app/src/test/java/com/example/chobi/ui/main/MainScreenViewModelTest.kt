package com.example.chobi.ui.main

import com.example.chobi.data.Budget
import com.example.chobi.data.BackupHelper
import com.example.chobi.data.Category
import com.example.chobi.data.Expense
import com.example.chobi.data.ExpenseRepository
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.SavedStateHandle
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun uiState_initiallyLoading() = runTest {
    val viewModel = MainScreenViewModel(FakeExpenseRepository(), SavedStateHandle())
    assertEquals(MainScreenUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun uiState_onItemSaved_isDisplayed() = runTest {
    val repository = FakeExpenseRepository()
    val viewModel = MainScreenViewModel(repository, SavedStateHandle())
    
    // Start collecting to activate stateIn sharing
    val collectJob = launch { viewModel.uiState.collect {} }
    
    viewModel.addExpense("Lunch", 15.50, "Food")
    
    val successState = viewModel.uiState.filterIsInstance<MainScreenUiState.Success>().first()
    assertEquals(1, successState.expenses.size)
    assertEquals("Lunch", successState.expenses[0].title)
    assertEquals(15.50, successState.expenses[0].amount)
    assertEquals("Food", successState.expenses[0].category)
    
    collectJob.cancel()
  }

  @Test
  fun uiState_onItemSavedWithCustomTimestamp_isDisplayed() = runTest {
    val repository = FakeExpenseRepository()
    val viewModel = MainScreenViewModel(repository, SavedStateHandle())
    
    val collectJob = launch { viewModel.uiState.collect {} }
    
    val customTimestamp = 1672531199000L
    viewModel.addExpense("Lunch", 15.50, "Food", customTimestamp)
    
    val successState = viewModel.uiState.filterIsInstance<MainScreenUiState.Success>().first()
    assertEquals(1, successState.expenses.size)
    assertEquals("Lunch", successState.expenses[0].title)
    assertEquals(15.50, successState.expenses[0].amount)
    assertEquals("Food", successState.expenses[0].category)
    assertEquals(customTimestamp, successState.expenses[0].timestamp)
    
    collectJob.cancel()
  }

  @Test
  fun uiState_onItemSavedWithSpecificBudgetId_isAssociatedWithBudget() = runTest {
    val repository = FakeExpenseRepository()
    val viewModel = MainScreenViewModel(repository, SavedStateHandle())
    
    val collectJob = launch { viewModel.uiState.collect {} }
    
    val budgetId = 42L
    viewModel.addExpense("Lunch", 15.50, "Food", budgetId = budgetId)
    
    val successState = viewModel.uiState.filterIsInstance<MainScreenUiState.Success>().first()
    assertEquals(1, successState.expenses.size)
    assertEquals("Lunch", successState.expenses[0].title)
    assertEquals(budgetId, successState.expenses[0].budgetId)
    
    collectJob.cancel()
  }

  @Test
  fun snackbarQueue_processesSequentiallyWithoutDelay() = runTest {
    val repository = FakeExpenseRepository()
    val viewModel = MainScreenViewModel(repository, SavedStateHandle())

    val expense1 = Expense(id = 1L, title = "Lunch", amount = 15.50, category = "Food", timestamp = 0L)
    val expense2 = Expense(id = 2L, title = "Coffee", amount = 4.50, category = "Food", timestamp = 0L)

    viewModel.swipeToDelete(expense1)
    viewModel.swipeToDelete(expense2)

    // The newly deleted item (expense2) should be displayed immediately
    assertEquals(expense2, viewModel.currentSnackbar.value)

    // Report result for expense2
    viewModel.reportSnackbarResult(SnackbarResult.Dismissed)

    // The previous item (expense1) from the stack/queue should now be shown
    val nextSnackbar = viewModel.currentSnackbar.first { it == expense1 }
    assertEquals(expense1, nextSnackbar)

    // Report result for expense1
    viewModel.reportSnackbarResult(SnackbarResult.Dismissed)

    // Queue is empty, should be null
    val finalSnackbar = viewModel.currentSnackbar.first { it == null }
    assertEquals(null, finalSnackbar)
  }

  @Test
  fun swipeToDelete_commitsDeletionImmediately() = runTest {
    val repository = FakeExpenseRepository()
    val viewModel = MainScreenViewModel(repository, SavedStateHandle())

    val expense = Expense(id = 1L, title = "Lunch", amount = 15.50, category = "Food", timestamp = 0L)
    repository.insertExpense(expense)
    
    // Verify it is in DB initially
    assertEquals(1, repository.getAllExpenses().first().size)

    // Swipe to delete commits the deletion immediately; the snackbar only offers undo.
    viewModel.swipeToDelete(expense)
    val remainingExpenses = repository.getAllExpenses().first { it.isEmpty() }
    assertEquals(0, remainingExpenses.size)
  }

  @Test
  fun undoRestoresDeletedExpense() = runTest {
    val repository = FakeExpenseRepository()
    val viewModel = MainScreenViewModel(repository, SavedStateHandle())
    val expense = Expense(id = 1L, title = "Lunch", amount = 15.50, category = "Food", timestamp = 0L)
    repository.insertExpense(expense)

    viewModel.swipeToDelete(expense)
    repository.getAllExpenses().first { it.isEmpty() }
    viewModel.reportSnackbarResult(expense, SnackbarResult.ActionPerformed)

    assertEquals(1, repository.getAllExpenses().first { it.isNotEmpty() }.size)
  }

  @Test
  fun selectedBudget_isPersistedAndCorrect() = runTest {
    val repository = FakeExpenseRepository()
    val viewModel = MainScreenViewModel(repository, SavedStateHandle())

    val budget1 = Budget(id = 1L, title = "Budget 1", limitAmount = 100.0, startTimestamp = 1000L, endTimestamp = 2000L)
    val budget2 = Budget(id = 2L, title = "Budget 2", limitAmount = 200.0, startTimestamp = 2000L, endTimestamp = null) // active budget

    repository.insertBudget(budget1)
    repository.insertBudget(budget2)

    val collectJob = launch { viewModel.uiState.collect {} }

    // Initially, it should default to the active budget (budget2)
    var successState = viewModel.uiState.filterIsInstance<MainScreenUiState.Success>().first()
    assertEquals(budget2.id, successState.selectedBudget?.id)

    // Select budget1 manually
    viewModel.selectBudget(budget1)
    successState = viewModel.uiState.filterIsInstance<MainScreenUiState.Success>().first { it.selectedBudget?.id == budget1.id }
    assertEquals(budget1.id, successState.selectedBudget?.id)

    // Select "All Expenses" (null)
    viewModel.selectBudget(null)
    successState = viewModel.uiState.filterIsInstance<MainScreenUiState.Success>().first { it.selectedBudget == null }
    assertEquals(null, successState.selectedBudget)

    collectJob.cancel()
  }
}

private class FakeExpenseRepository : ExpenseRepository {
  private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
  private val _categories = MutableStateFlow<List<Category>>(emptyList())
  private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
  
  override fun getAllExpenses(): Flow<List<Expense>> = _expenses

  override suspend fun insertExpense(expense: Expense) {
    val current = _expenses.value.toMutableList()
    current.add(0, expense.copy(id = (current.size + 1).toLong()))
    _expenses.value = current
  }

  override suspend fun deleteExpense(expense: Expense) {
    val current = _expenses.value.toMutableList()
    current.removeIf { it.id == expense.id }
    _expenses.value = current
  }

  override fun getAllCategories(): Flow<List<Category>> = _categories

  override suspend fun insertCategory(category: Category) {
    val current = _categories.value.toMutableList()
    current.add(category.copy(id = (current.size + 1).toLong()))
    _categories.value = current
  }

  override suspend fun updateCategory(category: Category, oldName: String) {
    val currentCats = _categories.value.toMutableList()
    val index = currentCats.indexOfFirst { it.id == category.id }
    if (index != -1) {
      currentCats[index] = category
    } else {
      currentCats.add(category)
    }
    _categories.value = currentCats

    if (category.name != oldName) {
      val currentExp = _expenses.value.map {
        if (it.category == oldName) it.copy(category = category.name) else it
      }
      _expenses.value = currentExp
    }
  }

  override suspend fun deleteCategory(category: Category) {
    val current = _categories.value.toMutableList()
    current.removeIf { it.id == category.id }
    _categories.value = current
  }

  override suspend fun prepopulateDefaultCategories() {
    _categories.value = listOf(
      Category(id = 1, name = "Food", iconName = "Restaurant", colorHex = "#FF9800"),
      Category(id = 2, name = "Transport", iconName = "DirectionsCar", colorHex = "#2196F3")
    )
  }

  override suspend fun clearAllData() {
    _expenses.value = emptyList()
    _categories.value = emptyList()
    _budgets.value = emptyList()
  }

  override suspend fun importData(
    categories: List<Category>,
    expenses: List<BackupHelper.BackupExpense>,
    budgets: List<Budget>,
    overwrite: Boolean
  ) {
    if (overwrite) {
      _categories.value = categories
      _budgets.value = budgets
      _expenses.value = expenses.map { it.expense }
    } else {
      val existingNames = _categories.value.map { it.name.lowercase() }.toSet()
      val newCats = _categories.value.toMutableList()
      for (cat in categories) {
        if (cat.name.lowercase() !in existingNames) {
          newCats.add(cat)
        }
      }
      _categories.value = newCats

      val newBudgets = _budgets.value.toMutableList()
      for (bud in budgets) {
        val isDuplicate = _budgets.value.any {
          it.title.lowercase() == bud.title.lowercase() &&
          it.limitAmount == bud.limitAmount &&
          it.startTimestamp == bud.startTimestamp
        }
        if (!isDuplicate) {
          newBudgets.add(bud)
        }
      }
      _budgets.value = newBudgets

      val newExpenses = _expenses.value.toMutableList()
      for (pair in expenses) {
        val exp = pair.expense
        val isDuplicate = _expenses.value.any {
          it.title.lowercase() == exp.title.lowercase() &&
          it.amount == exp.amount &&
          it.category.lowercase() == exp.category.lowercase() &&
          it.timestamp == exp.timestamp
        }
        if (!isDuplicate) {
          newExpenses.add(exp)
        }
      }
      _expenses.value = newExpenses
    }
  }

  override fun getAllBudgets(): Flow<List<Budget>> = _budgets

  override fun getActiveBudget(): Flow<Budget?> = _budgets.map { budgets ->
    budgets.firstOrNull { it.endTimestamp == null }
  }

  override suspend fun createBudget(title: String, limitAmount: Double, startTimestamp: Long): Long {
    val current = _budgets.value.toMutableList()
    current.indices
      .filter { current[it].endTimestamp == null }
      .forEach { index -> current[index] = current[index].copy(endTimestamp = startTimestamp) }
    val id = (current.maxOfOrNull { it.id } ?: 0L) + 1L
    current.add(Budget(id = id, title = title, limitAmount = limitAmount, startTimestamp = startTimestamp))
    _budgets.value = current
    return id
  }

  override suspend fun insertBudget(budget: Budget): Long {
    val current = _budgets.value.toMutableList()
    val id = (current.size + 1).toLong()
    current.add(budget.copy(id = id))
    _budgets.value = current
    return id
  }

  override suspend fun updateBudget(budget: Budget) {
    val current = _budgets.value.toMutableList()
    val index = current.indexOfFirst { it.id == budget.id }
    if (index != -1) {
      current[index] = budget
      _budgets.value = current
    }
  }

  override suspend fun deleteBudget(budget: Budget) {
    val current = _budgets.value.toMutableList()
    current.removeIf { it.id == budget.id }
    _budgets.value = current
  }
}
