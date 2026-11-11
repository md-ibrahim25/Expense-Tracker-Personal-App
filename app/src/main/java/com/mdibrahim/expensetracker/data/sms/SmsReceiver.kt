package com.mdibrahim.expensetracker.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives every incoming SMS (needs RECEIVE_SMS) and hands bank ones to [SmsImporter].
 *
 * Uses a Hilt EntryPoint rather than @AndroidEntryPoint: with the Hilt Gradle plugin,
 * an @AndroidEntryPoint BroadcastReceiver must call super.onReceive(), which Kotlin
 * rejects because BroadcastReceiver.onReceive is abstract.
 */
class SmsReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SmsReceiverEntryPoint {
        fun smsImporter(): SmsImporter
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        // A long SMS arrives as several parts; the inbox stores them joined, so join
        // here too or the dedupe hash wouldn't match a later inbox import.
        val sender = parts.first().originatingAddress
        val body = parts.joinToString("") { it.messageBody.orEmpty() }
        val receivedAt = System.currentTimeMillis()

        val importer = EntryPointAccessors
            .fromApplication(context.applicationContext, SmsReceiverEntryPoint::class.java)
            .smsImporter()

        // goAsync keeps the process alive past onReceive while the DB write finishes.
        val pending = goAsync()
        scope.launch {
            try {
                importer.import(sender, body, receivedAt)
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Failed to import SMS", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
