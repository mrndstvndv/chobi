package com.example.chobi.ui.components

import android.icu.text.NumberFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.chobi.data.Budget
import com.example.chobi.data.Expense

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryCard(
  expenses: List<Expense>,
  totalAmount: Double,
  budgets: List<Budget>,
  selectedBudget: Budget?,
  onSelectBudget: (Budget?) -> Unit,
  onNewBudgetClick: () -> Unit,
  onDeleteBudget: (Budget) -> Unit,
  currencyCode: String,
  currencyFormatter: NumberFormat,
  modifier: Modifier = Modifier
) {
  val cardShape = RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 28.dp)
  val colors = CardDefaults.cardColors(containerColor = Color.Transparent)
  var showDropdown by remember { mutableStateOf(false) }

  Card(
    modifier = modifier.clip(cardShape),
    colors = colors
  ) {
    Box(
      modifier = Modifier
        .background(
          Brush.linearGradient(
            colors = listOf(
              MaterialTheme.colorScheme.primaryContainer,
              MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
            )
          )
        )
        .padding(24.dp)
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        // Top Row: Title, Dropdown and Actions
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box {
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { showDropdown = true }
                .padding(vertical = 4.dp, horizontal = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = selectedBudget?.title ?: "All Expenses",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
              Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Switch Budget",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
              )
            }

            DropdownMenu(
              expanded = showDropdown,
              onDismissRequest = { showDropdown = false }
            ) {
              DropdownMenuItem(
                text = {
                  Text(
                    text = "All Expenses (No Budget)",
                    fontWeight = if (selectedBudget == null) FontWeight.Bold else FontWeight.Normal
                  )
                },
                onClick = {
                  onSelectBudget(null)
                  showDropdown = false
                }
              )
              if (budgets.isNotEmpty()) {
                HorizontalDivider()
                budgets.forEach { budget ->
                  val isSelected = selectedBudget?.id == budget.id
                  DropdownMenuItem(
                    text = {
                      Text(
                        text = budget.title,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                      )
                    },
                    onClick = {
                      onSelectBudget(budget)
                      showDropdown = false
                    }
                  )
                }
              }
              HorizontalDivider()
              DropdownMenuItem(
                text = { Text("+ Create New Budget") },
                onClick = {
                  onNewBudgetClick()
                  showDropdown = false
                },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                  )
                }
              )
            }
          }

          // Delete budget button if a budget is selected
          if (selectedBudget != null) {
            IconButton(
              onClick = { onDeleteBudget(selectedBudget) },
              modifier = Modifier.size(24.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Budget",
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedBudget != null) {
          // BUDGET / WALLET VIEW
          val limit = selectedBudget.limitAmount
          val spent = totalAmount
          val remaining = limit - spent
          val ratio = if (limit > 0) spent / limit else 0.0
          val progress = ratio.coerceIn(0.0..1.0).toFloat()
          
          val animatedProgress by animateFloatAsState(
            targetValue = progress,
            label = "progress"
          )
          
          val barColor by animateColorAsState(
            targetValue = when {
              ratio < 0.70 -> MaterialTheme.colorScheme.primary
              ratio < 0.90 -> Color(0xFFF57C00) // Orange Warning
              else -> Color(0xFFD32F2F) // Red Alert
            },
            label = "barColor"
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (remaining >= 0) "Remaining Balance" else "Over Budget",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
              )
              Spacer(modifier = Modifier.height(4.dp))
              
              val absRemaining = kotlin.math.abs(remaining)
              val remainingColor = if (remaining >= 0) {
                MaterialTheme.colorScheme.onPrimaryContainer
              } else {
                Color(0xFFD32F2F)
              }
              
              OdometerText(
                amount = absRemaining,
                text = (if (remaining < 0) "-" else "") + currencyFormatter.format(absRemaining),
                style = MaterialTheme.typography.headlineLarge.copy(
                  fontWeight = FontWeight.Bold
                ),
                color = remainingColor
              )
            }

            // Mini Trend Chart
            MiniTrendChart(
              expenses = expenses,
              modifier = Modifier
                .width(100.dp)
                .height(54.dp)
                .padding(start = 12.dp),
              lineColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Progress Bar
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

          // Spent & Limit Details
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Spent: ${currencyFormatter.format(spent)}",
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
          // GENERAL / ALL EXPENSES VIEW
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              val isNetIncome = totalAmount < 0
              val titleText = if (isNetIncome) {
                "Net Income ($currencyCode)"
              } else {
                "Total Expenses ($currencyCode)"
              }
              val absAmount = kotlin.math.abs(totalAmount)

              Text(
                text = titleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
              )
              Spacer(modifier = Modifier.height(6.dp))
              
              OdometerText(
                amount = absAmount,
                text = currencyFormatter.format(absAmount),
                style = MaterialTheme.typography.headlineLarge.copy(
                  fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
              Spacer(modifier = Modifier.height(10.dp))

              // Transaction count badge
              val transactionText = when (expenses.size) {
                0 -> "No transactions"
                1 -> "1 transaction"
                else -> "${expenses.size} transactions"
              }
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                modifier = Modifier.wrapContentSize()
              ) {
                Text(
                  text = transactionText,
                  style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                  ),
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }

            // Mini line trend chart on the right
            MiniTrendChart(
              expenses = expenses,
              modifier = Modifier
                .width(100.dp)
                .height(64.dp)
                .padding(start = 12.dp),
              lineColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
          }
        }
      }
    }
  }
}

@Composable
fun MiniTrendChart(
  expenses: List<Expense>,
  modifier: Modifier = Modifier,
  lineColor: Color = MaterialTheme.colorScheme.onPrimary
) {
  val dailyTotals = remember(expenses) {
    val now = System.currentTimeMillis()
    val dayMillis = 24 * 60 * 60 * 1000L
    (0..6).map { dayOffset ->
      val targetDayStart = now - (dayOffset * dayMillis)
      val calendar = java.util.Calendar.getInstance().apply { timeInMillis = targetDayStart }
      val year = calendar.get(java.util.Calendar.YEAR)
      val dayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)

      val totalForDay = expenses.filter { exp ->
        val expCal = java.util.Calendar.getInstance().apply { timeInMillis = exp.timestamp }
        expCal.get(java.util.Calendar.YEAR) == year && expCal.get(java.util.Calendar.DAY_OF_YEAR) == dayOfYear
      }.sumOf { if (it.amount < 0) 0.0 else it.amount }
      totalForDay.toFloat()
    }.reversed()
  }

  Canvas(modifier = modifier) {
    if (dailyTotals.isEmpty() || dailyTotals.all { it == 0f }) {
      drawLine(
        color = lineColor.copy(alpha = 0.3f),
        start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
        end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
        strokeWidth = 2.dp.toPx()
      )
      return@Canvas
    }

    val maxVal = dailyTotals.maxOrNull() ?: 1f
    val heightScale = size.height / (if (maxVal == 0f) 1f else maxVal)
    val stepX = size.width / (dailyTotals.size - 1)

    val path = Path()
    val fillPath = Path()

    dailyTotals.forEachIndexed { index, value ->
      val x = index * stepX
      val y = size.height - (value * heightScale * 0.7f) - 4.dp.toPx() // leave padding

      if (index == 0) {
        path.moveTo(x, y)
        fillPath.moveTo(x, size.height)
        fillPath.lineTo(x, y)
      } else {
        val prevX = (index - 1) * stepX
        val prevY = size.height - (dailyTotals[index - 1] * heightScale * 0.7f) - 4.dp.toPx()
        val controlX1 = prevX + stepX / 2f
        val controlY1 = prevY
        val controlX2 = prevX + stepX / 2f
        val controlY2 = y
        path.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
        fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
      }
      if (index == dailyTotals.lastIndex) {
        fillPath.lineTo(x, size.height)
        fillPath.close()
      }
    }

    // Draw fill area
    drawPath(
      path = fillPath,
      brush = Brush.verticalGradient(
        colors = listOf(lineColor.copy(alpha = 0.2f), Color.Transparent),
        startY = 0f,
        endY = size.height
      )
    )

    // Draw trend line
    drawPath(
      path = path,
      color = lineColor,
      style = Stroke(
        width = 2.5.dp.toPx(),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
      )
    )
  }
}
