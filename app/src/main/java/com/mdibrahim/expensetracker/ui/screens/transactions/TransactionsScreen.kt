package com.mdibrahim.expensetracker.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdibrahim.expensetracker.core.util.formatMoney
import com.mdibrahim.expensetracker.ui.components.MonthSelector
import com.mdibrahim.expensetracker.ui.components.TransactionRow
import com.mdibrahim.expensetracker.ui.theme.LocalMoneyColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")

@Composable
fun TransactionsScreen(
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val moneyColors = LocalMoneyColors.current

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            MonthSelector(uiState.month, viewModel::previousMonth, viewModel::nextMonth)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Out ${formatMoney(uiState.spentMinor)}", color = moneyColors.expense)
                Text("In ${formatMoney(uiState.receivedMinor)}", color = moneyColors.income)
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                items(TransactionFilter.entries) { option ->
                    val label = if (option == TransactionFilter.TO_REVIEW && uiState.toReviewCount > 0) {
                        "${option.label} (${uiState.toReviewCount})"
                    } else {
                        option.label
                    }
                    FilterChip(
                        selected = uiState.filter == option,
                        onClick = { viewModel.setFilter(option) },
                        label = { Text(label) },
                    )
                }
            }

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                uiState.days.isEmpty() -> Text(
                    text = if (uiState.filter == TransactionFilter.TO_REVIEW) {
                        "All caught up — nothing to review this month."
                    } else {
                        "No transactions this month."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn {
                    uiState.days.forEach { day ->
                        item(key = "header-${day.date}") { DayHeader(day.date) }
                        items(day.items, key = { it.transaction.id }) { item ->
                            TransactionRow(item, onClick = { onOpenTransaction(item.transaction.id) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddTransaction,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add transaction")
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    val today = LocalDate.now()
    val label = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DAY_FORMAT)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}
