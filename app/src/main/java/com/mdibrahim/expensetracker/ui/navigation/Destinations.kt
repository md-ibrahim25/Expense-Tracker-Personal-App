package com.mdibrahim.expensetracker.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.mdibrahim.expensetracker.R

/**
 * The four top-level destinations. What actually lives inside them is a
 * features-round decision; the shell exists to prove navigation and theming work.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home),
    TRANSACTIONS("transactions", R.string.nav_transactions, Icons.AutoMirrored.Filled.List),
    REPORTS("reports", R.string.nav_reports, Icons.Filled.PieChart),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}
