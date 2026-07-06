package com.example.chobi.ui.main

import com.example.chobi.data.Budget
import com.example.chobi.data.Category
import com.example.chobi.data.Expense
import com.example.chobi.data.ExpenseRepository
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
    val viewModel = MainScreenViewModel(FakeExpenseRepository())
    assertEquals(MainScreenUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun uiState_onItemSaved_isDisplayed() = runTest {
    val repository = FakeExpenseRepository()
    val viewModel = MainScreenViewModel(repository)
    
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
    val viewModel = MainScreenViewModel(repository)
    
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
    expenses: List<Pair<Expense, Long?>>,
    budgets: List<Budget>,
    overwrite: Boolean
  ) {
    if (overwrite) {
      _categories.value = categories
      _budgets.value = budgets
      _expenses.value = expenses.map { it.first }
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
        val exp = pair.first
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
