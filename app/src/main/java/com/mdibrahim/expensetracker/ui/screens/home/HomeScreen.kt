package com.mdibrahim.expensetracker.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.mdibrahim.expensetracker.ui.components.MonthSelector
import com.mdibrahim.expensetracker.ui.components.TransactionRow
import com.mdibrahim.expensetracker.ui.theme.LocalMoneyColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val AS_OF_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

@Composable
fun HomeScreen(
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onStartReview: (firstId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
            return@Box
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MonthSelector(uiState.month, viewModel::previousMonth, viewModel::nextMonth)
            }
            item { MonthSummaryCard(uiState) }
            if (uiState.balances.isNotEmpty()) {
                item { BalanceCard(uiState.balances) }
            }
            uiState.firstToReviewId?.let { firstId ->
                item { ReviewBanner(uiState.toReviewCount, onStartReview = { onStartReview(firstId) }) }
            }
            item { Text("Recent", style = MaterialTheme.typography.titleMedium) }
            if (uiState.recent.isEmpty()) {
                item {
                    Text(
                        text = "No transactions this month.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(uiState.recent, key = { it.transaction.id }) { item ->
                    Column {
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
private fun MonthSummaryCard(state: HomeUiState) {
    val moneyColors = LocalMoneyColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Label("Spent this month")
                Text(
                    text = formatMoney(state.spentMinor),
                    style = MaterialTheme.typography.headlineMedium,
                    color = moneyColors.expense,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Label("Received")
                    Text(formatMoney(state.receivedMinor), color = moneyColors.income)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Label("Net")
                    // Sign in text, not just colour.
                    val net = state.netMinor
                    Text(
                        text = (if (net < 0) "-" else "+") + formatMoney(kotlin.math.abs(net)),
                        color = if (net < 0) moneyColors.expense else moneyColors.income,
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(balances: List<BalanceLine>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("Current balance")
            balances.forEach { line ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(line.accountName)
                        Text(
                            "as of " + line.asOf.atZone(ZoneId.systemDefault()).format(AS_OF_FORMAT),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(formatMoney(line.balanceMinor), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ReviewBanner(count: Int, onStartReview: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (count == 1) "1 transaction to review" else "$count transactions to review",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Add a category and what it was for.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = onStartReview) { Text("Review") }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
