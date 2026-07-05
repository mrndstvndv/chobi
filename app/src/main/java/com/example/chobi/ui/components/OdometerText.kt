package com.example.chobi.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * A text component that animates numeric changes with a vertical rolling odometer/scoreboard effect.
 *
 * @param amount The numeric amount to track changes.
 * @param text The formatted string representation of the amount (e.g. "$1,234.56").
 * @param style The text style to apply.
 * @param color The color of the text.
 * @param modifier The modifier to apply to the component.
 */
@Composable
fun OdometerText(
    amount: Double,
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    var previousAmount by remember { mutableStateOf(amount) }
    val isIncrease = amount >= previousAmount

    LaunchedEffect(amount) {
        previousAmount = amount
    }

    val (prefix, body, suffix) = remember(text) {
        splitFormattedCurrency(text)
    }

    Row(
        modifier = modifier.animateContentSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (prefix.isNotEmpty()) {
            Text(
                text = prefix,
                style = style,
                color = color,
                softWrap = false
            )
        }

        val bodyLength = body.length
        val tabularStyle = remember(style) {
            style.copy(fontFeatureSettings = "tnum")
        }

        for (i in 0 until bodyLength) {
            val char = body[i]
            val key = bodyLength - 1 - i // Key based on position from the right (0 for rightmost)
            key(key) {
                AnimatedDigit(
                    char = char,
                    style = tabularStyle,
                    color = color,
                    isIncrease = isIncrease,
                    key = key
                )
            }
        }

        if (suffix.isNotEmpty()) {
            Text(
                text = suffix,
                style = style,
                color = color,
                softWrap = false
            )
        }
    }
}

@Composable
private fun AnimatedDigit(
    char: Char,
    style: TextStyle,
    color: Color,
    isIncrease: Boolean,
    key: Int
) {
    if (!char.isDigit()) {
        Text(
            text = char.toString(),
            style = style,
            color = color,
            softWrap = false
        )
        return
    }

    val digit = char.digitToInt()
    val animatable = remember { Animatable(digit.toFloat()) }

    LaunchedEffect(digit) {
        val currentVal = animatable.value
        val currentDigit = (currentVal.roundToInt() % 10 + 10) % 10
        if (digit != currentDigit) {
            // Stagger delay from right-to-left
            delay(key * 45L)
            
            var target = digit.toFloat()
            if (isIncrease && digit < currentDigit) {
                target += 10f
            } else if (!isIncrease && digit > currentDigit) {
                target -= 10f
            }
            
            animatable.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy, // subtle overshoot bounce
                    stiffness = Spring.StiffnessMediumLow         // smooth fluid speed
                )
            )
            
            // Snap back to base range [0, 9] to keep float values small
            val finalDigit = (target.roundToInt() % 10 + 10) % 10
            animatable.snapTo(finalDigit.toFloat())
        }
    }

    val minIndex = -11
    val maxIndex = 20

    Layout(
        modifier = Modifier.clipToBounds(),
        content = {
            for (i in minIndex..maxIndex) {
                val d = (i % 10 + 10) % 10
                Text(
                    text = d.toString(),
                    style = style,
                    color = color,
                    softWrap = false
                )
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val digitWidth = placeables.firstOrNull()?.width ?: 0
        val digitHeight = placeables.firstOrNull()?.height ?: 0

        layout(digitWidth, digitHeight) {
            val currentValue = animatable.value
            placeables.forEachIndexed { idx, placeable ->
                val layoutIndex = idx + minIndex
                val yOffset = ((layoutIndex - currentValue) * digitHeight).roundToInt()
                placeable.placeRelative(0, yOffset)
            }
        }
    }
}

internal fun splitFormattedCurrency(text: String): Triple<String, String, String> {
    val firstDigitIndex = text.indexOfFirst { it.isDigit() }
    val lastDigitIndex = text.indexOfLast { it.isDigit() }

    if (firstDigitIndex == -1 || lastDigitIndex == -1) {
        return Triple("", text, "")
    }

    val prefix = text.substring(0, firstDigitIndex)
    val body = text.substring(firstDigitIndex, lastDigitIndex + 1)
    val suffix = text.substring(lastDigitIndex + 1)

    return Triple(prefix, body, suffix)
}
