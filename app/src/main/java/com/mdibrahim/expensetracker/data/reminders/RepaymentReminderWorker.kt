package com.mdibrahim.expensetracker.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mdibrahim.expensetracker.MainActivity
import com.mdibrahim.expensetracker.R
import com.mdibrahim.expensetracker.core.model.PersonSummary
import com.mdibrahim.expensetracker.core.model.shouldRemind
import com.mdibrahim.expensetracker.core.util.formatMoney
import com.mdibrahim.expensetracker.data.repository.LendingRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Once a day: for each friend whose next due date is today -- or overdue, every
 * third day -- post "Ravi owes you ₹2,300". Tapping opens their page, where Remind
 * sends the WhatsApp message. Uses a Hilt EntryPoint like the other background parts.
 */
class RepaymentReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun lending(): LendingRepository
    }

    override suspend fun doWork(): Result {
        val lending = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).lending()
        val today = LocalDate.now()
        lending.summaries()
            .filter { it.outstandingMinor > 0 }
            .forEach { summary ->
                val due = summary.nextDue ?: return@forEach
                if (shouldRemind(due, today)) notify(applicationContext, summary, due, today)
            }
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "repayment_reminders"
        private const val WORK_NAME = "daily-repayment-reminders"
        private val REMIND_AT: LocalTime = LocalTime.of(9, 0)
        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

        /** Daily, first run at the next 9:00. KEEP: calling this on every launch doesn't reset it. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(REMIND_AT)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<RepaymentReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun notify(context: Context, summary: PersonSummary, due: LocalDate, today: LocalDate) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            ensureChannel(context)
            val person = summary.person
            val open = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PERSON_ID, person.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val pending = PendingIntent.getActivity(
                context,
                person.id.toInt(),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val whenText = if (due == today) "Due today." else "Overdue since ${due.format(DATE)}."
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle("${person.name} owes you ${formatMoney(summary.outstandingMinor)}")
                .setContentText("$whenText Tap to send them a reminder.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
            // One notification per friend, replaced rather than stacked.
            NotificationManagerCompat.from(context).notify(person.id.toInt(), notification)
        }

        private fun ensureChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Repayment reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "When money a friend owes you is due" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
