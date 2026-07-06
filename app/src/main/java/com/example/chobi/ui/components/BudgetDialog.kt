package com.example.chobi.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun BudgetDialog(
  onDismiss: () -> Unit,
  onConfirm: (title: String, limit: Double) -> Unit
) {
  val defaultTitle = remember {
    java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())
  }
  var title by remember { mutableStateOf(defaultTitle) }
  var limitInput by remember { mutableStateOf("") }
  val isConfirmEnabled = title.isNotBlank() && limitInput.toDoubleOrNull()?.let { it > 0 } == true

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("New Allowance / Budget") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Text(
          text = "Starting a new budget will close any currently active budget, and new expenses will count towards this one.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Budget Title (e.g. July Week 2)") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )

        OutlinedTextField(
          value = limitInput,
          onValueChange = { limitInput = it },
          label = { Text("Allowance / Limit Amount") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val limit = limitInput.toDoubleOrNull()
          if (title.isNotBlank() && limit != null) {
            onConfirm(title, limit)
          }
        },
        enabled = isConfirmEnabled
      ) {
        Text("Create")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
