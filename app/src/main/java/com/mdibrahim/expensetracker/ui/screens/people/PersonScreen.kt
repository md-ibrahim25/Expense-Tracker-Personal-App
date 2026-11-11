package com.mdibrahim.expensetracker.ui.screens.people

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.core.net.toUri
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdibrahim.expensetracker.core.model.DebtKind
import com.mdibrahim.expensetracker.core.model.OpenDebt
import com.mdibrahim.expensetracker.core.model.PersonDetail
import com.mdibrahim.expensetracker.core.model.reminderMessage
import com.mdibrahim.expensetracker.core.util.formatMoney
import com.mdibrahim.expensetracker.ui.components.AnimatedMoney
import com.mdibrahim.expensetracker.ui.theme.LocalMoneyColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(
    onBack: () -> Unit,
    viewModel: PersonViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleted by viewModel.isDeleted.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var recordingCash by remember { mutableStateOf(false) }
    var dueFor by remember { mutableStateOf<OpenDebt?>(null) }
    val context = LocalContext.current

    LaunchedEffect(deleted) { if (deleted) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.detail?.summary?.person?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, contentDescription = "Edit friend") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete friend") }
                },
            )
        },
    ) { padding ->
        val detail = state.detail
        if (state.isLoading || detail == null) {
            Box(Modifier.fillMaxSize().padding(padding)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { OwedHero(detail) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { sendReminder(context, detail) },
                        enabled = detail.summary.outstandingMinor > 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Remind")
                    }
                    OutlinedButton(onClick = { recordingCash = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Payments, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cash repaid")
                    }
                }
            }

            if (detail.summary.openDebts.isNotEmpty()) {
                item { SectionLabel("Still owed") }
                items(detail.summary.openDebts, key = { "open-${it.debt.id}" }) { open ->
                    OpenDebtRow(open, onSetDue = { dueFor = open })
                }
            }

            item { SectionLabel("History") }
            val history = detail.debts.map { HistoryItem.Owed(it.occurredAt, it.description ?: kindLabel(it.kind), it.amountMinor) } +
                detail.repayments.map { HistoryItem.Paid(it.occurredAt, it.id, it.transactionId == null, it.amountMinor) }
            items(history.sortedByDescending { it.at }, key = { it.key }) { item ->
                HistoryRow(item, onDeleteCash = { viewModel.deleteRepayment(it) })
            }
        }
    }

    if (recordingCash) CashRepaymentDialog(onDismiss = { recordingCash = false }, onSave = viewModel::recordCashRepayment)

    dueFor?.let { open ->
        DuePicker(
            initial = open.debt.dueDate,
            onDismiss = { dueFor = null },
            onPick = { viewModel.setDue(open.debt.id, it); dueFor = null },
        )
    }

    if (editing) {
        val person = state.detail?.summary?.person
        PersonFormDialog(
            title = "Edit friend",
            initialName = person?.name.orEmpty(),
            initialPhone = person?.phone.orEmpty(),
            onDismiss = { editing = false },
            onSave = { name, phone -> viewModel.updatePerson(name, phone); editing = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this friend?") },
            text = { Text("Their debts and repayments are removed. Your transactions stay.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.deletePerson() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun kindLabel(kind: DebtKind) = if (kind == DebtKind.LOAN) "Loan" else "Split"

/** WhatsApp straight to their chat when we have a number; otherwise the share sheet. */
private fun sendReminder(context: Context, detail: PersonDetail) {
    val message = reminderMessage(detail.summary)
    val phone = detail.summary.person.phone
    val intent = if (phone != null) {
        Intent(Intent.ACTION_VIEW, "https://wa.me/$phone?text=${Uri.encode(message)}".toUri())
    } else {
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message),
            "Send reminder",
        )
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message), "Send reminder"),
        )
    }
}

@Composable
private fun OwedHero(detail: PersonDetail) {
    val scheme = MaterialTheme.colorScheme
    val owes = detail.summary.outstandingMinor
    val today = LocalDate.now()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)))
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Initial(detail.summary.person.name, size = 40)
            Spacer(Modifier.width(12.dp))
            Text(
                when {
                    owes > 0 -> "${detail.summary.person.name} owes you"
                    owes < 0 -> "You owe ${detail.summary.person.name}"
                    else -> "All settled"
                },
                color = scheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        AnimatedMoney(kotlin.math.abs(owes), style = MaterialTheme.typography.displaySmall, color = scheme.onPrimary)
        detail.summary.nextDue?.let { due ->
            Text(
                if (due < today) "Overdue since ${due.format(SHORT_DATE)}" else "Next due ${due.format(SHORT_DATE)}",
                color = scheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun OpenDebtRow(open: OpenDebt, onSetDue: () -> Unit) {
    val today = LocalDate.now()
    val due = open.debt.dueDate
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(open.debt.description ?: kindLabel(open.debt.kind), style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${kindLabel(open.debt.kind)} · ${open.debt.occurredAt.atZone(ZoneId.systemDefault()).toLocalDate().format(SHORT_DATE)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatMoney(open.remainingMinor), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        AssistChip(
            onClick = onSetDue,
            leadingIcon = { Icon(Icons.Filled.Event, contentDescription = null, Modifier.size(18.dp)) },
            label = {
                Text(
                    when {
                        due == null -> "Set expected date"
                        due < today -> "Overdue · was due ${due.format(SHORT_DATE)}"
                        else -> "Expected by ${due.format(SHORT_DATE)}"
                    },
                    color = if (due != null && due < today) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            },
        )
    }
}

private sealed interface HistoryItem {
    val at: Instant
    val key: String

    data class Owed(override val at: Instant, val label: String, val amountMinor: Long) : HistoryItem {
        override val key = "owed-$at-$label-$amountMinor"
    }

    data class Paid(override val at: Instant, val id: Long, val cash: Boolean, val amountMinor: Long) : HistoryItem {
        override val key = "paid-$id"
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, onDeleteCash: (Long) -> Unit) {
    val colors = LocalMoneyColors.current
    val date = item.at.atZone(ZoneId.systemDefault()).toLocalDate().format(SHORT_DATE)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            when (item) {
                is HistoryItem.Owed -> Text("Owed: ${item.label}")
                is HistoryItem.Paid -> Text(if (item.cash) "Paid back in cash" else "Paid back (UPI)")
            }
            Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (item) {
            is HistoryItem.Owed -> Text("+" + formatMoney(item.amountMinor), color = colors.expense)
            is HistoryItem.Paid -> {
                Text("-" + formatMoney(item.amountMinor), color = colors.income)
                // Only hand-entered cash can be undone here; UPI ones belong to their transaction.
                if (item.cash) {
                    IconButton(onClick = { onDeleteCash(item.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove cash repayment", Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CashRepaymentDialog(onDismiss: () -> Unit, onSave: (String) -> Boolean) {
    var amount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paid back in cash") },
        text = {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it; error = false },
                label = { Text("Amount") },
                prefix = { Text("₹ ") },
                isError = error,
                supportingText = if (error) ({ Text("Enter an amount, like 300") }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(onClick = { if (onSave(amount)) onDismiss() else error = true }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuePicker(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate?) -> Unit) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now().plusDays(7)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(pickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = { onPick(null) }) { Text("No date") } },
    ) { DatePicker(state = pickerState) }
}
