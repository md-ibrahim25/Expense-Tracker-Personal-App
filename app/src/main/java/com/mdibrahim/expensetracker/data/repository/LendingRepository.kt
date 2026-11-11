package com.mdibrahim.expensetracker.data.repository

import com.mdibrahim.expensetracker.core.database.LendingDao
import com.mdibrahim.expensetracker.core.database.PersonEntity
import com.mdibrahim.expensetracker.core.database.PersonPayeeEntity
import com.mdibrahim.expensetracker.core.database.toDomain
import com.mdibrahim.expensetracker.core.database.toEntity
import com.mdibrahim.expensetracker.core.model.Debt
import com.mdibrahim.expensetracker.core.model.Person
import com.mdibrahim.expensetracker.core.model.PersonDetail
import com.mdibrahim.expensetracker.core.model.PersonSummary
import com.mdibrahim.expensetracker.core.model.Repayment
import com.mdibrahim.expensetracker.core.model.payeeKey
import com.mdibrahim.expensetracker.core.model.summarize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Friends, what they owe, and what they've paid back. */
interface LendingRepository {
    fun observePeople(): Flow<List<Person>>
    fun observeSummaries(): Flow<List<PersonSummary>>
    fun observePerson(personId: Long): Flow<PersonDetail?>

    /** transactionId -> paise of it that others owe (subtracted from the user's spending). */
    fun observeOwedPerTransaction(): Flow<Map<Long, Long>>

    /** One-shot, for the background reminder worker. */
    suspend fun summaries(): List<PersonSummary>

    suspend fun addPerson(name: String, phone: String?): Long
    suspend fun updatePerson(person: Person)
    suspend fun deletePerson(personId: Long)

    /** The person a UPI name in SMS was linked to, if any. */
    suspend fun personForPayee(payee: String): Long?
    suspend fun linkPayee(payee: String, personId: Long)

    suspend fun debtsFor(transactionId: Long): List<Debt>
    suspend fun repaymentFor(transactionId: Long): Repayment?
    suspend fun replaceDebts(transactionId: Long, debts: List<Debt>)
    suspend fun replaceRepayment(transactionId: Long, repayment: Repayment?)
    suspend fun recordCashRepayment(personId: Long, amountMinor: Long, at: Instant)
    suspend fun deleteRepayment(repaymentId: Long)
    suspend fun setDue(debtId: Long, due: LocalDate?)
}

class RoomLendingRepository @Inject constructor(
    private val dao: LendingDao,
) : LendingRepository {

    override fun observePeople(): Flow<List<Person>> =
        dao.observePeople().map { rows -> rows.map { it.toDomain() } }

    private val records = combine(
        observePeople(),
        dao.observeDebts().map { rows -> rows.map { it.toDomain() } },
        dao.observeRepayments().map { rows -> rows.map { it.toDomain() } },
        ::Triple,
    )

    override fun observeSummaries(): Flow<List<PersonSummary>> =
        records.map { (people, debts, repayments) -> people.map { summarize(it, debts, repayments) } }

    override fun observePerson(personId: Long): Flow<PersonDetail?> =
        records.map { (people, debts, repayments) ->
            val person = people.firstOrNull { it.id == personId } ?: return@map null
            PersonDetail(
                summary = summarize(person, debts, repayments),
                debts = debts.filter { it.personId == personId }.sortedByDescending { it.occurredAt },
                repayments = repayments.filter { it.personId == personId }.sortedByDescending { it.occurredAt },
            )
        }

    override fun observeOwedPerTransaction(): Flow<Map<Long, Long>> =
        dao.observeOwedPerTransaction().map { rows -> rows.associate { it.transactionId to it.amountMinor } }

    override suspend fun summaries(): List<PersonSummary> {
        val debts = dao.debts().map { it.toDomain() }
        val repayments = dao.repayments().map { it.toDomain() }
        return dao.people().map { summarize(it.toDomain(), debts, repayments) }
    }

    override suspend fun addPerson(name: String, phone: String?): Long =
        dao.insertPerson(PersonEntity(name = name.trim(), phone = phone?.normalisePhone()))

    override suspend fun updatePerson(person: Person) =
        dao.updatePerson(person.copy(name = person.name.trim(), phone = person.phone?.normalisePhone()).toEntity())

    override suspend fun deletePerson(personId: Long) = dao.deletePerson(personId)

    override suspend fun personForPayee(payee: String): Long? = dao.personForPayee(payeeKey(payee))
    override suspend fun linkPayee(payee: String, personId: Long) =
        dao.linkPayee(PersonPayeeEntity(payeeKey(payee), personId))

    override suspend fun debtsFor(transactionId: Long): List<Debt> = dao.debtsFor(transactionId).map { it.toDomain() }
    override suspend fun repaymentFor(transactionId: Long): Repayment? = dao.repaymentFor(transactionId)?.toDomain()

    override suspend fun replaceDebts(transactionId: Long, debts: List<Debt>) =
        dao.replaceDebts(transactionId, debts.map { it.copy(id = 0, transactionId = transactionId).toEntity() })

    override suspend fun replaceRepayment(transactionId: Long, repayment: Repayment?) =
        dao.replaceRepayment(transactionId, repayment?.copy(id = 0, transactionId = transactionId)?.toEntity())

    override suspend fun recordCashRepayment(personId: Long, amountMinor: Long, at: Instant) {
        require(amountMinor > 0) { "repayment must be positive" }
        dao.insertRepayment(Repayment(personId = personId, transactionId = null, amountMinor = amountMinor, occurredAt = at).toEntity())
    }

    override suspend fun deleteRepayment(repaymentId: Long) = dao.deleteRepayment(repaymentId)
    override suspend fun setDue(debtId: Long, due: LocalDate?) = dao.setDue(debtId, due?.toEpochDay())

    /** Keep digits only; assume India (+91) for a bare 10-digit mobile number. */
    private fun String.normalisePhone(): String? {
        val digits = filter(Char::isDigit)
        return when {
            digits.isEmpty() -> null
            digits.length == 10 -> "91$digits"
            else -> digits
        }
    }
}
