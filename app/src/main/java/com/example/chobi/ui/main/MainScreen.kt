package com.example.chobi.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.chobi.ChobiApplication
import com.example.chobi.data.Category
import com.example.chobi.data.CategoryIcons
import com.example.chobi.data.getGroupHeader
import com.example.chobi.data.Expense
import com.example.chobi.theme.ChobiTheme
import android.icu.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Helper to convert hex string to Compose Color
fun String.toColor(): Color {
    return try {
        Color(android.graphics.Color.parseColor(this))
    } catch (e: Exception) {
        Color.Gray
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current.applicationContext as ChobiApplication
  val viewModel: MainScreenViewModel = viewModel {
    MainScreenViewModel(context.expenseRepository)
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showBottomSheet by remember { mutableStateOf(false) }
  
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Expense Tracker", style = MaterialTheme.typography.titleLarge) },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Color.Transparent,
          titleContentColor = MaterialTheme.colorScheme.onSurface
        )
      )
    },
    floatingActionButton = {
      if (state is MainScreenUiState.Success) {
        FloatingActionButton(
          onClick = { showBottomSheet = true },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
          Icon(imageVector = Icons.Default.Add, contentDescription = "Add Expense")
        }
      }
    },
    modifier = modifier
  ) { paddingValues ->
    when (state) {
      MainScreenUiState.Loading -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator()
        }
      }
      is MainScreenUiState.Success -> {
        val successState = state as MainScreenUiState.Success
        MainContent(
          expenses = successState.expenses,
          categories = successState.categories,
          onDeleteExpense = { expense ->
            viewModel.deleteExpense(expense)
          },
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        )

        if (showBottomSheet) {
          ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
          ) {
            AddExpenseSheet(
              categories = successState.categories,
              onAddExpense = { title, amount, categoryName, timestamp ->
                viewModel.addExpense(title, amount, categoryName, timestamp)
                showBottomSheet = false
              },
              onAddCategory = { name, iconName, colorHex ->
                viewModel.addCategory(name, iconName, colorHex)
              },
              onUpdateCategory = { category, oldName ->
                viewModel.updateCategory(category, oldName)
              },
              onDeleteCategory = { category ->
                viewModel.deleteCategory(category)
              },
              onDismiss = { showBottomSheet = false }
            )
          }
        }
      }
      is MainScreenUiState.Error -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
          contentAlignment = Alignment.Center
        ) {
          Text("Error loading data: ${(state as MainScreenUiState.Error).throwable.message}")
        }
      }
    }
  }
}

@Composable
fun MainContent(
  expenses: List<Expense>,
  categories: List<Category>,
  onDeleteExpense: (Expense) -> Unit,
  modifier: Modifier = Modifier
) {
  val totalAmount = expenses.sumOf { it.amount }
  val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
  val systemCurrency = remember {
    try {
      android.icu.util.Currency.getInstance(Locale.getDefault())
    } catch (e: Exception) {
      android.icu.util.Currency.getInstance("USD")
    }
  }
  val currencyCode = systemCurrency.currencyCode

  val groupedExpenses = remember(expenses) {
    expenses.groupBy { getGroupHeader(it.timestamp) }
  }

  LazyColumn(
    modifier = modifier.padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Summary Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 16.dp, bottom = 8.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.primary
        )
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "Total Expenses ($currencyCode)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = currencyFormatter.format(totalAmount),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onPrimary
          )
        }
      }
    }

    if (expenses.isEmpty()) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "No expenses added yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    } else {
      groupedExpenses.forEach { (header, itemsForHeader) ->
        item(key = "header_$header") {
          Text(
            text = header,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
          )
        }
        items(itemsForHeader, key = { it.id }) { expense ->
          val matchingCategory = categories.firstOrNull { it.name.lowercase() == expense.category.lowercase() }
          ExpenseItem(
            expense = expense,
            category = matchingCategory,
            onDelete = { onDeleteExpense(expense) },
            currencyFormatter = currencyFormatter
          )
        }
      }
    }
  }
}
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
  categories: List<Category>,
  onAddExpense: (String, Double, String, Long) -> Unit,
  onAddCategory: (String, String, String) -> Unit,
  onUpdateCategory: (Category, String) -> Unit,
  onDeleteCategory: (Category) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  var title by remember { mutableStateOf("") }
  var amountStr by remember { mutableStateOf("") }
  var selectedCategoryName by remember(categories) {
    mutableStateOf(categories.firstOrNull()?.name ?: "Food")
  }
  var showAddCategoryDialog by remember { mutableStateOf(false) }
  var editingCategory by remember { mutableStateOf<Category?>(null) }

  var selectedTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
  var showDatePicker by remember { mutableStateOf(false) }
  val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

  val systemCurrency = remember {
    try {
      android.icu.util.Currency.getInstance(Locale.getDefault())
    } catch (e: Exception) {
      android.icu.util.Currency.getInstance("USD")
    }
  }
  val currencySymbol = remember(systemCurrency) {
    systemCurrency.getSymbol(Locale.getDefault()) ?: "$"
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .imePadding()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 24.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Start,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "New Expense",
        style = MaterialTheme.typography.headlineSmall
      )
    }

    OutlinedTextField(
      value = title,
      onValueChange = { title = it },
      label = { Text("Title") },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true
    )

    OutlinedTextField(
      value = amountStr,
      onValueChange = { amountStr = it },
      label = { Text("Amount") },
      prefix = { Text(currencySymbol) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = Modifier.fillMaxWidth(),
      singleLine = true
    )

    Box(
      modifier = Modifier.fillMaxWidth()
    ) {
      OutlinedTextField(
        value = dateFormatter.format(Date(selectedTimestamp)),
        onValueChange = {},
        readOnly = true,
        label = { Text("Date") },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = "Select Date"
          )
        },
        modifier = Modifier.fillMaxWidth()
      )
      Box(
        modifier = Modifier
          .matchParentSize()
          .clickable { showDatePicker = true }
      )
    }

    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      categories.forEach { cat ->
        val selected = selectedCategoryName == cat.name
        val catColor = cat.colorHex.toColor()
        
        val containerColor = if (selected) {
          MaterialTheme.colorScheme.primaryContainer
        } else {
          MaterialTheme.colorScheme.surface
        }
        val contentColor = if (selected) {
          MaterialTheme.colorScheme.onPrimaryContainer
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        }
        val border = if (selected) {
          null
        } else {
          BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = containerColor,
          contentColor = contentColor,
          border = border,
          modifier = Modifier.combinedClickable(
            onClick = { selectedCategoryName = cat.name },
            onLongClick = {
              editingCategory = cat
              showAddCategoryDialog = true
            }
          )
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = CategoryIcons.getIcon(cat.iconName),
              contentDescription = cat.name,
              tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else catColor,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = cat.name,
              style = MaterialTheme.typography.labelLarge
            )
          }
        }
      }

      // Add Custom Category Chip LAST
      FilterChip(
        selected = false,
        onClick = { 
          editingCategory = null
          showAddCategoryDialog = true 
        },
        label = { Text("Add Custom") },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add custom category",
            modifier = Modifier.size(18.dp)
          )
        }
      )
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      OutlinedButton(
        onClick = onDismiss,
        modifier = Modifier.weight(1f)
      ) {
        Text("Cancel")
      }
      Button(
        onClick = {
          val amt = amountStr.toDoubleOrNull() ?: 0.0
          if (title.isNotBlank() && amt > 0.0) {
            onAddExpense(title, amt, selectedCategoryName, selectedTimestamp)
          }
        },
        enabled = title.isNotBlank() && (amountStr.toDoubleOrNull() ?: 0.0) > 0.0,
        modifier = Modifier.weight(1f)
      ) {
        Text("Save")
      }
    }

    if (showAddCategoryDialog) {
      CategoryDialog(
        category = editingCategory,
        onDismiss = {
          showAddCategoryDialog = false
          editingCategory = null
        },
        onConfirm = { name, iconName, colorHex ->
          val cat = editingCategory
          if (cat == null) {
            onAddCategory(name, iconName, colorHex)
          } else {
            onUpdateCategory(cat.copy(name = name, iconName = iconName, colorHex = colorHex), cat.name)
          }
          selectedCategoryName = name
          showAddCategoryDialog = false
          editingCategory = null
        },
        onDelete = {
          val cat = editingCategory
          if (cat != null) {
            onDeleteCategory(cat)
            if (selectedCategoryName == cat.name) {
              selectedCategoryName = categories.firstOrNull { it.id != cat.id }?.name ?: ""
            }
          }
          showAddCategoryDialog = false
          editingCategory = null
        }
      )
    }

    if (showDatePicker) {
      val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedTimestamp
      )
      DatePickerDialog(
        onDismissRequest = { showDatePicker = false },
        confirmButton = {
          TextButton(
            onClick = {
              selectedTimestamp = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
              showDatePicker = false
            }
          ) {
            Text("OK")
          }
        },
        dismissButton = {
          TextButton(
            onClick = { showDatePicker = false }
          ) {
            Text("Cancel")
          }
        }
      ) {
        DatePicker(state = datePickerState)
      }
    }
  }
}

@Composable
fun ExpenseItem(
  expense: Expense,
  category: Category?,
  onDelete: () -> Unit,
  currencyFormatter: NumberFormat,
  modifier: Modifier = Modifier
) {
  val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
  val icon = category?.let { CategoryIcons.getIcon(it.iconName) } ?: Icons.Default.Star
  val iconColor = category?.colorHex?.toColor() ?: MaterialTheme.colorScheme.primary

  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = CardDefaults.outlinedCardBorder()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Icon Container
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(iconColor.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = icon,
            contentDescription = expense.category,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
          )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
          Text(
            text = expense.title,
            style = MaterialTheme.typography.titleMedium
          )
          Text(
            text = "${expense.category} • ${timeFormatter.format(Date(expense.timestamp))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = currencyFormatter.format(expense.amount),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(end = 8.dp)
        )
        IconButton(onClick = onDelete) {
          Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete expense",
            tint = MaterialTheme.colorScheme.error
          )
        }
      }
    }
  }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDialog(
  category: Category? = null,
  onDismiss: () -> Unit,
  onConfirm: (name: String, iconName: String, colorHex: String) -> Unit,
  onDelete: (() -> Unit)? = null
) {
  var name by remember { mutableStateOf(category?.name ?: "") }
  var selectedIconName by remember { mutableStateOf(category?.iconName ?: "Restaurant") }
  var selectedColorHex by remember { mutableStateOf(category?.colorHex ?: "#FF9800") }

  val icons = CategoryIcons.getAllIcons()
  val colors = listOf(
    "#FF9800", // Orange
    "#2196F3", // Blue
    "#4CAF50", // Green
    "#9C27B0", // Purple
    "#E91E63", // Pink
    "#F44336", // Red
    "#3F51B5", // Indigo
    "#00BCD4", // Teal
    "#8BC34A", // Light Green
    "#9E9E9E"  // Gray
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (category == null) "New Custom Category" else "Edit Category") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Category Name") },
          modifier = Modifier.fillMaxWidth()
        )

        // Color Picker Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Select Color", style = MaterialTheme.typography.titleSmall)
          val colorRows = colors.chunked(5)
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            colorRows.forEach { rowColors ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
              ) {
                rowColors.forEach { hex ->
                  val isSelected = selectedColorHex == hex
                  val color = hex.toColor()
                  Box(
                    modifier = Modifier
                      .size(36.dp)
                      .clip(CircleShape)
                      .background(color)
                      .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape
                      )
                      .clickable { selectedColorHex = hex }
                  )
                }
              }
            }
          }
        }

        // Icon Chooser Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Select Icon", style = MaterialTheme.typography.titleSmall)
          val iconRows = icons.chunked(5)
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            iconRows.forEach { rowIcons ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
              ) {
                rowIcons.forEach { (iconName, imageVector) ->
                  val isSelected = selectedIconName == iconName
                  val themeColor = selectedColorHex.toColor()
                  Box(
                    modifier = Modifier
                      .size(40.dp)
                      .clip(CircleShape)
                      .background(
                        if (isSelected) themeColor.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant
                      )
                      .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) themeColor else Color.Transparent,
                        shape = CircleShape
                      )
                      .clickable { selectedIconName = iconName },
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = imageVector,
                      contentDescription = iconName,
                      tint = if (isSelected) themeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(22.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (name.isNotBlank()) {
            onConfirm(name, selectedIconName, selectedColorHex)
          }
        },
        enabled = name.isNotBlank()
      ) {
        Text(if (category == null) "Add" else "Save")
      }
    },
    dismissButton = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (category != null && onDelete != null) {
          TextButton(
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
          ) {
            Text("Remove")
          }
        }
        TextButton(onClick = onDismiss) {
          Text("Cancel")
        }
      }
    }
  )
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
      onDeleteExpense = {}
    )
  }
}
