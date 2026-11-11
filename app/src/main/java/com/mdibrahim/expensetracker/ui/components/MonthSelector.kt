package com.mdibrahim.expensetracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The month being viewed, shared by Home and Transactions so switching tabs keeps
 * the same month. Process-scoped UI state; it resets to the current month on restart.
 */
@Singleton
class SelectedMonth @Inject constructor() {
    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    fun previous() = _month.update { it.minusMonths(1) }

    /** Never moves past the current month -- there's nothing to track in the future. */
    fun next() = _month.update { if (it < YearMonth.now()) it.plusMonths(1) else it }
}

/** [start, end) of this month in epoch millis, in the device's time zone. */
data class MonthRange(val startMillis: Long, val endMillis: Long)

fun YearMonth.epochRange(zone: ZoneId = ZoneId.systemDefault()) = MonthRange(
    startMillis = atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    endMillis = plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
)

private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
fun MonthSelector(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(month.format(MONTH_FORMAT), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext, enabled = month < YearMonth.now()) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}
