package com.example.chobi.ui.main

import android.icu.text.NumberFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.chobi.data.Budget
import com.example.chobi.data.Category
import com.example.chobi.data.Expense
import com.example.chobi.data.getGroupHeader
import com.example.chobi.theme.ChobiTheme
import com.example.chobi.ui.components.ExpenseItem
import com.example.chobi.ui.components.SummaryCard
import com.example.chobi.ui.components.BudgetDialog
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainContent(
  expenses: List<Expense>,
  categories: List<Category>,
  budgets: List<Budget>,
  selectedBudget: Budget?,
  onSelectBudget: (Budget?) -> Unit,
  onDeleteExpense: (Expense) -> Unit,
  onCreateBudget: (title: String, limitAmount: Double) -> Unit,
  onDeleteBudget: (Budget) -> Unit,
  currencyCode: String = "USD",
  timeFormatPreference: String = "auto",
  modifier: Modifier = Modifier,
  onExpenseClick: ((Expense) -> Unit)? = null
) {

  var showBudgetDialog by remember { mutableStateOf(false) }

  val filteredExpenses = remember(expenses, selectedBudget) {
    val currentBudget = selectedBudget
    if (currentBudget == null) {
      expenses
    } else {
      expenses.filter { it.budgetId == currentBudget.id }
    }
  }

  val totalAmount = filteredExpenses.sumOf { it.amount }
  val currencyFormatter = remember(currencyCode) {
    try {
      val icuCurrency = android.icu.util.Currency.getInstance(currencyCode)
      NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        currency = icuCurrency
      }
    } catch (e: Exception) {
      NumberFormat.getCurrencyInstance(Locale.getDefault())
    }
  }

  val groupedExpenses = remember(filteredExpenses) {
    filteredExpenses.groupBy { getGroupHeader(it.timestamp) }
  }

  if (showBudgetDialog) {
    BudgetDialog(
      onDismiss = { showBudgetDialog = false },
      onConfirm = { title, limit ->
        onCreateBudget(title, limit)
        showBudgetDialog = false
      }
    )
  }

  LazyColumn(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
  ) {
    // Summary/Wallet Card
    item {
      SummaryCard(
        expenses = filteredExpenses,
        totalAmount = totalAmount,
        budgets = budgets,
        selectedBudget = selectedBudget,
        onSelectBudget = onSelectBudget,
        onNewBudgetClick = { showBudgetDialog = true },
        onDeleteBudget = onDeleteBudget,
        currencyCode = currencyCode,
        currencyFormatter = currencyFormatter,
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
      )
    }

    if (filteredExpenses.isEmpty()) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 48.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = if (selectedBudget != null) "No expenses in this budget period." else "No expenses added yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    } else {
      groupedExpenses.forEach { (header, itemsForHeader) ->
        item(key = "header_$header") {
          val dayTotal = remember(itemsForHeader) { itemsForHeader.sumOf { it.amount } }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = header,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.primary
            )
            val isDayIncome = dayTotal < 0
            val dayDisplay = if (isDayIncome) {
              "+" + currencyFormatter.format(kotlin.math.abs(dayTotal))
            } else {
              currencyFormatter.format(dayTotal)
            }
            val dayColor = if (isDayIncome) {
              androidx.compose.ui.graphics.Color(0xFF2E7D32)
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
              text = dayDisplay,
              style = MaterialTheme.typography.titleMedium,
              color = dayColor
            )
          }
        }
        itemsIndexed(itemsForHeader, key = { _, it -> it.id }) { index, expense ->
          val matchingCategory = categories.firstOrNull { it.name.lowercase() == expense.category.lowercase() }
          ExpenseItem(
            expense = expense,
            category = matchingCategory,
            onDelete = { onDeleteExpense(expense) },
            currencyFormatter = currencyFormatter,
            timeFormatPreference = timeFormatPreference,
            onClick = { onExpenseClick?.invoke(expense) },
            shapes = ListItemDefaults.segmentedShapes(index, itemsForHeader.size),
            index = index,
            count = itemsForHeader.size,
            modifier = Modifier
              .animateItem()
              .padding(horizontal = 16.dp)
          )
        }
      }
    }
  }
}


@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
  ChobiTheme {
    MainContent(
      expenses = listOf(
        Expense(id = 1, title = "Lunch", amount = 15.50, category = "Food", timestamp = System.currentTimeMillis()),
        Expense(id = 2, title = "Bus", amount = 2.50, category = "Transport", timestamp = System.currentTimeMillis())
      ),
      categories = listOf(
        Category(id = 1, name = "Food", iconName = "Restaurant", colorHex = "#FF9800"),
        Category(id = 2, name = "Transport", iconName = "DirectionsCar", colorHex = "#2196F3")
      ),
      budgets = emptyList(),
      selectedBudget = null,
      onSelectBudget = {},
      onDeleteExpense = {},
      onCreateBudget = { _, _ -> },
      onDeleteBudget = {}
    )
  }
}
