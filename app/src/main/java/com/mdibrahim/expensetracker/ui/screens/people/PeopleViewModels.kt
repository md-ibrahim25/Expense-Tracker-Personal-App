package com.mdibrahim.expensetracker.ui.screens.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdibrahim.expensetracker.core.model.Person
import com.mdibrahim.expensetracker.core.model.PersonDetail
import com.mdibrahim.expensetracker.core.model.PersonSummary
import com.mdibrahim.expensetracker.core.util.rupeesToMinor
import com.mdibrahim.expensetracker.data.repository.LendingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class PeopleUiState(
    val isLoading: Boolean = true,
    val people: List<PersonSummary> = emptyList(),
) {
    val totalOwedMinor: Long get() = people.sumOf { it.outstandingMinor.coerceAtLeast(0) }
}

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val lending: LendingRepository,
) : ViewModel() {

    val uiState: StateFlow<PeopleUiState> = lending.observeSummaries().map { summaries ->
        val today = LocalDate.now()
        PeopleUiState(
            isLoading = false,
            // Overdue first, then whoever owes most; settled friends at the bottom.
            people = summaries.sortedWith(
                compareByDescending<PersonSummary> { it.outstandingMinor > 0 && it.isOverdue(today) }
                    .thenByDescending { it.outstandingMinor }
                    .thenBy { it.person.name.lowercase() },
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUiState())

    fun addPerson(name: String, phone: String) {
        if (name.isBlank()) return
        viewModelScope.launch { lending.addPerson(name, phone.ifBlank { null }) }
    }
}

data class PersonUiState(
    val isLoading: Boolean = true,
    val detail: PersonDetail? = null,
    val isDeleted: Boolean = false,
)

@HiltViewModel
class PersonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val lending: LendingRepository,
) : ViewModel() {

    private val personId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_PERSON_ID))
    private val deleted = MutableStateFlow(false)
    val isDeleted: StateFlow<Boolean> = deleted.asStateFlow()

    val uiState: StateFlow<PersonUiState> = lending.observePerson(personId)
        .map { PersonUiState(isLoading = false, detail = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonUiState())

    fun setDue(debtId: Long, due: LocalDate?) = viewModelScope.launch { lending.setDue(debtId, due) }

    /** Returns false if [amountText] isn't a valid positive amount. */
    fun recordCashRepayment(amountText: String): Boolean {
        val amount = rupeesToMinor(amountText)?.takeIf { it > 0 } ?: return false
        viewModelScope.launch { lending.recordCashRepayment(personId, amount, Instant.now()) }
        return true
    }

    fun deleteRepayment(repaymentId: Long) = viewModelScope.launch { lending.deleteRepayment(repaymentId) }

    fun updatePerson(name: String, phone: String) {
        if (name.isBlank()) return
        viewModelScope.launch { lending.updatePerson(Person(id = personId, name = name, phone = phone.ifBlank { null })) }
    }

    fun deletePerson() = viewModelScope.launch {
        lending.deletePerson(personId)
        deleted.value = true
    }

    companion object {
        const val ARG_PERSON_ID = "personId"
    }
}
