package com.mdibrahim.expensetracker.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdibrahim.expensetracker.core.model.TransactionDirection
import com.mdibrahim.expensetracker.data.repository.AccountRepository
import com.mdibrahim.expensetracker.data.repository.CategoryRepository
import com.mdibrahim.expensetracker.data.repository.TransactionRepository
import com.mdibrahim.expensetracker.ui.components.TransactionListItem
import com.mdibrahim.expensetracker.ui.components.buildListItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val spentThisMonthMinor: Long = 0,
    val receivedThisMonthMinor: Long = 0,
    val recent: List<TransactionListItem> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val month = YearMonth.now(zone)
    private val monthStart = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private val monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    val uiState: StateFlow<HomeUiState> = combine(
        transactionRepository.observeTransactionsBetween(monthStart, monthEnd),
        transactionRepository.observeTransactions(),
        accountRepository.observeAccounts(),
        categoryRepository.observeCategories(),
    ) { thisMonth, all, accounts, categories ->
        HomeUiState(
            isLoading = false,
            spentThisMonthMinor = thisMonth
                .filter { it.direction == TransactionDirection.DEBIT }
                .sumOf { it.amountMinor },
            receivedThisMonthMinor = thisMonth
                .filter { it.direction == TransactionDirection.CREDIT }
                .sumOf { it.amountMinor },
            recent = buildListItems(all.take(RECENT_COUNT), accounts, categories),
        )
    }.stateIn(
        scope = viewModelScope,
        // Survives brief config changes without re-querying, but stops collecting
        // when the screen is genuinely gone.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    private companion object {
        const val RECENT_COUNT = 5
    }
}
