package com.mdibrahim.expensetracker.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdibrahim.expensetracker.core.model.TransactionDirection
import com.mdibrahim.expensetracker.data.repository.AccountRepository
import com.mdibrahim.expensetracker.data.repository.CategoryRepository
import com.mdibrahim.expensetracker.data.repository.TransactionRepository
import com.mdibrahim.expensetracker.ui.components.TransactionListItem
import com.mdibrahim.expensetracker.ui.components.buildListItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** null = show both directions. */
typealias DirectionFilter = TransactionDirection?

data class DayGroup(val date: LocalDate, val items: List<TransactionListItem>)

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val filter: DirectionFilter = null,
    val days: List<DayGroup> = emptyList(),
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val filter = MutableStateFlow<DirectionFilter>(null)

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionRepository.observeTransactions(),
        accountRepository.observeAccounts(),
        categoryRepository.observeCategories(),
        filter,
    ) { transactions, accounts, categories, direction ->
        val zone = ZoneId.systemDefault()
        val visible = transactions.filter { direction == null || it.direction == direction }
        TransactionsUiState(
            isLoading = false,
            filter = direction,
            // Repository already orders newest first, and groupBy keeps that order.
            days = buildListItems(visible, accounts, categories)
                .groupBy { it.transaction.occurredAt.atZone(zone).toLocalDate() }
                .map { (date, items) -> DayGroup(date, items) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun setFilter(direction: DirectionFilter) {
        filter.value = direction
    }
}
