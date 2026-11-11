package com.mdibrahim.expensetracker.ui.screens.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdibrahim.expensetracker.core.util.formatMoney
import com.mdibrahim.expensetracker.ui.components.AnimatedMoney
import com.mdibrahim.expensetracker.ui.components.MonthSelector
import com.mdibrahim.expensetracker.ui.components.TransactionRow
import com.mdibrahim.expensetracker.ui.theme.LocalMoneyColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMM")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    // Back leaves selection mode instead of leaving the screen.
    BackHandler(enabled = uiState.isSelecting) { viewModel.clearSelection() }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)) {
                AnimatedContent(
                    targetState = uiState.isSelecting,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "topBar",
                ) { selecting ->
                    if (selecting) {
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
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val colors = LocalMoneyColors.current
                    TotalTile(Icons.Filled.NorthEast, "Money out", uiState.spentMinor, "-", colors.expense, Modifier.weight(1f))
                    TotalTile(Icons.Filled.SouthWest, "Money in", uiState.receivedMinor, "+", colors.income, Modifier.weight(1f))
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    items(TransactionFilter.entries) { option ->
                        val label = if (option == TransactionFilter.TO_REVIEW && uiState.toReviewCount > 0) {
                            "${option.label} · ${uiState.toReviewCount}"
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
            }

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                uiState.days.isEmpty() -> EmptyState(
                    if (uiState.filter == TransactionFilter.TO_REVIEW) {
                        Icons.Filled.DoneAll to "All caught up — nothing to review this month."
                    } else {
                        Icons.Outlined.Inbox to "No transactions this month."
                    },
                )
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 96.dp),
                ) {
                    uiState.days.forEach { day ->
                        // Pinned while its rows scroll past.
                        stickyHeader(key = "header-${day.date}") { DayHeader(day) }
                        items(day.items, key = { it.transaction.id }) { item ->
                            val id = item.transaction.id
                            TransactionRow(
                                item = item,
                                onClick = {
                                    if (uiState.isSelecting) viewModel.toggleSelection(id) else onOpenTransaction(id)
                                },
                                onLongClick = { viewModel.toggleSelection(id) },
                                selected = if (uiState.isSelecting) id in uiState.selectedIds else null,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }

        if (!uiState.isSelecting) {
            ExtendedFloatingActionButton(
                onClick = onAddTransaction,
                expanded = fabExpanded,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    if (confirmDelete) {
        val count = uiState.selectedIds.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
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
private fun TotalTile(
    icon: ImageVector,
    label: String,
    amountMinor: Long,
    prefix: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedMoney(amountMinor, prefix = prefix, style = MaterialTheme.typography.titleSmall, color = color)
        }
    }
}

@Composable
private fun DayHeader(day: DayGroup) {
    val today = LocalDate.now()
    val label = when (day.date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.date.format(DAY_FORMAT)
    }
    Row(
        Modifier
            .fillMaxWidth()
            // Opaque, so rows scrolling underneath the pinned header don't show through.
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (day.spentMinor > 0) {
            Text(
                "-" + formatMoney(day.spentMinor),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(content: Pair<ImageVector, String>) {
    Column(
        Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(content.first, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(content.second, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
        }
        Text(
            "$count selected",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        if (!allSelected) {
            TextButton(onClick = onSelectAll) { Text("Select all") }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
        }
    }
}
