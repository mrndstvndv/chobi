package com.example.chobi.ui.main

import com.example.chobi.data.Category
import com.example.chobi.data.Expense
import com.example.chobi.data.ExpenseRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
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
}
