package com.mdibrahim.expensetracker.data.sms

import android.content.Context
import android.provider.Telephony
import com.mdibrahim.expensetracker.core.model.AccountType
import com.mdibrahim.expensetracker.core.model.Transaction
import com.mdibrahim.expensetracker.core.model.TransactionSource
import com.mdibrahim.expensetracker.data.repository.AccountRepository
import com.mdibrahim.expensetracker.data.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class InboxImportResult(val bankMessages: Int, val added: Int)

/**
 * Turns bank SMS into transactions. Used both for live SMS ([SmsReceiver]) and for
 * back-filling from the inbox, and safe to run repeatedly: each SMS body is hashed
 * into Transaction.externalId, which is unique in the database.
 */
@Singleton
class SmsImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
) {

    /** Returns true if a new transaction was recorded. */
    suspend fun import(sender: String?, body: String, receivedAtMillis: Long): Boolean {
        if (!CanaraSmsParser.isFromCanara(sender, body)) return false
        val parsed = CanaraSmsParser.parse(body) ?: return false

        // One app account per bank account number seen in SMS, so two Canara
        // accounts don't get merged.
        val accountName = parsed.accountSuffix?.let { "Canara Bank ••$it" } ?: "Canara Bank"
        val accountId = accounts.getOrCreate(accountName, AccountType.BANK)

        val transaction = Transaction(
                amountMinor = parsed.amountMinor,
                direction = parsed.direction,
                occurredAt = Instant.ofEpochMilli(receivedAtMillis),
                accountId = accountId,
                // note stays empty: "what was it for" is the user's to fill in on review.
                counterparty = parsed.counterparty,
                balanceAfterMinor = parsed.balanceMinor,
                source = TransactionSource.SMS,
                externalId = smsId(body),
        )
        val added = transactions.addIfNew(transaction)
        // Seen before: backfill details an older version of the parser didn't record
        // (e.g. balance, added in schema v3).
        if (!added) transactions.fillMissingCapturedDetails(transaction)
        return added
    }

    /** Scans the SMS inbox (needs READ_SMS) and imports every Canara transaction. */
    suspend fun importInbox(): InboxImportResult = withContext(Dispatchers.IO) {
        var bankMessages = 0
        var added = 0
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, projection, null, null, "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                val sender = cursor.getString(addressCol)
                val body = cursor.getString(bodyCol) ?: continue
                if (!CanaraSmsParser.isFromCanara(sender, body)) continue
                bankMessages++
                if (import(sender, body, cursor.getLong(dateCol))) added++
            }
        }
        InboxImportResult(bankMessages, added)
    }

    /**
     * Identity of an SMS = hash of its body. Bank bodies carry a reference number
     * and running balance, so two genuinely different transactions don't collide,
     * while the same SMS seen live and later from the inbox does.
     */
    private fun smsId(body: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(body.trim().toByteArray())
        return "sms:" + digest.joinToString("") { "%02x".format(it) }
    }
}
