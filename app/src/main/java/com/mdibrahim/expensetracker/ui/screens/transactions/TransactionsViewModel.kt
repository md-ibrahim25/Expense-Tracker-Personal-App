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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    /** Non-empty = selection mode. */
    val selectedIds: Set<Long> = emptySet(),
) {
    val isSelecting: Boolean get() = selectedIds.isNotEmpty()
    val visibleIds: List<Long> get() = days.flatMap { day -> day.items.map { it.transaction.id } }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val selectedMonth: SelectedMonth,
) : ViewModel() {

    private val filter = MutableStateFlow(TransactionFilter.ALL)
    private val selection = MutableStateFlow<Set<Long>>(emptySet())

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
        selection,
    ) { (month, transactions), accounts, categories, activeFilter, selected ->
        val zone = ZoneId.systemDefault()
        val visible = transactions.filter(activeFilter::matches)
        TransactionsUiState(
            isLoading = false,
            month = month,
            filter = activeFilter,
            spentMinor = transactions.filter { it.direction == TransactionDirection.DEBIT }.sumOf { it.amountMinor },
            receivedMinor = transactions.filter { it.direction == TransactionDirection.CREDIT }.sumOf { it.amountMinor },
            toReviewCount = transactions.count { it.needsReview },
            // Repository already orders newest first, and groupBy keeps that order.
            days = buildListItems(visible, accounts, categories)
                .groupBy { it.transaction.occurredAt.atZone(zone).toLocalDate() }
                .map { (date, items) -> DayGroup(date, items) },
            // Only what's on screen can be selected; drop ids that scrolled out of the
            // list (deleted elsewhere, filtered away) so Delete never hits hidden rows.
            selectedIds = selected intersect visible.map { it.id }.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun setFilter(value: TransactionFilter) {
        filter.value = value
        clearSelection()
    }

    fun previousMonth() {
        clearSelection()
        selectedMonth.previous()
    }

    fun nextMonth() {
        clearSelection()
        selectedMonth.next()
    }

    /** Long-press starts selection; in selection mode a tap toggles too. */
    fun toggleSelection(id: Long) = selection.update { if (id in it) it - id else it + id }

    fun selectAllVisible() = selection.update { uiState.value.visibleIds.toSet() }
    fun clearSelection() = selection.update { emptySet() }

    fun deleteSelected() {
        val ids = uiState.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            transactionRepository.deleteAll(ids)
            clearSelection()
        }
    }
}
