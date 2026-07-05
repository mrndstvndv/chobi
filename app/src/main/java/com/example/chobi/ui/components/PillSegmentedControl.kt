package com.example.chobi.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun <T> PillSegmentedControl(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    showCheckmark: Boolean = true,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    optionToText: (T) -> String = { it.toString() }
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selectedOption
            
            // Animate background color change for selected segment
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    Color.Transparent
                },
                label = "segmented_control_bg"
            )
            
            val textColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(backgroundColor)
                    .clickable { onOptionSelected(option) },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    if (isSelected && showCheckmark) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = optionToText(option),
                        style = textStyle,
                        color = textColor
                    )
                }
            }
            
            // Add vertical divider between options, except after the last option
            if (index < options.lastIndex) {
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                )
            }
        }
    }
}
