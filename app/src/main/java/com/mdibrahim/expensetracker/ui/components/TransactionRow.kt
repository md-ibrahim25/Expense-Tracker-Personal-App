package com.mdibrahim.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mdibrahim.expensetracker.core.model.Account
import com.mdibrahim.expensetracker.core.model.Category
import com.mdibrahim.expensetracker.core.model.Transaction
import com.mdibrahim.expensetracker.core.model.TransactionDirection
import com.mdibrahim.expensetracker.core.model.TransactionSource
import com.mdibrahim.expensetracker.core.util.formatMoney
import com.mdibrahim.expensetracker.ui.theme.LocalMoneyColors
import com.mdibrahim.expensetracker.ui.theme.MoneyTextStyle
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A transaction plus the names a list row needs, resolved in the ViewModel. */
data class TransactionListItem(
    val transaction: Transaction,
    val accountName: String?,
    val categoryName: String?,
)

/** Joins transactions to their account/category names. Pure; used by ViewModels. */
fun buildListItems(
    transactions: List<Transaction>,
    accounts: List<Account>,
    categories: List<Category>,
): List<TransactionListItem> {
    val accountNames = accounts.associate { it.id to it.name }
    val categoryNames = categories.associate { it.id to it.name }
    return transactions.map { t ->
        TransactionListItem(t, accountNames[t.accountId], t.categoryId?.let(categoryNames::get))
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

@Composable
fun TransactionRow(
    item: TransactionListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null disables long-press (e.g. on Home). */
    onLongClick: (() -> Unit)? = null,
    /** Null = not in selection mode; otherwise whether this row is selected. */
    selected: Boolean? = null,
) {
    val transaction = item.transaction
    val moneyColors = LocalMoneyColors.current
    val isDebit = transaction.direction == TransactionDirection.DEBIT

    // Title: what it was for if the user said, else who it was paid to/from.
    val title = transaction.note?.takeIf { it.isNotBlank() }
        ?: transaction.counterparty
        ?: "Untitled"
    val details = buildAnnotatedString {
        append(transaction.occurredAt.atZone(ZoneId.systemDefault()).format(DATE_FORMAT))
        append(" · ")
        if (item.categoryName != null) {
            append(item.categoryName)
        } else {
            // Text, not just colour, marks it -- readable without colour vision.
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)) {
                append("Needs category")
            }
        }
        // When the title is the user's note, still show who was paid.
        if (transaction.note?.isNotBlank() == true && transaction.counterparty != null) {
            append(" · ${transaction.counterparty}")
        }
        if (transaction.source == TransactionSource.SMS) append(" · SMS")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (selected == true) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected != null) {
            // An icon, not just the background tint, shows the selection state.
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (selected) "Selected" else "Not selected",
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp, end = 12.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            // Sign carries the meaning, colour only reinforces it -- so the row is
            // still readable without colour vision.
            text = (if (isDebit) "-" else "+") + formatMoney(transaction.amountMinor),
            style = MoneyTextStyle,
            color = if (isDebit) moneyColors.expense else moneyColors.income,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
