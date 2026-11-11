package com.mdibrahim.expensetracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
) {
    val transaction = item.transaction
    val moneyColors = LocalMoneyColors.current
    val isDebit = transaction.direction == TransactionDirection.DEBIT

    val details = listOfNotNull(
        transaction.occurredAt.atZone(ZoneId.systemDefault()).format(DATE_FORMAT),
        item.categoryName ?: "Uncategorised",
        item.accountName,
        "SMS".takeIf { transaction.source == TransactionSource.SMS },
    ).joinToString(" · ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.note?.takeIf { it.isNotBlank() } ?: "Untitled",
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
