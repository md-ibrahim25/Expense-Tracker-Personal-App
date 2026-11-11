package com.mdibrahim.expensetracker.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdibrahim.expensetracker.core.model.Transaction
import com.mdibrahim.expensetracker.core.model.TransactionDirection
import com.mdibrahim.expensetracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val transactions: List<Transaction> = emptyList(),
    val totalSpentMinor: Long = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = transactionRepository.observeTransactions()
        .map { transactions ->
            HomeUiState(
                isLoading = false,
                transactions = transactions,
                totalSpentMinor = transactions
                    .filter { it.direction == TransactionDirection.DEBIT }
                    .sumOf { it.amountMinor },
            )
        }
        .stateIn(
            scope = viewModelScope,
            // Survives brief config changes without re-querying, but stops collecting
            // when the screen is genuinely gone.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )
}
