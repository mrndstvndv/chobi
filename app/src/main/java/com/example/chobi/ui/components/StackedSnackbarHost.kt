package com.example.chobi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.chobi.data.Expense
import kotlinx.coroutines.delay

private class StackedSnackbarState(
  val expense: Expense,
  val isVisible: MutableState<Boolean> = mutableStateOf(true),
  var lastDisplayPosition: Int = 0
)

private val BouncySpatialSpec = spring<Float>(
  dampingRatio = 0.68f, // Subtle, clean overshoot
  stiffness = 900f      // Faster, snappier motion
)

private val BouncyEffectsSpec = spring<Float>(
  dampingRatio = Spring.DampingRatioNoBouncy,
  stiffness = Spring.StiffnessMedium
)

@Composable
fun StackedSnackbarHost(
  activeSnackbars: List<Expense>,
  onSnackbarResult: (Expense, SnackbarResult) -> Unit,
  modifier: Modifier = Modifier
) {
  val localSnackbars = remember { mutableStateListOf<StackedSnackbarState>() }

  // Sync localSnackbars with activeSnackbars
  LaunchedEffect(activeSnackbars) {
    // 1. Add new items
    activeSnackbars.forEach { expense ->
      if (localSnackbars.none { it.expense.id == expense.id }) {
        localSnackbars.add(StackedSnackbarState(expense))
      }
    }
    // 2. Mark removed items as invisible
    localSnackbars.forEach { localItem ->
      val inActiveList = activeSnackbars.any { it.id == localItem.expense.id }
      if (!inActiveList && localItem.isVisible.value) {
        localItem.isVisible.value = false
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(16.dp),
    contentAlignment = Alignment.BottomCenter
  ) {
    val maxVisible = 3
    val visibleSnackbars = localSnackbars.filter { it.isVisible.value }
    val total = visibleSnackbars.size

    localSnackbars.forEach { localItem ->
      val visibleIndex = visibleSnackbars.indexOfFirst { it.expense.id == localItem.expense.id }
      val positionFromTop = if (visibleIndex != -1) total - 1 - visibleIndex else -1
      if (positionFromTop != -1) {
        localItem.lastDisplayPosition = positionFromTop
      }
      val displayPosition = localItem.lastDisplayPosition

      val targetScale = 1f - (displayPosition * 0.06f)
      val targetTranslationY = -(displayPosition * 10f)
      val targetAlpha = 1f
      val targetOverlayAlpha = displayPosition * 0.15f

      val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = BouncySpatialSpec,
        label = "scale"
      )
      val animatedTranslationY by animateFloatAsState(
        targetValue = targetTranslationY,
        animationSpec = BouncySpatialSpec,
        label = "translationY"
      )
      val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = BouncyEffectsSpec,
        label = "alpha"
      )
      val animatedOverlayAlpha by animateFloatAsState(
        targetValue = targetOverlayAlpha,
        animationSpec = BouncyEffectsSpec,
        label = "overlayAlpha"
      )

      val showCard = localItem.isVisible.value && displayPosition < maxVisible

      key(localItem.expense.id) {
        val visibleState = remember {
          MutableTransitionState(false).apply {
            targetState = true
          }
        }
        visibleState.targetState = showCard

        // Restart timer only when this snackbar is at the front (top of the stack)
        LaunchedEffect(localItem.expense.id, positionFromTop) {
          if (positionFromTop == 0) {
            delay(4000)
            onSnackbarResult(localItem.expense, SnackbarResult.Dismissed)
          }
        }

        if (!localItem.isVisible.value && !visibleState.targetState && !visibleState.currentState && visibleState.isIdle) {
          LaunchedEffect(Unit) {
            localSnackbars.remove(localItem)
          }
        }

        AnimatedVisibility(
          visibleState = visibleState,
          enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.68f, stiffness = 900f)
          ) + fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)),
          exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.68f, stiffness = 900f)
          ) + fadeOut(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)),
          modifier = Modifier.zIndex((total - displayPosition).toFloat())
        ) {
          SwipeableSnackbar(
            message = "Deleted \"${localItem.expense.title}\"",
            icon = Icons.Default.Delete,
            actionLabel = "Undo",
            onAction = { onSnackbarResult(localItem.expense, SnackbarResult.ActionPerformed) },
            onDismiss = { onSnackbarResult(localItem.expense, SnackbarResult.Dismissed) },
            darkOverlayAlpha = animatedOverlayAlpha,
            modifier = Modifier
              .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                translationY = animatedTranslationY.dp.toPx()
                alpha = animatedAlpha
              }
          )
        }
      }
    }
  }
}
