package com.mdibrahim.expensetracker.ui.screens.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdibrahim.expensetracker.core.model.Account
import com.mdibrahim.expensetracker.core.model.AccountType
import com.mdibrahim.expensetracker.core.model.Category
import com.mdibrahim.expensetracker.core.model.Transaction
import com.mdibrahim.expensetracker.core.model.TransactionDirection
import com.mdibrahim.expensetracker.core.model.TransactionSource
import com.mdibrahim.expensetracker.core.util.minorToMajor
import com.mdibrahim.expensetracker.core.util.rupeesToMinor
import com.mdibrahim.expensetracker.data.repository.AccountRepository
import com.mdibrahim.expensetracker.data.repository.CategoryRepository
import com.mdibrahim.expensetracker.data.repository.TransactionRepository
import com.mdibrahim.expensetracker.ui.components.epochRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class EditTransactionUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    /** Walking the month's uncategorised transactions: Save & next / Skip. */
    val reviewMode: Boolean = false,
    val source: TransactionSource = TransactionSource.MANUAL,
    /** From the bank SMS; shown read-only. */
    val counterparty: String? = null,
    val amountText: String = "",
    val amountError: String? = null,
    val direction: TransactionDirection = TransactionDirection.DEBIT,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val dateTime: LocalDateTime = LocalDateTime.now(),
    val note: String = "",
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** Set once saved or deleted; the screen navigates back when it sees this. */
    val isDone: Boolean = false,
    /** Review mode: the next transaction to open instead of going back. */
    val nextReviewId: Long? = null,
)

@HiltViewModel
class EditTransactionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    categories: CategoryRepository,
) : ViewModel() {

    private val transactionId: Long = savedStateHandle.get<Long>(ARG_ID) ?: NEW_ID
    private val reviewMode: Boolean = savedStateHandle.get<Boolean>(ARG_REVIEW) ?: false
    private var existing: Transaction? = null

    private val _uiState = MutableStateFlow(
        EditTransactionUiState(isNew = transactionId == NEW_ID, reviewMode = reviewMode),
    )
    val uiState: StateFlow<EditTransactionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(accounts.observeAccounts(), categories.observeCategories(), ::Pair)
                .collect { (accountList, categoryList) ->
                    _uiState.update { state ->
                        state.copy(
                            accounts = accountList,
                            categories = categoryList,
                            // Default a new entry to the first account.
                            accountId = state.accountId ?: accountList.firstOrNull()?.id,
                        )
                    }
                }
        }
        viewModelScope.launch {
            val loaded = if (transactionId != NEW_ID) transactions.get(transactionId) else null
            existing = loaded
            _uiState.update { state ->
                if (loaded == null) {
                    state.copy(isLoading = false)
                } else {
                    state.copy(
                        isLoading = false,
                        source = loaded.source,
                        counterparty = loaded.counterparty,
                        amountText = minorToMajor(loaded.amountMinor).toPlainString(),
                        direction = loaded.direction,
                        accountId = loaded.accountId,
                        categoryId = loaded.categoryId,
                        dateTime = LocalDateTime.ofInstant(loaded.occurredAt, ZoneId.systemDefault()),
                        note = loaded.note.orEmpty(),
                    )
                }
            }
        }
    }

    fun onAmountChange(text: String) = _uiState.update { it.copy(amountText = text, amountError = null) }
    fun onDirectionChange(direction: TransactionDirection) = _uiState.update { it.copy(direction = direction) }
    fun onAccountChange(id: Long) = _uiState.update { it.copy(accountId = id) }

    /** Tapping the selected category again clears it. */
    fun onCategoryChange(id: Long) =
        _uiState.update { it.copy(categoryId = if (it.categoryId == id) null else id) }

    fun onNoteChange(note: String) = _uiState.update { it.copy(note = note) }

    /** Changes the day but keeps the time of day. */
    fun onDateChange(date: LocalDate) =
        _uiState.update { it.copy(dateTime = date.atTime(it.dateTime.toLocalTime())) }

    fun save() {
        val state = _uiState.value
        val amountMinor = rupeesToMinor(state.amountText)
        val error = when {
            amountMinor == null -> "Enter an amount, like 450 or 450.50"
            // Direction carries the sign; a negative amount would invert it.
            amountMinor <= 0 -> "Amount must be more than zero"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(amountError = error) }
            return
        }

        viewModelScope.launch {
            // Release builds start with no accounts; don't make the user set one up
            // before their first entry.
            val accountId = state.accountId ?: accounts.getOrCreate("Cash", AccountType.CASH)
            val occurredAt = state.dateTime.atZone(ZoneId.systemDefault()).toInstant()
            val note = state.note.trim().ifBlank { null }
            val base = existing
            var saved: Transaction? = null
            if (base == null) {
                transactions.add(
                    Transaction(
                        amountMinor = amountMinor!!,
                        direction = state.direction,
                        occurredAt = occurredAt,
                        accountId = accountId,
                        categoryId = state.categoryId,
                        note = note,
                    ),
                )
            } else {
                // copy() keeps source, externalId, counterparty and balance, so an
                // edited SMS transaction still blocks the same SMS from re-importing.
                saved = base.copy(
                    amountMinor = amountMinor!!,
                    direction = state.direction,
                    occurredAt = occurredAt,
                    accountId = accountId,
                    categoryId = state.categoryId,
                    note = note,
                )
                transactions.update(saved)
            }
            finish(after = saved)
        }
    }

    /** Review mode: leave this one as-is and move to the next. */
    fun skip() {
        viewModelScope.launch { finish(after = existing) }
    }

    /** In review mode, move on to the next uncategorised transaction in the same month. */
    private suspend fun finish(after: Transaction?) {
        val next = if (reviewMode && after != null) {
            val range = YearMonth.from(after.occurredAt.atZone(ZoneId.systemDefault())).epochRange()
            transactions.nextNeedingReview(range.startMillis, range.endMillis, after)
        } else {
            null
        }
        _uiState.update { it.copy(isDone = next == null, nextReviewId = next) }
    }

    fun delete() {
        val base = existing ?: return
        viewModelScope.launch {
            transactions.delete(base)
            _uiState.update { it.copy(isDone = true) }
        }
    }

    companion object {
        const val ARG_ID = "id"
        const val ARG_REVIEW = "review"
        const val NEW_ID = -1L
    }
}
