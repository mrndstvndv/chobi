package com.example.chobi.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chobi.ui.main.CURRENCY_KEY
import com.example.chobi.ui.main.DYNAMIC_COLOR_KEY
import com.example.chobi.ui.main.MainScreenUiState
import com.example.chobi.ui.main.THEME_MODE_KEY
import com.example.chobi.ui.main.TIME_FORMAT_KEY
import com.example.chobi.ui.main.dataStore
import com.example.chobi.ui.main.rememberMainScreenViewModel
import java.util.Locale
import kotlinx.coroutines.launch

private enum class ImportType { JSON, SQLITE }

private val themeModes = listOf(
  "system" to "System",
  "light" to "Light",
  "dark" to "Dark"
)

private val timeFormats = listOf(
  "auto" to "Auto (system default)",
  "12h" to "12-hour (1:30 PM)",
  "24h" to "24-hour (13:30)"
)

private val commonCurrencies = listOf(
  "USD", "EUR", "GBP", "JPY", "INR", "CAD", "AUD", "CNY", "BDT", "SGD", "PHP"
)

private fun isValidCurrencyCode(code: String): Boolean = try {
  android.icu.util.Currency.getInstance(code) != null
} catch (e: Exception) {
  false
}

private fun currencyName(code: String): String? = try {
  android.icu.util.Currency.getInstance(code).getDisplayName(Locale.getDefault())
} catch (e: Exception) {
  null
}

private fun currencyLabel(code: String): String =
  currencyName(code)?.let { "$code · $it" } ?: code

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val viewModel = rememberMainScreenViewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val success = state as? MainScreenUiState.Success

  val preferences by remember(context) { context.dataStore.data }
    .collectAsStateWithLifecycle(initialValue = null)
  val localeCurrency = remember {
    try {
      android.icu.util.Currency.getInstance(Locale.getDefault()).currencyCode
    } catch (e: Exception) {
      "USD"
    }
  }
  val dynamicColor = preferences?.get(DYNAMIC_COLOR_KEY) ?: false
  val themeMode = preferences?.get(THEME_MODE_KEY) ?: "system"
  val timeFormat = preferences?.get(TIME_FORMAT_KEY) ?: "auto"
  val currencyCode = preferences?.get(CURRENCY_KEY) ?: localeCurrency

  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  fun showMessage(message: String) {
    scope.launch {
      snackbarHostState.currentSnackbarData?.dismiss()
      snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
    }
  }

  fun <T> setPreference(key: Preferences.Key<T>, value: T) {
    scope.launch { context.dataStore.edit { it[key] = value } }
  }

  var showCurrencyDialog by rememberSaveable { mutableStateOf(false) }
  var showTimeFormatDialog by rememberSaveable { mutableStateOf(false) }
  var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
  var pendingImportType by remember { mutableStateOf<ImportType?>(null) }

  val exportJsonLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/json")
  ) { uri ->
    if (uri != null && success != null) {
      viewModel.exportDataToJson(
        context = context,
        uri = uri,
        categories = success.categories,
        expenses = success.expenses,
        budgets = success.budgets,
        onSuccess = { showMessage("Backup exported") },
        onError = { showMessage("Export failed: ${it.message}") }
      )
    }
  }
  val importJsonLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      pendingImportUri = uri
      pendingImportType = ImportType.JSON
    }
  }
  val exportDbLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/octet-stream")
  ) { uri ->
    if (uri != null) {
      viewModel.exportRawDb(
        context = context,
        uri = uri,
        onSuccess = { showMessage("Database file exported") },
        onError = { showMessage("Database export failed: ${it.message}") }
      )
    }
  }
  val importDbLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      pendingImportUri = uri
      pendingImportType = ImportType.SQLITE
    }
  }

  if (showCurrencyDialog) {
    CurrencyDialog(
      current = currencyCode,
      onDismiss = { showCurrencyDialog = false },
      onConfirm = { code ->
        setPreference(CURRENCY_KEY, code)
        showCurrencyDialog = false
      }
    )
  }

  if (showTimeFormatDialog) {
    AlertDialog(
      onDismissRequest = { showTimeFormatDialog = false },
      title = { Text("Time format") },
      text = {
        Column {
          timeFormats.forEach { (key, label) ->
            RadioOption(
              title = label,
              selected = timeFormat == key,
              onClick = {
                setPreference(TIME_FORMAT_KEY, key)
                showTimeFormatDialog = false
              }
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showTimeFormatDialog = false }) { Text("Cancel") }
      }
    )
  }

  val importUri = pendingImportUri
  val importType = pendingImportType
  if (importUri != null && importType != null) {
    ImportDialog(
      type = importType,
      onDismiss = {
        pendingImportUri = null
        pendingImportType = null
      },
      onConfirm = { overwrite ->
        pendingImportUri = null
        pendingImportType = null
        if (importType == ImportType.JSON) {
          viewModel.importDataFromJson(
            context = context,
            uri = importUri,
            overwrite = overwrite,
            onSuccess = { showMessage("Backup imported") },
            onError = { showMessage("Import failed: ${it.message}") }
          )
        } else {
          viewModel.importRawDb(
            context = context,
            uri = importUri,
            onSuccess = { (context as? Activity)?.recreate() },
            onError = { showMessage("Database restore failed: ${it.message}") }
          )
        }
      }
    )
  }

  Scaffold(
    topBar = {
      LargeFlexibleTopAppBar(
        title = { Text("Settings") },
        subtitle = { Text("Appearance, currency and backups") },
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
    snackbarHost = { SnackbarHost(snackbarHostState) },
    containerColor = MaterialTheme.colorScheme.background,
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
  ) { paddingValues ->
    val listPadding = PaddingValues(
      start = 16.dp,
      end = 16.dp,
      top = paddingValues.calculateTopPadding() + 8.dp,
      bottom = paddingValues.calculateBottomPadding() + 24.dp
    )
    val versionName = remember(context) {
      try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
      } catch (e: Exception) {
        null
      }
    }

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .consumeWindowInsets(paddingValues),
      contentPadding = listPadding,
      verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
      item(key = "header_appearance") { SectionHeader("Appearance", first = true) }
      item(key = "palette") {
        ChoiceRow(
          title = "Color palette",
          supporting = if (dynamicColor) {
            "Colors follow your wallpaper"
          } else {
            "Chobi's emerald and gold palette"
          },
          options = listOf("Branded", "Material You"),
          selectedIndex = if (dynamicColor) 1 else 0,
          onSelect = { setPreference(DYNAMIC_COLOR_KEY, it == 1) },
          index = 0,
          count = 2
        )
      }
      item(key = "theme_mode") {
        ChoiceRow(
          title = "Theme",
          supporting = "Light, dark, or follow the system",
          options = themeModes.map { it.second },
          selectedIndex = themeModes.indexOfFirst { it.first == themeMode }.coerceAtLeast(0),
          onSelect = { setPreference(THEME_MODE_KEY, themeModes[it].first) },
          index = 1,
          count = 2
        )
      }

      item(key = "header_preferences") { SectionHeader("Preferences") }
      item(key = "currency") {
        ActionRow(
          title = "Currency",
          supporting = currencyLabel(currencyCode),
          onClick = { showCurrencyDialog = true },
          index = 0,
          count = 2
        )
      }
      item(key = "time_format") {
        ActionRow(
          title = "Time format",
          supporting = timeFormats.firstOrNull { it.first == timeFormat }?.second
            ?: timeFormats.first().second,
          onClick = { showTimeFormatDialog = true },
          index = 1,
          count = 2
        )
      }

      item(key = "header_backup") { SectionHeader("Backup and restore") }
      item(key = "export_json") {
        ActionRow(
          title = "Export backup",
          supporting = "Save categories, transactions and budgets as a JSON file",
          enabled = success != null,
          onClick = { exportJsonLauncher.launch("chobi_backup.json") },
          index = 0,
          count = 4
        )
      }
      item(key = "import_json") {
        ActionRow(
          title = "Import backup",
          supporting = "Merge a JSON backup into your data, or replace it",
          onClick = { importJsonLauncher.launch(arrayOf("application/json")) },
          index = 1,
          count = 4
        )
      }
      item(key = "export_db") {
        ActionRow(
          title = "Export database file",
          supporting = "Raw SQLite copy of everything in the app",
          onClick = { exportDbLauncher.launch("expense_database.db") },
          index = 2,
          count = 4
        )
      }
      item(key = "import_db") {
        ActionRow(
          title = "Restore database file",
          supporting = "Replaces all data and restarts the app",
          destructive = true,
          onClick = { importDbLauncher.launch(arrayOf("*/*")) },
          index = 3,
          count = 4
        )
      }

      if (versionName != null) {
        item(key = "header_about") { SectionHeader("About") }
        item(key = "version") {
          RowSurface(index = 0, count = 1) {
            Text("Version", style = MaterialTheme.typography.titleMedium)
            SupportingText(versionName)
          }
        }
      }
    }
  }
}

@Composable
private fun SectionHeader(text: String, first: Boolean = false) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 16.dp, end = 16.dp, top = if (first) 8.dp else 24.dp, bottom = 8.dp)
  )
}

@Composable
private fun SupportingText(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
    modifier = modifier
  )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rowShapes(index: Int, count: Int) = if (count == 1) {
  // A lone row would otherwise change corners with its pressed state.
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionRow(
  title: String,
  supporting: String,
  onClick: () -> Unit,
  index: Int,
  count: Int,
  enabled: Boolean = true,
  destructive: Boolean = false
) {
  val container = MaterialTheme.colorScheme.surfaceContainer
  val onContainer = MaterialTheme.colorScheme.onSurface
  SegmentedListItem(
    selected = false,
    onClick = onClick,
    enabled = enabled,
    shapes = rowShapes(index, count),
    colors = ListItemDefaults.segmentedColors(
      containerColor = container,
      contentColor = onContainer,
      selectedContainerColor = container,
      selectedContentColor = onContainer,
      disabledContainerColor = container,
      disabledContentColor = onContainer.copy(alpha = 0.38f)
    ),
    supportingContent = {
      Text(text = supporting, style = MaterialTheme.typography.bodySmall)
    },
    content = {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = if (destructive && enabled) MaterialTheme.colorScheme.error else Color.Unspecified
      )
    },
    modifier = Modifier.fillMaxWidth()
  )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChoiceRow(
  title: String,
  supporting: String,
  options: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  index: Int,
  count: Int
) {
  RowSurface(index = index, count = count) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    SupportingText(supporting)
    SingleChoiceSegmentedButtonRow(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 12.dp)
    ) {
      options.forEachIndexed { i, label ->
        SegmentedButton(
          selected = selectedIndex == i,
          onClick = { onSelect(i) },
          shape = SegmentedButtonDefaults.itemShape(i, options.size),
          label = { Text(label) }
        )
      }
    }
  }
}

/** A non-interactive container that shares the corner shapes of [SegmentedListItem] groups. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowSurface(
  index: Int,
  count: Int,
  content: @Composable () -> Unit
) {
  Surface(
    shape = rowShapes(index, count).shape,
    color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
      content()
    }
  }
}

@Composable
private fun RadioOption(
  title: String,
  selected: Boolean,
  onClick: () -> Unit,
  supporting: String? = null
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 4.dp)
  ) {
    RadioButton(selected = selected, onClick = onClick)
    Spacer(Modifier.width(8.dp))
    Column {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      if (supporting != null) {
        Text(
          supporting,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrencyDialog(
  current: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit
) {
  var input by rememberSaveable(current) { mutableStateOf(current) }
  val valid = input.length == 3 && isValidCurrencyCode(input)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Currency") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
          value = input,
          onValueChange = { input = it.uppercase().take(3) },
          label = { Text("ISO 4217 code") },
          singleLine = true,
          isError = input.length == 3 && !valid,
          supportingText = {
            Text(
              when {
                valid -> currencyName(input) ?: input
                input.length == 3 -> "Not a valid currency code"
                else -> "Three letters, e.g. USD"
              }
            )
          },
          modifier = Modifier.fillMaxWidth()
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          commonCurrencies.forEach { code ->
            FilterChip(
              selected = input == code,
              onClick = { input = code },
              label = { Text(code) }
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(input) }, enabled = valid) { Text("Save") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@Composable
private fun ImportDialog(
  type: ImportType,
  onDismiss: () -> Unit,
  onConfirm: (overwrite: Boolean) -> Unit
) {
  var overwrite by rememberSaveable { mutableStateOf(false) }
  val destructive = type == ImportType.SQLITE || overwrite

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = if (type == ImportType.SQLITE) {
      {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error
        )
      }
    } else {
      null
    },
    title = {
      Text(if (type == ImportType.JSON) "Import backup" else "Restore database file?")
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (type == ImportType.JSON) {
          Text(
            "Choose how to bring the backup into your data.",
            style = MaterialTheme.typography.bodyMedium
          )
          RadioOption(
            title = "Merge with existing data",
            supporting = "Keeps current entries and adds any new categories or expenses.",
            selected = !overwrite,
            onClick = { overwrite = false }
          )
          RadioOption(
            title = "Replace existing data",
            supporting = "Deletes all existing categories and expenses first, then loads the backup.",
            selected = overwrite,
            onClick = { overwrite = true }
          )
          if (overwrite) {
            WarningCard("Replacing deletes all current expenses and categories. This can't be undone.")
          }
        } else {
          Text(
            "This replaces the current database file with the one you picked.",
            style = MaterialTheme.typography.bodyMedium
          )
          WarningCard("All current data is erased immediately and the app restarts. This can't be undone.")
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(overwrite) },
        colors = if (destructive) {
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
          )
        } else {
          ButtonDefaults.buttonColors()
        }
      ) {
        Text(
          when {
            type == ImportType.SQLITE -> "Restore and restart"
            overwrite -> "Replace and import"
            else -> "Import"
          }
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@Composable
private fun WarningCard(text: String) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = Icons.Default.Warning,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onErrorContainer
      )
      Spacer(Modifier.width(8.dp))
      Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer
      )
    }
  }
}
