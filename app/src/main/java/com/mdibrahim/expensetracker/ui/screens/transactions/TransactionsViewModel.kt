package com.mdibrahim.expensetracker.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdibrahim.expensetracker.core.model.Transaction
import com.mdibrahim.expensetracker.core.model.TransactionDirection
import com.mdibrahim.expensetracker.data.repository.AccountRepository
import com.mdibrahim.expensetracker.data.repository.CategoryRepository
import com.mdibrahim.expensetracker.data.repository.TransactionRepository
import com.mdibrahim.expensetracker.ui.components.SelectedMonth
import com.mdibrahim.expensetracker.ui.components.TransactionListItem
import com.mdibrahim.expensetracker.ui.components.buildListItems
import com.mdibrahim.expensetracker.ui.components.epochRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

enum class TransactionFilter(val label: String) {
    ALL("All"),
    OUT("Money out"),
    IN("Money in"),
    TO_REVIEW("To review"),
    ;

    fun matches(t: Transaction) = when (this) {
        ALL -> true
        OUT -> t.direction == TransactionDirection.DEBIT
        IN -> t.direction == TransactionDirection.CREDIT
        TO_REVIEW -> t.needsReview
    }
}

data class DayGroup(val date: LocalDate, val items: List<TransactionListItem>)

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val filter: TransactionFilter = TransactionFilter.ALL,
    val spentMinor: Long = 0,
    val receivedMinor: Long = 0,
    val toReviewCount: Int = 0,
    val days: List<DayGroup> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val selectedMonth: SelectedMonth,
) : ViewModel() {

    private val filter = MutableStateFlow(TransactionFilter.ALL)

    private val monthTransactions = selectedMonth.month.flatMapLatest { month ->
        val range = month.epochRange()
        transactionRepository.observeTransactionsBetween(range.startMillis, range.endMillis)
            .map { month to it }
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        monthTransactions,
        accountRepository.observeAccounts(),
        categoryRepository.observeCategories(),
        filter,
    ) { (month, transactions), accounts, categories, activeFilter ->
        val zone = ZoneId.systemDefault()
        TransactionsUiState(
            isLoading = false,
            month = month,
            filter = activeFilter,
            spentMinor = transactions.filter { it.direction == TransactionDirection.DEBIT }.sumOf { it.amountMinor },
            receivedMinor = transactions.filter { it.direction == TransactionDirection.CREDIT }.sumOf { it.amountMinor },
            toReviewCount = transactions.count { it.needsReview },
            // Repository already orders newest first, and groupBy keeps that order.
            days = buildListItems(transactions.filter(activeFilter::matches), accounts, categories)
                .groupBy { it.transaction.occurredAt.atZone(zone).toLocalDate() }
                .map { (date, items) -> DayGroup(date, items) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun setFilter(value: TransactionFilter) {
        filter.value = value
    }

    fun previousMonth() = selectedMonth.previous()
    fun nextMonth() = selectedMonth.next()
}
