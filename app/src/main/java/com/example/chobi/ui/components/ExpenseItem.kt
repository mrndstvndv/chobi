package com.example.chobi.ui.components

import android.icu.text.NumberFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.chobi.data.Category
import com.example.chobi.data.CategoryIcons
import com.example.chobi.data.Expense
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
