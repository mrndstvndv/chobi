package com.example.chobi.ui.components

import android.icu.text.NumberFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateDpAsState
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

@OptIn(
  ExperimentalFoundationApi::class,
  ExperimentalMaterial3Api::class,
  ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun ExpenseItem(
  expense: Expense,
  category: Category?,
  onDelete: () -> Unit,
  currencyFormatter: NumberFormat,
  timeFormatPreference: String = "auto",
  modifier: Modifier = Modifier,
  shapes: ListItemShapes = ListItemDefaults.segmentedShapes(0, 1),
  onLongClick: (() -> Unit)? = null,
  onClick: (() -> Unit)? = null,
  index: Int = 0,
  count: Int = 1
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
  val isSwiping = dismissState.dismissDirection != SwipeToDismissBoxValue.Settled

  val isTop = index == 0
  val isBottom = index == count - 1
  val isSingle = count == 1

  val targetTopStart = if (isSwiping || isTop || isSingle) 16.dp else 0.dp
  val targetTopEnd = if (isSwiping || isTop || isSingle) 16.dp else 0.dp
  val targetBottomStart = if (isSwiping || isBottom || isSingle) 16.dp else 0.dp
  val targetBottomEnd = if (isSwiping || isBottom || isSingle) 16.dp else 0.dp

  val animatedTopStart by animateDpAsState(targetValue = targetTopStart, label = "topStart")
  val animatedTopEnd by animateDpAsState(targetValue = targetTopEnd, label = "topEnd")
  val animatedBottomStart by animateDpAsState(targetValue = targetBottomStart, label = "bottomStart")
  val animatedBottomEnd by animateDpAsState(targetValue = targetBottomEnd, label = "bottomEnd")

  val currentShape = RoundedCornerShape(
    topStart = animatedTopStart,
    topEnd = animatedTopEnd,
    bottomStart = animatedBottomStart,
    bottomEnd = animatedBottomEnd
  )

  val animatedShapes = ListItemDefaults.shapes(
    shape = currentShape,
    pressedShape = currentShape,
    focusedShape = currentShape,
    hoveredShape = currentShape,
    draggedShape = currentShape,
    selectedShape = currentShape
  )

  SwipeToDismissBox(
    state = dismissState,
    onDismiss = { value -> onDelete() },
    modifier = modifier
      .fillMaxWidth()
      .clip(currentShape),
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
      SegmentedListItem(
        onClick = { onClick?.invoke() },
        shapes = animatedShapes,
        onLongClick = onLongClick,
        colors = ListItemDefaults.segmentedColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = ListItemDefaults.elevation(
          1.dp,
          3.dp
        ),
        leadingContent = {
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
        },
        supportingContent = {
          Text(
            text = "${expense.category} • ${timeFormatter.format(Date(expense.timestamp))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        },
        trailingContent = {
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
        },
        content = {
          Text(
            text = expense.title,
            style = MaterialTheme.typography.titleMedium
          )
        },
        modifier = Modifier.fillMaxWidth()
      )
    }
  )
}











