package com.mdibrahim.expensetracker.ui.screens.edit

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mdibrahim.expensetracker.core.model.DebtKind
import com.mdibrahim.expensetracker.core.model.Person
import com.mdibrahim.expensetracker.core.model.TransactionDirection
import com.mdibrahim.expensetracker.core.model.equalShareMinor
import com.mdibrahim.expensetracker.core.util.formatMoney
import com.mdibrahim.expensetracker.core.util.rupeesToMinor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DUE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private enum class LendMode { SPLIT, LEND }

/**
 * "Friends" block on the edit form. Debits: split the bill or lend it. Credits:
 * mark it as a friend paying back.
 */
@Composable
fun LendingSection(state: EditTransactionUiState, viewModel: EditTransactionViewModel) {
    var dialogMode by remember { mutableStateOf<LendMode?>(null) }
    var pickRepayer by remember { mutableStateOf(false) }

    // Reminders are notifications; ask once a due date is first set (Android 13+).
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askForNotificationsIfDue(due: LocalDate?) {
        if (due != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("Friends", style = MaterialTheme.typography.titleSmall)
        }

        if (state.direction == TransactionDirection.DEBIT) {
            if (state.debts.isEmpty()) {
                state.suggestedPerson?.let { suggested ->
                    FilledTonalButton(onClick = { dialogMode = LendMode.LEND }) {
                        Text("Lent to ${suggested.person.name}?")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { dialogMode = LendMode.SPLIT }) {
                        Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Split bill")
                    }
                    OutlinedButton(onClick = { dialogMode = LendMode.LEND }) {
                        Icon(Icons.Filled.Handshake, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Lent")
                    }
                }
            } else {
                state.debts.forEach { debt ->
                    val due = debt.dueDate?.let { " · back by ${it.format(DUE_FORMAT)}" }.orEmpty()
                    Text("${state.personName(debt.personId)} owes ${formatMoney(debt.amountMinor)}$due")
                }
                if (state.debts.first().kind == DebtKind.SPLIT) {
                    rupeesToMinor(state.amountText)?.let { total ->
                        Text(
                            "Your share: ${formatMoney(total - state.debts.sumOf { it.amountMinor })}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row {
                    TextButton(onClick = {
                        dialogMode = if (state.debts.first().kind == DebtKind.LOAN) LendMode.LEND else LendMode.SPLIT
                    }) { Text("Change") }
                    TextButton(onClick = viewModel::clearDebts) { Text("Remove") }
                }
            }
        } else {
            val repayer = state.repaymentPersonId
            if (repayer == null) {
                state.suggestedPerson?.takeIf { it.outstandingMinor > 0 }?.let { s ->
                    FilledTonalButton(onClick = { viewModel.setRepayment(s.person.id) }) {
                        Text("Repayment from ${s.person.name} (owes ${formatMoney(s.outstandingMinor)})")
                    }
                }
                OutlinedButton(onClick = { pickRepayer = true }) { Text("A friend paying you back") }
            } else {
                Text("Repayment from ${state.personName(repayer)}")
                TextButton(onClick = viewModel::clearRepayment) { Text("Remove") }
            }
        }

        state.lendingError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    dialogMode?.let { mode ->
        LendDialog(
            initialMode = mode,
            people = state.people,
            preselected = state.debts.map { it.personId }.ifEmpty { listOfNotNull(state.suggestedPerson?.person?.id) },
            initialDue = state.debts.firstOrNull()?.dueDate,
            totalMinor = rupeesToMinor(state.amountText),
            onAddPerson = viewModel::addPerson,
            onDismiss = { dialogMode = null },
            onConfirm = { chosenMode, personIds, due ->
                dialogMode = null
                if (chosenMode == LendMode.SPLIT) viewModel.applySplit(personIds, due) else viewModel.applyLoan(personIds.single(), due)
                askForNotificationsIfDue(due)
            },
        )
    }

    if (pickRepayer) {
        PersonPickerDialog(
            people = state.people,
            onAddPerson = viewModel::addPerson,
            onDismiss = { pickRepayer = false },
            onPick = { pickRepayer = false; viewModel.setRepayment(it) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LendDialog(
    initialMode: LendMode,
    people: List<Person>,
    preselected: List<Long>,
    initialDue: LocalDate?,
    totalMinor: Long?,
    onAddPerson: (String, (Long) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (LendMode, List<Long>, LocalDate?) -> Unit,
) {
    var mode by remember { mutableStateOf(initialMode) }
    val selected = remember { mutableStateListOf<Long>().apply { addAll(preselected) } }
    var due by remember { mutableStateOf(initialDue) }
    var pickingDate by remember { mutableStateOf(false) }

    fun toggle(id: Long) {
        if (mode == LendMode.LEND) {
            selected.clear(); selected.add(id)
        } else if (id in selected) selected.remove(id) else selected.add(id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == LendMode.SPLIT) "Split the bill" else "Money lent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = mode == LendMode.SPLIT,
                        onClick = { mode = LendMode.SPLIT },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Split") }
                    SegmentedButton(
                        selected = mode == LendMode.LEND,
                        onClick = {
                            mode = LendMode.LEND
                            selected.firstOrNull()?.let { first -> selected.clear(); selected.add(first) }
                        },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Lent") }
                }

                Text(
                    if (mode == LendMode.SPLIT) "Who shared it with you?" else "Who did you lend it to?",
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEach { person ->
                        FilterChip(
                            selected = person.id in selected,
                            onClick = { toggle(person.id) },
                            label = { Text(person.name) },
                        )
                    }
                }
                NewPersonField(onAdd = { name -> onAddPerson(name) { id -> toggle(id) } })

                AssistChip(
                    onClick = { pickingDate = true },
                    label = { Text(due?.let { "Expected back by ${it.format(DUE_FORMAT)}" } ?: "Expected back by… (optional)") },
                    leadingIcon = { Icon(Icons.Filled.Event, contentDescription = null, Modifier.size(18.dp)) },
                )

                if (totalMinor != null && selected.isNotEmpty()) {
                    val preview = if (mode == LendMode.SPLIT) {
                        val share = equalShareMinor(totalMinor, selected.size)
                        "Each friend owes ${formatMoney(share)} · your share ${formatMoney(totalMinor - share * selected.size)}"
                    } else {
                        "They owe you ${formatMoney(totalMinor)}"
                    }
                    Text(preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(mode, selected.toList(), due) }, enabled = selected.isNotEmpty()) {
                Text("Done")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickingDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (due ?: LocalDate.now().plusDays(7))
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    due = pickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { due = null; pickingDate = false }) { Text("No date") }
            },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun PersonPickerDialog(
    people: List<Person>,
    onAddPerson: (String, (Long) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Who's paying you back?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                people.forEach { person ->
                    TextButton(onClick = { onPick(person.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(person.name, modifier = Modifier.fillMaxWidth())
                    }
                }
                NewPersonField(onAdd = { name -> onAddPerson(name, onPick) })
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NewPersonField(onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("New friend's name") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(
            onClick = { onAdd(name.trim()); name = "" },
            enabled = name.isNotBlank(),
        ) { Icon(Icons.Filled.Add, contentDescription = "Add friend") }
    }
}
