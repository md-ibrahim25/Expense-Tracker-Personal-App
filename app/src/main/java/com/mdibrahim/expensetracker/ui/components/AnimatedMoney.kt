package com.mdibrahim.expensetracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.mdibrahim.expensetracker.core.util.formatMoney
import kotlin.math.roundToLong

/**
 * Money that counts up/down to a new value. The animation runs on a 0..1 progress
 * value and interpolates in Long paise, and the final frame shows [amountMinor]
 * itself -- never a value round-tripped through Float, which can't hold large paise
 * amounts exactly. Formatting still goes through [formatMoney].
 */
@Composable
fun AnimatedMoney(
    amountMinor: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    prefix: String = "",
) {
    var from by remember { mutableLongStateOf(amountMinor) }
    var to by remember { mutableLongStateOf(amountMinor) }
    val progress = remember { Animatable(1f) }

    val shown = if (progress.value >= 1f) to else from + ((to - from) * progress.value.toDouble()).roundToLong()

    LaunchedEffect(amountMinor) {
        if (amountMinor == to) return@LaunchedEffect
        from = shown // continue smoothly from wherever an interrupted animation was
        to = amountMinor
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }

    Text(text = prefix + formatMoney(shown), modifier = modifier, style = style, color = color)
}
