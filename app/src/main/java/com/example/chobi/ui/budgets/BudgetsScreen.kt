package com.example.chobi.ui.budgets

import android.icu.text.NumberFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chobi.data.Budget
import com.example.chobi.data.Expense
import com.example.chobi.ui.components.BudgetDialog
import com.example.chobi.ui.main.CURRENCY_KEY
import com.example.chobi.ui.main.MainScreenUiState
import com.example.chobi.ui.main.dataStore
import com.example.chobi.ui.main.rememberMainScreenViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private enum class BudgetFilter(val label: String) {
  Active("Active"),
  Archived("Archived")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BudgetsScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val viewModel = rememberMainScreenViewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  val currencyFlow = remember(context) {
    context.dataStore.data.map { preferences ->
      preferences[CURRENCY_KEY] ?: try {
        android.icu.util.Currency.getInstance(Locale.getDefault()).currencyCode
      } catch (e: Exception) {
        "USD"
      }
    }
  }
  val currencyCode by currencyFlow.collectAsStateWithLifecycle(initialValue = "USD")
  val currencyFormatter = remember(currencyCode) {
    try {
      NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        currency = android.icu.util.Currency.getInstance(currencyCode)
      }
    } catch (e: Exception) {
      NumberFormat.getCurrencyInstance(Locale.getDefault())
    }
  }

  var filter by rememberSaveable { mutableStateOf(BudgetFilter.Active) }
  var showCreateDialog by remember { mutableStateOf(false) }
  var budgetToDelete by remember { mutableStateOf<Budget?>(null) }

  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  val success = state as? MainScreenUiState.Success
  val activeBudgets = remember(success?.budgets) { success?.budgets.orEmpty().filterNot { it.archived } }
  val archivedBudgets = remember(success?.budgets) { success?.budgets.orEmpty().filter { it.archived } }
  val spentByBudget = remember(success?.expenses) {
    success?.expenses.orEmpty()
      .filter { it.budgetId != null }
      .groupBy { it.budgetId }
      .mapValues { (_, list) -> list.sumOf(Expense::amount) }
  }
  val countByBudget = remember(success?.expenses) {
    success?.expenses.orEmpty().groupingBy { it.budgetId }.eachCount()
  }

  if (showCreateDialog) {
    BudgetDialog(
      onDismiss = { showCreateDialog = false },
      onConfirm = { title, limit ->
        viewModel.createNewBudget(title, limit)
        filter = BudgetFilter.Active
        showCreateDialog = false
      }
    )
  }

  budgetToDelete?.let { budget ->
    val transactionCount = countByBudget[budget.id] ?: 0
    AlertDialog(
      onDismissRequest = { budgetToDelete = null },
      icon = {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error
        )
      },
      title = { Text("Delete budget?") },
      text = {
        Text(
          buildString {
            append("\"${budget.title}\" will be deleted")
            if (transactionCount > 0) {
              append(" along with its $transactionCount ")
              append(if (transactionCount == 1) "transaction" else "transactions")
            }
            append(". This can't be undone.")
            if (!budget.archived) append(" Archive it instead to just hide it.")
          }
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteBudget(budget)
            budgetToDelete = null
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
          )
        ) { Text("Delete") }
      },
      dismissButton = {
        TextButton(onClick = { budgetToDelete = null }) { Text("Cancel") }
      }
    )
  }

  Scaffold(
    topBar = {
      LargeFlexibleTopAppBar(
        title = { Text("Budgets") },
        subtitle = {
          if (success != null) {
            Text("${activeBudgets.size} active · ${archivedBudgets.size} archived")
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
          scrolledContainerColor = MaterialTheme.colorScheme.background,
          titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        scrollBehavior = scrollBehavior
      )
    },
    floatingActionButton = {
      if (success != null) {
        ExtendedFloatingActionButton(
          onClick = { showCreateDialog = true },
          icon = { Icon(Icons.Default.Add, contentDescription = null) },
          text = { Text("New budget") }
        )
      }
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    containerColor = MaterialTheme.colorScheme.background,
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
  ) { paddingValues ->
    if (success == null) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        if (state is MainScreenUiState.Error) {
          Text(
            "Error loading budgets: ${(state as MainScreenUiState.Error).throwable.message}",
            color = MaterialTheme.colorScheme.error
          )
        } else {
          CircularProgressIndicator()
        }
      }
      return@Scaffold
    }

    val shown = if (filter == BudgetFilter.Active) activeBudgets else archivedBudgets
    // The FAB is ~56dp plus margins; extra bottom space keeps the last row reachable.
    val listPadding = PaddingValues(
      start = 16.dp,
      end = 16.dp,
      top = paddingValues.calculateTopPadding() + 8.dp,
      bottom = paddingValues.calculateBottomPadding() + 96.dp
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .consumeWindowInsets(paddingValues),
      contentPadding = listPadding,
      verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
      item(key = "filter") {
        SingleChoiceSegmentedButtonRow(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
        ) {
          BudgetFilter.entries.forEachIndexed { index, option ->
            SegmentedButton(
              selected = filter == option,
              onClick = { filter = option },
              shape = SegmentedButtonDefaults.itemShape(index, BudgetFilter.entries.size),
              label = { Text(option.label) }
            )
          }
        }
      }

      if (filter == BudgetFilter.Active) {
        item(key = "all_expenses") {
          BudgetRow(
            title = "All expenses",
            supporting = "Everything, regardless of budget",
            selected = success.selectedBudget == null,
            index = 0,
            count = 1,
            onClick = {
              viewModel.selectBudget(null)
              onBack()
            },
            modifier = Modifier.padding(bottom = 16.dp)
          )
        }
      }

      if (shown.isEmpty()) {
        item(key = "empty") {
          EmptyState(archived = filter == BudgetFilter.Archived)
        }
      }

      itemsIndexed(shown, key = { _, budget -> budget.id }) { index, budget ->
        val spent = spentByBudget[budget.id] ?: 0.0
        BudgetRow(
          title = budget.title,
          supporting = dateRange(budget),
          selected = !budget.archived && success.selectedBudget?.id == budget.id,
          isCurrent = budget.endTimestamp == null && !budget.archived,
          spent = spent,
          limit = budget.limitAmount,
          currencyFormatter = currencyFormatter,
          index = index,
          count = shown.size,
          onClick = if (budget.archived) null else {
            {
              viewModel.selectBudget(budget)
              onBack()
            }
          },
          trailing = {
            BudgetActions(
              budget = budget,
              onArchive = {
                viewModel.setBudgetArchived(budget, true)
                scope.launch {
                  snackbarHostState.currentSnackbarData?.dismiss()
                  val result = snackbarHostState.showSnackbar(
                    message = "\"${budget.title}\" archived",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                  )
                  if (result == SnackbarResult.ActionPerformed) {
                    viewModel.setBudgetArchived(budget, false)
                  }
                }
              },
              onRestore = { viewModel.setBudgetArchived(budget, false) },
              onDelete = { budgetToDelete = budget }
            )
          },
          modifier = Modifier.animateItem()
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BudgetRow(
  title: String,
  supporting: String,
  selected: Boolean,
  index: Int,
  count: Int,
  onClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
  isCurrent: Boolean = false,
  spent: Double? = null,
  limit: Double = 0.0,
  currencyFormatter: NumberFormat? = null,
  trailing: (@Composable () -> Unit)? = null
) {
  val container = if (selected) {
    MaterialTheme.colorScheme.primaryContainer
  } else {
    MaterialTheme.colorScheme.surfaceContainer
  }
  val onContainer = if (selected) {
    MaterialTheme.colorScheme.onPrimaryContainer
  } else {
    MaterialTheme.colorScheme.onSurface
  }

  SegmentedListItem(
    selected = selected,
    onClick = { onClick?.invoke() },
    enabled = onClick != null,
    shapes = if (count == 1) {
      // A lone row would otherwise change corners with its selected/pressed state.
      val round = RoundedCornerShape(16.dp)
      ListItemDefaults.shapes(
        shape = round,
        pressedShape = round,
        focusedShape = round,
        hoveredShape = round,
        draggedShape = round,
        selectedShape = round
      )
    } else {
      ListItemDefaults.segmentedShapes(index, count)
    },
    colors = ListItemDefaults.segmentedColors(
      containerColor = container,
      contentColor = onContainer,
      selectedContainerColor = container,
      selectedContentColor = onContainer,
      disabledContainerColor = container,
      disabledContentColor = onContainer
    ),
    supportingContent = {
      Column {
        Text(
          text = supporting,
          style = MaterialTheme.typography.bodySmall,
          color = onContainer.copy(alpha = 0.75f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (spent != null && currencyFormatter != null) {
          val ratio = if (limit > 0) spent / limit else 0.0
          Spacer(Modifier.height(8.dp))
          LinearProgressIndicator(
            progress = { ratio.coerceIn(0.0, 1.0).toFloat() },
            color = progressColor(ratio),
            trackColor = onContainer.copy(alpha = 0.15f),
            modifier = Modifier
              .fillMaxWidth()
              .height(8.dp)
              .clip(RoundedCornerShape(4.dp))
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = "${currencyFormatter.format(spent)} of ${currencyFormatter.format(limit)}",
            style = MaterialTheme.typography.labelMedium,
            color = onContainer.copy(alpha = 0.75f)
          )
        }
      }
    },
    trailingContent = trailing,
    content = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false)
        )
        if (isCurrent) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(start = 8.dp)
          ) {
            Text(
              text = "Current",
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
          }
        }
      }
    },
    modifier = modifier.fillMaxWidth()
  )
}

@Composable
private fun BudgetActions(
  budget: Budget,
  onArchive: () -> Unit,
  onRestore: () -> Unit,
  onDelete: () -> Unit
) {
  if (budget.archived) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = onRestore) {
        Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("Restore", modifier = Modifier.padding(start = 8.dp))
      }
      IconButton(onClick = onDelete) {
        Icon(
          Icons.Default.Delete,
          contentDescription = "Delete ${budget.title}",
          tint = MaterialTheme.colorScheme.error
        )
      }
    }
  } else {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
      IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = "Actions for ${budget.title}")
      }
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
          text = { Text("Archive") },
          leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
          onClick = {
            expanded = false
            onArchive()
          }
        )
        DropdownMenuItem(
          text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
          leadingIcon = {
            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
          },
          onClick = {
            expanded = false
            onDelete()
          }
        )
      }
    }
  }
}

@Composable
private fun EmptyState(archived: Boolean) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 48.dp, horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text(
      text = if (archived) "Nothing archived" else "No budgets yet",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      text = if (archived) {
        "Archived budgets are hidden from the home screen. Archive one from its menu and it will show up here."
      } else {
        "Create a budget to track spending against a limit."
      },
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun progressColor(ratio: Double): Color = when {
  ratio < 0.70 -> MaterialTheme.colorScheme.primary
  ratio < 0.90 -> Color(0xFFF57C00)
  else -> Color(0xFFD32F2F)
}

private fun dateRange(budget: Budget): String {
  val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
  val start = format.format(Date(budget.startTimestamp))
  val end = budget.endTimestamp?.let { format.format(Date(it)) } ?: "Present"
  return "$start – $end"
}
