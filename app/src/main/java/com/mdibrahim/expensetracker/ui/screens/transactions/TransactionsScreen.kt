package com.mdibrahim.expensetracker.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.mdibrahim.expensetracker.core.model.TransactionDirection
import com.mdibrahim.expensetracker.ui.components.TransactionRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

@Composable
fun TransactionsScreen(
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                FilterChip(
                    selected = uiState.filter == null,
                    onClick = { viewModel.setFilter(null) },
                    label = { Text("All") },
                )
                FilterChip(
                    selected = uiState.filter == TransactionDirection.DEBIT,
                    onClick = { viewModel.setFilter(TransactionDirection.DEBIT) },
                    label = { Text("Money out") },
                )
                FilterChip(
                    selected = uiState.filter == TransactionDirection.CREDIT,
                    onClick = { viewModel.setFilter(TransactionDirection.CREDIT) },
                    label = { Text("Money in") },
                )
            }

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                uiState.days.isEmpty() -> Text(
                    text = "No transactions.",
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
