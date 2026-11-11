package com.mdibrahim.expensetracker.ui.screens.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdibrahim.expensetracker.core.model.Account
import com.mdibrahim.expensetracker.core.model.AccountType
import com.mdibrahim.expensetracker.core.model.Category
import com.mdibrahim.expensetracker.core.model.Debt
import com.mdibrahim.expensetracker.core.model.DebtKind
import com.mdibrahim.expensetracker.core.model.LENDING_CATEGORY_NAME
import com.mdibrahim.expensetracker.core.model.Person
import com.mdibrahim.expensetracker.core.model.PersonSummary
import com.mdibrahim.expensetracker.core.model.Repayment
import com.mdibrahim.expensetracker.core.model.equalShareMinor
import com.mdibrahim.expensetracker.core.model.Transaction
import com.mdibrahim.expensetracker.core.model.TransactionDirection
import com.mdibrahim.expensetracker.core.model.TransactionSource
import com.mdibrahim.expensetracker.core.util.minorToMajor
import com.mdibrahim.expensetracker.core.util.rupeesToMinor
import com.mdibrahim.expensetracker.data.repository.AccountRepository
import com.mdibrahim.expensetracker.data.repository.CategoryRepository
import com.mdibrahim.expensetracker.data.repository.LendingRepository
import com.mdibrahim.expensetracker.data.repository.PayeeRuleRepository
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

/** A friend's share of this payment, as edited on the form (saved with the transaction). */
data class DraftDebt(
    val personId: Long,
    val amountMinor: Long,
    val kind: DebtKind,
    val dueDate: LocalDate?,
)

data class EditTransactionUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    /** Walking the month's uncategorised transactions: Save & next / Skip. */
    val reviewMode: Boolean = false,
    val source: TransactionSource = TransactionSource.MANUAL,
    /** From the bank SMS; shown read-only. */
    val counterparty: String? = null,
    /** "Always put this payee in this category" -- a payee rule on save. */
    val rememberPayee: Boolean = false,
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
    val people: List<Person> = emptyList(),
    /** Debits: what friends owe of this payment (split shares or a loan). */
    val debts: List<DraftDebt> = emptyList(),
    /** Credits: the friend paying the user back with this money, if any. */
    val repaymentPersonId: Long? = null,
    /** The friend this SMS payee is linked to, with what they owe -- for one-tap suggestions. */
    val suggestedPerson: PersonSummary? = null,
    val lendingError: String? = null,
) {
    val lendingCategoryId: Long? get() = categories.firstOrNull { it.name == LENDING_CATEGORY_NAME }?.id
    fun personName(id: Long): String = people.firstOrNull { it.id == id }?.name ?: "Someone"
}

@HiltViewModel
class EditTransactionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    categories: CategoryRepository,
    private val payeeRules: PayeeRuleRepository,
    private val lending: LendingRepository,
) : ViewModel() {

    private val transactionId: Long = savedStateHandle.get<Long>(ARG_ID) ?: NEW_ID
    private val reviewMode: Boolean = savedStateHandle.get<Boolean>(ARG_REVIEW) ?: false
    private var existing: Transaction? = null

    /** Whether a payee rule existed when the screen opened (so switching off forgets it). */
    private var hadPayeeRule = false

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
            lending.observePeople().collect { people -> _uiState.update { it.copy(people = people) } }
        }
        viewModelScope.launch {
            val loaded = if (transactionId != NEW_ID) transactions.get(transactionId) else null
            existing = loaded
            hadPayeeRule = loaded?.counterparty?.let { payeeRules.categoryFor(it) } != null
            val debts = loaded?.let { lending.debtsFor(it.id) }.orEmpty()
                .map { DraftDebt(it.personId, it.amountMinor, it.kind, it.dueDate) }
            val repaymentPersonId = loaded?.let { lending.repaymentFor(it.id) }?.personId
            val suggested = loaded?.counterparty?.let { lending.personForPayee(it) }
                ?.let { personId -> lending.summaries().firstOrNull { it.person.id == personId } }
            _uiState.update {
                it.copy(debts = debts, repaymentPersonId = repaymentPersonId, suggestedPerson = suggested)
            }
            _uiState.update { state ->
                if (loaded == null) {
                    state.copy(isLoading = false)
                } else {
                    state.copy(
                        isLoading = false,
                        source = loaded.source,
                        counterparty = loaded.counterparty,
                        rememberPayee = hadPayeeRule,
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
    /** Split/loan and repayment only make sense for one direction each, so switching clears them. */
    fun onDirectionChange(direction: TransactionDirection) = _uiState.update {
        if (it.direction == direction) it else it.copy(direction = direction, debts = emptyList(), repaymentPersonId = null)
    }
    fun onAccountChange(id: Long) = _uiState.update { it.copy(accountId = id) }

    /** Tapping the selected category again clears it. */
    fun onCategoryChange(id: Long) =
        _uiState.update { it.copy(categoryId = if (it.categoryId == id) null else id) }

    fun onNoteChange(note: String) = _uiState.update { it.copy(note = note) }
    fun onRememberPayeeChange(remember: Boolean) = _uiState.update { it.copy(rememberPayee = remember) }

    // ---- Lending ----

    /** Equal split between the user and [personIds]; the user picks the category for their share. */
    fun applySplit(personIds: List<Long>, due: LocalDate?) {
        val amount = currentAmount() ?: return
        val share = equalShareMinor(amount, personIds.size)
        _uiState.update { s ->
            s.copy(debts = personIds.map { DraftDebt(it, share, DebtKind.SPLIT, due) }, lendingError = null)
        }
    }

    /** The whole payment was lent to one person: not spending, so it goes to Lending. */
    fun applyLoan(personId: Long, due: LocalDate?) {
        val amount = currentAmount() ?: return
        _uiState.update { s ->
            s.copy(
                debts = listOf(DraftDebt(personId, amount, DebtKind.LOAN, due)),
                categoryId = s.lendingCategoryId ?: s.categoryId,
                lendingError = null,
            )
        }
    }

    fun clearDebts() = _uiState.update { s ->
        s.copy(debts = emptyList(), categoryId = s.categoryId.takeUnless { it == s.lendingCategoryId })
    }

    fun setRepayment(personId: Long) = _uiState.update { s ->
        s.copy(repaymentPersonId = personId, categoryId = s.lendingCategoryId ?: s.categoryId)
    }

    fun clearRepayment() = _uiState.update { s ->
        s.copy(repaymentPersonId = null, categoryId = s.categoryId.takeUnless { it == s.lendingCategoryId })
    }

    fun addPerson(name: String, onAdded: (Long) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch { onAdded(lending.addPerson(name, phone = null)) }
    }

    private fun currentAmount(): Long? {
        val amount = rupeesToMinor(_uiState.value.amountText)?.takeIf { it > 0 }
        if (amount == null) _uiState.update { it.copy(lendingError = "Enter the amount first.") }
        return amount
    }

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
        // A loan follows the amount if it was edited after lending; split shares don't
        // rescale, so they must still fit inside what was paid.
        val debts = state.debts.map { if (it.kind == DebtKind.LOAN) it.copy(amountMinor = amountMinor!!) else it }
        if (debts.sumOf { it.amountMinor } > amountMinor!!) {
            _uiState.update { it.copy(lendingError = "Friends' shares add up to more than the amount. Redo the split.") }
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
            val savedId: Long
            if (base == null) {
                savedId = transactions.add(
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
                savedId = base.id
            }
            saveLending(state, savedId, debts, amountMinor, occurredAt, note)
            updatePayeeRule(state)
            finish(after = saved)
        }
    }

    /** Writes this transaction's debts or repayment, and links the SMS payee to the friend. */
    private suspend fun saveLending(
        state: EditTransactionUiState,
        transactionId: Long,
        debts: List<DraftDebt>,
        amountMinor: Long,
        occurredAt: java.time.Instant,
        note: String?,
    ) {
        val isDebit = state.direction == TransactionDirection.DEBIT
        val categoryName = state.categories.firstOrNull { it.id == state.categoryId }
            ?.name?.takeUnless { it == LENDING_CATEGORY_NAME }
        lending.replaceDebts(
            transactionId,
            if (!isDebit) emptyList() else debts.map {
                Debt(
                    personId = it.personId,
                    transactionId = transactionId,
                    amountMinor = it.amountMinor,
                    kind = it.kind,
                    // Shown in reminders: "Dinner (5 Nov): ₹300".
                    description = note ?: if (it.kind == DebtKind.LOAN) "Loan" else categoryName,
                    occurredAt = occurredAt,
                    dueDate = it.dueDate,
                )
            },
        )
        val repayer = state.repaymentPersonId.takeIf { !isDebit }
        lending.replaceRepayment(
            transactionId,
            repayer?.let { Repayment(personId = it, transactionId = transactionId, amountMinor = amountMinor, occurredAt = occurredAt) },
        )
        // Remember whose UPI name this is, so next time it's a one-tap suggestion.
        // Not for splits: there the payee is the restaurant, not the friend.
        val linkedPerson = repayer ?: debts.singleOrNull { it.kind == DebtKind.LOAN }?.personId
        if (linkedPerson != null) state.counterparty?.let { lending.linkPayee(it, linkedPerson) }
    }

    /**
     * Saves or removes the payee rule. Runs after the transaction is saved; saving a
     * rule also categorises this payee's other waiting transactions, which the
     * review walk then skips.
     */
    private suspend fun updatePayeeRule(state: EditTransactionUiState) {
        val payee = state.counterparty ?: return
        val categoryId = state.categoryId
        when {
            state.rememberPayee && categoryId != null -> payeeRules.remember(payee, categoryId)
            !state.rememberPayee && hadPayeeRule -> payeeRules.forget(payee)
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
