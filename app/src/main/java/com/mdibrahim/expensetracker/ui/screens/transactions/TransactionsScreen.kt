package com.mdibrahim.expensetracker.ui.screens.transactions

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var confirmDelete by remember { mutableStateOf(false) }

    // Back leaves selection mode instead of leaving the screen.
    BackHandler(enabled = uiState.isSelecting) { viewModel.clearSelection() }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (uiState.isSelecting) {
                SelectionBar(
                    count = uiState.selectedIds.size,
                    allSelected = uiState.selectedIds.size == uiState.visibleIds.size,
                    onClose = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAllVisible,
                    onDelete = { confirmDelete = true },
                )
            } else {
                MonthSelector(uiState.month, viewModel::previousMonth, viewModel::nextMonth)
            }

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
                            val id = item.transaction.id
                            TransactionRow(
                                item = item,
                                onClick = {
                                    if (uiState.isSelecting) viewModel.toggleSelection(id) else onOpenTransaction(id)
                                },
                                onLongClick = { viewModel.toggleSelection(id) },
                                selected = if (uiState.isSelecting) id in uiState.selectedIds else null,
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        if (!uiState.isSelecting) {
            FloatingActionButton(
                onClick = onAddTransaction,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        }
    }

    if (confirmDelete) {
        val count = uiState.selectedIds.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (count == 1) "Delete 1 transaction?" else "Delete $count transactions?") },
            text = {
                Text("This can't be undone. Deleted bank SMS transactions won't come back on the next SMS import.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteSelected()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
        }
        Text("$count selected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (!allSelected) {
            TextButton(onClick = onSelectAll) { Text("Select all") }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
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
