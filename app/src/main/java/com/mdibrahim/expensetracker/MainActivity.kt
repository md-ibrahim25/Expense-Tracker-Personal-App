package com.mdibrahim.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mdibrahim.expensetracker.ui.navigation.AppScaffold
import com.mdibrahim.expensetracker.ui.theme.ExpenseTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draws behind the system bars, which is required behaviour on Android 15+.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Set when opened from a repayment-reminder notification.
        val openPersonId = intent.getLongExtra(EXTRA_PERSON_ID, -1L).takeIf { it > 0 }

        setContent {
            ExpenseTrackerTheme {
                AppScaffold(openPersonId = openPersonId)
            }
        }
    }

    companion object {
        const val EXTRA_PERSON_ID = "personId"
    }
}
