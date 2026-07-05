package com.example.chobi.ui.components

import android.icu.text.NumberFormat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SummaryCard(
  totalAmount: Double,
  currencyCode: String,
  currencyFormatter: NumberFormat,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primary
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = "Total Expenses ($currencyCode)",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onPrimary
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = currencyFormatter.format(totalAmount),
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onPrimary
      )
    }
  }
}
