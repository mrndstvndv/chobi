package com.example.chobi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableSnackbar(
  message: String,
  icon: ImageVector,
  actionLabel: String?,
  onAction: (() -> Unit)?,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  darkOverlayAlpha: Float = 0f
) {

  val dismissState = rememberSwipeToDismissBoxState()

  SwipeToDismissBox(
    state = dismissState,
    backgroundContent = {},
    onDismiss = {
      onDismiss()
    },
    modifier = modifier,
    content = {
      Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
          containerColor = Color(0xFF2A2D23),
          contentColor = Color(0xFFE2E3D8)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Box(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier
              .padding(horizontal = 8.dp, vertical = 8.dp)
              .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .background(Color(0xFFC5C995), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = icon,
                  contentDescription = null,
                  tint = Color(0xFF2A2D23),
                  modifier = Modifier.size(18.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE2E3D8)
              )
            }

            if (actionLabel != null && onAction != null) {
              TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(
                  contentColor = Color(0xFFFFB86D)
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

          if (darkOverlayAlpha > 0f) {
            Box(
              modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = darkOverlayAlpha))
            )
          }
        }
      }
    }
  )
}

@Composable
fun SwipeableSnackbar(
  data: SnackbarData,
  modifier: Modifier = Modifier
) {
  LaunchedEffect(data) {
    delay(4000)
    data.dismiss()
  }

  SwipeableSnackbar(
    message = data.visuals.message,
    icon = Icons.Default.Delete,
    actionLabel = data.visuals.actionLabel,
    onAction = { data.performAction() },
    onDismiss = { data.dismiss() },
    modifier = modifier
  )
}
