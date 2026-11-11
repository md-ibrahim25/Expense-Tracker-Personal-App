package com.mdibrahim.expensetracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/** A transaction plus what a list row needs, resolved in the ViewModel. */
data class TransactionListItem(
    val transaction: Transaction,
    val accountName: String?,
    val category: Category?,
) {
    val categoryName: String? get() = category?.name
}

/** Joins transactions to their account and category. Pure; used by ViewModels. */
fun buildListItems(
    transactions: List<Transaction>,
    accounts: List<Account>,
    categories: List<Category>,
): List<TransactionListItem> {
    val accountNames = accounts.associate { it.id to it.name }
    val categoriesById = categories.associateBy { it.id }
    return transactions.map { t ->
        TransactionListItem(t, accountNames[t.accountId], t.categoryId?.let(categoriesById::get))
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

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
    val rowColor by animateColorAsState(
        if (selected == true) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "rowSelection",
    )

    // Title: what it was for if the user said, else who it was paid to/from.
    val title = transaction.note?.takeIf { it.isNotBlank() }
        ?: transaction.counterparty
        ?: "Untitled"
    val details = buildAnnotatedString {
        if (item.category != null) {
            append(item.category.name)
        } else {
            // Text, not just colour, marks it -- readable without colour vision.
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                append("Needs category")
            }
        }
        append(" · ")
        append(transaction.occurredAt.atZone(ZoneId.systemDefault()).format(TIME_FORMAT))
        // When the title is the user's note, still show who was paid.
        if (transaction.note?.isNotBlank() == true && transaction.counterparty != null) {
            append(" · ${transaction.counterparty}")
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(rowColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(category = item.category, selected = selected == true)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
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
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
            Text(
                // Sign carries the meaning, colour only reinforces it -- so the row is
                // still readable without colour vision.
                text = (if (isDebit) "-" else "+") + formatMoney(transaction.amountMinor),
                style = MoneyTextStyle,
                color = if (isDebit) moneyColors.expense else moneyColors.income,
            )
            if (transaction.source == TransactionSource.SMS) {
                Text(
                    "SMS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
