package com.example.chobi.ui.dashboard

import android.icu.text.NumberFormat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.example.chobi.ChobiApplication
import com.example.chobi.data.Budget
import com.example.chobi.data.Category
import com.example.chobi.data.Expense
import com.example.chobi.data.ExpenseRepository
import com.example.chobi.ui.components.OdometerText
import com.example.chobi.ui.components.toColor
import com.example.chobi.ui.main.CURRENCY_KEY
import com.example.chobi.ui.main.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Error(val throwable: Throwable) : DashboardUiState
    data class Success(
        val budget: Budget?,
        val expenses: List<Expense>,
        val categories: List<Category>
    ) : DashboardUiState
}

class DashboardViewModel(
    private val expenseRepository: ExpenseRepository,
    val budgetId: Long?
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> =
        combine(
            expenseRepository.getAllExpenses(),
            expenseRepository.getAllCategories(),
            expenseRepository.getAllBudgets()
        ) { expenses, categories, budgets ->
            val budget = if (budgetId != null) budgets.firstOrNull { it.id == budgetId } else null
            val filteredExpenses = if (budgetId != null) {
                expenses.filter { it.budgetId == budgetId }
            } else {
                expenses
            }
            DashboardUiState.Success(
                budget = budget,
                expenses = filteredExpenses,
                categories = categories
            ) as DashboardUiState
        }
            .flowOn(Dispatchers.Default)
            .catch { emit(DashboardUiState.Error(it)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState.Loading)

    fun updateBudgetTitle(title: String) {
        val state = uiState.value as? DashboardUiState.Success ?: return
        val budget = state.budget ?: return
        viewModelScope.launch {
            expenseRepository.updateBudget(budget.copy(title = title))
        }
    }

    fun deleteBudget(onDeleted: () -> Unit) {
        val state = uiState.value as? DashboardUiState.Success ?: return
        val budget = state.budget ?: return
        viewModelScope.launch {
            expenseRepository.deleteBudget(budget)
            onDeleted()
        }
    }
}

data class CategorySpend(
    val name: String,
    val amount: Double,
    val color: Color,
    val percentage: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    budgetId: Long?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as ChobiApplication
    val viewModel: DashboardViewModel = viewModel(key = budgetId?.toString() ?: "all") {
        DashboardViewModel(app.expenseRepository, budgetId)
    }

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
    val selectedCurrencyCode by currencyFlow.collectAsStateWithLifecycle(initialValue = "USD")

    val currencyFormatter = remember(selectedCurrencyCode) {
        try {
            NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
                currency = android.icu.util.Currency.getInstance(selectedCurrencyCode)
            }
        } catch (e: Exception) {
            NumberFormat.getCurrencyInstance(Locale.getDefault())
        }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = when (val s = state) {
                        is DashboardUiState.Success -> {
                            val budget = s.budget
                            if (budget != null) "${budget.title} Dashboard" else "All Expenses Dashboard"
                        }
                        else -> {
                            if (budgetId != null) "Budget Dashboard" else "All Expenses Dashboard"
                        }
                    }
                    Text(titleText)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (state) {
                DashboardUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is DashboardUiState.Error -> {
                    val error = state as DashboardUiState.Error
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Error: ${error.throwable.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is DashboardUiState.Success -> {
                    val success = state as DashboardUiState.Success
                    val budget = success.budget
                    val expenses = success.expenses
                    val categories = success.categories
 
                    // Computations
                    val netSpending = expenses.sumOf { it.amount }
                    val positiveSpending = expenses
                        .filter { it.amount > 0.0 }
                        .sumOf { it.amount }
                    
                    val categoryMap = remember(categories) {
                        categories.associateBy { it.name.lowercase() }
                    }

                    val categorySpending = remember(expenses, categories, positiveSpending) {
                        expenses
                            .groupBy { it.category }
                            .map { (catName, list) ->
                                val amount = list
                                    .filter { it.amount > 0.0 }
                                    .sumOf { it.amount }
                                val color = categoryMap[catName.lowercase()]?.colorHex?.toColor() ?: Color.Gray
                                val pct = if (positiveSpending > 0.0 && amount > 0.0) {
                                    amount / positiveSpending
                                } else {
                                    0.0
                                }
                                CategorySpend(catName, amount, color, pct)
                            }
                            .filter { it.amount > 0.0 }
                            .sortedByDescending { it.amount }
                    }

                    // Days computation for Daily Average
                    val days = remember(expenses, budget) {
                        val start = minOf(budget?.startTimestamp ?: System.currentTimeMillis(), expenses.minOfOrNull { it.timestamp } ?: System.currentTimeMillis())
                        val end = budget?.endTimestamp ?: System.currentTimeMillis()
                        val diffMs = end - start
                        (diffMs.toDouble() / (1000.0 * 60.0 * 60.0 * 24.0)).coerceAtLeast(1.0)
                    }
                    val dailyAvg = if (days > 0.0) positiveSpending / days else 0.0

                    val largestPurchase = remember(expenses) {
                        expenses.filter { it.amount > 0 }.maxByOrNull { it.amount }
                    }
                    val smallestPurchase = remember(expenses) {
                        expenses.filter { it.amount > 0 }.minByOrNull { it.amount }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Budget Name Edit and Delete Actions
                        if (budget != null) {
                            BudgetNameEditHeader(
                                budget = budget,
                                onSaveTitle = { viewModel.updateBudgetTitle(it) },
                                onDeleteClick = { showDeleteConfirm = true }
                            )
                        }

                        // 2. Overview Balance Card
                        OverviewCard(
                            budget = budget,
                            totalSpent = netSpending,
                            currencyFormatter = currencyFormatter
                        )

                        // 3. Category Distribution Donut Chart & Legends
                        CategoryBreakdownCard(
                            categorySpending = categorySpending,
                            totalSpending = positiveSpending,
                            currencyFormatter = currencyFormatter
                        )

                        // 4. Key Metrics Grid
                        KeyMetricsSection(
                            dailyAvg = dailyAvg,
                            transactionCount = expenses.size,
                            largestPurchase = largestPurchase,
                            smallestPurchase = smallestPurchase,
                            currencyFormatter = currencyFormatter
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    if (showDeleteConfirm && budget != null) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            title = { Text("Delete Budget?") },
                            text = {
                                Text("Are you sure you want to delete \"${budget.title}\"? This will also delete all of its associated expenses. This action cannot be undone.")
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showDeleteConfirm = false
                                        viewModel.deleteBudget(onDeleted = onBack)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Delete")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetNameEditHeader(
    budget: Budget,
    onSaveTitle: (String) -> Unit,
    onDeleteClick: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var titleInput by remember(budget.title) { mutableStateOf(budget.title) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isEditing) {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Budget Title") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (titleInput.isNotBlank()) {
                            onSaveTitle(titleInput)
                            isEditing = false
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save Title",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = {
                    titleInput = budget.title
                    isEditing = false
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Editing",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = budget.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = { isEditing = true }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Title",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Budget",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun OverviewCard(
    budget: Budget?,
    totalSpent: Double,
    currencyFormatter: NumberFormat
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
        )
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .background(gradient)
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            if (budget != null) {
                val limit = budget.limitAmount
                val remaining = limit - totalSpent
                val ratio = if (limit > 0) totalSpent / limit else 0.0
                val progress = ratio.coerceIn(0.0..1.0).toFloat()

                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(1000, easing = FastOutSlowInEasing),
                    label = "budgetProgress"
                )

                val barColor by animateColorAsState(
                    targetValue = when {
                        ratio < 0.70 -> MaterialTheme.colorScheme.primary
                        ratio < 0.90 -> Color(0xFFF57C00)
                        else -> Color(0xFFD32F2F)
                    },
                    label = "barColor"
                )

                Text(
                    text = if (remaining >= 0) "Remaining Allowance" else "Over Allowance",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OdometerText(
                    amount = kotlin.math.abs(remaining),
                    text = (if (remaining < 0) "-" else "") + currencyFormatter.format(kotlin.math.abs(remaining)),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = if (remaining >= 0) MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFFD32F2F)
                )

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = barColor,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Spent: ${currencyFormatter.format(totalSpent)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "Limit: ${currencyFormatter.format(limit)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            } else {
                val isNetIncome = totalSpent < 0
                val titleText = if (isNetIncome) "Net Income" else "Total Expenses"
                val absAmount = kotlin.math.abs(totalSpent)

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OdometerText(
                    amount = absAmount,
                    text = currencyFormatter.format(absAmount),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun CategoryBreakdownCard(
    categorySpending: List<CategorySpend>,
    totalSpending: Double,
    currencyFormatter: NumberFormat
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Category Distribution",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.align(Alignment.Start)
            )

            // Animated Donut Chart
            DonutChart(
                categorySpending = categorySpending,
                totalSpending = totalSpending,
                currencyFormatter = currencyFormatter,
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )

            // Category Details List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                categorySpending.forEach { spend ->
                    val percentageText = String.format(Locale.getDefault(), "%.1f%%", spend.percentage * 100)
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(spend.color)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = spend.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currencyFormatter.format(spend.amount),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "($percentageText)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Elegant Progress Bar for Category Share
                        LinearProgressIndicator(
                            progress = { spend.percentage.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = spend.color,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DonutChart(
    categorySpending: List<CategorySpend>,
    totalSpending: Double,
    currencyFormatter: NumberFormat,
    modifier: Modifier = Modifier
) {
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(categorySpending) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 1000,
                easing = FastOutSlowInEasing
            )
        )
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val diameter = size.minDimension - 24.dp.toPx()
            val strokeWidth = 24.dp.toPx()
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val chartSize = Size(diameter, diameter)

            if (totalSpending == 0.0 || categorySpending.isEmpty()) {
                drawArc(
                    color = Color.LightGray.copy(alpha = 0.4f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth),
                    topLeft = topLeft,
                    size = chartSize
                )
            } else {
                var startAngle = -90f
                val hasMultipleSegments = categorySpending.size > 1
                categorySpending.forEach { spend ->
                    val sweepAngle = ((spend.amount / totalSpending) * 360f).toFloat()
                    val drawSweep = if (hasMultipleSegments) {
                        (sweepAngle - 2f).coerceAtLeast(1f)
                    } else {
                        sweepAngle
                    }
                    drawArc(
                        color = spend.color,
                        startAngle = startAngle + (if (hasMultipleSegments) 1f else 0f),
                        sweepAngle = drawSweep * animationProgress.value,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        topLeft = topLeft,
                        size = chartSize
                    )
                    startAngle += sweepAngle
                }
            }
        }

        // Center Info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Spent",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currencyFormatter.format(totalSpending),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun KeyMetricsSection(
    dailyAvg: Double,
    transactionCount: Int,
    largestPurchase: Expense?,
    smallestPurchase: Expense?,
    currencyFormatter: NumberFormat
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Key Metrics",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Daily Average",
                value = currencyFormatter.format(dailyAvg),
                description = "Average spent per day",
                icon = Icons.Default.Timeline,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Transactions",
                value = "$transactionCount",
                description = "Total expenses recorded",
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Largest Spending",
                value = largestPurchase?.let { currencyFormatter.format(it.amount) } ?: "—",
                description = largestPurchase?.title ?: "No transactions",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Smallest Spending",
                value = smallestPurchase?.let { currencyFormatter.format(it.amount) } ?: "—",
                description = smallestPurchase?.title ?: "No transactions",
                icon = Icons.AutoMirrored.Filled.TrendingDown,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
