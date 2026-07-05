package com.example.chobi.ui.components

import android.icu.text.NumberFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.chobi.data.Expense

@Composable
fun SummaryCard(
  expenses: List<Expense>,
  totalAmount: Double,
  currencyCode: String,
  currencyFormatter: NumberFormat,
  modifier: Modifier = Modifier
) {
  val cardShape = RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 28.dp)
  val colors = CardDefaults.cardColors(containerColor = Color.Transparent)

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
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.Center
        ) {
          val isNetIncome = totalAmount < 0
          val titleText = if (isNetIncome) {
            "Net Income ($currencyCode)"
          } else {
            "Total Expenses ($currencyCode)"
          }
          val absAmount = kotlin.math.abs(totalAmount)

          Text(
            text = titleText,
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onPrimaryContainer
          )
          Spacer(modifier = Modifier.height(6.dp))
          
          OdometerText(
            amount = absAmount,
            text = currencyFormatter.format(absAmount),
            style = MaterialTheme.typography.headlineLarge.copy(
              fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
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
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
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
