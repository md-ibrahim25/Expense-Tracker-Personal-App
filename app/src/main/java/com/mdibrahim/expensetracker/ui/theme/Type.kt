package com.mdibrahim.expensetracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

val AppTypography = Typography()

/**
 * Amounts use tabular-ish alignment: monospace digits so that figures in a list line
 * up column-wise instead of jittering as digit widths change.
 */
val MoneyTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    textAlign = TextAlign.End,
)
