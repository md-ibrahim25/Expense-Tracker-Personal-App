package com.mdibrahim.expensetracker.ui.theme

import androidx.compose.ui.graphics.Color

// Brand seed: a deep green, chosen because money apps read as "calm and trustworthy"
// rather than alarming. Used only when dynamic colour is unavailable or disabled.
val Green80 = Color(0xFF9DD9B8)
val GreenGrey80 = Color(0xFFB8CCC2)
val Sand80 = Color(0xFFE6D4A8)

val Green40 = Color(0xFF1E6B4A)
val GreenGrey40 = Color(0xFF4E635A)
val Sand40 = Color(0xFF6E5B2E)

// Semantic colours for money direction. Deliberately not pure red/green: they must
// stay distinguishable for red-green colour blindness, so they differ in lightness
// too, and the UI always pairs them with a +/- sign rather than relying on hue alone.
val ExpenseRed = Color(0xFFC62828)
val ExpenseRedDark = Color(0xFFFF8A80)
val IncomeGreen = Color(0xFF2E7D32)
val IncomeGreenDark = Color(0xFF81C784)
