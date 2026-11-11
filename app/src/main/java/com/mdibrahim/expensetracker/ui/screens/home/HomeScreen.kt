package com.mdibrahim.expensetracker.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdibrahim.expensetracker.core.util.formatMoney
import com.mdibrahim.expensetracker.ui.components.AnimatedMoney
import com.mdibrahim.expensetracker.ui.components.MonthSelector
import com.mdibrahim.expensetracker.ui.components.TransactionRow
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalConfiguration

private val AS_OF_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

@Composable
fun HomeScreen(
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onStartReview: (firstId: Long) -> Unit,
    onSeeAll: () -> Unit,
    onOpenPeople: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // The FAB shrinks to an icon once the user scrolls, so it doesn't cover rows.
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
            return@Box
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Greeting() }
            item { MonthSelector(uiState.month, viewModel::previousMonth, viewModel::nextMonth) }
            item { SpendingHero(uiState) }
            if (uiState.balances.isNotEmpty()) {
                item { BalanceCard(uiState.balances) }
            }
            if (uiState.owedToYouMinor > 0) {
                item { OwedCard(uiState.owedToYouMinor, uiState.overdueCount, onOpenPeople) }
            }
            item {
                AnimatedVisibility(
                    visible = uiState.firstToReviewId != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    ReviewCard(
                        count = uiState.toReviewCount,
                        onStartReview = { uiState.firstToReviewId?.let(onStartReview) },
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onSeeAll) {
                        Text("See all")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, Modifier.size(16.dp))
                    }
                }
            }
            if (uiState.recent.isEmpty()) {
                item { EmptyMonth(uiState.month) }
            } else {
                items(uiState.recent, key = { it.transaction.id }) { item ->
                    TransactionRow(
                        item = item,
                        onClick = { onOpenTransaction(item.transaction.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAddTransaction,
            expanded = fabExpanded,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun Greeting() {
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Column(Modifier.padding(top = 8.dp)) {
        Text(greeting, style = MaterialTheme.typography.headlineMedium)
        Text(
            "Here's where your money went.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The month's headline numbers on a gradient. Primary and tertiary sit at the same
 * tone in Material's scheme and both pair with on-colours of the same tone, so
 * onPrimary stays readable across the whole gradient -- including with dynamic colour.
 * On this card the +/- signs carry direction; there's no red/green here.
 */
@Composable
private fun SpendingHero(state: HomeUiState) {
    val scheme = MaterialTheme.colorScheme
    val monthName = state.month.month.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)))
            .padding(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column {
                Text("Spent in $monthName", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
                AnimatedMoney(
                    amountMinor = state.spentMinor,
                    style = MaterialTheme.typography.displaySmall,
                    color = scheme.onPrimary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroStat(
                    icon = Icons.Filled.SouthWest,
                    label = "Received",
                    amountMinor = state.receivedMinor,
                    prefix = "+",
                    modifier = Modifier.weight(1f),
                )
                val net = state.netMinor
                HeroStat(
                    icon = Icons.Filled.SwapVert,
                    label = "Net",
                    amountMinor = kotlin.math.abs(net),
                    prefix = if (net < 0) "-" else "+",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroStat(
    icon: ImageVector,
    label: String,
    amountMinor: Long,
    prefix: String,
    modifier: Modifier = Modifier,
) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Row(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(onPrimary.copy(alpha = 0.14f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = onPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = onPrimary)
            AnimatedMoney(
                amountMinor = amountMinor,
                prefix = prefix,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = onPrimary,
            )
        }
    }
}

@Composable
private fun BalanceCard(balances: List<BalanceLine>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            balances.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.AccountBalance)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(line.accountName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Balance as of " + line.asOf.atZone(ZoneId.systemDefault()).format(AS_OF_FORMAT),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        formatMoney(line.balanceMinor),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun OwedCard(owedMinor: Long, overdueCount: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Handshake)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Owed to you", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (overdueCount > 0) "$overdueCount overdue — tap to remind" else "Tap to see who owes what",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatMoney(owedMinor), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReviewCard(count: Int, onStartReview: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (count == 1) "1 to review" else "$count to review",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "Add a category and what it was for.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            FilledTonalButton(onClick = onStartReview) { Text("Review") }
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    Box(
        Modifier
            .size(42.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun EmptyMonth(month: YearMonth) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.ReceiptLong,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (month == YearMonth.now()) "Nothing yet this month" else "No transactions this month",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
