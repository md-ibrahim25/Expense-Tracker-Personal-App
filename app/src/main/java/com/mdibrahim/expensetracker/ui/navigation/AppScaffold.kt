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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mdibrahim.expensetracker.ui.screens.edit.EditTransactionScreen
import com.mdibrahim.expensetracker.ui.screens.edit.EditTransactionViewModel
import com.mdibrahim.expensetracker.ui.screens.home.HomeScreen
import com.mdibrahim.expensetracker.ui.screens.placeholder.PlaceholderScreen
import com.mdibrahim.expensetracker.ui.screens.settings.SettingsScreen
import com.mdibrahim.expensetracker.ui.screens.transactions.TransactionsScreen

/** Add (no id) or edit (id) a transaction. Not a tab, so the bottom bar hides here. */
private const val EDIT_ROUTE = "transaction/edit?${EditTransactionViewModel.ARG_ID}={${EditTransactionViewModel.ARG_ID}}"

private fun NavHostController.openEditor(id: Long = EditTransactionViewModel.NEW_ID) =
    navigate("transaction/edit?${EditTransactionViewModel.ARG_ID}=$id")

@Composable
fun AppScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
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
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.HOME.route) {
                HomeScreen(
                    onAddTransaction = { navController.openEditor() },
                    onOpenTransaction = { navController.openEditor(it) },
                )
            }
            composable(TopLevelDestination.TRANSACTIONS.route) {
                TransactionsScreen(
                    onAddTransaction = { navController.openEditor() },
                    onOpenTransaction = { navController.openEditor(it) },
                )
            }
            composable(TopLevelDestination.REPORTS.route) {
                PlaceholderScreen(title = "Reports")
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen()
            }
            composable(
                route = EDIT_ROUTE,
                arguments = listOf(
                    navArgument(EditTransactionViewModel.ARG_ID) {
                        type = NavType.LongType
                        defaultValue = EditTransactionViewModel.NEW_ID
                    },
                ),
            ) {
                EditTransactionScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}
