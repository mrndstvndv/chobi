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
      val isNetIncome = totalAmount < 0
      val titleText = if (isNetIncome) {
        "Net Income ($currencyCode)"
      } else {
        "Total Expenses ($currencyCode)"
      }
      val absAmount = kotlin.math.abs(totalAmount)
      Text(
        text = titleText,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onPrimary
      )
      Spacer(modifier = Modifier.height(8.dp))
      OdometerText(
        amount = absAmount,
        text = currencyFormatter.format(absAmount),
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onPrimary
      )
    }
  }
}
