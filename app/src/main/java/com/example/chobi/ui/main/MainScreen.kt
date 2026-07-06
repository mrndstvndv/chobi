package com.example.chobi.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.chobi.ChobiApplication
import com.example.chobi.data.Expense
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

enum class ImportType {
  JSON, SQLITE
}

// Helper to convert hex string to Compose Color
fun String.toColor(): Color {
    return try {
        Color(android.graphics.Color.parseColor(this))
    } catch (e: Exception) {
        Color.Gray
    }
}

fun isValidCurrencyCode(code: String): Boolean {
    return try {
        android.icu.util.Currency.getInstance(code) != null
    } catch (e: Exception) {
        false
    }
}

val android.content.Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chobi_settings")
val CURRENCY_KEY = stringPreferencesKey("currency_code")
val TIME_FORMAT_KEY = stringPreferencesKey("time_format")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  
  val currencyFlow = remember(context) {
    context.dataStore.data.map { preferences ->
      preferences[CURRENCY_KEY] ?: try {
        android.icu.util.Currency.getInstance(Locale.getDefault()).currencyCode
      } catch (e: Exception) {
        "USD"
      }
    }
  }
  val selectedCurrencyCode by currencyFlow.collectAsStateWithLifecycle(initialValue = "USD")

  val timeFormatFlow = remember(context) {
    context.dataStore.data.map { preferences ->
      preferences[TIME_FORMAT_KEY] ?: "auto"
    }
  }
  val selectedTimeFormat by timeFormatFlow.collectAsStateWithLifecycle(initialValue = "auto")

  val app = context.applicationContext as ChobiApplication
  val viewModel: MainScreenViewModel = viewModel {
    MainScreenViewModel(app.expenseRepository)
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showBottomSheet by remember { mutableStateOf(false) }
  var showSettingsDialog by remember { mutableStateOf(false) }
  var expenseToEdit by remember { mutableStateOf<Expense?>(null) }

  // Dialog state for importing
  var showImportConfirmDialog by remember { mutableStateOf(false) }
  var selectedImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
  var importType by remember { mutableStateOf<ImportType?>(null) }
  var importOverwrite by remember { mutableStateOf(false) }

  val successState = state as? MainScreenUiState.Success
  val currentCategories = successState?.categories ?: emptyList()
  val currentExpenses = successState?.expenses ?: emptyList()
  val currentBudgets = successState?.budgets ?: emptyList()

  val exportJsonLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json")
  ) { uri ->
    if (uri != null) {
      viewModel.exportDataToJson(
        context = context,
        uri = uri,
        categories = currentCategories,
        expenses = currentExpenses,
        budgets = currentBudgets,
        onSuccess = {
          Toast.makeText(context, "Data exported successfully", Toast.LENGTH_SHORT).show()
        },
        onError = { error ->
          Toast.makeText(context, "Export failed: ${error.message}", Toast.LENGTH_LONG).show()
        }
      )
    }
  }

  val importJsonLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      selectedImportUri = uri
      importType = ImportType.JSON
      importOverwrite = false
      showImportConfirmDialog = true
    }
  }

  val exportDbLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/octet-stream")
  ) { uri ->
    if (uri != null) {
      viewModel.exportRawDb(
        context = context,
        uri = uri,
        onSuccess = {
          Toast.makeText(context, "Database exported successfully", Toast.LENGTH_SHORT).show()
        },
        onError = { error ->
          Toast.makeText(context, "Database export failed: ${error.message}", Toast.LENGTH_LONG).show()
        }
      )
    }
  }

  val importDbLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      selectedImportUri = uri
      importType = ImportType.SQLITE
      showImportConfirmDialog = true
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Chobi", style = MaterialTheme.typography.titleLarge) },
        actions = {
          IconButton(onClick = { showSettingsDialog = true }) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = "Settings & Backup"
            )
          }
        },
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
        val success = state as MainScreenUiState.Success
        MainContent(
          expenses = success.expenses,
          categories = success.categories,
          budgets = success.budgets,
          onDeleteExpense = { expense ->
            viewModel.deleteExpense(expense)
          },
          onCreateBudget = { title, limit ->
            viewModel.createNewBudget(title, limit)
          },
          onDeleteBudget = { budget ->
            viewModel.deleteBudget(budget)
          },
          onExpenseClick = { expense ->
            expenseToEdit = expense
            showBottomSheet = true
          },
          currencyCode = selectedCurrencyCode,
          timeFormatPreference = selectedTimeFormat,
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        )

        if (showBottomSheet) {
          ModalBottomSheet(
            onDismissRequest = {
              expenseToEdit = null
              showBottomSheet = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
          ) {
            AddExpenseSheet(
              categories = success.categories,
              expenseToEdit = expenseToEdit,
              onAddExpense = { title, amount, categoryName, timestamp ->
                viewModel.addExpense(title, amount, categoryName, timestamp)
                showBottomSheet = false
              },
              onUpdateExpense = { updatedExpense ->
                viewModel.updateExpense(updatedExpense)
                expenseToEdit = null
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
              onDismiss = {
                expenseToEdit = null
                showBottomSheet = false
              },
              currencyCode = selectedCurrencyCode
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

  // Settings Dialog
  if (showSettingsDialog) {
    AlertDialog(
      onDismissRequest = { showSettingsDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
          )
          Spacer(modifier = Modifier.width(12.dp))
          Text("Settings & Database")
        }
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Text(
            text = "Backup, restore, or transfer your categories and expenses data.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          
          HorizontalDivider()

          // CURRENCY SECTION
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "Currency Preference",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "Choose your preferred currency for expense tracking.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            var currencyInput by remember(selectedCurrencyCode) { mutableStateOf(selectedCurrencyCode) }
            var isError by remember { mutableStateOf(false) }
            var expandedCurrencyDropdown by remember { mutableStateOf(false) }
            
            val commonCurrencies = listOf(
              "USD" to "USD ($)",
              "EUR" to "EUR (€)",
              "GBP" to "GBP (£)",
              "JPY" to "JPY (¥)",
              "INR" to "INR (₹)",
              "CAD" to "CAD ($)",
              "AUD" to "AUD ($)",
              "CNY" to "CNY (¥)",
              "BDT" to "BDT (৳)",
              "SGD" to "SGD ($)",
              "PHP" to "PHP (₱)"
            )
            
            Box(modifier = Modifier.fillMaxWidth()) {
              OutlinedTextField(
                value = currencyInput,
                onValueChange = { newValue ->
                  val upperValue = newValue.uppercase().take(3)
                  currencyInput = upperValue
                  if (upperValue.length == 3 && isValidCurrencyCode(upperValue)) {
                    isError = false
                    coroutineScope.launch {
                      context.dataStore.edit { preferences ->
                        preferences[CURRENCY_KEY] = upperValue
                      }
                    }
                  } else {
                    isError = true
                  }
                },
                label = { Text("Currency Code (e.g. USD)") },
                trailingIcon = {
                  IconButton(onClick = { expandedCurrencyDropdown = !expandedCurrencyDropdown }) {
                    Icon(
                      imageVector = Icons.Default.ArrowDropDown,
                      contentDescription = "Select Currency"
                    )
                  }
                },
                isError = isError,
                supportingText = {
                  if (isError) {
                    Text("Invalid ISO 4217 code", color = MaterialTheme.colorScheme.error)
                  }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
              )
              
              DropdownMenu(
                expanded = expandedCurrencyDropdown,
                onDismissRequest = { expandedCurrencyDropdown = false },
                modifier = Modifier.fillMaxWidth(0.7f)
              ) {
                commonCurrencies.forEach { (code, displayName) ->
                  DropdownMenuItem(
                    text = { Text(displayName) },
                    onClick = {
                      currencyInput = code
                      isError = false
                      coroutineScope.launch {
                        context.dataStore.edit { preferences ->
                          preferences[CURRENCY_KEY] = code
                        }
                      }
                      expandedCurrencyDropdown = false
                    }
                  )
                }
              }
            }
          }

          HorizontalDivider()

          // TIME FORMAT SECTION
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "Time Format Preference",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "Choose whether to display 12-hour time, 24-hour time, or use system defaults.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            var expandedTimeDropdown by remember { mutableStateOf(false) }
            val timeOptions = listOf(
              "auto" to "Auto (System default)",
              "12h" to "12-hour format (e.g., 1:30 PM)",
              "24h" to "24-hour format (e.g., 13:30)"
            )
            
            val selectedOptionName = remember(selectedTimeFormat) {
              timeOptions.firstOrNull { it.first == selectedTimeFormat }?.second ?: "Auto (System default)"
            }
            
            Box(modifier = Modifier.fillMaxWidth()) {
              OutlinedTextField(
                value = selectedOptionName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Time Format") },
                trailingIcon = {
                  IconButton(onClick = { expandedTimeDropdown = !expandedTimeDropdown }) {
                    Icon(
                      imageVector = Icons.Default.ArrowDropDown,
                      contentDescription = "Select Time Format"
                    )
                  }
                },
                modifier = Modifier.fillMaxWidth()
              )
              
              Box(
                modifier = Modifier
                  .matchParentSize()
                  .clickable { expandedTimeDropdown = !expandedTimeDropdown }
              )
              
              DropdownMenu(
                expanded = expandedTimeDropdown,
                onDismissRequest = { expandedTimeDropdown = false },
                modifier = Modifier.fillMaxWidth(0.7f)
              ) {
                timeOptions.forEach { (formatCode, displayName) ->
                  DropdownMenuItem(
                    text = { Text(displayName) },
                    onClick = {
                      coroutineScope.launch {
                        context.dataStore.edit { preferences ->
                          preferences[TIME_FORMAT_KEY] = formatCode
                        }
                      }
                      expandedTimeDropdown = false
                    }
                  )
                }
              }
            }
          }

          HorizontalDivider()

          // JSON BACKUP SECTION
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "Data Backup (JSON)",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "Safe format. Exports categories (including custom ones) and expenses. Allows merging or overwriting.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Button(
                onClick = {
                  showSettingsDialog = false
                  exportJsonLauncher.launch("chobi_backup.json")
                },
                modifier = Modifier.weight(1f)
              ) {
                Text("Export JSON")
              }
              OutlinedButton(
                onClick = {
                  showSettingsDialog = false
                  importJsonLauncher.launch(arrayOf("application/json"))
                },
                modifier = Modifier.weight(1f)
              ) {
                Text("Import JSON")
              }
            }
          }

          HorizontalDivider()

          // SQLITE DB SECTION
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "Database File (SQLite)",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "Extracts/restores raw database file. Importing replaces all data and closes/restarts the application.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Button(
                onClick = {
                  showSettingsDialog = false
                  exportDbLauncher.launch("expense_database.db")
                },
                modifier = Modifier.weight(1f)
              ) {
                Text("Export DB")
              }
              OutlinedButton(
                onClick = {
                  showSettingsDialog = false
                  importDbLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier.weight(1f)
              ) {
                Text("Import DB")
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showSettingsDialog = false }) {
          Text("Close")
        }
      }
    )
  }

  // Import Confirmation Dialog
  if (showImportConfirmDialog && selectedImportUri != null) {
    val uri = selectedImportUri!!
    val type = importType!!
    
    AlertDialog(
      onDismissRequest = {
        showImportConfirmDialog = false
        selectedImportUri = null
        importType = null
      },
      title = {
        Text(
          text = if (type == ImportType.JSON) "Import Backup Options" else "Confirm DB Restore"
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          if (type == ImportType.JSON) {
            Text(
              text = "Select how you would like to import the categories and expenses from the JSON file:",
              style = MaterialTheme.typography.bodyMedium
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { importOverwrite = false }
                  .padding(vertical = 4.dp)
              ) {
                RadioButton(
                  selected = !importOverwrite,
                  onClick = { importOverwrite = false }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text("Merge with existing data", style = MaterialTheme.typography.bodyLarge)
                  Text("Keeps current entries and adds any new categories or expenses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
              
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { importOverwrite = true }
                  .padding(vertical = 4.dp)
              ) {
                RadioButton(
                  selected = importOverwrite,
                  onClick = { importOverwrite = true }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text("Overwrite / Replace data", style = MaterialTheme.typography.bodyLarge)
                  Text("Deletes all existing categories and expenses first, then loads the backup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
            
            if (importOverwrite) {
              Spacer(modifier = Modifier.height(8.dp))
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Warning: Overwriting will delete all current expenses and categories. This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                  )
                }
              }
            }
          } else {
            Text(
              text = "You are restoring a raw database file. This will completely replace the current database files on disk.",
              style = MaterialTheme.typography.bodyMedium
            )
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Warning,
                  contentDescription = "Warning",
                  tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Crucial: All current data will be erased immediately. The application will close to apply the backup.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer
                )
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showImportConfirmDialog = false
            if (type == ImportType.JSON) {
              viewModel.importDataFromJson(
                context = context,
                uri = uri,
                overwrite = importOverwrite,
                onSuccess = {
                  Toast.makeText(context, "Data imported successfully", Toast.LENGTH_SHORT).show()
                  selectedImportUri = null
                  importType = null
                },
                onError = { error ->
                  Toast.makeText(context, "Import failed: ${error.message}", Toast.LENGTH_LONG).show()
                  selectedImportUri = null
                  importType = null
                }
              )
            } else {
              viewModel.importRawDb(
                context = context,
                uri = uri,
                onSuccess = {
                  Toast.makeText(context, "Database restored. Restarting...", Toast.LENGTH_SHORT).show()
                  android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    android.os.Process.killProcess(android.os.Process.myPid())
                  }, 1500)
                },
                onError = { error ->
                  Toast.makeText(context, "Database restore failed: ${error.message}", Toast.LENGTH_LONG).show()
                  selectedImportUri = null
                  importType = null
                }
              )
            }
          },
          colors = if (importOverwrite || type == ImportType.SQLITE) {
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
          } else {
            ButtonDefaults.buttonColors()
          }
        ) {
          Text(
            text = if (type == ImportType.JSON) {
              if (importOverwrite) "Overwrite & Import" else "Import (Merge)"
            } else {
              "Restore & Restart"
            }
          )
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            showImportConfirmDialog = false
            selectedImportUri = null
            importType = null
          }
        ) {
          Text("Cancel")
        }
      }
    )
  }
}
