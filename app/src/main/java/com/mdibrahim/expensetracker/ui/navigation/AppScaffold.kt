package com.mdibrahim.expensetracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mdibrahim.expensetracker.ui.screens.home.HomeScreen
import com.mdibrahim.expensetracker.ui.screens.placeholder.PlaceholderScreen

@Composable
fun AppScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val label = stringResource(destination.labelRes)
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            if (currentRoute != destination.route) {
                                navController.navigate(destination.route) {
                                    // Single instance per tab, and returning to a tab
                                    // restores where you left off rather than resetting.
                                    popUpTo(TopLevelDestination.HOME.route) {
                                        saveState = true
                                        inclusive = false
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.HOME.route) { HomeScreen() }
            composable(TopLevelDestination.TRANSACTIONS.route) {
                PlaceholderScreen(title = "Transactions")
            }
            composable(TopLevelDestination.REPORTS.route) {
                PlaceholderScreen(title = "Reports")
            }
            composable(TopLevelDestination.SETTINGS.route) {
                PlaceholderScreen(title = "Settings")
            }
        }
    }
}
