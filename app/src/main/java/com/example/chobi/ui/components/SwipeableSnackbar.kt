package com.example.chobi.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun SwipeableSnackbar(
  data: SnackbarData,
  modifier: Modifier = Modifier
) {
  key(data) {
    var targetProgress by remember { mutableStateOf(1f) }
    val progress by animateFloatAsState(
      targetValue = targetProgress,
      animationSpec = tween(durationMillis = 4000, easing = LinearEasing),
      label = "snackbarProgress"
    )

    LaunchedEffect(data) {
      targetProgress = 0f
      delay(4000L)
      data.dismiss()
    }

    val dismissState = rememberSwipeToDismissBoxState(
      confirmValueChange = { value ->
        if (value != SwipeToDismissBoxValue.Settled) {
          data.dismiss()
        }
        true
      }
    )

    SwipeToDismissBox(
      state = dismissState,
      backgroundContent = {},
      content = {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
          ),
          elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
          border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
          ),
          modifier = modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .padding(horizontal = 16.dp, vertical = 8.dp)
              .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
              ) {
                CircularProgressIndicator(
                  progress = { progress },
                  modifier = Modifier.fillMaxSize(),
                  color = MaterialTheme.colorScheme.primary,
                  trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                  strokeWidth = 3.dp
                )
                Icon(
                  imageVector = Icons.Default.Delete,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(16.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            data.visuals.actionLabel?.let { actionLabel ->
              TextButton(
                onClick = { data.performAction() },
                colors = ButtonDefaults.textButtonColors(
                  contentColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Undo,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = actionLabel,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
      }
    )
  }
}
