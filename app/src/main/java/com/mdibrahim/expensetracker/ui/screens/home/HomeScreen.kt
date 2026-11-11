package com.mdibrahim.expensetracker.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import com.mdibrahim.expensetracker.ui.components.TransactionRow
import com.mdibrahim.expensetracker.ui.theme.LocalMoneyColors

@Composable
fun HomeScreen(
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            MonthSummaryCard(uiState.spentThisMonthMinor, uiState.receivedThisMonthMinor)

            Spacer(Modifier.height(16.dp))
            Text("Recent", style = MaterialTheme.typography.titleMedium)

            if (uiState.recent.isEmpty()) {
                Text(
                    text = "No transactions yet. Tap + to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                LazyColumn {
                    items(uiState.recent, key = { it.transaction.id }) { item ->
                        TransactionRow(item, onClick = { onOpenTransaction(item.transaction.id) })
                        HorizontalDivider()
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
private fun MonthSummaryCard(spentMinor: Long, receivedMinor: Long) {
    val moneyColors = LocalMoneyColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Spent this month",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatMoney(spentMinor),
                    style = MaterialTheme.typography.headlineSmall,
                    color = moneyColors.expense,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Received",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatMoney(receivedMinor),
                    style = MaterialTheme.typography.titleMedium,
                    color = moneyColors.income,
                )
            }
        }
    }
}
