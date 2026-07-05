package com.example.chobi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.chobi.data.Category
import com.example.chobi.data.CategoryIcons

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDialog(
  category: Category? = null,
  onDismiss: () -> Unit,
  onConfirm: (name: String, iconName: String, colorHex: String) -> Unit,
  onDelete: (() -> Unit)? = null
) {
  var name by remember { mutableStateOf(category?.name ?: "") }
  var selectedIconName by remember { mutableStateOf(category?.iconName ?: "Restaurant") }
  var selectedColorHex by remember { mutableStateOf(category?.colorHex ?: "#FF9800") }

  val icons = CategoryIcons.getAllIcons()
  val colors = listOf(
    "#FF9800", // Orange
    "#2196F3", // Blue
    "#4CAF50", // Green
    "#9C27B0", // Purple
    "#E91E63", // Pink
    "#F44336", // Red
    "#3F51B5", // Indigo
    "#00BCD4", // Teal
    "#8BC34A", // Light Green
    "#9E9E9E"  // Gray
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (category == null) "New Custom Category" else "Edit Category") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Category Name") },
          modifier = Modifier.fillMaxWidth()
        )

        // Color Picker Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Select Color", style = MaterialTheme.typography.titleSmall)
          val colorRows = colors.chunked(5)
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            colorRows.forEach { rowColors ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
              ) {
                rowColors.forEach { hex ->
                  val isSelected = selectedColorHex == hex
                  val color = hex.toColor()
                  Box(
                    modifier = Modifier
                      .size(36.dp)
                      .clip(CircleShape)
                      .background(color)
                      .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape
                      )
                      .clickable { selectedColorHex = hex }
                  )
                }
              }
            }
          }
        }

        // Icon Chooser Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Select Icon", style = MaterialTheme.typography.titleSmall)
          val iconRows = icons.chunked(5)
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            iconRows.forEach { rowIcons ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
              ) {
                rowIcons.forEach { (iconName, imageVector) ->
                  val isSelected = selectedIconName == iconName
                  val themeColor = selectedColorHex.toColor()
                  Box(
                    modifier = Modifier
                      .size(40.dp)
                      .clip(CircleShape)
                      .background(
                        if (isSelected) themeColor.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant
                      )
                      .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) themeColor else Color.Transparent,
                        shape = CircleShape
                      )
                      .clickable { selectedIconName = iconName },
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = imageVector,
                      contentDescription = iconName,
                      tint = if (isSelected) themeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(22.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (name.isNotBlank()) {
            onConfirm(name, selectedIconName, selectedColorHex)
          }
        },
        enabled = name.isNotBlank()
      ) {
        Text(if (category == null) "Add" else "Save")
      }
    },
    dismissButton = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (category != null && onDelete != null) {
          TextButton(
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
          ) {
            Text("Remove")
          }
        }
        TextButton(onClick = onDismiss) {
          Text("Cancel")
        }
      }
    }
  )
}
