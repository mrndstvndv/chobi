package com.example.chobi.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chobi.data.Category
import com.example.chobi.data.Expense
import com.example.chobi.data.ExpenseRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
}

sealed interface MainScreenUiState {
  data object Loading : MainScreenUiState
  data class Error(val throwable: Throwable) : MainScreenUiState
  data class Success(val expenses: List<Expense>, val categories: List<Category>) : MainScreenUiState
}
