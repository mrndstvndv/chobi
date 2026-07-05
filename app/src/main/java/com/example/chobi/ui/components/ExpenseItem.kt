package com.example.chobi.ui.components

import android.icu.text.NumberFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.chobi.data.Category
import com.example.chobi.data.CategoryIcons
import com.example.chobi.data.Expense
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExpenseItem(
  expense: Expense,
  category: Category?,
  onDelete: () -> Unit,
  currencyFormatter: NumberFormat,
  timeFormatPreference: String = "auto",
  modifier: Modifier = Modifier,
  onLongClick: (() -> Unit)? = null,
  onClick: (() -> Unit)? = null
) {
  val context = LocalContext.current
  val timeFormatter = remember(timeFormatPreference) {
    when (timeFormatPreference) {
      "12h" -> SimpleDateFormat("h:mm a", Locale.getDefault())
      "24h" -> SimpleDateFormat("HH:mm", Locale.getDefault())
      else -> android.text.format.DateFormat.getTimeFormat(context)
    }
  }
  val icon = category?.let { CategoryIcons.getIcon(it.iconName) } ?: Icons.Default.Star
  val iconColor = category?.colorHex?.toColor() ?: MaterialTheme.colorScheme.primary

  val dismissState = rememberSwipeToDismissBoxState()

  SwipeToDismissBox(
    state = dismissState,
    onDismiss = { value -> onDelete() },
    modifier = modifier.fillMaxWidth(),
    backgroundContent = {
      val direction = dismissState.dismissDirection
      val color = when (direction) {
        SwipeToDismissBoxValue.EndToStart, SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.errorContainer
        else -> Color.Transparent
      }
      val alignment = when (direction) {
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        else -> Alignment.Center
      }
      val deleteIcon = Icons.Default.Delete

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(color)
          .padding(horizontal = 24.dp),
        contentAlignment = alignment
      ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
          Icon(
            imageVector = deleteIcon,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(24.dp)
          )
        }
      }
    },
    content = {
      val itemModifier = if (onLongClick != null || onClick != null) {
        Modifier
          .fillMaxWidth()
          .combinedClickable(
            onClick = { onClick?.invoke() },
            onLongClick = { onLongClick?.invoke() }
          )
      } else {
        Modifier.fillMaxWidth()
      }

      Row(
        modifier = itemModifier
          .background(MaterialTheme.colorScheme.surface)
          .padding(horizontal = 16.dp, vertical = 12.dp),
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
        val isIncome = expense.amount < 0
        val displayAmount = if (isIncome) {
          "+" + currencyFormatter.format(kotlin.math.abs(expense.amount))
        } else {
          currencyFormatter.format(expense.amount)
        }
        val amountColor = if (isIncome) {
          Color(0xFF2E7D32) // Nice green color for income/funds
        } else {
          MaterialTheme.colorScheme.primary
        }
        Text(
          text = displayAmount,
          style = MaterialTheme.typography.titleLarge,
          color = amountColor
        )
      }
    }
  )
}
