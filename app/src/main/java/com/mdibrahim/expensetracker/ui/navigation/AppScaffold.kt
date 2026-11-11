package com.mdibrahim.expensetracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.mdibrahim.expensetracker.ui.screens.people.PeopleScreen
import com.mdibrahim.expensetracker.ui.screens.people.PersonScreen
import com.mdibrahim.expensetracker.ui.screens.people.PersonViewModel
import com.mdibrahim.expensetracker.ui.screens.settings.SettingsScreen
import com.mdibrahim.expensetracker.ui.screens.transactions.TransactionsScreen

private const val ARG_ID = EditTransactionViewModel.ARG_ID
private const val ARG_REVIEW = EditTransactionViewModel.ARG_REVIEW

/** Add (no id), edit (id) or review (id + review=true). Not a tab, so the bottom bar hides. */
private const val EDIT_ROUTE = "transaction/edit?$ARG_ID={$ARG_ID}&$ARG_REVIEW={$ARG_REVIEW}"

private fun editRoute(id: Long, review: Boolean) = "transaction/edit?$ARG_ID=$id&$ARG_REVIEW=$review"

private fun NavHostController.openTab(destination: TopLevelDestination) =
    navigate(destination.route) {
        // Single instance per tab, and returning to a tab restores where you left
        // off rather than resetting.
        popUpTo(TopLevelDestination.HOME.route) {
            saveState = true
            inclusive = false
        }
        launchSingleTop = true
        restoreState = true
    }

private fun NavHostController.openEditor(id: Long = EditTransactionViewModel.NEW_ID, review: Boolean = false) =
    navigate(editRoute(id, review))

private const val PERSON_ROUTE = "person/{${PersonViewModel.ARG_PERSON_ID}}"
private fun personRoute(id: Long) = "person/$id"

/** [openPersonId]: from a reminder notification -- open that friend's page on launch. */
@Composable
fun AppScaffold(openPersonId: Long? = null) {
    val navController = rememberNavController()
    LaunchedEffect(openPersonId) { openPersonId?.let { navController.navigate(personRoute(it)) } }
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
                                if (currentRoute != destination.route) navController.openTab(destination)
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
                    onStartReview = { navController.openEditor(it, review = true) },
                    onSeeAll = { navController.openTab(TopLevelDestination.TRANSACTIONS) },
                    onOpenPeople = { navController.openTab(TopLevelDestination.PEOPLE) },
                )
            }
            composable(TopLevelDestination.TRANSACTIONS.route) {
                TransactionsScreen(
                    onAddTransaction = { navController.openEditor() },
                    onOpenTransaction = { navController.openEditor(it) },
                )
            }
            composable(TopLevelDestination.PEOPLE.route) {
                PeopleScreen(onOpenPerson = { navController.navigate(personRoute(it)) })
            }
            composable(
                route = PERSON_ROUTE,
                arguments = listOf(navArgument(PersonViewModel.ARG_PERSON_ID) { type = NavType.LongType }),
            ) {
                PersonScreen(onBack = { navController.popBackStack() })
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen()
            }
            composable(
                route = EDIT_ROUTE,
                arguments = listOf(
                    navArgument(ARG_ID) {
                        type = NavType.LongType
                        defaultValue = EditTransactionViewModel.NEW_ID
                    },
                    navArgument(ARG_REVIEW) {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) {
                EditTransactionScreen(
                    onDone = { navController.popBackStack() },
                    onNextReview = { nextId ->
                        // Replace, don't stack: Back from any review step returns home.
                        navController.navigate(editRoute(nextId, review = true)) {
                            popUpTo(EDIT_ROUTE) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
