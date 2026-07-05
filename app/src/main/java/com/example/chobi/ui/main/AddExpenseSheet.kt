package com.example.chobi.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.chobi.data.Category
import com.example.chobi.data.CategoryIcons
import com.example.chobi.data.Expense
import com.example.chobi.ui.components.CategoryDialog
import com.example.chobi.ui.components.toColor
import com.example.chobi.ui.components.PillSegmentedControl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
  categories: List<Category>,
  onAddExpense: (String, Double, String, Long) -> Unit,
  onAddCategory: (String, String, String) -> Unit,
  onUpdateCategory: (Category, String) -> Unit,
  onDeleteCategory: (Category) -> Unit,
  onDismiss: () -> Unit,
  currencyCode: String = "USD",
  modifier: Modifier = Modifier,
  expenseToEdit: Expense? = null,
  onUpdateExpense: ((Expense) -> Unit)? = null
) {
  var title by remember(expenseToEdit) { mutableStateOf(expenseToEdit?.title ?: "") }
  var amountStr by remember(expenseToEdit) {
    mutableStateOf(
      expenseToEdit?.amount?.let {
        val absAmt = kotlin.math.abs(it)
        if (absAmt % 1.0 == 0.0) absAmt.toInt().toString() else absAmt.toString()
      } ?: ""
    )
  }
  var isIncome by remember(expenseToEdit) {
    mutableStateOf(expenseToEdit?.let { it.amount < 0 } ?: false)
  }
  var selectedCategoryName by remember(expenseToEdit, categories) {
    mutableStateOf(expenseToEdit?.category ?: categories.firstOrNull()?.name ?: "Food")
  }
  var showAddCategoryDialog by remember { mutableStateOf(false) }
  var editingCategory by remember { mutableStateOf<Category?>(null) }

  var selectedTimestamp by remember(expenseToEdit) { mutableStateOf(expenseToEdit?.timestamp ?: System.currentTimeMillis()) }
  var showDatePicker by remember { mutableStateOf(false) }
  val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

  val systemCurrency = remember(currencyCode) {
    try {
      android.icu.util.Currency.getInstance(currencyCode)
    } catch (e: Exception) {
      try {
        android.icu.util.Currency.getInstance(Locale.getDefault())
      } catch (ex: Exception) {
        android.icu.util.Currency.getInstance("USD")
      }
    }
  }
  val currencySymbol = remember(systemCurrency) {
    systemCurrency.getSymbol(Locale.getDefault()) ?: "$"
  }

  val focusManager = LocalFocusManager.current
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) {
    kotlinx.coroutines.delay(250)
    focusRequester.requestFocus()
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
    TextField(
      value = title,
      onValueChange = { title = it },
      textStyle = MaterialTheme.typography.headlineMedium.copy(
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
      ),
      placeholder = {
        Text(
          text = if (expenseToEdit != null) "Edit Transaction" else "New Transaction",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
      },
      colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent
      ),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 0.dp)
        .focusRequester(focusRequester),
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
      keyboardActions = KeyboardActions(
        onNext = {
          focusManager.moveFocus(FocusDirection.Down)
        }
      )
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

    PillSegmentedControl(
      options = listOf("-", "+"),
      selectedOption = if (isIncome) "+" else "-",
      onOptionSelected = { option ->
        isIncome = option == "+"
      },
      showCheckmark = false,
      textStyle = MaterialTheme.typography.titleLarge.copy(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
      ),
      modifier = Modifier.fillMaxWidth()
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

        val scale by animateFloatAsState(
          targetValue = if (selected) 1.06f else 1.0f,
          animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 500f
          ),
          label = "chip_scale"
        )

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = containerColor,
          contentColor = contentColor,
          border = border,
          modifier = Modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .combinedClickable(
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
        label = { Text("Add Category") },
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
            val finalAmt = if (isIncome) -amt else amt
            val exp = expenseToEdit
            if (exp != null) {
              onUpdateExpense?.invoke(exp.copy(title = title, amount = finalAmt, category = selectedCategoryName, timestamp = selectedTimestamp))
            } else {
              onAddExpense(title, finalAmt, selectedCategoryName, selectedTimestamp)
            }
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
