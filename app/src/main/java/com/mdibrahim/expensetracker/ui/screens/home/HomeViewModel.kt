package com.mdibrahim.expensetracker.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdibrahim.expensetracker.core.model.ownTotals
import com.mdibrahim.expensetracker.data.repository.LendingRepository
import com.mdibrahim.expensetracker.data.repository.AccountRepository
import com.mdibrahim.expensetracker.data.repository.CategoryRepository
import com.mdibrahim.expensetracker.data.repository.TransactionRepository
import com.mdibrahim.expensetracker.ui.components.SelectedMonth
import com.mdibrahim.expensetracker.ui.components.TransactionListItem
import com.mdibrahim.expensetracker.ui.components.buildListItems
import com.mdibrahim.expensetracker.ui.components.epochRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class BalanceLine(val accountName: String, val balanceMinor: Long, val asOf: Instant)

data class HomeUiState(
    val isLoading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val spentMinor: Long = 0,
    val receivedMinor: Long = 0,
    /** Bank-reported balances -- current, not tied to the selected month. */
    val balances: List<BalanceLine> = emptyList(),
    val toReviewCount: Int = 0,
    /** Total friends owe the user right now (not tied to the month). */
    val owedToYouMinor: Long = 0,
    val overdueCount: Int = 0,
    /** Oldest transaction this month needing review -- where the review walk starts. */
    val firstToReviewId: Long? = null,
    val recent: List<TransactionListItem> = emptyList(),
) {
    val netMinor: Long get() = receivedMinor - spentMinor
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    lendingRepository: LendingRepository,
    private val selectedMonth: SelectedMonth,
) : ViewModel() {

    private val monthTransactions = selectedMonth.month.flatMapLatest { month ->
        val range = month.epochRange()
        transactionRepository.observeTransactionsBetween(range.startMillis, range.endMillis)
            .map { month to it }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        monthTransactions,
        transactionRepository.observeLatestBalances(),
        accountRepository.observeAccounts(),
        categoryRepository.observeCategories(),
        combine(lendingRepository.observeOwedPerTransaction(), lendingRepository.observeSummaries(), ::Pair),
    ) { (month, transactions), balances, accounts, categories, (owedPerTransaction, summaries) ->
        val accountNames = accounts.associate { it.id to it.name }
        // Self transfer / Lending aren't spending, and friends' share of a split isn't yours.
        val excluded = categories.filter { it.excludeFromTotals }.map { it.id }.toSet()
        val totals = ownTotals(transactions, excluded, owedPerTransaction)
        val today = LocalDate.now()
        HomeUiState(
            isLoading = false,
            month = month,
            spentMinor = totals.spentMinor,
            receivedMinor = totals.receivedMinor,
            owedToYouMinor = summaries.sumOf { it.outstandingMinor.coerceAtLeast(0) },
            overdueCount = summaries.count { it.outstandingMinor > 0 && it.isOverdue(today) },
            balances = balances.map {
                BalanceLine(accountNames[it.accountId] ?: "Account", it.balanceMinor, it.asOf)
            },
            toReviewCount = transactions.count { it.needsReview },
            firstToReviewId = transactions.filter { it.needsReview }
                .minWithOrNull(compareBy({ it.occurredAt }, { it.id }))?.id,
            recent = buildListItems(transactions.take(RECENT_COUNT), accounts, categories),
        )
    }.stateIn(
        scope = viewModelScope,
        // Survives brief config changes without re-querying, but stops collecting
        // when the screen is genuinely gone.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun previousMonth() = selectedMonth.previous()
    fun nextMonth() = selectedMonth.next()

    private companion object {
        const val RECENT_COUNT = 5
    }
}
